package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.json.*;

public final class SettingsActivity extends PageActivity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    page("设置");
    section("书写方式", "让每一次落笔更顺手 · 修改后自动保存");
    toggle("finger", "启用手指书写", false);
    label("没有触控笔时可用单指书写；关闭后单指拖动画布。双指用于缩放。");
    toggle("penCursor", "显示笔触提示（悬浮 / 落笔圆圈）", false);
    toggle("eraserCursor", "显示橡皮提示（悬浮 / 擦除圆圈）", true);
    section("手写笔快捷动作", "分别设置笔上手势的响应动作");
    toggle("doubleTap", "启用手写笔快捷动作", true);
    penAction("penDoubleTap", "双击");
    penAction("penLongPress", "长按");
    penAction("penSingleTap", "单击");
    label(StylusAdapter.Registry.select().name());
    label("可用手势取决于笔和系统支持。按住侧键落笔也使用长按设置；设备仅上报双击时，单击和长按无法触发。");
    section("多指手势", "轻点触发；拖动和缩放不会触发撤销或重做");
    toggle("twoFingerUndo", "双指单击撤销", false);
    toggle("threeFingerRedo", "三指单击重做", false);
    section("书写体验", "");
    toggle("penPrediction", "低延迟笔迹预测", true);
    label("预测仅用于屏幕预览，保存和提交只使用真实采样点。设备不支持厂商能力时自动使用 Android 兜底。");
    toggle("penHaptics", "vivo / iQOO 书写振动", false);
    section("笔压与橡皮", "控制笔触变化和自动擦除");
    toggle("pressure", "开启笔触压感", true);
    toggle("tiltShading", "启用侧锋阴影", false);
    label("关闭时保持圆形笔尖；开启后随倾斜角度产生更宽、更浅的阴影笔触。");
    toggle("pressureEraser", "开启压感橡皮", false);
    slider("threshold", "压感橡皮阈值", .05f, 1.5f, .85f, "（超过后保持擦除，抬笔再落笔恢复）");
    section("辅助书写", "");
    toggle("shapes", "一笔成形", false);
    slider("shapeDistance", "停留距离阈值", 2, 40, 12, "dp（允许轻微手抖）");
    slider("shapeHold", "停留时间阈值", 200, 2000, 450, "毫秒");
    label("在距离阈值内停留超过时间阈值，自动规整直线、椭圆和矩形；不需要严格停在原点或先抬笔。");
    section("笔与纸张", "两支常用笔，搭配适合手写的行距");
    color("answerColor", "答题笔颜色", 0xff22313f);
    color("correctionColor", "批注笔颜色", 0xffb44747);
    slider("width", "默认笔宽", 1, 12, 2.8f, "dp");
    slider("spacing", "纸张行高 / 行距", 32, 200, 80, "dp");
    section("学习偏好", "");
    toggle("timer", "题目页显示计时器", true);
    toggle("timerAllowPause", "允许点击计时器暂停 / 继续", true);
    toggle("timerAllowReset", "允许长按计时器归零", true);
    label("归零仅重置当前题的显示用时，已记录的学习统计保留。暂停、模型等待和后台时间不计入作答用时。");
    action("每日学习计划与打卡  ›", () -> startActivity(new Intent(this, PlanActivity.class)));
    label("计时器仅显示当前题的累计学习时间；关闭显示仍会统计时长。模型等待、后台及其它页面不计时。");
    section("提交后的显示内容", "原版参考解析在答对、答错时均可显示；修改只影响显示");
    toggle("resultAi", "显示 AI 针对性解析", true);
    toggle("resultStudent", "显示识别到的答案", true);
    toggle("resultScore", "显示分数", true);
    toggle("resultWeaknesses", "显示薄弱点", true);
    toggle("resultReference", "显示原版参考解析", true);
    toggle("resultKnowledge", "显示所属知识点", true);
    section("学习数据", "记录、回顾与导出");
    toggle("logs", "保存 LLM 对话日志", false);
    label("开启后保存请求、图片附件及模型回复。日志包含作答内容，保存在本机；导出不包含 API Key。关闭只停止新增，已有日志仍可导出。");
    action(
        "导出学习记录与对话日志 ZIP",
        () -> startActivity(new Intent(this, StatisticsActivity.class).putExtra("export", true)));
    action("查看已保存的对话日志", () -> startActivity(new Intent(this, LogsActivity.class)));
    toggle("stream", "开启流式输出", false);
    section("智能伴学", "配置支持图片输入的模型服务");
    provider();
    about();
    section("题库与导入", "支持 Markdown、LaTeX 公式与图片题目");
    action("各题库合并提交题数（n合1）  ›", () -> BatchPractice.choose(this));
    action(
        "查看自定义题库规范  ›",
        () -> {
          ScrollView scroll = new ScrollView(this);
          LinearLayout body = new LinearLayout(this);
          body.setOrientation(LinearLayout.VERTICAL);
          body.setPadding(dp(20), dp(8), dp(20), dp(16));
          scroll.addView(body);
          MathView spec = new MathView(this);
          body.addView(spec, new LinearLayout.LayoutParams(-1, dp(48)));
          spec.setMarkdown(BankTemplate.SPEC);
          new AlertDialog.Builder(this)
              .setTitle("自定义题库规范")
              .setView(scroll)
              .setPositiveButton("知道了", null)
              .show();
        });
    action(
        "下载题库模板 ZIP",
        () ->
            startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "handymath-bank-template.zip"),
                41));
  }

  private static final String PROJECT_URL = "https://github.com/xnbx2012/HandyStudy";

  private String appVersion() {
    try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
    catch (Exception ignored) { return "未知"; }
  }

  private void about() {
    section("关于应用", "手写伴学 · HandyStudy");
    label("面向 Android 平板的手写学习工具。支持手写与键盘作答、AI 识别和批改、知识点与薄弱点整理、错题回顾及自定义题库。");
    label("当前版本：" + appVersion());
    action("检查更新  ›", this::checkUpdates);
    action("GitHub 开源地址  ›", () -> openLink(PROJECT_URL));
    label("作者：PeterZhong");
    action("联系作者：xnbx2012@163.com  ›", () ->
        startActivity(new Intent(Intent.ACTION_SENDTO,
            android.net.Uri.parse("mailto:xnbx2012@163.com"))));
  }

  private void openLink(String url) {
    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
  }

  private static int compareVersion(String remote, String current) {
    String[] a = remote.replaceFirst("^[vV]", "").split("\\.");
    String[] b = current.replaceFirst("^[vV]", "").split("\\.");
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      int x = i < a.length ? Integer.parseInt(a[i]) : 0;
      int y = i < b.length ? Integer.parseInt(b[i]) : 0;
      if (x != y) return Integer.compare(x, y);
    }
    return 0;
  }

  private void checkUpdates() {
    Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();
    worker.execute(() -> {
      try {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
            new java.net.URL("https://api.github.com/repos/xnbx2012/HandyStudy/releases/latest")
                .openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "HandyStudy-Android");
        int status = connection.getResponseCode();
        if (status != 200) throw new IOException("GitHub 返回 HTTP " + status);
        StringBuilder body = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
          String line; while ((line = reader.readLine()) != null) body.append(line);
        } finally { connection.disconnect(); }
        JSONObject release = new JSONObject(body.toString());
        String latest = release.getString("tag_name");
        boolean newer = compareVersion(latest, appVersion()) > 0;
        String releaseUrl = release.optString("html_url", PROJECT_URL + "/releases/latest");
        runOnUiThread(() -> {
          if (isDestroyed()) return;
          new AlertDialog.Builder(this)
              .setTitle(newer ? "发现新版本 " + latest : "已是最新版本")
              .setMessage(newer ? "当前版本 " + appVersion() + "。可前往 GitHub 下载适合设备的 APK。"
                  : "当前版本 " + appVersion() + "。")
              .setPositiveButton(newer ? "查看下载" : "知道了", (d, w) -> {
                if (newer) openLink(releaseUrl);
              }).show();
        });
      } catch (Exception e) {
        runOnUiThread(() -> {
          if (!isDestroyed()) new AlertDialog.Builder(this).setTitle("检查更新失败")
              .setMessage("无法获取 GitHub 最新版本：" + e.getMessage())
              .setPositiveButton("知道了", null).show();
        });
      }
    });
  }

  private void penAction(String key, String name) {
    TextView row = label("");
    Runnable refresh = () -> row.setText(name + "：" + AppSettings.PEN_ACTIONS[
        Math.max(0, Math.min(AppSettings.PEN_ACTIONS.length - 1,
            AppSettings.prefs(this).getInt(key + "Action", AppSettings.SWITCH_TOOL)))] + "  ›");
    refresh.run();
    row.setMinHeight(dp(52));
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(name + "响应动作")
        .setSingleChoiceItems(AppSettings.PEN_ACTIONS,
            AppSettings.prefs(this).getInt(key + "Action", AppSettings.SWITCH_TOOL),
            (dialog, which) -> {
              AppSettings.prefs(this).edit().putInt(key + "Action", which).apply();
              refresh.run();
              dialog.dismiss();
            }).setNegativeButton("取消", null).show());
    divider();
  }

  private void toggle(String key, String label, boolean fallback) {
    Switch s = new Switch(this);
    s.setText(label);
    s.setTextSize(15);
    s.setTextColor(INK);
    s.setMinHeight(dp(58));
    s.setPadding(0, dp(12), 0, dp(12));
    s.setThumbTintList(
        new android.content.res.ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {TEAL, 0xff9aa7ad}));
    s.setChecked(
        key.equals("finger")
            ? getSharedPreferences("study", 0).getBoolean(key, fallback)
            : AppSettings.enabled(this, key, fallback));
    panel.addView(s);
    s.setOnCheckedChangeListener(
        (v, on) ->
            (key.equals("finger") ? getSharedPreferences("study", 0) : AppSettings.prefs(this))
                .edit()
                .putBoolean(key, on)
                .apply());
    divider();
  }

  private void slider(String key, String name, float min, float max, float fallback, String unit) {
    float value = AppSettings.prefs(this).getFloat(key, fallback);
    TextView valueText = label("");
    SeekBar bar = new SeekBar(this);
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
    bar.setThumbTintList(android.content.res.ColorStateList.valueOf(TEAL));
    bar.setMax(1000);
    bar.setProgress(Math.round((value - min) / (max - min) * 1000));
    panel.addView(bar);
    valueText.setText(String.format(java.util.Locale.CHINA, "%s：%.2f %s", name, value, unit));
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int n, boolean user) {
            float v = min + (max - min) * n / 1000;
            valueText.setText(String.format(java.util.Locale.CHINA, "%s：%.2f %s", name, v, unit));
            if (user) AppSettings.prefs(SettingsActivity.this).edit().putFloat(key, v).apply();
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
  }

  private void color(String key, String name, int fallback) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(12), 0, dp(12));
    row.setMinimumHeight(dp(64));
    TextView title = new TextView(this);
    title.setText(name);
    title.setTextColor(INK);
    title.setTextSize(15);
    row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    View swatch = new View(this);
    swatch.setBackground(surface(AppSettings.prefs(this).getInt(key, fallback), 9));
    row.addView(swatch, new LinearLayout.LayoutParams(dp(48), dp(30)));
    TextView arrow = new TextView(this);
    arrow.setText("  ›");
    arrow.setTextSize(22);
    arrow.setTextColor(MUTED);
    row.addView(arrow);
    row.setContentDescription(name);
    panel.addView(row);
    divider();
    row.setOnClickListener(v -> colorDialog(key, name, fallback, swatch));
  }

  private void colorDialog(String key, String name, int fallback, View settingSwatch) {
    Dialog dialog = new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(24), dp(24), dp(24), dp(20));
    box.setBackground(surface(0xffffffff, 24));
    TextView title = new TextView(this);
    title.setText(name);
    title.setTextSize(22);
    title.setTextColor(INK);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    box.addView(title);
    TextView desc = new TextView(this);
    desc.setText("选择色相与饱和度，打造自己的笔色");
    desc.setTextColor(MUTED);
    desc.setPadding(0, dp(8), 0, dp(8));
    box.addView(desc);
    float[] hsv = new float[3];
    Color.colorToHSV(AppSettings.prefs(this).getInt(key, fallback), hsv);
    TextView preview = new TextView(this);
    preview.setGravity(Gravity.CENTER);
    preview.setTextSize(16);
    Runnable update =
        () -> {
          int c = Color.HSVToColor(hsv);
          preview.setText(String.format("Aa   123   #%06X", c & 0xffffff));
          preview.setTextColor(hsv[2] > .65f && hsv[1] < .45f ? INK : Color.WHITE);
          preview.setBackground(surface(c, 14));
        };
    ColorWheel wheel = new ColorWheel(this, hsv, update);
    box.addView(wheel, new LinearLayout.LayoutParams(-1, dp(220)));
    box.addView(preview, new LinearLayout.LayoutParams(-1, dp(56)));
    update.run();
    TextView value = new TextView(this);
    value.setText("明度");
    value.setTextColor(MUTED);
    value.setPadding(0, dp(16), 0, 0);
    box.addView(value);
    SeekBar brightness = new SeekBar(this);
    brightness.setMax(100);
    brightness.setProgress(Math.round(hsv[2] * 100));
    brightness.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
    brightness.setThumbTintList(android.content.res.ColorStateList.valueOf(TEAL));
    box.addView(brightness);
    brightness.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int n, boolean u) {
            hsv[2] = n / 100f;
            update.run();
            wheel.invalidate();
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    LinearLayout buttons = new LinearLayout(this);
    for (String text : new String[] {"取消", "使用颜色"}) {
      Button button = new Button(this);
      button.setText(text);
      button.setAllCaps(false);
      button.setTextColor(text.equals("取消") ? TEAL : Color.WHITE);
      button.setBackground(surface(text.equals("取消") ? 0xffe5f0ee : TEAL, 12));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
      lp.setMargins(dp(4), dp(16), dp(4), 0);
      buttons.addView(button, lp);
      button.setOnClickListener(
          v -> {
            if (!text.equals("取消")) {
              int c = Color.HSVToColor(hsv);
              AppSettings.prefs(this).edit().putInt(key, c).apply();
              settingSwatch.setBackground(surface(c, 9));
            }
            dialog.dismiss();
          });
    }
    box.addView(buttons);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    dialog.setContentView(scroll);
    dialog
        .getWindow()
        .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    dialog.show();
    dialog
        .getWindow()
        .setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(400)), -2);
  }

  private static final class ColorWheel extends View {
    final float[] hsv;
    final Runnable changed;
    final Paint p = new Paint(3);

    ColorWheel(Context c, float[] hsv, Runnable r) {
      super(c);
      this.hsv = hsv;
      changed = r;
      setContentDescription("颜色调色盘");
    }

    protected void onDraw(Canvas c) {
      float x = getWidth() / 2f, y = getHeight() / 2f, r = Math.min(x, y) - 12;
      int[] colors = {
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
      };
      p.setShader(new SweepGradient(x, y, colors, null));
      c.drawCircle(x, y, r, p);
      p.setShader(new RadialGradient(x, y, r, Color.WHITE, 0x00ffffff, Shader.TileMode.CLAMP));
      c.drawCircle(x, y, r, p);
      p.setShader(null);
      double angle = Math.toRadians(hsv[0]);
      p.setColor(Color.WHITE);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(3);
      c.drawCircle(
          x + (float) Math.cos(angle) * r * hsv[1],
          y + (float) Math.sin(angle) * r * hsv[1],
          10,
          p);
      p.setColor(INK);
      p.setStrokeWidth(2);
      c.drawCircle(
          x + (float) Math.cos(angle) * r * hsv[1], y + (float) Math.sin(angle) * r * hsv[1], 7, p);
      p.setStyle(Paint.Style.FILL);
    }

    public boolean onTouchEvent(MotionEvent e) {
      float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
      hsv[0] = ((float) Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360;
      hsv[1] =
          Math.min(1, (float) Math.hypot(dx, dy) / (Math.min(getWidth(), getHeight()) / 2f - 12));
      changed.run();
      invalidate();
      if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
      return true;
    }

    public boolean performClick() {
      super.performClick();
      return true;
    }
  }

  private EditText input(String label, String value, boolean secret) {
    label(label);
    EditText e = new EditText(this);
    e.setText(value);
    e.setSingleLine(true);
    e.setInputType(
        secret
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT);
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setPadding(dp(14), dp(12), dp(14), dp(12));
    e.setBackground(surface(0xfff1f5f4, 12));
    panel.addView(e, new LinearLayout.LayoutParams(-1, dp(52)));
    return e;
  }

  private void provider() {
    panel.addView(ProviderUi.picker(this));
    action("模型思考强度 / 关闭思考  ›", () -> ModelThinking.editCurrent(this));
    label("思考强度单独保存到当前提供商。强度越高通常等待越久；可用档位由模型决定，不支持调整时请选择「模型默认」。");
    action("模型输出长度上限  ›", () -> ModelThinking.editOutput(this));
    label("思考和解析正文共用输出额度。长解析被截断时可调高上限；自动适配会为 MiniMax-M3.1-Flash 预留 65536 Token。实际可用上限以提供商为准，上限不等于实际用量。");
    action("管理提供商、费率与余额接口  ›", () -> startActivity(new Intent(this, ProvidersActivity.class)));
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 41 && result == RESULT_OK && data != null) {
      loading("正在写入模板…");
      worker.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
              BankTemplate.write(out);
              runOnUiThread(
                  () -> {
                    if (!isDestroyed()) {
                      loaded();
                      Toast.makeText(this, "模板已下载", Toast.LENGTH_SHORT).show();
                    }
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    if (!isDestroyed()) failure(e);
                  });
            }
          });
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    ProviderUi.refresh(this, content);
  }

  @Override
  protected void onDestroy() {
    worker.shutdownNow();
    super.onDestroy();
  }
}
