package com.handymath.app;

import android.app.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

abstract class PageActivity extends Activity {
    LinearLayout panel;
    LinearLayout content;
    static final int INK = 0xff233446, TEAL = 0xff197d78, MUTED = 0xff647585;

    GradientDrawable surface(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    void section(String title, String subtitle) {
        panel = content;
        TextView heading = label(title);
        heading.setTextSize(19);
        heading.setTypeface(null, Typeface.BOLD);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(8), dp(18), dp(14));
        card.setBackground(surface(0xffffffff, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        content.addView(card, lp);
        panel = card;
        if (!subtitle.isEmpty()) {
            TextView t = label(subtitle);
            t.setTextSize(13);
            t.setTextColor(MUTED);
        }
    }

    void divider() {
        View v = new View(this);
        v.setBackgroundColor(0xffedf1f0);
        panel.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    ProgressDialog progress;
    String lastError = "";

    int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    void page(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(30), dp(16), dp(20));
        root.setBackgroundColor(0xfff1f5f4);
        setContentView(root);
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setOnApplyWindowInsetsListener(
                (v, i) -> {
                    root.setPadding(
                            dp(16) + i.getSystemWindowInsetLeft(),
                            dp(8) + i.getSystemWindowInsetTop(),
                            dp(16) + i.getSystemWindowInsetRight(),
                            dp(12) + i.getSystemWindowInsetBottom());
                    return i;
                });
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(32);
        back.setTextColor(TEAL);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setBackground(surface(0xffe5f0ee, 16));
        back.setOnClickListener(v -> finish());
        head.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(26);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(INK);
        t.setPadding(dp(16), dp(16), 0, dp(16));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(head);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(12), 0, dp(20));
        FrameLayout center = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        center.addView(
                content,
                new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        panel = content;
        scroll.addView(center);
        scroll.addOnLayoutChangeListener(
                (v, l, top, r, b, ol, ot, or, ob) -> {
                    int width = Math.min(r - l, dp(760));
                    if (content.getLayoutParams().width != width) {
                        content.getLayoutParams().width = width;
                        content.requestLayout();
                    }
                });
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTextColor(0xff233446);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        panel.addView(t);
        return t;
    }

    Button action(String s, Runnable run) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEAL);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setMinHeight(dp(52));
        b.setBackground(surface(0xffe5f0ee, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        panel.addView(b, lp);
        b.setOnClickListener(v -> run.run());
        return b;
    }

    void math(String s) {
        MathView v = new MathView(this);
        panel.addView(v, new LinearLayout.LayoutParams(-1, dp(48)));
        v.setMarkdown(s);
    }

    void loading(String s) {
        progress = new ProgressDialog(this);
        progress.setMessage(s);
        progress.setCancelable(false);
        progress.setCanceledOnTouchOutside(false);
        progress.show();
    }

    void loaded() {
        if (progress != null) {
            progress.dismiss();
            progress = null;
        }
    }

    void failure(Exception e) {
        lastError = e.getMessage();
        loaded();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        MathView message = new MathView(this);
        body.addView(message, new LinearLayout.LayoutParams(-1, dp(48)));
        message.setMarkdown(e.getMessage() == null ? "操作失败，请重试" : e.getMessage());
        new AlertDialog.Builder(this)
                .setTitle("暂时无法完成")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        loaded();
        super.onDestroy();
    }
}
