package com.handymath.app;

import android.content.*;
import android.os.*;
import android.widget.*;

import org.json.*;

import java.io.*;

public final class ReviewActivity extends PageActivity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        page("作答历史");
        try (LearningDb db = new LearningDb(this)) {
            JSONObject a = db.attempt(getIntent().getLongExtra("attemptId", -1));
            if (a == null) {
                label("记录不存在");
                return;
            }
            JSONObject q = a.getJSONObject("question");
            String location = a.optString("bankLocation");
            BankRepository bank = null;
            try { bank = BankRepository.loadLocation(this, location); }
            catch (Exception missing) {
                label("原题库已移除，已保存的文字、作答笔迹和点评仍可查看。重新导入题库后可继续练习。");
            }
            label(
                    q.getString("title")
                            + " · "
                            + a.getString("day")
                            + " · "
                            + a.getDouble("score")
                            + "分\n截至该次提交的累计用时 "
                            + LearningDb.duration(a.optLong("activeMs")));
            math(q.optString("questionMarkdown"));
            JSONArray pictures = q.optJSONArray("questionImages");
            if (bank != null && pictures != null) {
                for (int i = 0; i < pictures.length(); i++) image(bank, pictures.getString(i));
            } else if (bank != null && !q.optString("questionImage").isEmpty())
                image(bank, q.getString("questionImage"));
            math(
                    "### 识别到的作答\n"
                            + a.optString("studentWork")
                            + "\n### 智能点评\n"
                            + a.optString("feedback")
                            + "\n### 薄弱点\n"
                            + a.optJSONArray("weaknesses"));
            panel.addView(new ThinkingPanel(this, ThinkingPanel.State.from(a.optJSONArray("thinking"))));
            if (a.has("ink")) {
                InkView ink = new InkView(this, StylusAdapter.Registry.select());
                panel.addView(ink, new LinearLayout.LayoutParams(-1, dp(360)));
                ink.restore(a.getJSONObject("ink"));
                ink.post(ink::fitInk);
                ink.viewOnly = true;
                label("原始矢量笔迹：手指拖动、双指缩放查看。");
            }
            math("### 参考解析\n" + q.optString("answerMarkdown"));
            JSONArray images = q.optJSONArray("answerImages");
            if (bank != null && images != null)
                for (int i = 0; i < images.length(); i++) image(bank, images.getString(i));
            JSONObject source = a;
            if (bank != null) {
              action(
                    "针对本题生成近似题",
                    () ->
                            startActivity(
                                    new Intent(this, PracticeActivity.class)
                                            .putExtra("attemptId", source.optLong("id"))));
              action(
                    "回到此题继续练习",
                    () ->
                            startActivity(
                                    new Intent(this, MainActivity.class)
                                            .setFlags(
                                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            .putExtra("bankLocation", location)
                                            .putExtra("questionId", q.optString("id"))));
            }
        } catch (Exception e) {
            failure(e);
        }
    }

    private void image(BankRepository bank, String path) throws Exception {
        ImageView image = new ImageView(this);
        image.setImageBitmap(bank.image(path));
        image.setAdjustViewBounds(true);
        panel.addView(image, new LinearLayout.LayoutParams(-1, -2));
        image.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, ImageViewerActivity.class)
                                        .putExtra("bank", bank.location())
                                        .putExtra("image", path)));
    }
}
