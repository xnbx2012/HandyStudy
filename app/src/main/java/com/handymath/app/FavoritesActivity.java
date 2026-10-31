package com.handymath.app;

import android.app.AlertDialog;
import android.content.Intent;
import org.json.*;

/** Bookmarks retain stable bank/question identities independently of answer drafts. */
public final class FavoritesActivity extends PageActivity {
  @Override protected void onResume() {
    super.onResume();
    refresh();
  }

  private void refresh() {
    page("我的收藏");
    try (LearningDb db = new LearningDb(this)) {
      JSONArray rows = db.favorites();
      label(rows.length() == 0 ? "还没有收藏题目。在题目页点击「☆ 收藏」即可添加。" : "共 " + rows.length() + " 道收藏题目 · 点击继续作答");
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i);
        section(row.getString("title"), row.getString("bankTitle"));
        action("继续作答  ›", () -> {
          try {
            BankRepository bank = BankRepository.loadLocation(this, row.optString("location"));
            boolean exists = false;
            for (BankRepository.Question q : bank.questions) if (q.id.equals(row.optString("q"))) { exists = true; break; }
            if (!exists) throw new IllegalStateException("原题已不在题库中，可以取消收藏。");
            startActivity(new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("bankLocation", row.optString("location")).putExtra("questionId", row.optString("q")));
          } catch (Exception e) { failure(e); }
        });
        action("取消收藏", () -> new AlertDialog.Builder(this).setTitle("取消收藏这道题？")
            .setNegativeButton("保留", null).setPositiveButton("取消收藏", (dialog, which) -> {
              try (LearningDb edit = new LearningDb(this)) { edit.unfavorite(row.optString("bank"), row.optString("q")); }
              refresh();
            }).show());
      }
    } catch (Exception e) { failure(e); }
  }
}
