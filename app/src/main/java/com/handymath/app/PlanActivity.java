package com.handymath.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.time.*;
import java.util.*;

public final class PlanActivity extends PageActivity {
    private int reminderMinutes;
    private YearMonth month = YearMonth.now();
    private LinearLayout calendar;
    private TextView monthTitle, selectedDay, summary;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        page("学习计划");
        section("今天的小目标", "");
        summary = label(LearningPlan.summary(this));
        label("新题与复习按当日不同题目计数，提交即完成一次练习；达到两项目标自动打卡。关闭计划后可自由刷题，统计仍保留。");
        action(
                "开始今日学习  →",
                () -> {
                    try {
                        BankRepository bank =
                                BankRepository.loadLocation(this, LearningPlan.location(this));
                        int next = LearningPlan.next(this, bank);
                        if (!LearningPlan.enabled(this)) {
                            new AlertDialog.Builder(this)
                                    .setMessage("先在下方开启并保存计划，或返回首页自由刷题。")
                                    .setPositiveButton("知道了", null)
                                    .show();
                            return;
                        }
                        if (next < 0) {
                            new AlertDialog.Builder(this)
                                    .setMessage("今日目标已完成，或暂时没有到期复习 / 未学新题。可以返回题库自由刷题，日历只在达到目标时打卡。")
                                    .setPositiveButton("知道了", null)
                                    .show();
                            return;
                        }
                        startActivity(
                                new Intent(this, MainActivity.class)
                                        .putExtra("bankLocation", bank.location())
                                        .putExtra("questionId", bank.questions.get(next).id)
                                        .putExtra("filterType", LearningPlan.filter(this).type)
                                        .putExtra(
                                                "filterKnowledge",
                                                LearningPlan.filter(this).knowledge));
                    } catch (Exception e) {
                        failure(e);
                    }
                });
        section("计划设置", "修改后点击保存 · 提醒由本机系统发送");
        SharedPreferences p = LearningPlan.prefs(this);
        Switch enabled = switchRow("启用每日学习与打卡", p.getBoolean("enabled", false));
        label("学习题库");
        Spinner banks = new Spinner(this);
        List<BankRepository.Entry> entries;
        try {
            entries = BankRepository.catalog(this);
        } catch (Exception e) {
            failure(e);
            return;
        }
        List<String> titles = new ArrayList<>();
        int selection = 0;
        for (int i = 0; i < entries.size(); i++) {
            titles.add(entries.get(i).title);
            if (entries.get(i).location.equals(LearningPlan.location(this))) selection = i;
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, titles);
        banks.setAdapter(adapter);
        banks.setSelection(selection);
        panel.addView(banks);
        Spinner questionType = new Spinner(this);
        List<String> typeCodes = new ArrayList<>();
        typeCodes.add("");
        final String[] knowledge = {p.getString("knowledge", "")};
        Button knowledgeButton = action("知识点：全部", () -> {});
        Runnable updateScope =
                () -> {
                    try {
                        BankRepository selectedBank =
                                BankRepository.loadLocation(
                                        this,
                                        entries.get(banks.getSelectedItemPosition()).location);
                        typeCodes.clear();
                        typeCodes.add("");
                        for (BankRepository.Question q : selectedBank.questions)
                            if (!typeCodes.contains(q.type)) typeCodes.add(q.type);
                        List<String> names = new ArrayList<>();
                        for (String code : typeCodes)
                            names.add(code.isEmpty() ? "全部题型" : StudyFilter.typeName(code));
                        questionType.setAdapter(
                                new ArrayAdapter<>(
                                        this,
                                        android.R.layout.simple_spinner_dropdown_item,
                                        names));
                        questionType.setSelection(
                                Math.max(0, typeCodes.indexOf(p.getString("type", ""))));
                        if (!knowledge[0].isEmpty()
                                && !selectedBank.knowledge.nodes.containsKey(knowledge[0])
                                && !knowledge[0].startsWith("topic:")) knowledge[0] = "";
                        knowledgeButton.setText(
                                knowledge[0].isEmpty()
                                        ? "知识点：全部"
                                        : selectedBank.knowledge.path(knowledge[0]));
                        knowledgeButton.setOnClickListener(
                                v ->
                                        StudyFilter.pickKnowledge(
                                                this,
                                                selectedBank,
                                                "",
                                                id -> {
                                                    knowledge[0] = id;
                                                    knowledgeButton.setText(
                                                            id.isEmpty()
                                                                    ? "知识点：全部"
                                                                    : selectedBank.knowledge.path(
                                                                            id));
                                                }));
                    } catch (Exception e) {
                        failure(e);
                    }
                };
        panel.addView(questionType);
        banks.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> v,
                            android.view.View row,
                            int pos,
                            long id) {
                        updateScope.run();
                    }

                    public void onNothingSelected(android.widget.AdapterView<?> v) {}
                });
        updateScope.run();
        SeekBar fresh = goalBar("每日新题", LearningPlan.goal(this, false));
        SeekBar review = goalBar("每日复习", LearningPlan.goal(this, true));
        label("错题重现间隔（天）");
        EditText intervals = new EditText(this);
        intervals.setSingleLine(true);
        intervals.setText(p.getString("intervals", "1,3,7,14,30"));
        intervals.setPadding(dp(12), dp(12), dp(12), dp(12));
        intervals.setBackground(surface(0xfff1f5f4, 12));
        panel.addView(intervals);
        label(
                "答错后按第一个间隔重现；随后答对则进入下一间隔，再答错会重新开始。复习优先选择已到期题目，不在当天重复计数。今日目标会按剩余新题与到期复习数量调整，没有到期题时不强制复习。");
        Switch reminder = switchRow("开启打卡提醒", p.getBoolean("reminder", false));
        reminderMinutes = p.getInt("reminderMinutes", 20 * 60);
        Button time = action("", () -> {});
        updateTime(time);
        time.setOnClickListener(
                v ->
                        new TimePickerDialog(
                                        this,
                                        (picker, h, m) -> {
                                            reminderMinutes = h * 60 + m;
                                            updateTime(time);
                                        },
                                        reminderMinutes / 60,
                                        reminderMinutes % 60,
                                        true)
                                .show());
        label("完成当日打卡后不再提醒。省电模式可能延后通知；系统关闭通知时需重新允许。");
        action(
                "系统通知设置  ›",
                () ->
                        startActivity(
                                new Intent(
                                                android.provider.Settings
                                                        .ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(
                                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                                getPackageName())));
        action(
                "保存学习计划",
                () -> {
                    try {
                        LearningPlan.parseIntervals(intervals.getText().toString());
                        if (fresh.getProgress() + review.getProgress() == 0)
                            throw new IllegalArgumentException("新题与复习目标不能同时为 0");
                        String old = p.getString("bank", "@builtin/wzx-full"),
                                loc = entries.get(banks.getSelectedItemPosition()).location;
                        p.edit()
                                .putBoolean("enabled", enabled.isChecked())
                                .putString("bank", loc)
                                .putString(
                                        "type",
                                        typeCodes.get(questionType.getSelectedItemPosition()))
                                .putString("knowledge", knowledge[0])
                                .putInt("new", fresh.getProgress())
                                .putInt("reviews", review.getProgress())
                                .putString("intervals", intervals.getText().toString().trim())
                                .putBoolean("reminder", reminder.isChecked())
                                .putInt("reminderMinutes", reminderMinutes)
                                .apply();
                        JSONObject today = LearningPlan.day(this, LearningPlan.today());
                        if (today.has("bank")) {
                            JSONObject archived = today;
                            today = new JSONObject();
                            if (archived.optBoolean("checkedIn")) today.put("checkedIn", true);
                            today.put("previousPlan", archived);
                            p.edit()
                                    .putString("day-" + LearningPlan.today(), today.toString())
                                    .apply();
                            // Keep today's distinct question counts when adjusting the same bank.
                            if (old.equals(loc)) {
                                for (String field :
                                        new String[] {"bank", "title", "newIds", "reviewIds"})
                                    if (archived.has(field)) today.put(field, archived.get(field));
                                p.edit()
                                        .putString("day-" + LearningPlan.today(), today.toString())
                                        .apply();
                            }
                        }
                        BankRepository active = BankRepository.loadLocation(this, loc);
                        if (enabled.isChecked()) new StudyStore(this, active.id).reschedule(active);
                        LearningPlan.snapshot(this, active);
                        if (enabled.isChecked()
                                && reminder.isChecked()
                                && Build.VERSION.SDK_INT >= 33
                                && checkSelfPermission(
                                                android.Manifest.permission.POST_NOTIFICATIONS)
                                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                            requestPermissions(
                                    new String[] {android.Manifest.permission.POST_NOTIFICATIONS},
                                    73);
                        PlanReminder.cancelNotification(this);
                        PlanReminder.schedule(this);
                        Toast.makeText(this, "学习计划已保存", Toast.LENGTH_SHORT).show();
                        summary.setText(LearningPlan.summary(this));
                        renderCalendar();
                    } catch (Exception e) {
                        failure(e);
                    }
                });
        section("打卡日历", "圆形实心：已达成计划 · 浅色：已练习，目标未完成");
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(nav);
        Button prev = new Button(this);
        prev.setText("‹");
        prev.setTextColor(TEAL);
        prev.setBackground(surface(0xffe5f0ee, 14));
        prev.setOnClickListener(
                v -> {
                    month = month.minusMonths(1);
                    renderCalendar();
                });
        nav.addView(prev);
        monthTitle = new TextView(this);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setTextColor(INK);
        monthTitle.setTextSize(18);
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button next = new Button(this);
        next.setText("›");
        next.setTextColor(TEAL);
        next.setBackground(surface(0xffe5f0ee, 14));
        next.setOnClickListener(
                v -> {
                    month = month.plusMonths(1);
                    renderCalendar();
                });
        nav.addView(next);
        calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        panel.addView(calendar);
        selectedDay = label("点击日期查看当日计划记录");
        renderCalendar();
    }

    private void updateTime(Button b) {
        b.setText(
                String.format(
                        Locale.CHINA,
                        "提醒时间    %02d:%02d  ›",
                        reminderMinutes / 60,
                        reminderMinutes % 60));
    }

    private Switch switchRow(String title, boolean checked) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(15);
        s.setTextColor(INK);
        s.setPadding(0, dp(12), 0, dp(12));
        s.setChecked(checked);
        panel.addView(s);
        divider();
        return s;
    }

    private SeekBar goalBar(String title, int count) {
        TextView t = label(title + "：" + count + " 题");
        SeekBar s = new SeekBar(this);
        s.setMax(100);
        s.setProgress(count);
        s.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(TEAL));
        panel.addView(s);
        s.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar b, int n, boolean u) {
                        t.setText(title + "：" + n + " 题");
                    }

                    public void onStartTrackingTouch(SeekBar b) {}

                    public void onStopTrackingTouch(SeekBar b) {}
                });
        return s;
    }

    private void renderCalendar() {
        calendar.removeAllViews();
        monthTitle.setText(month.getYear() + " 年 " + month.getMonthValue() + " 月");
        LinearLayout weekdays = new LinearLayout(this);
        calendar.addView(weekdays);
        for (String name : new String[] {"一", "二", "三", "四", "五", "六", "日"}) {
            TextView t = new TextView(this);
            t.setText(name);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(MUTED);
            weekdays.addView(t, new LinearLayout.LayoutParams(0, dp(36), 1));
        }
        int start = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int week = 0; week < (start + month.lengthOfMonth() + 6) / 7; week++) {
            LinearLayout row = new LinearLayout(this);
            calendar.addView(row);
            for (int col = 0; col < 7; col++) {
                int n = week * 7 + col - start + 1;
                TextView t = new TextView(this);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(15);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(t, lp);
                if (n < 1 || n > month.lengthOfMonth()) continue;
                String date = month.atDay(n).toString();
                JSONObject d = LearningPlan.day(this, date);
                boolean checked = d.optBoolean("checkedIn"),
                        active = LearningPlan.count(d, false) + LearningPlan.count(d, true) > 0;
                t.setText(String.valueOf(n));
                t.setTextColor(checked ? android.graphics.Color.WHITE : INK);
                t.setBackground(surface(checked ? TEAL : active ? 0xffe5f0ee : 0xfff7f9f8, 24));
                t.setContentDescription(date + (checked ? " 已打卡" : ""));
                t.setOnClickListener(
                        v ->
                                selectedDay.setText(
                                        date
                                                + " · "
                                                + (checked ? "已打卡" : active ? "目标未完成" : "暂无计划记录")
                                                + "\n新题 "
                                                + LearningPlan.count(d, false)
                                                + " / "
                                                + d.optInt("newGoal", 0)
                                                + " · 复习 "
                                                + LearningPlan.count(d, true)
                                                + " / "
                                                + d.optInt("reviewGoal", 0)));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 73) {
            PlanReminder.schedule(this);
            if (!PlanReminder.allowed(this))
                new AlertDialog.Builder(this)
                        .setMessage("系统未允许通知。学习计划与打卡仍正常记录，可在系统通知设置中开启提醒。")
                        .setPositiveButton("知道了", null)
                        .show();
        }
    }
}
