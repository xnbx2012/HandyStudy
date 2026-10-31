package com.handymath.app;

import android.content.*;

import org.json.*;

import java.time.*;
import java.util.*;

/** Optional plan ledger; distinct questions/day, snapshots of goals, no fabricated check-ins. */
final class LearningPlan {
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("learning-plan", 0);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    static String location(Context c) {
        return prefs(c).getString("bank", "@builtin/wzx-full");
    }

    static boolean applies(Context c, BankRepository b) {
        return enabled(c) && location(c).equals(b.location());
    }

    static String today() {
        return LocalDate.now().toString();
    }

    static int goal(Context c, boolean review) {
        return prefs(c).getInt(review ? "reviews" : "new", review ? 5 : 10);
    }

    static int[] intervals(Context c) {
        return parseIntervals(prefs(c).getString("intervals", "1,3,7,14,30"));
    }

    static int[] parseIntervals(String raw) {
        String[] parts = raw.trim().replace('，', ',').split(",");
        if (parts.length < 1 || parts.length > 12)
            throw new IllegalArgumentException("请输入 1–12 个递增天数，例如 1,3,7,14,30");
        int[] days = new int[parts.length];
        int prev = 0;
        try {
            for (int i = 0; i < days.length; i++) {
                days[i] = Integer.parseInt(parts[i].trim());
                if (days[i] <= prev || days[i] > 365) throw new IllegalArgumentException();
                prev = days[i];
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("复习间隔须为 1–365 天的递增整数");
        }
        return days;
    }

    static JSONObject day(Context c, String date) {
        try {
            return new JSONObject(prefs(c).getString("day-" + date, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static StudyFilter filter(Context c) {
        StudyFilter f = new StudyFilter();
        f.type = prefs(c).getString("type", "");
        f.knowledge = prefs(c).getString("knowledge", "");
        return f;
    }

    static String filterKey(Context c) {
        StudyFilter f = filter(c);
        return f.type + "|" + f.knowledge;
    }

    static JSONObject snapshot(Context c, BankRepository bank) {
        JSONObject d = day(c, today());
        if (d.has("newGoal")
                && bank.id.equals(d.optString("bank"))
                && filterKey(c).equals(d.optString("filter", "|"))) return d;
        try {
            int fresh = 0, reviews = 0;
            StudyStore store = new StudyStore(c, bank.id);
            for (BankRepository.Question q : bank.questions)
                if (q.referenceAvailable && filter(c).matches(bank, q)) {
                    if (!store.practiced(q.id)) fresh++;
                    else if (store.due(q.id) <= System.currentTimeMillis()) reviews++;
                }
            JSONObject previous = d;
            d =
                    new JSONObject()
                            .put("bank", bank.id)
                            .put("title", bank.title)
                            .put("filter", filterKey(c))
                            .put("newGoal", Math.min(goal(c, false), fresh))
                            .put("reviewGoal", Math.min(goal(c, true), reviews));
            if (bank.id.equals(previous.optString("bank"))) {
                for (String key : new String[] {"newIds", "reviewIds"})
                    if (previous.has(key)) {
                        JSONArray selected = new JSONArray(), old = previous.getJSONArray(key);
                        for (int i = 0; i < old.length(); i++) {
                            String id = old.getString(i);
                            for (BankRepository.Question q : bank.questions)
                                if (id.equals(bank.id + "/" + q.id) && filter(c).matches(bank, q)) {
                                    selected.put(id);
                                    break;
                                }
                        }
                        d.put(key, selected);
                    }
                d.put("newGoal", Math.min(goal(c, false), fresh + count(d, false)))
                        .put("reviewGoal", Math.min(goal(c, true), reviews + count(d, true)));
            }
            if (previous.optBoolean("checkedIn")) d.put("checkedIn", true);
            if (previous.has("bank") && !bank.id.equals(previous.optString("bank")))
                d.put("previousPlan", previous);
            if (enabled(c)
                    && count(d, false) + count(d, true) > 0
                    && count(d, false) >= d.optInt("newGoal")
                    && count(d, true) >= d.optInt("reviewGoal")) d.put("checkedIn", true);
            prefs(c).edit().putString("day-" + today(), d.toString()).apply();
        } catch (Exception ignored) {
        }
        return d;
    }

    static int count(JSONObject day, boolean review) {
        return day.optJSONArray(review ? "reviewIds" : "newIds") == null
                ? 0
                : day.optJSONArray(review ? "reviewIds" : "newIds").length();
    }

    static boolean done(Context c, String bank, String q) {
        JSONObject d = day(c, today());
        String id = bank + "/" + q;
        for (String key : new String[] {"newIds", "reviewIds"}) {
            JSONArray a = d.optJSONArray(key);
            if (a != null)
                for (int i = 0; i < a.length(); i++) if (id.equals(a.optString(i))) return true;
        }
        return false;
    }

    static synchronized void record(Context c, String bank, String q, boolean review) {
        if (!enabled(c)) return;
        try {
            BankRepository b = BankRepository.loadLocation(c, location(c));
            if (!bank.equals(b.id)) return;
            boolean eligible = false;
            for (BankRepository.Question question : b.questions)
                if (question.id.equals(q) && filter(c).matches(b, question)) eligible = true;
            if (!eligible) return;
            JSONObject d = snapshot(c, b);
            // A new plan/bank starts a fresh today ledger without borrowing another bank's
            // progress.
            if (!bank.equals(d.optString("bank"))) d = new JSONObject();
            if (done(c, bank, q)) return;
            if (!d.has("newGoal"))
                d.put("newGoal", goal(c, false)).put("reviewGoal", goal(c, true));
            String key = review ? "reviewIds" : "newIds";
            JSONArray ids = d.optJSONArray(key);
            if (ids == null) ids = new JSONArray();
            ids.put(bank + "/" + q);
            d.put(key, ids).put("bank", bank).put("title", b.title);
            boolean met =
                    count(d, false) >= d.getInt("newGoal")
                            && count(d, true) >= d.getInt("reviewGoal");
            if (met) d.put("checkedIn", true);
            prefs(c).edit().putString("day-" + today(), d.toString()).apply();
            if (met) PlanReminder.cancelNotification(c);
        } catch (Exception ignored) {
            /* The saved answer remains valid even if optional plan bookkeeping fails. */
        }
    }

    static int next(Context c, BankRepository bank) {
        if (!applies(c, bank)) return -1;
        JSONObject d = snapshot(c, bank);
        int newGoal = d.optInt("newGoal", goal(c, false)),
                reviewGoal = d.optInt("reviewGoal", goal(c, true));
        StudyStore store = new StudyStore(c, bank.id);
        if (count(d, true) < reviewGoal) {
            int best = -1;
            long oldest = Long.MAX_VALUE;
            for (int i = 0; i < bank.questions.size(); i++) {
                BankRepository.Question q = bank.questions.get(i);
                long due = store.due(q.id);
                if (q.referenceAvailable
                        && filter(c).matches(bank, q)
                        && store.practiced(q.id)
                        && due <= System.currentTimeMillis()
                        && due < oldest
                        && !done(c, bank.id, q.id)) {
                    best = i;
                    oldest = due;
                }
            }
            if (best >= 0) return best;
        }
        if (count(d, false) < newGoal)
            for (int i = 0; i < bank.questions.size(); i++) {
                BankRepository.Question q = bank.questions.get(i);
                if (q.referenceAvailable
                        && filter(c).matches(bank, q)
                        && !store.practiced(q.id)
                        && !done(c, bank.id, q.id)) return i;
            }
        return -1;
    }

    static String summary(Context c) {
        if (!enabled(c)) return "自由刷题 · 学习计划未开启";
        JSONObject d = day(c, today());
        try {
            d = snapshot(c, BankRepository.loadLocation(c, location(c)));
        } catch (Exception ignored) {
        }
        return "今日计划 · 新题 "
                + count(d, false)
                + "/"
                + d.optInt("newGoal", goal(c, false))
                + " · 复习 "
                + count(d, true)
                + "/"
                + d.optInt("reviewGoal", goal(c, true))
                + (d.optBoolean("checkedIn") ? " · 已打卡" : "");
    }

    static long dueAfterDays(long now, int days) {
        return Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(days)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
