package com.handymath.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Public provider reasoning, independently collapsible from the annotated answer. */
final class ThinkingPanel extends LinearLayout {
  static final class Part {
    int step; String stage = "", text = "";
    boolean finished, expanded;
  }
  static final class State {
    final List<Part> parts = new ArrayList<>();
    boolean running, failed;
    String stage = "正在连接模型…";
    static State from(JSONArray saved) {
      State state = new State();
      if (saved != null) for (int i = 0; i < saved.length(); i++) {
        JSONObject item = saved.optJSONObject(i); if (item == null || item.optString("text").isEmpty()) continue;
        Part part = new Part();part.step = item.optInt("step", i + 1);part.stage = item.optString("stage", "处理题目");
        part.text = item.optString("text");part.finished = true;state.parts.add(part);
      }
      return state;
    }
  }

  private State state;
  private final TextView title;
  private final LinearLayout rows;
  private final Map<Integer, Row> views = new HashMap<>();
  private final class Row {
    final Part part; final TextView header; final ScrollView scroll;
    TextView text;
    boolean followTail = true;
    boolean bodyShown;
    Row(Part part) {
      this.part = part;
      LinearLayout row = new LinearLayout(getContext());row.setOrientation(VERTICAL);rows.addView(row);
      header = AppUi.text(getContext(), "", 14, AppUi.TEAL);header.setMinHeight(dp(44));
      header.setGravity(Gravity.CENTER_VERTICAL);row.addView(header);
      scroll = new ScrollView(getContext()) {
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
          if (event.getActionMasked() == MotionEvent.ACTION_DOWN
              || event.getActionMasked() == MotionEvent.ACTION_MOVE)
            getParent().requestDisallowInterceptTouchEvent(true);
          return super.dispatchTouchEvent(event);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
          int maximum = dp(220);
          if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) maximum = Math.min(maximum, MeasureSpec.getSize(heightSpec));
          super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
        }
      };scroll.setFillViewport(false);
      scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(view, x, y, oldX, oldY) -> {
        if (text != null) followTail = y + scroll.getHeight() >= text.getHeight() - dp(24);
      });
      row.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
      header.setOnClickListener(v -> {if (!part.text.isEmpty()) {part.expanded = !part.expanded;update();}});
      update();
    }
    void update() {
      String status = part.finished ? (part.text.isEmpty() ? "处理完成" : "思考结束")
          : state.running ? (part.text.isEmpty() ? "等待模型回应" : "正在思考") : "已中断";
      header.setText((part.text.isEmpty() ? "" : part.expanded ? "▾ " : "▸ ") + part.step + " · " + part.stage + " · " + status
          + (part.text.isEmpty() ? "" : "（" + part.text.length() + "字）"));
      header.setContentDescription("思考阶段 " + part.step + "：" + part.stage + (part.expanded ? "，点击折叠" : "，点击展开"));
      header.setClickable(!part.text.isEmpty());
      scroll.setVisibility(part.expanded && !part.text.isEmpty() ? VISIBLE : GONE);
      if (part.expanded && !part.text.isEmpty()) {
        if (text == null) {
          text = AppUi.text(getContext(), "", 13, AppUi.INK);
          text.setPadding(dp(4), dp(6), dp(4), dp(6));
          scroll.addView(text, new ScrollView.LayoutParams(-1, -2));
          text.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (followTail && state.running && !part.finished && part.expanded) scroll.post(() -> scroll.fullScroll(FOCUS_DOWN));
          });
        }
        text.setText(part.text);
      } else if (text != null && bodyShown) text.setText(part.text);
      bodyShown = part.expanded && !part.text.isEmpty();
    }
  }

  ThinkingPanel(Context context, State state) {
    super(context);setOrientation(VERTICAL);setPadding(dp(12), dp(8), dp(12), dp(8));
    setBackground(AppUi.surface(context, 0xffeff6f5, 12));
    title = AppUi.text(context, "", 14, AppUi.INK);title.setTypeface(null, Typeface.BOLD);addView(title);
    rows = new LinearLayout(context);rows.setOrientation(VERTICAL);addView(rows);setState(state);
  }
  private int dp(int value) {return (int)(value * getResources().getDisplayMetrics().density + .5f);}
  void setState(State next) {
    MathView.releaseTree(rows);rows.removeAllViews();views.clear();state = next;
    for (Part part : state.parts) views.put(part.step, new Row(part));refresh();
  }
  void start() {state.running = true;state.failed = false;state.stage = "正在连接模型…";refresh();}
  void stage(int step, String label) {
    state.stage = label;state.running = true;
    if (!views.containsKey(step)) {Part part = new Part();part.step = step;part.stage = label;state.parts.add(part);views.put(step, new Row(part));}
    refresh();
  }
  void thinking(int step, String value, boolean finished) {
    Row row = views.get(step);if (row == null) {stage(step, "处理题目");row = views.get(step);}
    boolean first = row.part.text.isEmpty(), ended = !row.part.finished && finished;
    row.part.text = value;row.part.finished = finished;
    if (first && !value.isEmpty() && !finished) row.part.expanded = true;
    if (ended) row.part.expanded = false;
    row.update();refresh();
  }
  void finish(boolean success) {
    state.running = false;state.failed = !success;
    for (Row row : views.values()) {
      if (!row.part.finished) {if (success) row.part.finished = true;row.part.expanded = false;}
      row.update();
    }
    refresh();
  }
  private void refresh() {
    boolean hasText = false;for (Part part : state.parts) hasText |= !part.text.isEmpty();
    setVisibility(state.running || hasText ? VISIBLE : GONE);
    title.setText(state.running ? "模型处理进度 · " + state.stage
        : state.failed ? "本次处理已中断 · 点击各阶段查看思考" : "思考过程 · 点击各阶段展开");
  }
}
