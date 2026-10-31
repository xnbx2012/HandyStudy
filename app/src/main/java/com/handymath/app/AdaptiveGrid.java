package com.handymath.app;

import android.content.Context;
import android.view.*;

/** Column count follows actual available width, including split-screen resizing. */
final class AdaptiveGrid extends ViewGroup {
  private final int gap, minimum;
  private int columns, cell, rowHeight;

  AdaptiveGrid(Context c) {
    super(c);
    float d = getResources().getDisplayMetrics().density;
    gap = (int) (8 * d);
    minimum = (int) (160 * d);
  }

  protected void onMeasure(int ws, int hs) {
    int width = MeasureSpec.getSize(ws);
    columns = Math.max(1, (width + gap) / (minimum + gap));
    cell = Math.max(1, (width - gap * (columns - 1)) / columns);
    rowHeight = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      child.measure(
          MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
    }
    int rows = (getChildCount() + columns - 1) / columns;
    setMeasuredDimension(width, rows == 0 ? 0 : rows * rowHeight + (rows - 1) * gap);
  }

  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    for (int i = 0; i < getChildCount(); i++) {
      int x = (i % columns) * (cell + gap), y = (i / columns) * (rowHeight + gap);
      getChildAt(i).layout(x, y, x + cell, y + rowHeight);
    }
  }
}
