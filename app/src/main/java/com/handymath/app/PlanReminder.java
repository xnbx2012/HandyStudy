package com.handymath.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.*;

/** Local opt-in notification. Inexact alarms respect Android battery scheduling. */
public final class PlanReminder extends BroadcastReceiver {
    static final String CHANNEL = "study-plan";

    static PendingIntent alarm(Context c) {
        return PendingIntent.getBroadcast(
                c,
                73,
                new Intent(c, PlanReminder.class).setAction("com.handymath.app.PLAN_REMINDER"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        manager.cancel(alarm(c));
        if (!LearningPlan.enabled(c) || !LearningPlan.prefs(c).getBoolean("reminder", false))
            return;
        int minutes = LearningPlan.prefs(c).getInt("reminderMinutes", 20 * 60);
        ZonedDateTime now = ZonedDateTime.now(),
                at = now.toLocalDate().atTime(minutes / 60, minutes % 60).atZone(now.getZone());
        if (!at.isAfter(now)) at = at.plusDays(1);
        manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), alarm(c));
    }

    static void cancelNotification(Context c) {
        c.getSystemService(NotificationManager.class).cancel(73);
    }

    static boolean allowed(Context c) {
        return (Build.VERSION.SDK_INT < 33
                        || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED)
                && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (!"com.handymath.app.PLAN_REMINDER".equals(action)) {
            schedule(c);
            return;
        }
        if (!LearningPlan.enabled(c) || !LearningPlan.prefs(c).getBoolean("reminder", false))
            return;
        org.json.JSONObject day = LearningPlan.day(c, LearningPlan.today());
        try {
            day =
                    LearningPlan.snapshot(
                            c, BankRepository.loadLocation(c, LearningPlan.location(c)));
        } catch (Exception ignored) {
        }
        if (!day.optBoolean("checkedIn")
                && day.optInt("newGoal") + day.optInt("reviewGoal") > 0
                && allowed(c)) {
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL, "学习打卡提醒", NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open =
                    PendingIntent.getActivity(
                            c,
                            74,
                            new Intent(c, PlanActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.notify(
                    73,
                    new Notification.Builder(c, CHANNEL)
                            .setSmallIcon(android.R.drawable.ic_menu_edit)
                            .setContentTitle("手写伴学 · 完成今天的学习计划")
                            .setContentText(LearningPlan.summary(c))
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build());
        }
        schedule(c);
    }
}
