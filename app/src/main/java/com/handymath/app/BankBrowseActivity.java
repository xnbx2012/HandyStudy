package com.handymath.app;

import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Read-only access to every stored question field, independent of study drafts and clocks. */
public final class BankBrowseActivity extends PageActivity {
  private BankRepository bank;
  private final List<Integer> matches = new ArrayList<>();
  private final List<String> chapters = new ArrayList<>(), searchIndex = new ArrayList<>();
  private LinearLayout rows;
  private EditText query;
  private Spinner chapter;
  private TextView count;
  private int pageIndex, lastChapter = -1;
  private String lastQuery;
  private boolean viewingQuestion;
  private static final int PAGE_SIZE = 30;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    try {
      bank = BankRepository.loadLocation(this, getIntent().getStringExtra("bankLocation"));
      chapters.add("全部章节");
      for (BankRepository.Question q : bank.questions) {
        if (!chapters.contains(q.chapter)) chapters.add(q.chapter);
        StringBuilder text = new StringBuilder(q.title + " " + q.chapter + " " + q.topic + " " + q.questionMarkdown + " " + q.answerMarkdown);
        for (int i = 0; i < q.knowledgeIds.length(); i++) text.append(' ').append(bank.knowledge.path(q.knowledgeIds.optString(i)));
        searchIndex.add(text.toString().toLowerCase(Locale.ROOT));
      }
      showList();
    } catch (Exception e) { replacePage("题库浏览"); failure(e); }
  }

  private void replacePage(String title) {
    if (content != null) MathView.releaseTree(content);
    page(title);
  }

  @Override protected void onDestroy() {
    if (content != null) MathView.releaseTree(content);
    super.onDestroy();
  }

  private void showList() {
    String savedQuery = query == null ? "" : query.getText().toString();
    int savedChapter = chapter == null ? 0 : chapter.getSelectedItemPosition();
    viewingQuestion = false;
    replacePage(bank.title + " · 只读浏览");
    label(bank.questions.size() + "题 · " + BankRepository.describe(bank.data));
    label("直接查看题面、原版解析、知识点和原始字段，不产生作答记录或学习用时。可搜索题干、解析及知识点。");
    action("查看题库全局元数据 / 知识树", () -> {
      try {
        JSONObject metadata = new JSONObject(bank.data.toString());
        metadata.remove("questions");
        showRaw("题库全局元数据（题目字段见单题详情）", metadata);
      } catch (JSONException e) { failure(e); }
    });
    chapter = new Spinner(this);
    chapter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chapters));
    chapter.setSelection(Math.max(0, savedChapter));
    panel.addView(chapter);
    query = new EditText(this);
    query.setText(savedQuery);
    query.setSingleLine(true); query.setHint("搜索题干、解析、章节或知识点");
    panel.addView(query);
    count = label("");
    rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); panel.addView(rows);
    query.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int n, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int n) { filter(); }
      public void afterTextChanged(Editable text) {}
    });
    chapter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> p, View view, int position, long id) { filter(); }
      public void onNothingSelected(AdapterView<?> p) {}
    });
    filter();
  }

  private void filter() {
    String term = query.getText().toString().trim().toLowerCase(Locale.ROOT);
    int selected = chapter.getSelectedItemPosition();
    if (!term.equals(lastQuery) || selected != lastChapter) {
      matches.clear(); pageIndex = 0; lastQuery = term; lastChapter = selected;
      for (int i = 0; i < bank.questions.size(); i++)
        if ((selected <= 0 || bank.questions.get(i).chapter.equals(chapters.get(selected)))
            && searchIndex.get(i).contains(term)) matches.add(i);
    }
    renderRows();
  }

  private void renderRows() {
    rows.removeAllViews();
    panel = rows;
    int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    count.setText("筛选结果 " + matches.size() + "题 · 第 " + (pageIndex + 1) + " / " + pages + " 页");
    for (int i = pageIndex * PAGE_SIZE; i < Math.min(matches.size(), (pageIndex + 1) * PAGE_SIZE); i++) {
      int index = matches.get(i);
      BankRepository.Question q = bank.questions.get(index);
      boolean reference = !q.answerMarkdown.isEmpty() || !q.answers.isEmpty();
      action((index + 1) + ". " + q.title + "\n" + StudyFilter.typeName(q.type)
          + " · " + (reference ? "有参考答案/解析" : "无参考内容") + " · " + q.topic, () -> showQuestion(index));
    }
    if (matches.isEmpty()) label("没有匹配的题目。");
    if (pageIndex > 0) action("上一页", () -> { pageIndex--; renderRows(); });
    if (pageIndex + 1 < pages) action("下一页", () -> { pageIndex++; renderRows(); });
  }

  private void showQuestion(int index) {
    viewingQuestion = true;
    replacePage("题库核对 · " + (index + 1) + " / " + bank.questions.size());
    action("‹ 返回题目目录", this::showList);
    if (index > 0) action("‹ 查看上一题", () -> showQuestion(index - 1));
    if (index + 1 < bank.questions.size()) action("查看下一题 ›", () -> showQuestion(index + 1));
    BankRepository.Question q = bank.questions.get(index);
    section(q.title, q.chapter + " · 原题号 " + q.number + " · " + StudyFilter.typeName(q.type));
    math("### 题面\n\n" + q.questionMarkdown);
    for (String path : q.questionImages) image(path);
    section("原版参考答案 / 解析", "参考策略：" + q.referencePolicy + " · 配对状态：" + (q.referenceAvailable ? "可用" : "待核对"));
    if (!q.expectedChoice.isEmpty()) label("标准选项：" + q.expectedChoice);
    math(q.answerMarkdown.isEmpty() ? "题库未保存文字解析。" : q.answerMarkdown);
    for (String path : q.answers) image(path);
    section("所属知识点与标签", q.topic);
    for (int i = 0; i < q.knowledgeIds.length(); i++) label(bank.knowledge.path(q.knowledgeIds.optString(i)));
    if (q.tags != null) math("```json\n" + q.tags.toString() + "\n```");
    if (!q.initialStudentMarkdown.isEmpty()) { section("预置作答内容", "题库自带的初始内容"); math(q.initialStudentMarkdown); }
    for (String path : q.studentSourceImages) image(path);
    action("查看本题全部原始字段（含来源、扩展信息）", () -> {
      try { showRaw("本题全部原始字段", bank.data.getJSONArray("questions").getJSONObject(index)); }
      catch (JSONException e) { failure(e); }
    });
  }

  @Override public void onBackPressed() {
    if (viewingQuestion) showList();
    else super.onBackPressed();
  }

  private void image(String path) {
    try {
      ImageView view = new ImageView(this);
      view.setImageBitmap(bank.image(path)); view.setAdjustViewBounds(true);
      panel.addView(view, new LinearLayout.LayoutParams(-1, -2));
      label(path);
      view.setOnClickListener(v -> startActivity(new Intent(this, ImageViewerActivity.class)
          .putExtra("bank", bank.location()).putExtra("image", path)));
    } catch (Exception e) { label("图片无法读取：" + path + " · " + e.getMessage()); }
  }

  private void showRaw(String title, JSONObject data) {
    try {
      ScrollView scroll = new ScrollView(this);
      TextView text = new TextView(this);
      text.setText(data.toString(2)); text.setTextIsSelectable(true); text.setTypeface(Typeface.MONOSPACE);
      text.setTextSize(13); text.setPadding(dp(16), dp(12), dp(16), dp(12)); scroll.addView(text);
      new android.app.AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭", null).show();
    } catch (JSONException e) { failure(e); }
  }
}
