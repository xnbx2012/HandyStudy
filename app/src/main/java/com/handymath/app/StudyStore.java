package com.handymath.app;

import android.content.Context;
import android.util.AtomicFile;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class StudyStore {
    private final Context c;
    private final String bank;

    StudyStore(Context c, String bank) {
        this.c = c;
        this.bank = bank;
    }

    private AtomicFile file(String id) {
        File dir = new File(c.getFilesDir(), "study/" + bank);
        dir.mkdirs();
        return new AtomicFile(new File(dir, id + ".json"));
    }

    JSONObject load(String id) throws Exception {
        AtomicFile f = file(id);
        if (!f.getBaseFile().exists()) return new JSONObject();
        return new JSONObject(new String(f.readFully(), StandardCharsets.UTF_8));
    }

    void save(String id, JSONObject state) throws Exception {
        AtomicFile f = file(id);
        FileOutputStream out = f.startWrite();
        try {
            out.write(state.toString().getBytes(StandardCharsets.UTF_8));
            f.finishWrite(out);
        } catch (Exception e) {
            f.failWrite(out);
            throw e;
        }
    }

    void grade(String id, double score, JSONArray weaknesses, int hints) {
        android.content.SharedPreferences p = c.getSharedPreferences("progress-" + bank, 0);
        boolean before = practiced(id);
        LearningPlan.record(c, bank, id, before);
        int stage = score == 10 && before ? p.getInt(id + "-stage", 0) + 1 : 0;
        int streak = score == 10 ? p.getInt(id + "-streak", 0) + 1 : 0;
        // Assisted successes are revisited sooner; unassisted mastery uses expanding intervals.
        long days = score == 10 ? (hints > 0 ? 1 : Math.min(30, 1L << Math.min(streak, 5))) : 0;
        if (LearningPlan.enabled(c)) {
            int[] intervals = LearningPlan.intervals(c);
            days = intervals[Math.min(stage, intervals.length - 1)];
        }
        p.edit()
                .putFloat(id + "-score", (float) score)
                .putString(id + "-weak", weaknesses.toString())
                .putInt(id + "-streak", streak)
                .putInt(id + "-stage", stage)
                .putLong(id + "-at", System.currentTimeMillis())
                .putLong(
                        id + "-due",
                        days == 0
                                ? System.currentTimeMillis() + 10 * 60 * 1000
                                : LearningPlan.dueAfterDays(System.currentTimeMillis(), (int) days))
                .apply();
    }

    void reschedule(BankRepository repository) throws Exception {
        android.content.SharedPreferences p = c.getSharedPreferences("progress-" + bank, 0);
        java.util.Map<String, Long> last = new java.util.HashMap<>();
        try (LearningDb db = new LearningDb(c)) {
            JSONArray attempts = db.attempts(false);
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject a = attempts.getJSONObject(i);
                if (bank.equals(a.getString("bank")))
                    last.putIfAbsent(a.getString("q"), a.getLong("at"));
            }
        }
        int[] intervals = LearningPlan.intervals(c);
        android.content.SharedPreferences.Editor edit = p.edit();
        for (BankRepository.Question q : repository.questions)
            if (practiced(q.id)) {
                long at =
                        p.getLong(
                                q.id + "-at", last.getOrDefault(q.id, System.currentTimeMillis()));
                int stage = p.getFloat(q.id + "-score", 0) < 10 ? 0 : p.getInt(q.id + "-stage", 0);
                edit.putLong(
                        q.id + "-due",
                        LearningPlan.dueAfterDays(
                                at, intervals[Math.min(stage, intervals.length - 1)]));
            }
        edit.apply();
    }

    boolean practiced(String id) {
        return c.getSharedPreferences("progress-" + bank, 0).contains(id + "-score");
    }

    long due(String id) {
        return c.getSharedPreferences("progress-" + bank, 0).getLong(id + "-due", 0);
    }

    String summary(BankRepository.Question q) {
        android.content.SharedPreferences p = c.getSharedPreferences("progress-" + bank, 0);
        if (!p.contains(q.id + "-score")) return "未练习";
        return p.getFloat(q.id + "-score", 0)
                + "/10 · "
                + (due(q.id) <= System.currentTimeMillis() ? "待复习" : "已安排复习")
                + "\n薄弱点："
                + p.getString(q.id + "-weak", "[]");
    }
}
