package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

final class UsageChart extends View {
  final double[] values;
  final boolean[] unknown;
  final Paint paint = new Paint(3);

  UsageChart(
      Context c,
      double input,
      double output,
      double cache,
      boolean inputUnknown,
      boolean outputUnknown,
      boolean cacheUnknown) {
    super(c);
    unknown = new boolean[] {inputUnknown, outputUnknown, cacheUnknown};
    values = new double[] {input, output, cache};
    setContentDescription(
        String.format(
            Locale.CHINA, "输入 %.0f、输出 %.0f、缓存命中 %.0f Token；缓存已包含在输入中", input, output, cache));
  }

  protected void onDraw(Canvas c) {
    float d = getResources().getDisplayMetrics().density;
    double max = Math.max(1, Math.max(values[0], Math.max(values[1], values[2])));
    String[] names = {"输入", "输出", "缓存命中"};
    int[] colors = {0xff197d78, 0xff5489ad, 0xff97bdb2};
    paint.setTextSize(12 * d);
    for (int i = 0; i < 3; i++) {
      float y = (16 + i * 39) * d;
      paint.setColor(0xff647585);
      c.drawText(names[i], 0, y, paint);
      String number = unknown[i] ? "未知" : String.format(Locale.CHINA, "%,.0f", values[i]);
      c.drawText(number, getWidth() - paint.measureText(number), y, paint);
      float top = y + 6 * d, right = getWidth();
      paint.setColor(0xffedf3f2);
      c.drawRoundRect(0, top, right, top + 10 * d, 5 * d, 5 * d, paint);
      paint.setColor(colors[i]);
      c.drawRoundRect(0, top, (float) (right * values[i] / max), top + 10 * d, 5 * d, 5 * d, paint);
    }
  }
}
