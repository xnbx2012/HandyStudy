package com.handymath.app;

import android.content.*;

final class AppSettings {
    static final String[] PEN_ACTIONS = {"切换笔 / 橡皮", "撤销", "无动作", "切换笔触类型（答题 / 批注）"};
    static final int SWITCH_TOOL = 0, UNDO = 1, NONE = 2, SWITCH_TYPE = 3;

    static int penAction(Context c, String gesture) {
        if (!enabled(c, "doubleTap", true)) return NONE;
        int value = prefs(c).getInt(gesture + "Action", SWITCH_TOOL);
        return value >= 0 && value < PEN_ACTIONS.length ? value : NONE;
    }
    static int retries(Context c) { return Math.max(0, Math.min(5, prefs(c).getInt("llmFormatRetries", 3))); }
    static float predictionStrength(Context c) {
        return Math.max(0f, Math.min(1f, prefs(c).getFloat("penPredictionStrength", .5f)));
    }
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("preferences", 0);
    }

    static boolean enabled(Context c, String key, boolean fallback) {
        return prefs(c).getBoolean(key, fallback);
    }

    static void apply(InkView v) {
        SharedPreferences p = prefs(v.getContext());
        v.pressure = p.getBoolean("pressure", true);
        v.tiltShading = p.getBoolean("tiltShading", false);
        v.pressureEraser = p.getBoolean("pressureEraser", false);
        v.pressureThreshold = p.getFloat("threshold", .85f);
        v.pressureEraserDelayMillis = (long) p.getFloat("pressureEraserDelay", 200);
        v.pressureEraserRadius = p.getFloat("pressureEraserRadius", 12);
        v.shapeDistance = p.getFloat("shapeDistance", 12);
        v.shapeHoldMillis = (long) p.getFloat("shapeHold", 450);
        v.shapes = p.getBoolean("shapes", false);
        v.penWidth = p.getFloat("width", 2.8f);
        v.setLineSpacing(p.getFloat("spacing", 80));
        v.penColor =
                p.getInt(
                        v.correction ? "correctionColor" : "answerColor",
                        v.correction ? 0xffb44747 : 0xff22313f);
    }
}
