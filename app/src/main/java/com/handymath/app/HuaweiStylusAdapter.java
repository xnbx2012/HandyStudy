package com.handymath.app;

import android.app.Activity;
import android.content.*;
import android.os.Build;
import android.provider.Settings;

/**
 * Huawei documented standalone double-tap integration for HarmonyOS 2.0+ Android apps. Does not
 * require the proprietary Pencil Engine brush SDK.
 * https://developer.huawei.com/consumer/cn/doc/connectivity-Guides/double-click-function-0000001166918053
 */
public final class HuaweiStylusAdapter extends ManagedStylusAdapter {
    static final String DOUBLE_TAP = "com.huawei.stylus.action.BUTTON_DOUBLE_PRESSED";
    private boolean registered;
    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!DOUBLE_TAP.equals(intent.getAction()) || !activity.hasWindowFocus())
                        return;
                    int mode =
                            Settings.Global.getInt(
                                    context.getContentResolver(), "double_click_switch_mode", 0);
                    if (mode != 3) gesture(2);
                    // Mode 3 means disabled; respect the user's system preference.
                }
            };

    public String name() {
        return "华为 M-Pencil · 压感 / 双击切工具 / 系统双击设置";
    }

    public boolean supports(String maker) {
        return maker.contains("huawei");
    }

    @Override
    public void attach(Activity a, Runnable toggle, Runnable palette) {
        super.attach(a, toggle, palette);
    }

    @android.annotation.SuppressLint(
            "UnspecifiedRegisterReceiverFlag") // API <33 has no exported flag; newer platforms use
    // it below.
    @Override
    public void focusChanged(boolean focused) {
        super.focusChanged(focused);
        if (activity == null) return;
        if (focused && !registered) {
            IntentFilter filter = new IntentFilter(DOUBLE_TAP);
            // Broadcast originates from Huawei's system service, outside the app UID.
            if (Build.VERSION.SDK_INT >= 33)
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else activity.registerReceiver(receiver, filter);
            registered = true;
        } else if (!focused && registered) {
            activity.unregisterReceiver(receiver);
            registered = false;
        }
    }

    @Override
    public void detach() {
        focusChanged(false);
        super.detach();
    }
}
