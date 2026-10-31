package com.handymath.app;

import android.os.*;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class LogsActivity extends PageActivity {
    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        page("LLM 对话日志");
        label("仅列出开启日志开关后保存的请求；图片附件会随 ZIP 一同导出。");
        File[] dirs = new File(getFilesDir(), "llm-logs").listFiles();
        if (dirs == null || dirs.length == 0) {
            label("暂无日志");
            return;
        }
        java.util.Arrays.sort(dirs, (a, c) -> Long.compare(c.lastModified(), a.lastModified()));
        for (File f : dirs)
            try {
                JSONObject o =
                        new JSONObject(
                                new String(
                                        BankRepository.read(
                                                new FileInputStream(
                                                        new File(f, "conversation.json"))),
                                        StandardCharsets.UTF_8));
                action(
                        new java.text.SimpleDateFormat(
                                                "yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                                        .format(new java.util.Date(o.getLong("at")))
                                + " · "
                                + o.getJSONObject("request").optString("model"),
                        () -> {
                            page("对话详情");
                            renderReply(o.optString("response"));
                            if (!o.optString("error").isEmpty())
                                math("### 请求错误\n" + o.optString("error"));
                            action(
                                    "查看原始回复 JSON",
                                    () ->
                                            TextViewHelper.show(
                                                    this, panel, o.optString("response")));
                            label("请求（附件路径对应 ZIP 中的图片）");
                            TextViewHelper.show(this, panel, o.optJSONObject("request").toString());
                        });
            } catch (Exception ignored) {
            }
    }

    private void renderReply(String raw) {
        try {
            JSONObject response = new JSONObject(raw);
            label("模型回复");
            java.util.Iterator<String> keys = response.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = response.get(key);
                if (value instanceof String) math("**" + key + "**\n\n" + value);
                else if (value instanceof JSONArray) {
                    JSONArray items = (JSONArray) value;
                    StringBuilder text = new StringBuilder("**" + key + "**\n");
                    for (int i = 0; i < items.length(); i++)
                        text.append("\n- ").append(items.optString(i));
                    math(text.toString());
                } else label(key + "：" + value);
            }
        } catch (Exception e) {
            math("### 模型回复\n" + raw);
        }
    }

    static class TextViewHelper {
        static void show(android.content.Context c, android.widget.LinearLayout p, String s) {
            android.widget.TextView t = new android.widget.TextView(c);
            t.setText(s);
            t.setTextIsSelectable(true);
            p.addView(t);
        }
    }
}
