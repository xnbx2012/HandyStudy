package com.handymath.app;

import android.content.*;
import android.graphics.*;
import android.view.*;

/** Two stable writing surfaces; only their bounds and order change when adjusted. */
final class StudySplitLayout extends ViewGroup {
  private final SharedPreferences prefs;
  final boolean horizontal;
  final View divider;
  private View question, answer;
  private float questionShare;
  private boolean swapped;
  private final int dividerSize;

  StudySplitLayout(Context context, String bankId, boolean horizontal) {
    super(context);
    this.horizontal = horizontal;
    prefs = context.getSharedPreferences("study-layout-" + bankId, 0);
    questionShare = clamp(prefs.getFloat(shareKey(), .4f));
    swapped = prefs.getBoolean("swapped", false);
    dividerSize = dp(24);
    divider = new Divider(context);
    addView(divider);
  }

  private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private String shareKey() { return horizontal ? "widthShare" : "heightShare"; }
  private static float clamp(float value) { return Float.isFinite(value) ? Math.max(.2f, Math.min(.8f, value)) : .4f; }
  float questionShare() { return questionShare; }
  boolean swapped() { return swapped; }

  void setPanes(View question, View answer) {
    this.question = question;
    this.answer = answer;
    addView(question, 0);
    addView(answer, 1);
  }

  private void persist() {
    prefs.edit().putFloat(shareKey(), questionShare).putBoolean("swapped", swapped).apply();
  }

  @Override protected void onMeasure(int ws, int hs) {
    int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
    setMeasuredDimension(w, h);
    if (question == null) return;
    int available = Math.max(0, (horizontal ? w : h) - dividerSize);
    int q = Math.round(available * questionShare), a = available - q;
    question.measure(MeasureSpec.makeMeasureSpec(horizontal ? q : w, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(horizontal ? h : q, MeasureSpec.EXACTLY));
    answer.measure(MeasureSpec.makeMeasureSpec(horizontal ? a : w, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(horizontal ? h : a, MeasureSpec.EXACTLY));
    divider.measure(MeasureSpec.makeMeasureSpec(horizontal ? dividerSize : w, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(horizontal ? h : dividerSize, MeasureSpec.EXACTLY));
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (question == null) return;
    View first = swapped ? answer : question, second = swapped ? question : answer;
    if (horizontal) {
      int edge = first.getMeasuredWidth();
      first.layout(0, 0, edge, getHeight());
      divider.layout(edge, 0, edge + dividerSize, getHeight());
      second.layout(edge + dividerSize, 0, getWidth(), getHeight());
    } else {
      int edge = first.getMeasuredHeight();
      first.layout(0, 0, getWidth(), edge);
      divider.layout(0, edge, getWidth(), edge + dividerSize);
      second.layout(0, edge + dividerSize, getWidth(), getHeight());
    }
  }

  private final class Divider extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float downX, downY, originalShare;
    private boolean dragging, moved, centerTap;
    private final Runnable hold = () -> {
      if (moved) return;
      dragging = true;
      performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      invalidate();
    };

    Divider(Context context) {
      super(context);
      setContentDescription(horizontal ? "分隔线：长按左右拖动调整宽度，点击中间按钮互换两侧" : "分隔线：长按上下拖动调整高度，点击中间按钮互换上下区域");
      setFocusable(true);
      setClickable(true);
      setOnClickListener(v -> { swapped = !swapped; persist(); requestLayout(); });
    }

    @Override protected void onDraw(Canvas canvas) {
      float x = getWidth()/2f, y = getHeight()/2f;
      paint.setColor(dragging ? 0xff197d78 : 0xffc2d4d0);
      paint.setStrokeWidth(dp(dragging ? 3 : 1));
      if (horizontal) canvas.drawLine(x, 0, x, getHeight(), paint);
      else canvas.drawLine(0, y, getWidth(), y, paint);
      paint.setColor(dragging ? 0xffcce7e1 : 0xffe0eeea);
      canvas.drawRoundRect(x-dp(horizontal ? 11 : 22), y-dp(horizontal ? 22 : 11),
          x+dp(horizontal ? 11 : 22), y+dp(horizontal ? 22 : 11), dp(8), dp(8), paint);
      paint.setColor(0xff197d78);
      paint.setTextSize(dp(18));
      paint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText(horizontal ? "⇄" : "⇅", x, y-(paint.ascent()+paint.descent())/2, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = event.getRawX(); downY = event.getRawY(); originalShare = questionShare;
          dragging = moved = false;
          centerTap = Math.abs((horizontal ? event.getY()-getHeight()/2f : event.getX()-getWidth()/2f)) <= dp(24);
          getParent().requestDisallowInterceptTouchEvent(true);
          postDelayed(hold, ViewConfiguration.getLongPressTimeout());
          return true;
        case MotionEvent.ACTION_MOVE:
          float dx = event.getRawX()-downX, dy = event.getRawY()-downY;
          if (!dragging && Math.hypot(dx,dy)>ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            moved = true; removeCallbacks(hold);
          }
          if (dragging) {
            float distance = horizontal ? dx : dy;
            int available = Math.max(1, (horizontal ? StudySplitLayout.this.getWidth() : StudySplitLayout.this.getHeight())-dividerSize);
            questionShare = clamp(originalShare + (swapped ? -distance : distance)/available);
            StudySplitLayout.this.requestLayout();
          }
          return true;
        case MotionEvent.ACTION_UP:
          removeCallbacks(hold);
          if (dragging) persist();
          else if (!moved && centerTap) performClick();
          dragging = false; invalidate();
          getParent().requestDisallowInterceptTouchEvent(false);
          return true;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_POINTER_DOWN:
          removeCallbacks(hold);
          if (dragging) { questionShare = originalShare; StudySplitLayout.this.requestLayout(); }
          dragging = false; moved = true; invalidate();
          return true;
      }
      return true;
    }

    @Override protected void onDetachedFromWindow() { removeCallbacks(hold); super.onDetachedFromWindow(); }
  }
}
