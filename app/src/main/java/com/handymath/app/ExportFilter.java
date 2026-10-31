package com.handymath.app;

import android.app.*;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

final class ExportFilter {
  String bank = "", start = "", end = "";

  boolean all() {
    return bank.isEmpty() && start.isEmpty() && end.isEmpty();
  }

  boolean accepts(JSONObject row) {
    if (!bank.isEmpty() && !bank.equals(row.optString("bank"))) return false;
    String day = row.optString("day", "");
    if (day.isEmpty() && row.optLong("at", 0) > 0) day = LearningDb.day(row.optLong("at"));
    if ((!start.isEmpty() || !end.isEmpty()) && day.isEmpty()) return false;
    return (start.isEmpty() || day.compareTo(start) >= 0)
        && (end.isEmpty() || day.compareTo(end) <= 0);
  }

  JSONArray apply(JSONArray rows) {
    JSONArray out = new JSONArray();
    for (int i = 0; i < rows.length(); i++)
      if (accepts(rows.optJSONObject(i))) out.put(rows.optJSONObject(i));
    return out;
  }

  JSONObject json() throws JSONException {
    return new JSONObject().put("bank", bank).put("start", start).put("end", end);
  }

  interface Selected {
    void run(ExportFilter filter);
  }

  static void choose(Activity a, String current, Selected selected) {
    try {
      List<BankRepository.Entry> banks = BankRepository.catalog(a);
      LinearLayout box = new LinearLayout(a);
      box.setOrientation(LinearLayout.VERTICAL);
      int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
      box.setPadding(pad, pad, pad, pad);
      TextView note = new TextView(a);
      note.setText("日期包含起止当天，留空表示不限。筛选导出仅包含匹配的记录、笔迹、题库与可归属日志；旧日志无题库信息时仅在全部题库下导出。");
      box.addView(note);
      List<String> names = new ArrayList<>();
      names.add("全部题库");
      int initial = 0;
      for (int i = 0; i < banks.size(); i++) {
        names.add(banks.get(i).title);
        if (banks.get(i).id.equals(current)) initial = i + 1;
      }
      Spinner bank = new Spinner(a);
      bank.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, names));
      bank.setSelection(initial);
      box.addView(bank);
      EditText start = new EditText(a), end = new EditText(a);
      start.setHint("开始日期 YYYY-MM-DD（不限可留空）");
      end.setHint("结束日期 YYYY-MM-DD（不限可留空）");
      start.setSingleLine();
      end.setSingleLine();
      box.addView(start);
      box.addView(end);
      TextView error = new TextView(a);
      error.setTextColor(0xffb44747);
      box.addView(error);
      AlertDialog dialog =
          new AlertDialog.Builder(a)
              .setTitle("导出范围")
              .setView(box)
              .setNegativeButton("取消", null)
              .setNeutralButton("全部导出", (d, n) -> selected.run(new ExportFilter()))
              .setPositiveButton("导出所选范围", null)
              .create();
      dialog.setOnShowListener(
          d ->
              dialog
                  .getButton(AlertDialog.BUTTON_POSITIVE)
                  .setOnClickListener(
                      v -> {
                        try {
                          ExportFilter filter = new ExportFilter();
                          filter.start = start.getText().toString().trim();
                          filter.end = end.getText().toString().trim();
                          if (!filter.start.isEmpty())
                            filter.start = LocalDate.parse(filter.start).toString();
                          if (!filter.end.isEmpty())
                            filter.end = LocalDate.parse(filter.end).toString();
                          if (!filter.start.isEmpty()
                              && !filter.end.isEmpty()
                              && filter.start.compareTo(filter.end) > 0)
                            throw new IllegalArgumentException();
                          int index = bank.getSelectedItemPosition();
                          filter.bank = index == 0 ? "" : banks.get(index - 1).id;
                          dialog.dismiss();
                          selected.run(filter);
                        } catch (Exception invalid) {
                          error.setText("请输入有效日期（如 2026-10-15），结束日期不能早于开始日期。");
                        }
                      }));
      dialog.show();
    } catch (Exception e) {
      Toast.makeText(a, "无法读取导出题库：" + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }
}
