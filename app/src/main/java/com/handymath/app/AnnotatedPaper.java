package com.handymath.app;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Stable logical page width: source layout and vector annotations scale together on rotation. */
final class AnnotatedPaper extends android.view.ViewGroup {
  final LinearLayout body;
  final InkView ink;
  private final int logicalWidth;

  AnnotatedPaper(Context c, StylusAdapter adapter) {
    super(c);
    logicalWidth = (int) (600 * c.getResources().getDisplayMetrics().density);
    setClipChildren(true);
    body = new LinearLayout(c);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPivotX(0);
    body.setPivotY(0);
    addView(body);
    ink = new InkView(c, adapter);
    ink.annotationSurface = true;
    ink.setBackgroundColor(Color.TRANSPARENT);
    ink.setContentDescription("题面矢量批注，手指滚动题面");
    addView(ink);
  }

  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    // Observe taps without taking ordinary finger scrolling away from the question.
    if (!ink.finger && !ink.locked && !ink.viewOnly
        && !StylusAdapter.isPen(event, event.getActionIndex())
        && ink.allowFingerGesture() && ink.fingerTap(event)) {
      ink.activated.run();
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  protected void onMeasure(int ws, int hs) {
    int width = MeasureSpec.getSize(ws);
    body.measure(
        MeasureSpec.makeMeasureSpec(logicalWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    float scale = width / (float) logicalWidth;
    int height = Math.max(1, (int) Math.ceil(body.getMeasuredHeight() * scale));
    setMeasuredDimension(width, height);
    ink.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
  }

  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    float scale = (r - l) / (float) logicalWidth;
    body.layout(0, 0, logicalWidth, body.getMeasuredHeight());
    body.setScaleX(scale);
    body.setScaleY(scale);
    ink.layout(0, 0, r - l, b - t);
    ink.zoom = scale;
    ink.panX = 0;
    ink.panY = 0;
    ink.invalidate();
  }

  java.util.List<android.graphics.Bitmap> snapshot() throws java.io.IOException {
    java.util.List<android.graphics.Bitmap> images = new java.util.ArrayList<>();
    // Capture the entire laid-out paper, including offscreen annotations, in readable strips.
    if (getWidth() < 1 || getHeight() < 1) throw new java.io.IOException("题面尚未完成排版，请稍后提交");
    int width = getWidth();
    int strip = Math.max(1, Math.min(1800, 2000000 / width));
    if ((long) width * getHeight() > Math.min(12000000L, Runtime.getRuntime().maxMemory() / 32)) throw new java.io.IOException("题面图片过大，请缩短题面后提交");
    ink.exporting = true;
    try {
      for (int y = 0; y < getHeight(); y += strip) {
        int height = Math.min(strip, getHeight() - y);
        android.graphics.Bitmap bitmap =
            android.graphics.Bitmap.createBitmap(
                width, height, android.graphics.Bitmap.Config.ARGB_8888);
        images.add(bitmap);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        canvas.translate(0, -y);
        draw(canvas);
      }
      return images;
    } catch (RuntimeException | OutOfMemoryError error) {
      for (android.graphics.Bitmap bitmap : images) bitmap.recycle();
      throw new java.io.IOException("题面图片生成失败，请减少内容后重试", error);
    } finally { ink.exporting = false; }
  }

  void restore(org.json.JSONObject data) throws Exception {
    ink.restore(data);
    requestLayout();
  }
}
