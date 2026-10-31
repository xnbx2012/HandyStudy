package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;

import java.util.*;

final class StudyChart extends View {
    final List<LearningDb.Daily> rows;
    final String mode;
    final Paint p = new Paint(3);
    final float density;
    int selected = -1;
    float tapX, tapY;

    StudyChart(Context c, List<LearningDb.Daily> rows, String mode) {
        super(c);
        this.rows = rows;
        this.mode = mode;
        density = getResources().getDisplayMetrics().density;
        setContentDescription(mode.equals("heat") ? "最近84天完成题目热力图" : mode + "趋势图");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        selected = -1;
    }

    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        p.setTextSize(11 * density);
        if (rows.isEmpty()) return;
        if (mode.equals("heat")) {
            float cell = Math.min((w - 48 * density) / 12, (h - 35 * density) / 7);
            for (int i = 0; i < rows.size(); i++) {
                int count = rows.get(i).count;
                p.setColor(
                        count == 0
                                ? 0xffdfe8e5
                                : count < 3 ? 0xffb2d7cc : count < 6 ? 0xff75b4a4 : 0xff197d78);
                float x = (w - 12 * cell) / 2 + (i / 7) * cell, y = (i % 7) * cell;
                c.drawRoundRect(x + 2, y + 2, x + cell - 2, y + cell - 2, 3, 3, p);
            }
            p.setColor(0xff647585);
            c.drawText(rows.get(0).day + " → " + rows.get(rows.size() - 1).day, 5, h - 8, p);
            tooltip(c);
            return;
        }
        float l = 45 * density, t = 18 * density, r = w - 12 * density, b = h - 30 * density;
        double max = mode.equals("score") ? 10 : 1;
        for (LearningDb.Daily d : rows) max = Math.max(max, value(d));
        p.setColor(0xff647585);
        p.setStrokeWidth(density);
        c.drawLine(l, t, l, b, p);
        c.drawLine(l, b, r, b, p);
        c.drawText(String.format(Locale.CHINA, "%.1f", max), 2, t + 10, p);
        c.drawText("0", 20 * density, b, p);
        float step = (r - l) / rows.size();
        Path path = new Path();
        boolean first = true;
        for (int i = 0; i < rows.size(); i++) {
            LearningDb.Daily d = rows.get(i);
            float x = l + step * (i + .5f), y = b - (float) (value(d) / max) * (b - t);
            p.setColor(0xff197d78);
            if (mode.equals("count")) c.drawRect(x - step * .34f, y, x + step * .34f, b, p);
            else if (!mode.equals("score") || d.attempts > 0) {
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else path.lineTo(x, y);
                c.drawCircle(x, y, 3 * density, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * density);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                first = true;
            }
        }
        if (!mode.equals("count")) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * density);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0xff647585);
        c.drawText(rows.get(0).day.substring(5), l, h - 8, p);
        c.drawText(rows.get(rows.size() - 1).day.substring(5), r - 40 * density, h - 8, p);
        tooltip(c);
    }

    double value(LearningDb.Daily d) {
        return mode.equals("count") ? d.count : mode.equals("score") ? d.score : d.ms / 60000.0;
    }

    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int i;
            if (mode.equals("heat")) {
                float cell =
                        Math.min(
                                (getWidth() - 48 * density) / 12, (getHeight() - 35 * density) / 7);
                float left = (getWidth() - 12 * cell) / 2;
                if (e.getX() < left || e.getX() >= left + 12 * cell || e.getY() >= 7 * cell)
                    return true;
                i = (int) ((e.getX() - left) / cell) * 7 + (int) (e.getY() / cell);
            } else
                i = (int) ((e.getX() - 45 * density) / ((getWidth() - 57 * density) / rows.size()));
            if (i >= 0 && i < rows.size()) {
                selected = i;
                tapX = e.getX();
                tapY = e.getY();
                invalidate();
            }
            performClick();
        }
        return true;
    }

    private void tooltip(Canvas c) {
        if (selected < 0 || selected >= rows.size()) return;
        LearningDb.Daily d = rows.get(selected);
        String[] lines = {
            d.day + " · 完成 " + d.count + " 题",
            LearningDb.duration(d.ms)
                    + " · "
                    + (d.attempts == 0 ? "暂无得分" : String.format(Locale.CHINA, "均分 %.1f", d.score))
        };
        p.setTextSize(12 * density);
        p.setStyle(Paint.Style.FILL);
        float width = Math.max(p.measureText(lines[0]), p.measureText(lines[1])) + 24 * density,
                height = 54 * density;
        float left =
                Math.max(4 * density, Math.min(getWidth() - width - 4 * density, tapX - width / 2));
        float top = tapY - height - 12 * density;
        if (top < 4 * density)
            top = Math.min(getHeight() - height - 4 * density, tapY + 12 * density);
        p.setColor(0xff233446);
        c.drawRoundRect(left, top, left + width, top + height, 10 * density, 10 * density, p);
        Path arrow = new Path();
        float edge = top > tapY ? top : top + height;
        float x = Math.max(left + 12 * density, Math.min(left + width - 12 * density, tapX));
        arrow.moveTo(x - 6 * density, edge);
        arrow.lineTo(x + 6 * density, edge);
        arrow.lineTo(tapX, tapY);
        arrow.close();
        c.drawPath(arrow, p);
        p.setColor(Color.WHITE);
        c.drawText(lines[0], left + 12 * density, top + 21 * density, p);
        c.drawText(lines[1], left + 12 * density, top + 40 * density, p);
    }

    public boolean performClick() {
        super.performClick();
        return true;
    }
}
