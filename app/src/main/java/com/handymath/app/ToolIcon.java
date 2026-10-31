package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Compact vector icons; circular hit area, no dependence on emoji fonts. */
@android.annotation.SuppressLint(
        "ViewConstructor") // All icons are constructed programmatically with an action.
final class ToolIcon extends View {
    final String icon;
    int tint = 0xff197d78;
    final Paint p = new Paint(3);
    final Path path = new Path();

    ToolIcon(Context c, String icon, String label, Runnable run) {
        super(c);
        this.icon = icon;
        setContentDescription(label);
        setTooltipText(label);
        setOnClickListener(v -> run.run());
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 40f;
        c.save();
        c.scale(s, s);
        p.setStyle(Paint.Style.FILL);
        p.setColor(isSelected() ? tint : 0xffe6f1ef);
        c.drawCircle(20, 20, 18, p);
        p.setColor(isSelected() ? Color.WHITE : tint);
        p.setStrokeWidth(1.9f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        switch (icon) {
            case "text":
                c.drawLine(11, 11, 29, 11, p);
                c.drawLine(20, 11, 20, 29, p);
                c.drawLine(16, 29, 24, 29, p);
                break;
            case "pen":
                path.moveTo(12, 28);
                path.lineTo(15, 20);
                path.lineTo(26, 9);
                path.lineTo(31, 14);
                path.lineTo(20, 25);
                path.close();
                c.drawPath(path, p);
                c.drawLine(24, 11, 29, 16, p);
                break;
            case "erase":
                path.moveTo(10, 24);
                path.lineTo(22, 11);
                path.lineTo(31, 20);
                path.lineTo(22, 29);
                path.lineTo(15, 29);
                path.close();
                c.drawPath(path, p);
                c.drawLine(16, 18, 25, 27, p);
                break;
            case "palette":
                c.drawCircle(20, 20, 10, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 4; i++) c.drawCircle(15 + i % 2 * 8, 15 + i / 2 * 8, 2, p);
                break;
            case "undo":
            case "redo":
                c.drawArc(11, 13, 29, 29, icon.equals("undo") ? 190 : 310, 230, false, p);
                if (icon.equals("undo")) {
                    c.drawLine(10, 20, 11, 12, p);
                    c.drawLine(11, 12, 19, 13, p);
                } else {
                    c.drawLine(30, 20, 29, 12, p);
                    c.drawLine(29, 12, 21, 13, p);
                }
                break;
            case "trash":
                c.drawRoundRect(13, 15, 27, 29, 2, 2, p);
                c.drawLine(11, 12, 29, 12, p);
                c.drawLine(16, 9, 24, 9, p);
                c.drawLine(17, 18, 17, 25, p);
                c.drawLine(23, 18, 23, 25, p);
                break;
            case "fit":
                c.drawRect(11, 11, 29, 29, p);
                c.drawLine(14, 20, 26, 20, p);
                c.drawLine(20, 14, 20, 26, p);
                break;
            default:
                c.drawCircle(20, 12, 1, p);
                c.drawCircle(20, 20, 1, p);
                c.drawCircle(20, 28, 1, p);
        }
        c.restore();
    }
}
