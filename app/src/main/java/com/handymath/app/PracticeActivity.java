package com.handymath.app;

import android.content.*;
import android.os.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class PracticeActivity extends PageActivity {
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    JSONObject source;
    EditText topic;
    boolean busy;
    String practiceId = "smart-practice", practiceSubject = "数学";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        page("智能加练");
        try (LearningDb db = new LearningDb(this)) {
            long id = getIntent().getLongExtra("attemptId", -1);
            JSONArray attempts = db.attempts(false);
            for (int i = 0; i < attempts.length(); i++)
                if (attempts.getJSONObject(i).getLong("id") == id) {
                    source = attempts.getJSONObject(i);
                    break;
                }
        } catch (Exception e) {
            failure(e);
        }
        if (source == null && getIntent().hasExtra("questionId"))
            try {
                String location = getIntent().getStringExtra("bankLocation");
                BankRepository bank = BankRepository.loadLocation(this, location);
                for (BankRepository.Question q : bank.questions)
                    if (q.id.equals(getIntent().getStringExtra("questionId"))) {
                        source =
                                new JSONObject(getIntent().getStringExtra("result"))
                                        .put("bankLocation", bank.location())
                                        .put("bank", bank.id)
                                        .put("subject", bank.subject)
                                        .put("q", q.id)
                                        .put("question", LearningDb.snapshot(q));
                        break;
                    }
            } catch (Exception e) {
                failure(e);
            }
        if (source != null) {
            JSONObject q = source.optJSONObject("question");
            label("原题：" + q.optString("title"));
            math("**本次薄弱点**\n" + source.optJSONArray("weaknesses"));
        }
        label("结合原题考点、已暴露弱项生成一道相近难度的解答题。生成成功后保存到「智能加练」题库。");
        topic = new EditText(this);
        topic.setHint("补充指定考点或要求（可留空）");
        topic.setText(getIntent().getStringExtra("topic"));
        panel.addView(topic);
        action("生成并复核近似题", this::generate);
    }

    private void generate() {
        if (busy) return;
        final ProviderSettings config;
        try {
            config = ProviderSettings.load(this);
            config.validate();
            if (!config.visionVerified) throw new IOException("请先在设置页检测多模态提供商");
            if (source == null && topic.getText().toString().trim().isEmpty())
                throw new IOException("请指定考点");
        } catch (Exception e) {
            failure(e);
            return;
        }
        lastError = "";
        String desired = topic.getText().toString();
        practiceSubject =
                source != null
                        ? source.optString("subject", "数学")
                        : desired.startsWith("英语") ? "英语" : desired.startsWith("政治") ? "政治" : "其它";
        practiceId =
                practiceSubject.equals("数学")
                        ? "smart-practice"
                        : "smart-practice-" + Integer.toHexString(practiceSubject.hashCode());
        busy = true;
        loading("正在生成题目并独立验算…");
        worker.execute(
                () -> {
                    UsageLedger.scope.set(new String[] {source == null ? practiceId : source.optString("bank"), source == null ? "" : source.optString("q")});
                    try {
                        JSONArray context = new JSONArray();
                        context.put(TutorClient.text("指定考点或要求：" + desired));
                        if (source != null) {
                            JSONObject q = source.getJSONObject("question");
                            context.put(
                                    TutorClient.text(
                                            "原题数据："
                                                    + q
                                                    + "；学生答案："
                                                    + source.optString("studentWork")
                                                    + "；点评："
                                                    + source.optString("feedback")
                                                    + "；弱项："
                                                    + source.optJSONArray("weaknesses")
                                                    + "；分类："
                                                    + source.optJSONObject("tags")));
                            String loc = source.optString("bankLocation");
                            BankRepository bank = BankRepository.loadLocation(this, loc);
                            List<String> images = new ArrayList<>();
                            JSONArray questionPictures = q.optJSONArray("questionImages");
                            if (questionPictures != null) {
                                for (int i = 0; i < questionPictures.length(); i++)
                                    images.add(questionPictures.getString(i));
                            } else if (!q.optString("questionImage").isEmpty())
                                images.add(q.getString("questionImage"));
                            JSONArray answers = q.optJSONArray("answerImages");
                            if (answers != null)
                                for (int i = 0; i < answers.length(); i++)
                                    images.add(answers.getString(i));
                            for (String path : images) {
                                android.graphics.Bitmap image = bank.image(path);
                                try {
                                    context.put(TutorClient.image(image));
                                } finally {
                                    image.recycle();
                                }
                            }
                        }
                        try (LearningDb db = new LearningDb(this)) {
                            JSONArray previous = db.attempts(true);
                            int included = 0;
                            for (int i = 0; i < previous.length() && included < 6; i++) {
                                JSONObject a = previous.getJSONObject(i);
                                boolean relevant =
                                        source != null
                                                ? a.optString("bank")
                                                                .equals(source.optString("bank"))
                                                        && a.optString("q")
                                                                .equals(source.optString("q"))
                                                : a.optJSONObject("tags") != null
                                                        && a.getJSONObject("tags")
                                                                .toString()
                                                                .contains(
                                                                        desired.replaceFirst(
                                                                                "^(知识点|考点|题型)：",
                                                                                ""));
                                if (relevant) {
                                    context.put(
                                            TutorClient.text(
                                                    "该考点的真实历史错误：学生作答="
                                                            + a.optString("studentWork")
                                                            + "；点评="
                                                            + a.optString("feedback")
                                                            + "；弱项="
                                                            + a.optJSONArray("weaknesses")));
                                    included++;
                                }
                            }
                        }
                        JSONObject generated = TutorClient.generate(config, context);
                        String id = "practice-" + UUID.randomUUID();
                        generated
                                .put("id", id)
                                .put("type", "solution")
                                .put("topic", generated.getJSONArray("examPoints").getString(0));
                        saveQuestion(generated);
                        try (LearningDb db = new LearningDb(this)) {
                            JSONObject tags = new JSONObject();
                            for (String field :
                                    new String[] {
                                        "knowledgePoints",
                                        "examPoints",
                                        "questionType",
                                        "difficulty"
                                    }) tags.put(field, generated.get(field));
                            db.tags(practiceId, id, tags);
                        }
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed()) return;
                                    busy = false;
                                    loaded();
                                    page("加练题已保存");
                                    label(generated.optString("title"));
                                    math(generated.optString("questionMarkdown"));
                                    action(
                                            "开始作答",
                                            () ->
                                                    startActivity(
                                                            new Intent(this, MainActivity.class)
                                                                    .setFlags(
                                                                            Intent
                                                                                            .FLAG_ACTIVITY_CLEAR_TOP
                                                                                    | Intent
                                                                                            .FLAG_ACTIVITY_SINGLE_TOP)
                                                                    .putExtra(
                                                                            "bankLocation",
                                                                            practiceId)
                                                                    .putExtra("questionId", id)));
                                    action(
                                            "查看参考解析",
                                            () -> math(generated.optString("answerMarkdown")));
                                    action(
                                            "再生成一道",
                                            () -> {
                                                page("智能加练");
                                                topic = new EditText(this);
                                                topic.setText(desired);
                                                panel.addView(topic);
                                                action("生成并复核", this::generate);
                                            });
                                });
                    } catch (Exception e) {
                        runOnUiThread(
                                () -> {
                                    if (!isDestroyed()) {
                                        busy = false;
                                        failure(e);
                                    }
                                });
                    } finally { UsageLedger.scope.remove(); }
                });
    }

    private static final Object SAVE_LOCK = new Object();

    private void saveQuestion(JSONObject question) throws Exception {
        synchronized (SAVE_LOCK) {
            File dir = new File(getFilesDir(), "banks/" + practiceId);
            dir.mkdirs();
            android.util.AtomicFile file = new android.util.AtomicFile(new File(dir, "bank.json"));
            JSONObject bank =
                    file.getBaseFile().exists()
                            ? new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8))
                            : new JSONObject()
                                    .put("version", 1)
                                    .put("id", practiceId)
                                    .put("subject", practiceSubject)
                                    .put("title", practiceSubject + " · 智能加练")
                                    .put("questions", new JSONArray());
            if (bank.getJSONArray("questions").length() >= 1000)
                throw new IOException("加练题库已达到1000题上限");
            bank.getJSONArray("questions").put(question);
            FileOutputStream out = file.startWrite();
            try {
                out.write(bank.toString().getBytes(StandardCharsets.UTF_8));
                file.finishWrite(out);
            } catch (Exception e) {
                file.failWrite(out);
                throw e;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!busy) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
