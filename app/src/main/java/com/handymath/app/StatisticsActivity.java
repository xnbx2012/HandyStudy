package com.handymath.app;

import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class StatisticsActivity extends PageActivity {
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  LearningDb db;
  String scope = "";
  String location = "";
  ExportFilter exportFilter = new ExportFilter();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    page("学习统计");
    db = new LearningDb(this);
    if (b != null) {
      exportFilter.bank = b.getString("exportBank", "");
      exportFilter.start = b.getString("exportStart", "");
      exportFilter.end = b.getString("exportEnd", "");
    }
    selector();
    refresh();
    if (b == null && getIntent().getBooleanExtra("export", false)) export();
  }

  private void refresh() {
    try {
      JSONArray attempts = scoped(db.attempts(false)), times = scoped(db.times());
      long total = 0;
      Set<String> completed = new HashSet<>();
      double scores = 0;
      Map<String, double[]> questionScores = new HashMap<>();
      Map<String, Long> questionTimes = new HashMap<>();
      for (int i = 0; i < times.length(); i++) {
        JSONObject t = times.getJSONObject(i);
        long ms = t.getLong("ms");
        total += ms;
        String key = t.getString("bank") + "/" + t.getString("q");
        questionTimes.put(key, questionTimes.getOrDefault(key, 0L) + ms);
      }
      for (int i = 0; i < attempts.length(); i++) {
        JSONObject a = attempts.getJSONObject(i);
        completed.add(a.getString("bank") + "/" + a.getString("q"));
        scores += a.getDouble("score");
        double[] aggregate =
            questionScores.computeIfAbsent(
                a.getString("bank") + "/" + a.getString("q"), k -> new double[3]);
        aggregate[0] += a.getDouble("score");
        aggregate[1]++;
        if (!a.getBoolean("correct")) aggregate[2]++;
      }
      long completedTime = 0;
      for (String key : completed) completedTime += questionTimes.getOrDefault(key, 0L);
      List<LearningDb.Daily> daily = db.daily(84, scope);
      LearningDb.Daily today = daily.get(daily.size() - 1);
      section("学习概览", "今天完成 " + today.count + " 题 · " + LearningDb.duration(today.ms));
      LinearLayout grid = new LinearLayout(this);
      grid.setOrientation(LinearLayout.VERTICAL);
      panel.addView(grid);
      metricRow(grid, "累计完成", completed.size() + " 题", "学习总时长", LearningDb.duration(total));
      metricRow(
          grid,
          "平均每题",
          LearningDb.duration(completed.isEmpty() ? 0 : completedTime / completed.size()),
          "平均得分",
          String.format(
              Locale.CHINA, "%.1f / 10", attempts.length() == 0 ? 0 : scores / attempts.length()));
      long residence = 0;
      JSONArray stays = scoped(db.times(true));
      for (int i = 0; i < stays.length(); i++) residence += stays.getJSONObject(i).getLong("ms");
      label("页面停留总时长：" + LearningDb.duration(residence) + "（包括提交后的查看与模型等待，单独统计）");
      usage();
      if (!location.isEmpty()) {
        BankRepository bank = BankRepository.loadLocation(this, location);
        if (!bank.knowledge.nodes.isEmpty()) {
          section("知识体系与掌握度", "雷达刻度 0–10 分，按每题最新成绩汇总；未练节点不画分数，点击图上查看详情");
          panel.addView(
              new KnowledgeChart(this, bank, attempts, true),
              new LinearLayout.LayoutParams(-1, dp(300)));
          section("题目数量分布", "按一级知识节点统计，跨知识点题目可能重复计入");
          panel.addView(
              new KnowledgeChart(this, bank, attempts, false),
              new LinearLayout.LayoutParams(-1, dp(280)));
          action(
              "查看知识思维导图 / 分层弱项 / 定向练习",
              () ->
                  startActivity(
                      new Intent(this, KnowledgeExplorerActivity.class)
                          .putExtra("bankLocation", location)));
        }
      }
      label("完成题数按每天不同题目去重；均分按全部提交计算。平均每题为已提交题目的累计有效时长 / 已提交题数。旧版本记录没有时间与提交历史，从本版本开始累计。");
      chart("最近84天 · 完成题目热力图（点击查看日期）", daily, "heat", 190);
      List<LearningDb.Daily> month = db.daily(30, scope);
      chart("每天完成题数", month, "count", 180);
      chart("每天学习时长（分钟）", month, "time", 180);
      chart("每天平均得分（无提交时断开）", month, "score", 180);
      action("导出学习记录 / 笔迹 / LLM 日志 ZIP", this::export);
      section("薄弱点与加练", "按知识点、考点与题型梳理 · 点击分类生成加练");
      for (String field : new String[] {"knowledgePoints", "examPoints", "questionType"})
        categories(attempts, field);
      section("答题足迹", "点击进入加练 · 长按查看用时、成绩与历史");
      if (attempts.length() == 0) label("从第一道题开始，记录你的每一点进步。");
      AdaptiveGrid footprint = tiles();
      Set<String> shown = new HashSet<>();
      for (int i = 0; i < attempts.length(); i++) {
        JSONObject a = attempts.getJSONObject(i);
        String key = a.getString("bank") + "/" + a.getString("q");
        if (!shown.add(key)) continue;
        double[] aggregate = questionScores.get(key);
        tile(
            footprint,
            a.getJSONObject("question").getString("title"),
            aggregate[0] / aggregate[1],
            "提交 "
                + (int) aggregate[1]
                + " 次 · 错误 "
                + (int) aggregate[2]
                + " 次\n平均得分 "
                + String.format(Locale.CHINA, "%.1f", aggregate[0] / aggregate[1])
                + " / 10\n累计用时 "
                + LearningDb.duration(questionTimes.getOrDefault(key, 0L))
                + "\n最近提交 "
                + a.optString("day")
                + "\n最近得分 "
                + a.optDouble("score")
                + " / 10",
            () -> practice(a),
            () -> review(a));
      }
      section("错题回顾", "保留每次错误提交，包括后来已改正的题目");
      JSONArray wrong = scoped(db.attempts(true));
      if (wrong.length() == 0) label("暂无错题记录");
      AdaptiveGrid mistakes = tiles();
      for (int i = 0; i < wrong.length(); i++) {
        JSONObject a = wrong.getJSONObject(i);
        tile(
            mistakes,
            a.getJSONObject("question").getString("title"),
            a.getDouble("score"),
            "提交日期 "
                + a.optString("day")
                + "\n得分 "
                + a.optDouble("score")
                + " / 10\n"
                + a.optString("feedback"),
            () -> practice(a),
            () -> review(a));
      }
      section("专属加练", "选择一个考点，针对性巩固");
      EditText topic = new EditText(this);
      topic.setHint("例如：洛必达法则 / 等价无穷小");
      topic.setTextSize(15);
      topic.setTextColor(INK);
      topic.setPadding(dp(14), dp(12), dp(14), dp(12));
      topic.setBackground(surface(0xfff1f5f4, 12));
      panel.addView(topic);
      action(
          "生成该考点加练题",
          () -> {
            if (!topic.getText().toString().trim().isEmpty()) practice(topic.getText().toString());
          });
    } catch (Exception e) {
      failure(e);
    }
  }

  private void selector() {
    try {
      java.util.List<BankRepository.Entry> banks = BankRepository.catalog(this);
      java.util.List<String> names = new ArrayList<>();
      names.add("全部题集 · 全部学科");
      for (BankRepository.Entry b : banks) names.add(b.subject + " · " + b.title);
      Spinner selector = new Spinner(this);
      selector.setAdapter(
          new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
      content.addView(selector);
      selector.setOnItemSelectedListener(
          new AdapterView.OnItemSelectedListener() {
            boolean initial = true;

            public void onNothingSelected(AdapterView<?> v) {}

            public void onItemSelected(AdapterView<?> p, android.view.View v, int n, long id) {
              if (initial) {
                initial = false;
                return;
              }
              scope = n == 0 ? "" : banks.get(n - 1).id;
              location = n == 0 ? "" : banks.get(n - 1).location;
              while (content.getChildCount() > 1) content.removeViewAt(1);
              panel = content;
              refresh();
            }
          });
    } catch (Exception e) {
      failure(e);
    }
  }

  private JSONArray scoped(JSONArray source) {
    JSONArray out = new JSONArray();
    for (int i = 0; i < source.length(); i++) {
      JSONObject o = source.optJSONObject(i);
      if (scope.isEmpty() || scope.equals(o.optString("bank"))) out.put(o);
    }
    return out;
  }

  private void usage() throws Exception {
    section("模型用量与费用", "输入包含缓存命中；费用按请求时单价估算，不同币种分别统计");
    try (UsageLedger ledger = new UsageLedger(this)) {
      JSONArray rows = ledger.rows(scope);
      Map<String, double[]> sums = new LinkedHashMap<>();
      for (int i = 0; i < rows.length(); i++) {
        JSONObject r = rows.getJSONObject(i);
        String key =
            r.optString("name") + " · " + r.optString("model") + " · " + r.optString("currency");
        double[] n = sums.computeIfAbsent(key, k -> new double[9]);
        String[] cols = {"prompt", "output", "cache", "cost"};
        for (int j = 0; j < 4; j++) {
          if (r.isNull(cols[j])) n[j + 4]++;
          else n[j] += r.optDouble(cols[j]);
        }
        n[8]++;
      }
      if (sums.isEmpty()) label("暂无模型调用记录");
      for (Map.Entry<String, double[]> e : sums.entrySet()) {
        double[] n = e.getValue();
        label(e.getKey()).setTypeface(null, android.graphics.Typeface.BOLD);
        metricRow(
            panel,
            "输入 Token",
            n[4] == n[8] ? "未知" : String.format(Locale.CHINA, "%,.0f", n[0]),
            "输出 Token",
            n[5] == n[8] ? "未知" : String.format(Locale.CHINA, "%,.0f", n[1]));
        metricRow(
            panel,
            "已知费用 · " + e.getKey().substring(e.getKey().lastIndexOf(" · ") + 3),
            n[7] == n[8] ? "未知" : String.format(Locale.CHINA, "%.6f", n[3]),
            "模型调用",
            String.format(Locale.CHINA, "%.0f 次", n[8]));
        panel.addView(
            new UsageChart(this, n[0], n[1], n[2], n[4] == n[8], n[5] == n[8], n[6] == n[8]),
            new LinearLayout.LayoutParams(-1, dp(130)));
        if (n[4] + n[5] + n[6] + n[7] > 0)
          label(
              String.format(
                  Locale.CHINA,
                  "未知记录：输入 %.0f / 输出 %.0f / 缓存 %.0f / 费用 %.0f；未计入已知小计",
                  n[4],
                  n[5],
                  n[6],
                  n[7]));
        divider();
      }
    }
    label("账户余额 · 当前提供商");
    panel.addView(ProviderUi.picker(this));
    TextView balance = label("点击查询余额；余额不由 Token 消耗推算");
    action(
        "刷新账户余额",
        () -> {
          try {
            ProviderSettings config = ProviderSettings.load(this);
            config.validate();
            balance.setText("正在查询…");
            worker.execute(
                () -> {
                  String text;
                  try {
                    text = ProviderUi.balance(config);
                  } catch (Exception e) {
                    text = "余额未知：" + e.getMessage();
                  }
                  final String result = text;
                  runOnUiThread(
                      () -> {
                        if (!isDestroyed())
                          balance.setText(
                              config.name
                                  + " · "
                                  + result
                                  + "\n查询时间："
                                  + new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                                      .format(new Date()));
                      });
                });
          } catch (Exception e) {
            balance.setText("余额未知：" + e.getMessage());
          }
        });
  }

  private void metricRow(
      LinearLayout grid, String first, String value, String second, String other) {
    LinearLayout row = new LinearLayout(this);
    grid.addView(row);
    String[] names = {first, second}, values = {value, other};
    for (int i = 0; i < 2; i++) {
      LinearLayout tile = new LinearLayout(this);
      tile.setOrientation(LinearLayout.VERTICAL);
      tile.setPadding(dp(16), dp(16), dp(16), dp(16));
      tile.setBackground(surface(i == 0 ? 0xffeaf4f1 : 0xfff1f5f8, 16));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(dp(4), dp(4), dp(4), dp(4));
      row.addView(tile, lp);
      TextView name = new TextView(this);
      name.setText(names[i]);
      name.setTextSize(13);
      name.setTextColor(MUTED);
      tile.addView(name);
      TextView number = new TextView(this);
      number.setText(values[i]);
      number.setTextSize(24);
      number.setSingleLine(true);
      number.setAutoSizeTextTypeUniformWithConfiguration(
          13, 24, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
      number.setTextColor(TEAL);
      number.setTypeface(null, android.graphics.Typeface.BOLD);
      number.setPadding(0, dp(8), 0, 0);
      tile.addView(number);
    }
  }

  private void chart(String title, List<LearningDb.Daily> data, String mode, int height) {
    section(title, "");
    panel.addView(new StudyChart(this, data, mode), new LinearLayout.LayoutParams(-1, dp(height)));
  }

  private void categories(JSONArray attempts, String field) throws Exception {
    String title =
        field.equals("knowledgePoints") ? "知识点" : field.equals("examPoints") ? "考点" : "题型";
    label(title);
    Map<String, double[]> map = new TreeMap<>();
    Map<String, Long> sources = new HashMap<>();
    for (int i = 0; i < attempts.length(); i++) {
      JSONObject a = attempts.getJSONObject(i), tags = a.optJSONObject("tags");
      if (tags == null) continue;
      JSONArray values =
          field.equals("questionType")
              ? new JSONArray().put(tags.optString(field))
              : tags.optJSONArray(field);
      if (values == null) continue;
      for (int j = 0; j < values.length(); j++) {
        String name = values.getString(j).trim();
        if (name.isEmpty()) continue;
        sources.putIfAbsent(name, a.optLong("id"));
        double[] n = map.computeIfAbsent(name, k -> new double[3]);
        n[0]++;
        n[1] += a.getDouble("score");
        if (!a.getBoolean("correct")) n[2]++;
      }
    }
    if (map.isEmpty()) label("提交后由模型识别分类");
    List<Map.Entry<String, double[]>> sorted = new ArrayList<>(map.entrySet());
    sorted.sort(
        (a, b) ->
            Double.compare(a.getValue()[1] / a.getValue()[0], b.getValue()[1] / b.getValue()[0]));
    AdaptiveGrid grid = tiles();
    for (Map.Entry<String, double[]> e : sorted) {
      double[] n = e.getValue();
      tile(
          grid,
          e.getKey(),
          n[1] / n[0],
          String.format(
              Locale.CHINA,
              "分类：%s\n提交 %d 次 · 错误 %d 次\n平均得分 %.1f / 10",
              title,
              (int) n[0],
              (int) n[2],
              n[1] / n[0]),
          () ->
              startActivity(
                  new Intent(this, PracticeActivity.class)
                      .putExtra("topic", title + "：" + e.getKey())
                      .putExtra("attemptId", sources.get(e.getKey()))),
          null);
    }
  }

  private AdaptiveGrid tiles() {
    AdaptiveGrid grid = new AdaptiveGrid(this);
    panel.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    return grid;
  }

  private void tile(
      AdaptiveGrid grid,
      String title,
      double score,
      String details,
      Runnable click,
      Runnable review) {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setPadding(dp(14), dp(14), dp(14), dp(14));
    TextView name = new TextView(this);
    name.setText(title);
    name.setTextSize(14);
    name.setTextColor(INK);
    name.setMaxLines(2);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    v.addView(name);
    TextView value = new TextView(this);
    value.setText(String.format(Locale.CHINA, "%.1f 分  ›", score));
    value.setTextSize(20);
    value.setTextColor(TEAL);
    value.setPadding(0, dp(10), 0, 0);
    v.addView(value);
    v.setContentDescription(title + String.format(Locale.CHINA, "，均分 %.1f；点击加练，长按详情", score));
    float t = (float) Math.max(0, Math.min(1, score / 10));
    int color =
        android.graphics.Color.rgb(
            (int) (246 - 70 * t), (int) (244 - 31 * t), (int) (238 - 39 * t));
    v.setBackground(surface(color, 14));
    v.setMinimumHeight(dp(100));
    v.setOnClickListener(w -> click.run());
    v.setOnLongClickListener(
        w -> {
          android.app.AlertDialog.Builder dialog =
              new android.app.AlertDialog.Builder(this)
                  .setTitle(title)
                  .setMessage(details)
                  .setPositiveButton("进入加练", (d, n) -> click.run())
                  .setNegativeButton("关闭", null);
          if (review != null) dialog.setNeutralButton("回顾作答", (d, n) -> review.run());
          dialog.show();
          return true;
        });
    grid.addView(v);
  }

  private void practice(JSONObject attempt) {
    startActivity(
        new Intent(this, PracticeActivity.class).putExtra("attemptId", attempt.optLong("id")));
  }

  private void practice(String topic) {
    startActivity(new Intent(this, PracticeActivity.class).putExtra("topic", topic));
  }

  private void review(JSONObject a) {
    startActivity(new Intent(this, ReviewActivity.class).putExtra("attemptId", a.optLong("id")));
  }

  private void export() {
    ExportFilter.choose(
        this,
        scope,
        filter -> {
          exportFilter = filter;
          startActivityForResult(
              new Intent(Intent.ACTION_CREATE_DOCUMENT)
                  .addCategory(Intent.CATEGORY_OPENABLE)
                  .setType("application/zip")
                  .putExtra(
                      Intent.EXTRA_TITLE,
                      "handymath-learning-" + LearningDb.day(System.currentTimeMillis()) + ".zip"),
              42);
        });
  }

  @Override
  protected void onActivityResult(int r, int result, Intent data) {
    super.onActivityResult(r, result, data);
    if (r == 42 && result == RESULT_OK && data != null) {
      loading("正在打包学习记录、笔迹与日志…");
      worker.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
              LearningExport.write(this, out, exportFilter);
              runOnUiThread(
                  () -> {
                    if (!isDestroyed()) {
                      loaded();
                      Toast.makeText(this, "导出完成", Toast.LENGTH_LONG).show();
                    }
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    if (!isDestroyed()) failure(e);
                  });
            }
          });
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("exportBank", exportFilter.bank);
    state.putString("exportStart", exportFilter.start);
    state.putString("exportEnd", exportFilter.end);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onResume() {
    super.onResume();
    ProviderUi.refresh(this, content);
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    if (db != null) db.close();
    super.onDestroy();
  }
}
