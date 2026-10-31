package com.handymath.app;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * HTTP/SSE boundary shared by every tutoring request. Injectable connection only for
 * instrumentation.
 */
final class LlmTransport {
    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws Exception;
    }

    interface Partial {
        void text(String raw);
        default void reasoning(String raw, boolean finished) {}
    }

    static ConnectionFactory connections = url -> (HttpURLConnection) url.openConnection();

    static String call(ProviderSettings config, JSONObject body, Partial listener)
            throws Exception {
        HttpURLConnection conn = connections.open(new URL(config.endpoint()));
        JSONObject usage = null;
        String result = "", error = "";
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(ModelThinking.needsBudget(config) ? 600000 : 120000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + config.key);
        conn.setRequestProperty("Content-Type", "application/json");
        try {
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status != 200)
                throw new IOException(
                        switch (status) {
                            case 400 -> config.reasoningEffort.equals("default")
                                    ? "模型未接受请求参数，请检查模型配置及输入内容"
                                    : "模型未接受请求参数，请检查模型支持的思考档位，或选择「模型默认」后重试";
                            case 401, 403 -> "提供商鉴权失败，请检查 API Key 与模型权限";
                            case 429 -> "提供商限流或余额不足";
                            default -> "提供商请求失败（HTTP " + status + "）";
                        });
            if (body.optBoolean("stream")) {
                StringBuilder text = new StringBuilder();
                StringBuilder reasoning = new StringBuilder();
                long lastReasoningUpdate = 0;
                boolean reasoningFinished = false;
                boolean completed = false;
                String finish = "";
                try (BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder event = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (event.length() == 0) continue;
                            String data = event.toString();
                            event.setLength(0);
                            if (data.equals("[DONE]")) {
                                completed = true;
                                break;
                            }
                            JSONObject chunk = new JSONObject(data);
                            if (chunk.has("error")) throw new IOException("提供商流式输出中断");
                            if (chunk.optJSONObject("usage") != null)
                                usage = chunk.getJSONObject("usage");
                            JSONArray choices = chunk.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                if (!choice.isNull("finish_reason"))
                                    finish = choice.optString("finish_reason");
                                JSONObject delta = choice.optJSONObject("delta");
                                if (delta != null) {
                                    String thought = reasoningText(delta);
                                    if (!thought.isEmpty()) {
                                        reasoning.append(thought);
                                        if (reasoning.length() > 4 * 1024 * 1024) throw new IOException("模型思考内容过大");
                                        long now = System.nanoTime();
                                        if (listener != null && now - lastReasoningUpdate >= 100000000L) {
                                            listener.reasoning(reasoning.toString(), false); lastReasoningUpdate = now;
                                        }
                                    }
                                }
                                if (delta != null && !delta.optString("content").isEmpty() && !delta.isNull("content")) {
                                    if (!reasoningFinished && reasoning.length() > 0) {
                                        if (listener != null) listener.reasoning(reasoning.toString(), true);
                                        reasoningFinished = true;
                                    }
                                    text.append(delta.optString("content"));
                                    if (text.length() > 4 * 1024 * 1024)
                                        throw new IOException("模型输出过大");
                                    if (listener != null) listener.text(text.toString());
                                }
                            }
                        } else if (line.startsWith("data:")) {
                            if (event.length() > 0) event.append('\n');
                            event.append(line.substring(5).stripLeading());
                        }
                    }
                }
                if (finish.equals("length")) {
                    if (!reasoningFinished && reasoning.length() > 0 && listener != null) listener.reasoning(reasoning.toString(), false);
                    throw lengthExceeded(config, body);
                }
                if (!completed && !finish.equals("stop"))
                    throw new IOException("流式连接提前结束，结果尚未完成，请重试");
                if (!reasoningFinished && reasoning.length() > 0 && listener != null)
                    listener.reasoning(reasoning.toString(), true);
                result = text.toString();
            } else {
                JSONObject response =
                        new JSONObject(
                                new String(
                                        BankRepository.read(conn.getInputStream()),
                                        StandardCharsets.UTF_8));
                usage = response.optJSONObject("usage");
                JSONObject choice = response.getJSONArray("choices").getJSONObject(0);
                if ("length".equals(choice.optString("finish_reason")))
                    throw lengthExceeded(config, body);
                result = choice.getJSONObject("message").optString("content", "");
            }
            if (result.trim().isEmpty()) throw new IOException("提供商返回了空内容，请重试");
            ConversationLog.save(config.context, body, result, "");
            return result;
        } catch (SocketTimeoutException e) {
            error = "请求超时，草稿已保留";
            throw new IOException(error);
        } catch (Exception e) {
            error = e.getMessage() == null ? "提供商请求失败" : e.getMessage();
            throw e;
        } finally {
            UsageLedger.record(config, usage, error);
            if (!error.isEmpty()) ConversationLog.save(config.context, body, "", error);
            conn.disconnect();
        }
    }

    private static String reasoningText(JSONObject delta) {
        for (String key : new String[]{"reasoning_content", "reasoning"}) {
            Object value = delta.opt(key);
            if (value instanceof String && !((String)value).isEmpty()) return (String)value;
        }
        StringBuilder text = new StringBuilder();
        JSONArray details = delta.optJSONArray("reasoning_details");
        if (details != null) for (int i = 0; i < details.length(); i++) {
            JSONObject item = details.optJSONObject(i); if (item == null) continue;
            if (item.optString("type").equals("reasoning.text")) text.append(item.optString("text"));
            else if (item.optString("type").equals("reasoning.summary")) text.append(item.optString("summary"));
        }
        return text.toString();
    }

    private static IOException lengthExceeded(ProviderSettings config, JSONObject body) {
        int limit = body.optInt("max_completion_tokens", body.optInt("max_tokens", ModelThinking.outputLimit(config)));
        return new IOException("模型达到本次输出上限（" + limit + " Token），结果不完整，未保存。"
                + "请在设置中提高「模型输出长度上限」，或降低思考强度后重试。");
    }
}
