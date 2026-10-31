package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
  private static final int INK = 0xff233446, TEAL = 0xff197d78, MUTED = 0xff647585, BG = 0xfff1f5f4;
  private final ExecutorService worker = Executors.newSingleThreadExecutor(),
      saver = Executors.newSingleThreadExecutor();
  private BankRepository bank;
  private boolean freeSession;
  private StudyStore store;
  private StylusAdapter stylus;
  private int index = 0, hints = 0;
  private JSONArray history = new JSONArray();
  private JSONObject lastResult;
  private InkView ink;
  private LinearLayout root, feedbackBox, questionBox;
  private MathView hintText;
  private AnnotatedPaper paper, hintPaper, resultPaper;
  private JSONObject renderedHint, renderedResult;
  private InkView activeInk;
  private StudyFilter filter = new StudyFilter();
  private TextView status, timerText, zoomLabel;
  private View previousQuestionButton, nextQuestionButton;
  private String renderedResultLayout = "";
  private ScrollView studyScroll;
  private StudySplitLayout studySplit;
  private boolean home = true, busy = false, solved = false, savePending = false;
  private ProgressDialog loadingDialog;
  private Dialog bankImportDialog;
  private LearningDb learning;
  private long clockStart = -1, clockWall, questionMs;
  private long stayStart = -1, stayWall;
  private boolean answerStopped = false, streamStarted = false;
  private String liveText = "";
  private String livePhase = "";
  private boolean streamingRequest;
  private ThinkingPanel thinkingPanel;
  private ThinkingPanel.State thinkingState = new ThinkingPanel.State();
  private TutorClient.Mode liveMode;
  private long lastStreamRender;
  private boolean resumed = false;
  private String lastError = "";
  private Button next;
  private String batchChoice = "";
  private int renderedBatchSize = 1;
  private boolean batchSubmitting;
  private Button batchSummary;
  private LinearLayout batchAnswerBox;
  private final List<Button> actionButtons = new ArrayList<>();
  private final List<ToolIcon> tools = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable saveTask =
      () -> {
        savePending = false;
        if (ink != null && (ink.writing() || paper.ink.writing() || hintPaper.ink.writing() || resultPaper.ink.writing())) {
          scheduleSave();
          return;
        }
        save();
      };
  private final Map<String, Memory> memories = new HashMap<>();

  private static final class Memory {
    InkView.Document document;
    float cx, cy, zoom;
    String history;
    JSONObject annotations;
    int hints;
    JSONObject result;
    boolean solved;
    boolean answerStopped;
    String batchChoice = "";
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    stylus = StylusAdapter.Registry.select();
    stylus.attach(
        this,
        () -> {
          if (activeInk != null && !home && !busy && AppSettings.enabled(this, "doubleTap", true)) {
            boolean erase = !activeInk.eraser;
            ink.eraser = paper.ink.eraser = erase;
            ink.textMode = paper.ink.textMode = false;
            updateTools();
          }
        },
        this::palette);
    stylus.setGestureAllowed(() -> !home && !busy && activeInk != null && !activeInk.viewOnly);
    stylus.setActionHandler(action -> {
      if (home || busy || activeInk == null || activeInk.locked || activeInk.viewOnly) return;
      if (action == AppSettings.UNDO) { activeInk.cancelContact(); activeInk.undo(); }
      else {
        activeInk.cancelContact();
        ink.textMode = paper.ink.textMode = false;
        if (action == AppSettings.SWITCH_TOOL) ink.eraser = paper.ink.eraser = !activeInk.eraser;
        else if (action == AppSettings.SWITCH_TYPE) {
          ink.correction = paper.ink.correction = !activeInk.correction;
          ink.eraser = paper.ink.eraser = false;
          AppSettings.apply(ink); AppSettings.apply(paper.ink);
        }
        updateTools();
      }
    });
    learning = new LearningDb(this);
    if (!handleOpen(getIntent())) showHome();
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private GradientDrawable background(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(14));
    return d;
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(dp(4), dp(4), dp(4), dp(4));
    return t;
  }

  private Button button(String label, Runnable run, boolean primary) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(primary ? Color.WHITE : TEAL);
    b.setBackground(background(primary ? TEAL : 0xffe5f0ee));
    b.setMinHeight(dp(42));
    b.setMinimumWidth(0);
    b.setPadding(dp(12), dp(4), dp(12), dp(4));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(44));
    p.setMargins(dp(3), dp(3), dp(3), dp(3));
    b.setLayoutParams(p);
    b.setOnClickListener(
        v -> {
          if (!busy) run.run();
        });
    return b;
  }

  private void base() {
    if (root != null) MathView.releaseTree(root);
    root = column();
    root.setBackgroundColor(BG);
    root.setPadding(dp(16), dp(8), dp(16), dp(10));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          root.setPadding(
              dp(16) + insets.getSystemWindowInsetLeft(),
              dp(8) + insets.getSystemWindowInsetTop(),
              dp(16) + insets.getSystemWindowInsetRight(),
              dp(10) + insets.getSystemWindowInsetBottom());
          return insets;
        });
    root.requestApplyInsets();
  }

  private void showHome() {
    if (busy) return;
    stopClock();
    save();
    stopResidence();
    home = true;
    setIntent(new Intent(this, MainActivity.class));
    ink = null;
    base();
    LinearLayout header = row();
    TextView title = text("手写伴学 · 我的题库", 26, INK);
    title.setTypeface(null, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(button("收藏", () -> startActivity(new Intent(this, FavoritesActivity.class)), false));
    header.addView(
        button("统计", () -> startActivity(new Intent(this, StatisticsActivity.class)), false));
    header.addView(button("设置", this::settings, false));
    root.addView(header);
    root.addView(
        button(
            LearningPlan.summary(this) + "  ›",
            () -> startActivity(new Intent(this, PlanActivity.class)),
            false));
    root.addView(text("HandyStudy · 选择一本题库，继续最近练习", 14, MUTED));
    LinearLayout list = column();
    ScrollView scroll = new ScrollView(this);
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    try {
      String lastSubject = "";
      List<BankRepository.Entry> catalog = BankRepository.catalog(this);
      catalog.sort(java.util.Comparator.comparing(e -> e.subject));
      for (BankRepository.Entry e : catalog) {
        if (!e.subject.equals(lastSubject)) {
          TextView heading = text(e.subject, 20, INK);
          heading.setTypeface(null, Typeface.BOLD);
          heading.setPadding(0, dp(16), 0, dp(4));
          list.addView(heading);
          lastSubject = e.subject;
        }
        LinearLayout card = column();
        card.setBackground(background(Color.WHITE));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(10), 0, dp(4));
        list.addView(card, p);
        LinearLayout cardHeader = row();
        cardHeader.addView(text(e.title, 22, INK), new LinearLayout.LayoutParams(0, -2, 1));
        cardHeader.addView(button(BatchPractice.size(this,e.id)+"合1",()->BatchPractice.edit(this,e.id,e.title,this::showHome),false));
        if (e.imported()) {
          Button delete = button("删除", () -> confirmDeleteBank(e), false);
          delete.setContentDescription("删除题库：" + e.title);
          cardHeader.addView(delete);
        }
        card.addView(cardHeader);
        int latest =
            Math.min(e.count - 1, getSharedPreferences("study", 0).getInt("index-" + e.id, 0));
        card.addView(
            text(
                e.count
                    + " "
                    + e.unitLabel
                    + " · 最近练习第 "
                    + (latest + 1)
                    + " "
                    + e.unitLabel
                    + "  →",
                15,
                TEAL));
        card.addView(text(e.details, 13, MUTED));
        card.addView(button("只读浏览 · 题目 / 解析 / 知识点", () -> startActivity(
            new Intent(this, BankBrowseActivity.class).putExtra("bankLocation", e.location)), false));
        card.setContentDescription(e.title);
        card.setOnClickListener(v -> openBank(e.location));
      }
    } catch (Exception e) {
      error(e);
    }
    root.addView(button("＋ 自定义题目", () -> startActivity(new Intent(this, CustomQuestionActivity.class)), true));
    root.addView(button("＋ 导入题库 ZIP", this::importBank, true));
  }

  private void openBank(String location) {
    freeSession = false;
    try {
      bank = BankRepository.loadLocation(this, location);
      store = new StudyStore(this, bank.id);
      filter = StudyFilter.load(this, bank.id);
      if (getIntent().hasExtra("filterType"))
        filter.type = getIntent().getStringExtra("filterType");
      if (getIntent().hasExtra("filterKnowledge"))
        filter.knowledge = getIntent().getStringExtra("filterKnowledge");
      filter.save(this, bank.id);
      index =
          Math.max(
              0,
              Math.min(
                  bank.questions.size() - 1,
                  getSharedPreferences("study", 0).getInt("index-" + bank.id, 0)));
      getSharedPreferences("study", 0).edit().putString("bank", location).apply();
      if (!filter.matches(bank, bank.questions.get(index))) {
        for (int i = 0; i < bank.questions.size(); i++)
          if (filter.matches(bank, bank.questions.get(i))) {
            index = i;
            break;
          }
      }
      home = false;
      loadQuestion();
    } catch (Exception e) {
      error(e);
    }
  }

  private BankRepository.Question question() {
    return bank.questions.get(index);
  }

  private void loadQuestion() {
    stopClock();
    stopResidence();
    answerStopped = false;
    liveText = "";
    streamStarted = false;
    streamingRequest = false;livePhase = "";thinkingState = new ThinkingPanel.State();
    questionMs = learning.questionTime(bank.id, question().id);
    hints = 0;
    history = new JSONArray();
    lastResult = null;
    batchChoice = "";
    solved = false;
    build();
    try {
      Memory m = memories.get(bank.id + "/" + question().id);
      if (m != null) {
        hints = m.hints;
        history = new JSONArray(m.history);
        lastResult = m.result;
        solved = m.solved;
        answerStopped = m.answerStopped;
        batchChoice = m.batchChoice;
        ink.restore(m.document.json(m.cx, m.cy, m.zoom));
        if (m.annotations != null) paper.restore(m.annotations);
      } else {
        JSONObject state = store.load(question().id);
        hints = state.optInt("hints", 0);
        history = state.optJSONArray("history");
        if (history == null) history = new JSONArray();
        lastResult = state.optJSONObject("result");
        solved = state.optBoolean("solved", false);
        answerStopped = state.optBoolean("answerStopped", lastResult != null);
        batchChoice = state.optString("batchChoice", "");
        if (state.has("ink")) ink.restore(state.getJSONObject("ink"));
        else if (!question().initialStudentMarkdown.trim().isEmpty())
          ink.putText(null, question().initialStudentMarkdown, 24, 0, 680);
        if (state.has("annotations")) paper.restore(state.getJSONObject("annotations"));
      }
      if (lastResult != null && !lastResult.has("studentWork")) {
        lastResult = null;
        solved = false;
      }
      if(!batchMode()&&!batchChoice.isEmpty()){
        RectF bounds=InkView.contentBounds(ink.strokes);
        ink.putText(null,"最终选项："+batchChoice,24,bounds==null?0:bounds.bottom+80,680);
        batchChoice="";
      }
      JSONObject thinkingSource = lastResult;
      JSONObject lastHint = history.optJSONObject(history.length() - 1);
      if (lastHint != null && (thinkingSource == null || lastHint.optLong("generatedAt") > thinkingSource.optLong("generatedAt"))) thinkingSource = lastHint;
      thinkingState = ThinkingPanel.State.from(thinkingSource == null ? null : thinkingSource.optJSONArray("thinking"));
      thinkingPanel.setState(thinkingState);
      refreshFeedback();
    } catch (Exception e) {
      error(e);
    }
    getSharedPreferences("study", 0).edit().putInt("index-" + bank.id, index).apply();
    AppSettings.apply(ink);
    AppSettings.apply(paper.ink);
    startResidence();
    startClock();
  }

  private MathView math(LinearLayout parent, String value) {
    MathView v = new MathView(this);
    parent.addView(v, new LinearLayout.LayoutParams(-1, dp(48)));
    v.setMarkdown(value);
    return v;
  }

  private void build() {
    renderedBatchSize=BatchPractice.size(this,bank.id);
    base();
    actionButtons.clear();
    tools.clear();
    next = null;
    LinearLayout header = row();
    header.addView(button("‹ 首页", this::showHome, false));
    TextView title = text(question().title, 18, INK);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        ProviderUi.picker(this),
        new LinearLayout.LayoutParams(
            dp(Math.max(78, Math.min(190, getResources().getConfiguration().screenWidthDp / 5))),
            dp(42)));
    header.addView(button("选题", this::showLibrary, false));
    Button favorite = button(learning.favorite(bank.id, question().id) ? "★ 已收藏" : "☆ 收藏", () -> {}, false);
    favorite.setOnClickListener(v -> {
      if (learning.favorite(bank.id, question().id)) learning.unfavorite(bank.id, question().id);
      else learning.favorite(bank, question());
      favorite.setText(learning.favorite(bank.id, question().id) ? "★ 已收藏" : "☆ 收藏");
    });
    header.addView(favorite);
    header.addView(button("设置", this::settings, false));
    root.addView(header);
    status = text("", 12, MUTED);
    timerText = text("", 12, TEAL);
    timerText.setMinHeight(dp(32));
    timerText.setGravity(Gravity.CENTER);
    timerText.setContentDescription("作答计时器：点击暂停或继续，长按归零");
    timerText.setOnClickListener(v -> toggleTimer());
    timerText.setOnLongClickListener(v -> { resetTimer(); return true; });
    LinearLayout statusLine = row();
    statusLine.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    statusLine.addView(timerText);
    root.addView(statusLine);
    boolean landscape =
        getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    studySplit = new StudySplitLayout(this, bank.id, landscape);
    root.addView(studySplit, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout left = column();
    left.setPadding(dp(10), dp(4), dp(10), dp(8));
    left.setBackground(background(Color.WHITE));
    studyScroll = new ScrollView(this);
    studyScroll.addView(left);
    paper = new AnnotatedPaper(this, stylus);
    questionBox = paper.body;
    left.addView(paper, new LinearLayout.LayoutParams(-1, -2));
    AppSettings.apply(paper.ink);
    paper.ink.finger = getSharedPreferences("study", 0).getBoolean("finger", false);
    paper.ink.changed = this::scheduleSave;
    LinearLayout chapterLine = row();
    TextView chapterTitle = text(question().chapter + " · " + (index + 1) + " / " + bank.questions.size(), 14, TEAL);
    chapterLine.addView(chapterTitle, new LinearLayout.LayoutParams(0, -2, 1));
    previousQuestionButton = questionNavigation("‹", "上一题", -1);
    nextQuestionButton = questionNavigation("›", "下一题", 1);
    chapterLine.addView(previousQuestionButton, new LinearLayout.LayoutParams(dp(34), dp(34)));
    chapterLine.addView(nextQuestionButton, new LinearLayout.LayoutParams(dp(34), dp(34)));
    left.addView(chapterLine, 0);
    if (!question().questionMarkdown.isEmpty()) math(questionBox, question().questionMarkdown);
    for (String picture : question().questionImages)
      try {
        addImage(questionBox, picture, dp(1200));
      } catch (Exception e) {
        error(e);
      }
    if (!question().studentSourceImages.isEmpty())
      left.addView(button("核对原手写作答照片", () -> {
        if (question().studentSourceImages.size() == 1) showImage(question().studentSourceImages.get(0));
        else {
          String[] labels = new String[question().studentSourceImages.size()];
          for (int i = 0; i < labels.length; i++) labels[i] = "手写作答照片 " + (i + 1);
          new AlertDialog.Builder(this).setTitle("核对识别来源").setItems(labels,
              (d, which) -> showImage(question().studentSourceImages.get(which))).show();
        }
      }, false));
    if (bank.materials.length() > 0)
      left.addView(
          button(
              "题库附加资料 / 听力",
              () ->
                  startActivity(
                      new Intent(this, BankMaterialsActivity.class)
                          .putExtra("bankLocation", bank.location())),
              false));
    batchAnswerBox = column();left.addView(batchAnswerBox);
    thinkingPanel = new ThinkingPanel(this, thinkingState);
    left.addView(thinkingPanel, new LinearLayout.LayoutParams(-1, -2));
    hintPaper = generatedPaper("思路提示手写批注，手指滚动提示");
    renderedHint = renderedResult = null;
    hintText = math(hintPaper.body, "");
    hintPaper.setVisibility(View.GONE);
    left.addView(hintPaper);
    feedbackBox = column();
    left.addView(feedbackBox);
    resultPaper = generatedPaper("答案解析手写批注，手指滚动解析");
    FrameLayout canvasFrame = new FrameLayout(this);
    canvasFrame.setBackground(background(Color.WHITE));
    ink = new InkView(this, stylus);
    AppSettings.apply(ink);
    ink.finger = getSharedPreferences("study", 0).getBoolean("finger", false);
    ink.changed = this::scheduleSave;
    // Generated feedback belongs to the last submission. Later annotations only
    // update the draft; they must not invalidate or rewrite that feedback.
    paper.ink.activated = () -> activeInk = paper.ink;
    ink.activated = () -> activeInk = ink;
    activeInk = ink;
    ink.viewportChanged =
        () -> {
          if (zoomLabel != null)
            zoomLabel.setText(String.format(Locale.CHINA, "%.0f%% · 手指拖动 / 双指缩放", ink.zoom * 100));
        };
    canvasFrame.addView(ink, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout toolbar = row();
    String[][] specs = {
      {"pen", "答题笔"},
      {"erase", "橡皮擦"},
      {"pen", "修改笔"},
      {"undo", "撤销"},
      {"redo", "重做"},
      {"trash", "清空当前区域"},
      {"fit", "查看全部笔迹"},
      {"more", "回到原点与识别预览"},
      {"text", "打字：点击答题区某一行输入，长按文字编辑"}
    };
    for (int i = 0; i < specs.length; i++) {
      final int tool = i;
      ToolIcon icon =
          new ToolIcon(
              this,
              specs[i][0],
              specs[i][1],
              () -> {
                if (busy) return;
                switch (tool) {
                  case 0:
                    ink.textMode = paper.ink.textMode = false;
                    activeInk.correction = false;
                    activeInk.eraser = false;
                    AppSettings.apply(activeInk);
                    break;
                  case 1:
                    ink.textMode = paper.ink.textMode = false;
                    activeInk.eraser = true;
                    break;
                  case 2:
                    ink.textMode = paper.ink.textMode = false;
                    activeInk.correction = true;
                    activeInk.eraser = false;
                    AppSettings.apply(activeInk);
                    break;
                  case 3:
                    activeInk.undo();
                    break;
                  case 4:
                    activeInk.redo();
                    break;
                  case 5:
                    new AlertDialog.Builder(this)
                        .setTitle("清空当前区域笔迹及本题答案解析？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("清空", (d, w) -> clearCurrentArea())
                        .show();
                    break;
                  case 6:
                    activeInk.fitInk();
                    break;
                  case 8:
                    activeInk = ink;
                    ink.textMode = !ink.textMode;
                    ink.eraser = false;
                    android.widget.Toast.makeText(this, ink.textMode ? "点击答题区的一行输入文字；长按已有文字可编辑" : "已退出打字模式", android.widget.Toast.LENGTH_SHORT).show();
                    break;
                  default:
                    new AlertDialog.Builder(this)
                        .setItems(
                            new String[] {"缩放恢复 100% / 回到原点", "提示记录", "查看最后提交的识别内容"},
                            (d, w) -> {
                              if (w == 0) activeInk.home();
                              else if (w == 1) showHistory();
                              else showRecognition();
                            })
                        .show();
                }
                if (tool <= 2) {
                  ink.correction = paper.ink.correction = activeInk.correction;
                  ink.eraser = paper.ink.eraser = activeInk.eraser;
                  AppSettings.apply(ink);
                  AppSettings.apply(paper.ink);
                }
                updateTools();
              });
      toolbar.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
      tools.add(icon);
    }
    HorizontalScrollView toolScroll = new HorizontalScrollView(this);
    toolScroll.setHorizontalScrollBarEnabled(false);
    toolScroll.addView(toolbar);
    LinearLayout commandRow = row();
    commandRow.setContentDescription("画布工具与作答操作");
    commandRow.addView(toolScroll, new LinearLayout.LayoutParams(0, dp(42), 1));
    root.addView(commandRow, 2, new LinearLayout.LayoutParams(-1, dp(50)));
    zoomLabel = text("100% · 手指拖动 / 双指缩放", 11, MUTED);
    FrameLayout.LayoutParams zp =
        new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
    zp.setMargins(dp(8), 0, 0, dp(4));
    canvasFrame.addView(zoomLabel, zp);
    studySplit.setPanes(studyScroll, canvasFrame);
    LinearLayout actions = row();
    for (TutorClient.Mode mode : TutorClient.Mode.values()) {
      String label =
          switch (mode) {
            case GRADE -> "提交批改";
            case HINT -> "没思路";
            case PARTIAL -> "只能做到这";
          };
      if(mode==TutorClient.Mode.GRADE&&batchMode())label="提交本组（"+batchGroup().size()+"题）";
      Button b = button(label, () -> request(mode), mode == TutorClient.Mode.GRADE);
      b.setSingleLine(true);
      b.setMinWidth(dp(88));
      b.setMinimumWidth(dp(88));
      b.setPadding(dp(16), dp(4), dp(16), dp(4));
      actions.addView(b);
      actionButtons.add(b);
    }
    commandRow.addView(actions, new LinearLayout.LayoutParams(-2, -1));
    updateTools();
  }

  private void updateTools() {
    syncGeneratedTools();
    for (int i = 0; i < tools.size(); i++) {
      if (i == 0 || i == 2)
        tools.get(i).tint =
            AppSettings.prefs(this)
                .getInt(
                    i == 0 ? "answerColor" : "correctionColor", i == 0 ? 0xff22313f : 0xffb44747);
      tools
          .get(i)
          .setSelected(
              i == 0 && !ink.textMode && !ink.eraser && !ink.correction
                  || i == 1 && ink.eraser
                  || i == 2 && !ink.textMode && !ink.eraser && ink.correction
                  || i == 8 && ink.textMode);
      tools.get(i).invalidate();
    }
    if (ink != null) ink.invalidate();
  }

  private void addImage(LinearLayout target, String path, int max) throws Exception {
    ImageView v = new ImageView(this);
    v.setImageBitmap(bank.image(path));
    v.setAdjustViewBounds(true);
    v.setMaxHeight(max);
    v.setScaleType(ImageView.ScaleType.FIT_CENTER);
    v.setContentDescription("题面或解析图片，可缩放查看");
    target.addView(v, new LinearLayout.LayoutParams(-1, -2));
    v.setOnClickListener(w -> showImage(path));
  }

  private void showImage(String path) {
    startActivity(
        new Intent(this, ImageViewerActivity.class)
            .putExtra("bank", bank.location())
            .putExtra("image", path));
  }

  private void showHistory() {
    StringBuilder s = new StringBuilder("## 提示记录\n");
    for (int i = 0; i < history.length(); i++) {
      JSONObject h = history.optJSONObject(i);
      s.append("\n### ")
          .append(i + 1)
          .append("\n")
          .append(h.optString("hint"))
          .append("\n")
          .append(h.optString("feedback"));
    }
    showMathPage("提示记录", s.toString());
  }

  private void showRecognition() {
    showMathPage(
        "最后一次提交的识别",
        lastResult == null
            ? "还没有提交批改。"
            : lastResult.optString("studentWork", "旧版本评分未保存识别内容，请重新提交。"));
  }

  private void showMathPage(String title, String body) {
    ScrollView scroll = new ScrollView(this);
    LinearLayout c = column();
    scroll.addView(c);
    math(c, body);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setView(scroll)
        .setPositiveButton("关闭", null)
        .show();
  }

  private AnnotatedPaper generatedPaper(String description) {
    AnnotatedPaper page = new AnnotatedPaper(this, stylus);
    page.ink.setContentDescription(description);
    AppSettings.apply(page.ink);
    page.ink.finger = getSharedPreferences("study", 0).getBoolean("finger", false);
    page.ink.activated = () -> activeInk = page.ink;
    return page;
  }

  private void bindAnnotations(AnnotatedPaper page, JSONObject source) {
    bindAnnotations(page, source, null);
  }

  private void bindAnnotations(AnnotatedPaper page, JSONObject source, String layout) {
    try {
      JSONObject saved = source == null ? null : source.optJSONObject("inkAnnotations");
      if (layout != null && source != null) {
        JSONObject layouts = source.optJSONObject("annotationLayouts");
        saved = layouts == null ? null : layouts.optJSONObject(layout);
        if (saved == null && layout.equals("111111")) saved = source.optJSONObject("inkAnnotations");
      }
      page.restore(saved == null ? new JSONObject() : saved);
      page.ink.changed = () -> {
        if (source == null) return;
        try {
          JSONObject marks = page.ink.freeze().json(0, 0, 1);
          if (layout == null || layout.equals("111111")) source.put("inkAnnotations", marks);
          if (layout != null) {
            JSONObject layouts = source.optJSONObject("annotationLayouts");
            if (layouts == null) { layouts = new JSONObject(); source.put("annotationLayouts", layouts); }
            layouts.put(layout, marks);
          }
        }
        catch (JSONException e) { error(e); }
        scheduleSave();
      };
    } catch (Exception e) { error(e); }
  }

  private void syncGeneratedTools() {
    if (ink == null) return;
    for (AnnotatedPaper page : new AnnotatedPaper[]{hintPaper, resultPaper}) {
      if (page == null) continue;
      page.ink.correction = ink.correction;
      page.ink.eraser = ink.eraser;
      page.ink.textMode = false;
      page.ink.finger = ink.finger;
      page.ink.locked = busy;
      AppSettings.apply(page.ink);
      page.ink.invalidate();
    }
  }

  private void refreshFeedback() {
    if (home || ink == null) return;
    refreshBatchControls();
    if (busy && streamStarted) {
      showLive(true);
      return;
    }
    updateStatus();
    hintPaper.setVisibility(history.length() > 0 ? View.VISIBLE : View.GONE);
    hintPaper.ink.setVisibility(View.VISIBLE);
    JSONObject latestHint = history.optJSONObject(history.length() - 1);
    if (renderedHint != latestHint) { bindAnnotations(hintPaper, latestHint); renderedHint = latestHint; }
    String resultLayout = resultLayout();
    if (renderedResult != lastResult || !renderedResultLayout.equals(resultLayout)) {
      bindAnnotations(resultPaper, lastResult, resultLayout); renderedResult = lastResult; renderedResultLayout = resultLayout;
    }
    if (history.length() > 0) {
      JSONObject h = history.optJSONObject(history.length() - 1);
      hintText.setMarkdown(
          "**提示 " + hints + "**  " + h.optString("hint") + "\n\n" + h.optString("feedback"));
    }
    // Detach the annotation page before rebuilding the surrounding action buttons.
    if (resultPaper.getParent() == feedbackBox) feedbackBox.removeView(resultPaper);
    feedbackBox.removeAllViews();
    MathView.releaseTree(resultPaper.body);
    resultPaper.body.removeAllViews();
    feedbackBox.setVisibility(lastResult == null ? View.GONE : View.VISIBLE);
    if (lastResult != null) {
      if(batchMode())feedbackBox.addView(button("查看本组 "+batchGroup().size()+" 题批改结果",this::showBatchGroup,false));
      feedbackBox.addView(resultPaper);
      if (AppSettings.enabled(this, "resultScore", true))
        resultPaper.body.addView(text("最近批改 · " + lastResult.optDouble("score") + " / 10", 21, TEAL));
      if (AppSettings.enabled(this, "resultStudent", true))
        math(resultPaper.body, "**识别到你的答案**\n\n" + lastResult.optString("studentWork", "旧评分没有识别记录，建议重新提交。"));
      if (AppSettings.enabled(this, "resultAi", true))
        math(resultPaper.body, "**针对你的作答点评**\n\n" + lastResult.optString("feedback", ""));
      if (AppSettings.enabled(this, "resultReference", true)) {
        String heading = question().referencePolicy.equals("reference") ? "### 原版参考解析\n" : "### 评价依据（源题集未附官方答案）\n";
        math(resultPaper.body, heading + (question().answerMarkdown.isEmpty() && question().answers.isEmpty()
            ? "题库未提供参考解析。" : question().answerMarkdown));
        for (String p : question().answers) try { addImage(resultPaper.body, p, dp(1000)); } catch (Exception e) { error(e); }
      }
      if (AppSettings.enabled(this, "resultWeaknesses", true)) {
        JSONArray weak = lastResult.optJSONArray("weaknesses");
        StringBuilder body = new StringBuilder("**本次薄弱点**\n");
        if (weak == null || weak.length() == 0) body.append("\n本次未记录薄弱点。");
        else for (int i = 0; i < weak.length(); i++) body.append("\n- ").append(weak.optString(i));
        math(resultPaper.body, body.toString());
      }
      if (AppSettings.enabled(this, "resultKnowledge", true)) math(resultPaper.body, knowledgeMarkdown());
      feedbackBox.addView(
          button(
              "智能生成近似题",
              () ->
                  startActivity(
                      new Intent(this, PracticeActivity.class)
                          .putExtra("attemptId", lastResult.optLong("attemptId", -1))
                          .putExtra("bankLocation", bank.location())
                          .putExtra("questionId", question().id)
                          .putExtra("result", lastResult.toString())),
              false));
      if (answerStopped)
        feedbackBox.addView(
            button(
                "继续作答（重新开始计时）",
                () -> {
                  answerStopped = false;
                  startClock();
                  save();
                  updateStatus();
                },
                false));
      next = button(batchMode()?"下一组 →":"下一题 →", batchMode()?this::advanceBatch:this::advance, true);
      feedbackBox.addView(next);
    }
    if (activeInk == resultPaper.ink && lastResult == null || activeInk == hintPaper.ink && history.length() == 0) activeInk = ink;
    syncGeneratedTools();
    ink.locked = busy;
    if (paper != null) paper.ink.locked = busy;
    for (Button b : actionButtons) b.setEnabled(!busy);
    if (previousQuestionButton != null) previousQuestionButton.setEnabled(!busy && adjacentIndex(-1) >= 0);
    if (nextQuestionButton != null) nextQuestionButton.setEnabled(!busy && adjacentIndex(1) >= 0);
    if (next != null) next.setEnabled(!busy);
  }

  private void request(TutorClient.Mode mode) {
    if(mode==TutorClient.Mode.GRADE&&batchMode()){requestBatch(false);return;}
    if (ink.writing() || paper.ink.writing() || hintPaper.ink.writing() || resultPaper.ink.writing()) {
      Toast.makeText(this, "请先完成当前文字输入或书写，再向 AI 提问", Toast.LENGTH_SHORT).show();
      return;
    }
    if (!question().referenceAvailable) {
      error(new Exception("本题尚未配对可靠的原书解析，暂不启用自动批改或提示；可以继续手写并保存练习。"));
      return;
    }
    if (busy) return;
    lastError = "";
    if (mode != TutorClient.Mode.HINT && !ink.hasInk() && !paper.ink.hasInk()) {
      toast("请先写下你的思路或答案");
      return;
    }
    final ProviderSettings config;
    try {
      config = ProviderSettings.load(this);
      config.validate();
      if (!config.visionVerified) {
        toast("请先检测并保存多模态提供商");
        settings();
        return;
      }
    } catch (Exception e) {
      settings();
      return;
    }
    save();
    final InkView.Document document = ink.freeze();
    final BankRepository targetBank = bank;
    final BankRepository.Question targetQuestion = question();
    final int level = mode == TutorClient.Mode.HINT ? Math.min(hints + 1, 4) : hints;
    final JSONArray previous;
    try {
      previous = new JSONArray(history.toString());
      // Reading annotations stay in the local draft, outside model prompt history.
      for (int i = 0; i < previous.length(); i++)
        {previous.getJSONObject(i).remove("inkAnnotations");previous.getJSONObject(i).remove("thinking");}
    } catch (Exception e) {
      error(e);
      return;
    }
    stopClock();
    final JSONObject submittedAnnotations;
    final InkView.Document annotationDocument = paper.ink.freeze();
    final boolean hasQuestionAnnotations = paper.ink.hasInk();
    final java.util.List<android.graphics.Bitmap> annotatedPictures;
    try {
      submittedAnnotations = annotationDocument.json(0, 0, 1);
      annotatedPictures = paper.snapshot();
    } catch (Exception e) {
      startClock();
      error(e);
      return;
    }
    final boolean wasPaused = answerStopped;
    if (mode == TutorClient.Mode.GRADE) answerStopped = true;
    liveMode = mode;
    liveText = "";
    streamStarted = false;
    streamingRequest = AppSettings.enabled(this, "stream", false);
    livePhase = "正在连接模型…";
    thinkingState = new ThinkingPanel.State();thinkingPanel.setState(thinkingState);
    if (streamingRequest) {
      thinkingPanel.start();
      studyScroll.post(() -> studyScroll.smoothScrollTo(0, thinkingPanel.getTop()));
    }
    lastStreamRender = 0;
    save();
    busy = true;
    showLoading();
    refreshFeedback();
    worker.execute(
        () -> {
          List<InkView.Sheet> sheets = new ArrayList<>();
          try {
            sheets = document.export();
            // The question snapshot already contains ink at its original position.
            // Do not send a second, context-free copy of those annotation strokes.
            if (sheets.size() + annotatedPictures.size() > 64)
              throw new java.io.IOException("作答与批注合计超过64张图片，请减少笔迹后提交");
            long imagePixels = 0;
            for (InkView.Sheet sheet : sheets)
              imagePixels += (long) sheet.bitmap.getWidth() * sheet.bitmap.getHeight();
            for (android.graphics.Bitmap picture : annotatedPictures)
              imagePixels += (long) picture.getWidth() * picture.getHeight();
            if (imagePixels > Math.min(24000000L, Runtime.getRuntime().maxMemory() / 24))
              throw new java.io.IOException("题面、批注与作答图片合计超过内存预算，请减少内容后提交");
            if (sheets.isEmpty() && !hasQuestionAnnotations && mode != TutorClient.Mode.HINT)
              throw new java.io.IOException("当前没有可见笔迹（可能已全部擦除）");
            TutorClient.Reply reply =
                TutorClient.ask(
                    config,
                    targetBank,
                    targetQuestion,
                    sheets,
                    mode,
                    level,
                    previous,
                    annotatedPictures,
                    new TutorClient.StreamListener() {
                      public void stage(int step, String label) {
                        runOnUiThread(() -> {
                          if (isDestroyed() || !busy) return;
                          livePhase = label;hideLoading();thinkingPanel.stage(step, label);updateStatus();
                        });
                      }
                      public void thinking(int step, String text, boolean finished) {
                        runOnUiThread(() -> {
                          if (isDestroyed() || !busy) return;
                          hideLoading();thinkingPanel.thinking(step, text, finished);
                        });
                      }
                      public void partial(String partial) {runOnUiThread(
                            () -> {
                              if (isDestroyed() || !busy) return;
                              long now = SystemClock.uptimeMillis();
                              liveText = partial;
                              if (!streamStarted) {
                                streamStarted = true;
                                hideLoading();
                              }
                              if (now - lastStreamRender >= 100) {
                                lastStreamRender = now;
                                showLive(true);
                              }
                            });}
                    });
            final long attemptId;
            if (mode == TutorClient.Mode.GRADE) {
              try (LearningDb db = new LearningDb(this)) {
                attemptId =
                    db.grade(this, targetBank, targetQuestion, reply, document, previous.length());
                db.annotations(attemptId, submittedAnnotations);
              }
            } else attemptId = -1;
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  busy = false;
                  thinkingPanel.finish(true);
                  hideLoading();
                  if (streamStarted) showLive(false);
                  streamStarted = false;
                  startClock();
                  try {
                    if (mode == TutorClient.Mode.GRADE) {
                      solved = reply.correct;
                      lastResult =
                          new JSONObject()
                              .put("score", reply.score)
                              .put("correct", reply.correct)
                              .put("feedback", reply.feedback)
                              .put("studentWork", reply.studentWork)
                              .put("weaknesses", reply.weaknesses)
                              .put("tags", reply.tags)
                              .put("thinking", reply.thinking)
                              .put("generatedAt", System.currentTimeMillis())
                              .put("attemptId", attemptId);
                      store.grade(question().id, reply.score, reply.weaknesses, history.length());
                    } else {
                      if (mode == TutorClient.Mode.HINT) hints++;
                      history.put(
                          new JSONObject()
                              .put("mode", mode.name())
                              .put("hint", reply.hint)
                              .put("feedback", reply.feedback)
                              .put("thinking", reply.thinking)
                              .put("generatedAt", System.currentTimeMillis())
                              .put("partialValid", reply.partialValid));
                    }
                    save();
                    refreshFeedback();
                    studyScroll.post(
                        () ->
                            studyScroll.smoothScrollTo(
                                0,
                                mode == TutorClient.Mode.GRADE
                                    ? feedbackBox.getTop()
                                    : hintPaper.getTop()));
                  } catch (Exception e) {
                    error(e);
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  busy = false;
                  thinkingPanel.finish(false);
                  answerStopped = wasPaused;
                  hideLoading();
                  startClock();
                  refreshFeedback();
                  if (streamStarted) {
                    liveText += "\n\n> 本次输出未完成，未保存为有效结果。请重试。";
                    showLive(false);
                  }
                  error(e);
                });
          } finally {
            for (InkView.Sheet s : sheets) s.bitmap.recycle();
            for (android.graphics.Bitmap picture : annotatedPictures) picture.recycle();
          }
        });
  }

  private void clearCurrentArea() {
    if (busy || activeInk == null) return;
    thinkingState = new ThinkingPanel.State();thinkingPanel.setState(thinkingState);
    activeInk.clear();
    if(activeInk==ink)batchChoice="";
    lastResult = null;
    solved = false;
    refreshFeedback();
    scheduleSave();
  }

  private void scheduleSave() {
    handler.removeCallbacks(saveTask);
    savePending = true;
    handler.postDelayed(saveTask, 1400);
  }

  private void save() {
    handler.removeCallbacks(saveTask);
    savePending = false;
    if (ink == null || store == null || home || busy && batchSubmitting) return;
    ink.commitText();
    Memory m = new Memory();
    m.document = ink.freeze();
    m.cx = ink.worldX(ink.getWidth() / 2f);
    m.cy = ink.worldY(ink.getHeight() / 2f);
    m.zoom = ink.zoom;
    m.history = history.toString();
    m.hints = hints;
    m.solved = solved;
    m.answerStopped = answerStopped;
    m.batchChoice = batchChoice;
    try {
      m.annotations = paper.ink.freeze().json(0, 0, 1);
    } catch (Exception ignored) {
    }
    try { m.result = lastResult == null ? null : new JSONObject(lastResult.toString()); }
    catch (JSONException e) { error(e); return; }
    String id = question().id;
    String bankId = bank.id;
    List<android.graphics.Bitmap> captured = Collections.emptyList();
    if(batchMode()&&BatchPractice.hasInk(m.annotations)&&paper.getWidth()>0) {
      try{captured=paper.snapshot();}catch(Exception e){toast("题面批注截图暂未保存，请回到本题后再合并提交");}
    }
    final List<android.graphics.Bitmap> batchPictures=captured;
    StudyStore target = store;
    memories.put(bank.id + "/" + id, m);
    saver.execute(
        () -> {
          try {
            JSONObject s =
                new JSONObject()
                    .put("ink", m.document.json(m.cx, m.cy, m.zoom))
                    .put("history", new JSONArray(m.history))
                    .put("hints", m.hints)
                    .put("solved", m.solved)
                    .put("answerStopped", m.answerStopped)
                    .put("batchChoice", m.batchChoice)
                    .put("annotations", m.annotations);
            if (m.result != null) s.put("result", m.result);
            target.save(id, s);
            if(!batchPictures.isEmpty())BatchPractice.writeSnapshot(this,bankId,id,m.annotations,batchPictures);
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (!isDestroyed()) toast("草稿保存失败：" + e.getMessage());
                });
          }finally{for(android.graphics.Bitmap picture:batchPictures)picture.recycle();}
        });
  }

  private String resultLayout() {
    StringBuilder key = new StringBuilder();
    for (String setting : new String[]{"resultScore", "resultStudent", "resultAi", "resultReference", "resultWeaknesses", "resultKnowledge"})
      key.append(AppSettings.enabled(this, setting, true) ? '1' : '0');
    return key.toString();
  }

  private String knowledgeMarkdown() {
    StringBuilder body = new StringBuilder("### 所属知识点\n\n- " + question().topic);
    for (int i = 0; i < question().knowledgeIds.length(); i++)
      body.append("\n- ").append(bank.knowledge.path(question().knowledgeIds.optString(i)));
    JSONObject tags = lastResult == null ? question().tags : lastResult.optJSONObject("tags");
    if (tags != null) for (String name : new String[]{"knowledgePoints", "examPoints"}) {
      JSONArray items = tags.optJSONArray(name);
      if (items != null) for (int i = 0; i < items.length(); i++) body.append("\n- ").append(items.optString(i));
    }
    return body.toString();
  }

  private View questionNavigation(String symbol, String label, int direction) {
    TextView control = text(symbol, 25, TEAL);
    control.setGravity(Gravity.CENTER); control.setBackground(background(0xffe5f0ee));
    control.setContentDescription(label); control.setTooltipText(label);
    control.setOnClickListener(v -> navigateQuestion(direction));
    control.setEnabled(adjacentIndex(direction) >= 0);
    return control;
  }

  private boolean batchMode(){return bank!=null&&BatchPractice.size(this,bank.id)>1;}
  private List<Integer> batchGroup(){return BatchPractice.group(bank,filter,index,BatchPractice.size(this,bank.id));}
  private JSONObject batchState(int position)throws Exception{
    BankRepository.Question q=bank.questions.get(position);Memory m=memories.get(bank.id+"/"+q.id);
    JSONObject state=m==null?store.load(q.id):new JSONObject().put("ink",m.document.json(0,0,1)).put("annotations",m.annotations).put("batchChoice",m.batchChoice).put("result",m.result);
    if(position==index)state.put("ink",ink.freeze().json(0,0,1)).put("annotations",paper.ink.freeze().json(0,0,1)).put("batchChoice",batchChoice).put("result",lastResult);
    return state;
  }
  private void refreshBatchControls(){
    if(batchAnswerBox==null)return;batchAnswerBox.removeAllViews();batchAnswerBox.setVisibility(batchMode()?View.VISIBLE:View.GONE);if(!batchMode())return;
    try{
      List<Integer> group=batchGroup();int filled=0;for(int n:group){BatchPractice.Item item=new BatchPractice.Item(bank.questions.get(n),batchState(n));if(item.ink()||!item.choice.isEmpty())filled++;}
      batchSummary=button(BatchPractice.size(this,bank.id)+"合1 · 已填 "+filled+"/"+group.size()+" · 本组题目 / 批改",this::showBatchGroup,false);
      batchSummary.setContentDescription("本组题目与批改");batchAnswerBox.addView(batchSummary);
      BankRepository.Question q=question();boolean choice=q.type.equals("choice")||q.type.equals("multi-choice")||!q.expectedChoice.isEmpty();
      if(choice){
        LinearLayout choices=row();choices.addView(text(q.type.equals("multi-choice")||q.expectedChoice.length()>1?"多选：":"单选：",14,MUTED));
        boolean multi=q.type.equals("multi-choice")||q.expectedChoice.length()>1;
        for(String value:new String[]{"A","B","C","D"}){
          Button b=button(value,()->{batchChoice=multi?batchChoice.contains(value)?batchChoice.replace(value,""):TutorClient.normalizeChoice(batchChoice+value):batchChoice.equals(value)?"":value;save();refreshBatchControls();},batchChoice.contains(value));
          b.setContentDescription("快捷选项 "+value);b.setEnabled(!busy);choices.addView(b);
        }
        if(adjacentIndex(1)>=0)choices.addView(button("下一题 ›",()->navigateQuestion(1),false));
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(choices);batchAnswerBox.addView(scroll);
      }
    }catch(Exception e){toast("本组草稿暂时无法读取："+e.getMessage());}
  }
  private void showBatchGroup(){
    if(busy)return;save();try{
      List<Integer> group=batchGroup();String[] labels=new String[group.size()];
      for(int i=0;i<group.size();i++){
        int n=group.get(i);JSONObject state=batchState(n);BatchPractice.Item item=new BatchPractice.Item(bank.questions.get(n),state);JSONObject result=state.optJSONObject("result");
        boolean submitted=result!=null&&BatchPractice.fingerprint(item).equals(result.optString("batchFingerprint"));
        labels[i]=(i+1)+". "+item.question.title+" · "+(!item.choice.isEmpty()?item.choice:item.ink()?"已书写":"未作答")+(result==null?" · 待提交":" · "+result.optDouble("score")+"/10"+(submitted?"":"（作答待提交）"));
      }
      new AlertDialog.Builder(this).setTitle("本组 "+group.size()+" 题 · 点击题目查看作答与解析")
          .setItems(labels,(d,i)->{stopClock();stopResidence();save();index=group.get(i);loadQuestion();})
          .setPositiveButton("提交本组",(d,w)->requestBatch(false)).setNeutralButton("下一组",(d,w)->advanceBatch()).setNegativeButton("关闭",null).show();
    }catch(Exception e){error(e);}
  }
  private void advanceBatch(){
    if(busy)return;List<Integer> group=batchGroup();int last=group.get(group.size()-1),target=-1;
    for(int n=last+1;n<bank.questions.size();n++)if(filter.matches(bank,bank.questions.get(n))){target=n;break;}
    if(target<0){toast("当前筛选范围已到最后一组");return;}
    final int destination=target;boolean pending=false;
    try{for(int n:group){JSONObject state=batchState(n);BatchPractice.Item item=new BatchPractice.Item(bank.questions.get(n),state);JSONObject result=state.optJSONObject("result");if((item.ink()||!item.choice.isEmpty())&&(result==null||!BatchPractice.fingerprint(item).equals(result.optString("batchFingerprint"))))pending=true;}}catch(Exception e){error(e);return;}
    Runnable move=()->{stopClock();stopResidence();save();index=destination;loadQuestion();};
    if(pending)new AlertDialog.Builder(this).setTitle("本组还有未提交的作答")
        .setMessage("草稿会保留。可以先统一批改，或继续下一组。")
        .setPositiveButton("提交本组",(d,w)->requestBatch(false)).setNeutralButton("保留草稿并继续",(d,w)->move.run()).setNegativeButton("取消",null).show();
    else move.run();
  }

  private TutorClient.StreamListener liveListener(){
    return new TutorClient.StreamListener(){
      public void stage(int step,String label){runOnUiThread(()->{if(isDestroyed()||!busy)return;livePhase=label;hideLoading();thinkingPanel.stage(step,label);updateStatus();});}
      public void thinking(int step,String text,boolean finished){runOnUiThread(()->{if(isDestroyed()||!busy)return;hideLoading();thinkingPanel.thinking(step,text,finished);});}
      public void partial(String partial){runOnUiThread(()->{if(isDestroyed()||!busy)return;liveText=partial;if(!streamStarted){streamStarted=true;hideLoading();}long now=SystemClock.uptimeMillis();if(now-lastStreamRender>=100){lastStreamRender=now;showLive(true);}});}
    };
  }
  private void requestBatch(boolean allowBlank){
    if(busy||home)return;
    if(ink.writing()||paper.ink.writing()||hintPaper.ink.writing()||resultPaper.ink.writing()){toast("请先完成当前书写或文字输入");return;}
    final List<Integer> group=batchGroup();final ProviderSettings config;
    try{
      int filled=0;for(int n:group){BatchPractice.Item item=new BatchPractice.Item(bank.questions.get(n),batchState(n));if(item.ink()||!item.choice.isEmpty())filled++;}
      if(filled==0){toast("请先完成本组作答");return;}
      if(filled<group.size()&&!allowBlank){new AlertDialog.Builder(this).setTitle("本组还有 "+(group.size()-filled)+" 题未作答")
          .setMessage("继续提交会将未作答题按空白计分。已有笔迹和选择会保留。")
          .setPositiveButton("仍然提交",(d,w)->requestBatch(true)).setNegativeButton("继续作答",null).show();return;}
      config=ProviderSettings.load(this);config.validate();if(!config.visionVerified)throw new IOException("请先检测并保存多模态提供商");
    }catch(Exception e){error(e);return;}
    final boolean wasPaused=answerStopped;stopClock();save();answerStopped=true;save();
    final BankRepository targetBank=bank;final StudyStore targetStore=store;
    final Future<?> saved=saver.submit(()->{});
    batchSubmitting=true;busy=true;lastError="";liveMode=TutorClient.Mode.GRADE;liveText="";streamStarted=false;lastStreamRender=0;
    streamingRequest=AppSettings.enabled(this,"stream",false);livePhase="准备本组 "+group.size()+" 题";
    thinkingState=new ThinkingPanel.State();thinkingPanel.setState(thinkingState);if(streamingRequest)thinkingPanel.start();
    showLoading();refreshFeedback();studyScroll.post(()->studyScroll.smoothScrollTo(0,thinkingPanel.getTop()));
    worker.execute(()->{
      try{
        saved.get(30,TimeUnit.SECONDS);List<BatchPractice.Item> items=new ArrayList<>();
        for(int n:group)items.add(new BatchPractice.Item(targetBank.questions.get(n),targetStore.load(targetBank.questions.get(n).id)));
        TutorClient.BatchReply batch=TutorClient.askBatch(config,targetBank,items,liveListener());
        Map<String,JSONObject> originals=new LinkedHashMap<>();List<Long> attempts=new ArrayList<>();
        try(LearningDb db=new LearningDb(this)){
          db.getWritableDatabase().beginTransaction();
          try{
            for(BatchPractice.Item item:items){
              TutorClient.Reply reply=batch.results.get(item.question.id);reply.thinking=batch.thinking;
              long attempt=db.grade(this,targetBank,item.question,reply,item.document,item.state.optInt("hints"));attempts.add(attempt);db.annotations(attempt,item.annotations);
              originals.put(item.question.id,new JSONObject(item.state.toString()));
              JSONObject result=new JSONObject().put("score",reply.score).put("correct",reply.correct).put("studentWork",reply.studentWork).put("feedback",reply.feedback)
                  .put("weaknesses",reply.weaknesses).put("tags",reply.tags).put("thinking",batch.thinking).put("generatedAt",System.currentTimeMillis()).put("attemptId",attempt).put("batchCount",items.size()).put("batchFingerprint",BatchPractice.fingerprint(item));
              targetStore.save(item.question.id,new JSONObject(item.state.toString()).put("result",result).put("solved",reply.correct).put("answerStopped",true));
            }
            db.getWritableDatabase().setTransactionSuccessful();
          }catch(Exception failure){
            for(Map.Entry<String,JSONObject> original:originals.entrySet())targetStore.save(original.getKey(),original.getValue());
            for(long attempt:attempts)new File(getFilesDir(),"learning-ink/"+attempt+".json").delete();throw failure;
          }finally{db.getWritableDatabase().endTransaction();}
        }
        for(BatchPractice.Item item:items){TutorClient.Reply reply=batch.results.get(item.question.id);targetStore.grade(item.question.id,reply.score,reply.weaknesses,item.state.optInt("hints"));}
        runOnUiThread(()->{
          if(isDestroyed())return;batchSubmitting=false;busy=false;hideLoading();thinkingPanel.finish(true);
          for(int n:group)memories.remove(targetBank.id+"/"+targetBank.questions.get(n).id);
          loadQuestion();toast("本组 "+group.size()+" 题已批改，可在「本组题目 / 批改」查看逐题结果");
          studyScroll.post(()->studyScroll.smoothScrollTo(0,feedbackBox.getTop()));
        });
      }catch(Exception failure){runOnUiThread(()->{if(isDestroyed())return;batchSubmitting=false;busy=false;answerStopped=wasPaused;hideLoading();thinkingPanel.finish(false);refreshFeedback();startClock();error(failure);});}
    });
  }

  private int adjacentIndex(int direction) {
    for (int n = index + direction; n >= 0 && n < bank.questions.size(); n += direction)
      if (filter.matches(bank, bank.questions.get(n))) return batchMode()&&!batchGroup().contains(n)?-1:n;
    return -1;
  }

  private void navigateQuestion(int direction) {
    if (busy || ink.writing() || paper.ink.writing() || hintPaper.ink.writing() || resultPaper.ink.writing()) return;
    int target = adjacentIndex(direction);
    if (target < 0) return;
    stopClock(); stopResidence(); save(); index = target; loadQuestion();
  }

  private void advance() {
    if (busy) return;
    stopResidence();
    stopClock();
    save();
    if (!LearningPlan.applies(this, bank)
        || freeSession
        || !filter.type.equals(LearningPlan.prefs(this).getString("type", ""))
        || !filter.knowledge.equals(LearningPlan.prefs(this).getString("knowledge", ""))) {
      for (int step = 1; step <= bank.questions.size(); step++) {
        int n = (index + step) % bank.questions.size();
        if (filter.matches(bank, bank.questions.get(n))) {
          index = n;
          loadQuestion();
          return;
        }
      }
      toast("该范围暂无题目");
      return;
    }
    int candidate = LearningPlan.next(this, bank);
    if (candidate >= 0) {
      index = candidate;
      loadQuestion();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("今日计划暂无待学题目")
        .setMessage("目标已完成，或复习尚未到期。你也可以继续自由刷题。")
        .setNeutralButton(
            "继续自由刷题",
            (d, w) -> {
              freeSession = true;
              for (int step = 1; step <= bank.questions.size(); step++) {
                int n = (index + step) % bank.questions.size();
                if (filter.matches(bank, bank.questions.get(n))) {
                  index = n;
                  break;
                }
              }
              loadQuestion();
            })
        .setPositiveButton("选择题目", (d, w) -> showLibrary())
        .setNegativeButton("留在当前题", null)
        .setOnDismissListener(d -> startClock())
        .show();
  }

  private void showLibrary() {
    if (busy) return;
    save();
    LinearLayout box = column();
    box.setPadding(dp(20), dp(8), dp(20), dp(16));
    List<String> types = new ArrayList<>();
    types.add("");
    for (BankRepository.Question q : bank.questions) if (!types.contains(q.type)) types.add(q.type);
    Spinner type = new Spinner(this);
    List<String> typeNames = new ArrayList<>();
    for (String t : types) typeNames.add(t.isEmpty() ? "全部题型" : StudyFilter.typeName(t));
    type.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, typeNames));
    type.setSelection(Math.max(0, types.indexOf(filter.type)));
    box.addView(type);
    final String[] knowledge = {filter.knowledge};
    Button choose =
        button(
            "知识点：" + (filter.knowledge.isEmpty() ? "全部" : bank.knowledge.path(filter.knowledge)),
            () -> {},
            false);
    choose.setOnClickListener(
        v ->
            StudyFilter.pickKnowledge(
                this,
                bank,
                "",
                id -> {
                  knowledge[0] = id;
                  choose.setText(id.isEmpty() ? "全部知识点" : bank.knowledge.path(id));
                }));
    box.addView(choose);
    List<String> chapters = new ArrayList<>();
    chapters.add("全部章节");
    for (BankRepository.Question q : bank.questions)
      if (!chapters.contains(q.chapter)) chapters.add(q.chapter);
    Spinner chapter = new Spinner(this);
    chapter.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, chapters));
    box.addView(chapter);
    EditText number = new EditText(this);
    number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    number.setHint("题库序号 1–" + bank.questions.size() + "；选章节后为原题号");
    box.addView(number);
    box.addView(text("不填题号则进入筛选结果第一题。章节内重号时，请先选择题型。", 13, MUTED));
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("选题与练习范围")
            .setView(box)
            .setNegativeButton("取消", null)
            .setNeutralButton(
                "清除筛选",
                (d, w) -> {
                  filter = new StudyFilter();
                  filter.save(this, bank.id);
                })
            .setPositiveButton("开始练习", null)
            .create();
    dialog.show();
    dialog
        .getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(
            v -> {
              StudyFilter selected = new StudyFilter();
              selected.type = types.get(type.getSelectedItemPosition());
              selected.knowledge = knowledge[0];
              String ch = chapters.get(chapter.getSelectedItemPosition()),
                  raw = number.getText().toString().trim();
              int target = -1, count = 0;
              try {
                int n = raw.isEmpty() ? -1 : Integer.parseInt(raw);
                if (n == 0) throw new IllegalArgumentException();
                for (int i = 0; i < bank.questions.size(); i++) {
                  BankRepository.Question q = bank.questions.get(i);
                  if (!selected.matches(bank, q)
                      || chapter.getSelectedItemPosition() > 0 && !q.chapter.equals(ch)) continue;
                  boolean match =
                      raw.isEmpty()
                          || chapter.getSelectedItemPosition() == 0 && i + 1 == n
                          || chapter.getSelectedItemPosition() > 0 && q.number == n;
                  if (match) {
                    if (target < 0) target = i;
                    count++;
                    if (raw.isEmpty()) break;
                  }
                }
              } catch (Exception e) {
                number.setError("请输入有效题号");
                return;
              }
              if (target < 0) {
                number.setError("该题号或筛选范围暂无题目");
                return;
              }
              if (count > 1) {
                number.setError("本章有重号，请先选择题型");
                return;
              }
              stopClock();
              stopResidence();
              save();
              filter = selected;
              filter.save(this, bank.id);
              index = target;
              dialog.dismiss();
              loadQuestion();
            });
  }

  private void importBank() {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip");
    startActivityForResult(i, 10);
  }

  private AlertDialog confirmDeleteBank(BankRepository.Entry entry) {
    if (busy || !entry.imported()) return null;
    return new AlertDialog.Builder(this)
        .setTitle("删除「" + entry.title + "」？")
        .setMessage("将移除题库文件及该题库的收藏，已保存的作答记录、草稿和学习统计保留。"
            + "\n\n同一题库重复导入的副本会一并移除，需要时可重新导入原 ZIP。")
        .setNegativeButton("取消", null)
        .setPositiveButton("删除", (dialog, which) -> {
          busy = true;
          bankImportDialog = ProgressDialog.show(this, "删除题库", "正在移除题库文件…", true, false);
          worker.execute(() -> {
            Exception failure = null;
            try { BankRepository.deleteImported(this, entry.location); }
            catch (Exception e) { failure = e; }
            final Exception result = failure;
            runOnUiThread(() -> {
              if (isDestroyed() || isFinishing()) return;
              if (bankImportDialog != null) { bankImportDialog.dismiss(); bankImportDialog = null; }
              busy = false;
              if (bank != null && bank.id.equals(entry.id)) { bank = null; store = null; memories.clear(); }
              showHome();
              if (result == null) toast("已删除题库"); else error(result);
            });
          });
        }).show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 10 && result == RESULT_OK && data != null) {
      busy = true;
      bankImportDialog = new Dialog(this);
      LinearLayout importPanel = AppUi.card(this);
      importPanel.addView(AppUi.heading(this, "正在导入题库"));
      TextView importNote = AppUi.text(this, "正在解压并校验，可一次导入多个嵌套题库。", 14, MUTED);
      importNote.setPadding(0, dp(12), 0, dp(16));
      importPanel.addView(importNote);
      ProgressBar importProgress = new ProgressBar(this);
      importProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(TEAL));
      importPanel.addView(importProgress, new LinearLayout.LayoutParams(dp(30), dp(30)));
      bankImportDialog.setContentView(importPanel);
      bankImportDialog.setCancelable(false);
      bankImportDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
      bankImportDialog.show();
      bankImportDialog.getWindow().setLayout(Math.min(dp(480), getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
      worker.execute(
          () -> {
            try {
              List<BankRepository> imported = BankRepository.importZips(this, data.getData());
              int questionCount = 0;
              for (BankRepository item : imported) questionCount += item.questions.size();
              final int totalQuestions = questionCount;
              runOnUiThread(
                  () -> {
                    if (isDestroyed() || isFinishing()) return;
                    if (bankImportDialog != null) { bankImportDialog.dismiss(); bankImportDialog = null; }
                    busy = false;
                    showHome();
                    toast("已导入 " + imported.size() + " 个题库，共 " + totalQuestions + " 题");
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    if (isDestroyed() || isFinishing()) return;
                    if (bankImportDialog != null) { bankImportDialog.dismiss(); bankImportDialog = null; }
                    busy = false;
                    error(e);
                  });
            }
          });
    }
  }

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }

  private void settings() {
    if (!busy) {
      save();
      startActivity(new Intent(this, SettingsActivity.class));
    }
  }

  private void palette() {
    if (busy || home || activeInk == null) return;
    Dialog dialog = new Dialog(this);
    LinearLayout card = AppUi.card(this);
    card.addView(AppUi.heading(this, "画笔颜色"));
    LinearLayout colors = row();
    for (int color : new int[]{0xff22313f, 0xffb44747, 0xff197d78, 0xff386bb4, 0xff854bb0, 0xffc2862b}) {
      View swatch = new View(this);
      swatch.setBackground(AppUi.surface(this, color, 24));
      swatch.setContentDescription(String.format("画笔颜色 #%06X", color & 0xffffff));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
      lp.setMargins(dp(5), dp(16), dp(5), dp(16));
      colors.addView(swatch, lp);
      swatch.setOnClickListener(v -> {
        AppSettings.prefs(this).edit().putInt(activeInk.correction ? "correctionColor" : "answerColor", color).apply();
        AppSettings.apply(ink); AppSettings.apply(paper.ink); updateTools(); dialog.dismiss();
      });
    }
    card.addView(colors);
    card.addView(AppUi.button(this, "更多书写设置", false, () -> { dialog.dismiss(); settings(); }));
    dialog.setContentView(card); dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    dialog.show();
    dialog.getWindow().setLayout(Math.min(dp(380), getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
  }

  @Override public boolean dispatchKeyEvent(KeyEvent event) {
    if (stylus != null && stylus.key(event)) return true;
    return super.dispatchKeyEvent(event);
  }

  private void showLive(boolean loading) {
    if (hintText == null) return;
    hintPaper.setVisibility(View.VISIBLE);
    hintPaper.ink.setVisibility(View.INVISIBLE);
    hintText.setMarkdownStreaming(
        "**"
            + (liveMode == TutorClient.Mode.GRADE
                ? "智能点评（校验中）"
                : liveMode == TutorClient.Mode.PARTIAL ? "下一步提示" : "思路提示")
            + "**\n\n"
            + liveText,
        loading);
    if (activeInk == resultPaper.ink && lastResult == null || activeInk == hintPaper.ink && history.length() == 0) activeInk = ink;
    syncGeneratedTools();
    ink.locked = busy;
    if (paper != null) paper.ink.locked = busy;
    if (loading && studyScroll != null)
      studyScroll.post(
          () -> {
            if (hintPaper.getTop() > studyScroll.getScrollY() + studyScroll.getHeight() / 2)
              studyScroll.smoothScrollTo(0, hintPaper.getTop());
          });
  }

  private void showLoading() {
    if (streamingRequest) {hideLoading();return;}
    if (streamStarted) {
      showLive(true);
      return;
    }
    hideLoading();
    loadingDialog = new ProgressDialog(this);
    loadingDialog.setTitle("模型正在处理");
    loadingDialog.setMessage("正在识别作答、推理并检查结果…");
    loadingDialog.setCancelable(false);
    loadingDialog.setCanceledOnTouchOutside(false);
    loadingDialog.show();
  }

  private void hideLoading() {
    if (loadingDialog != null) {
      loadingDialog.dismiss();
      loadingDialog = null;
    }
  }

  private long totalQuestionMs() {
    return questionMs + (clockStart < 0 ? 0 : SystemClock.elapsedRealtime() - clockStart);
  }

  private void toggleTimer() {
    if (home || busy || !AppSettings.enabled(this, "timer", true) || !AppSettings.enabled(this, "timerAllowPause", true)) return;
    if (answerStopped) { answerStopped = false; startClock(); }
    else { stopClock(); answerStopped = true; }
    save(); updateStatus(); refreshFeedback();
  }

  private void resetTimer() {
    if (home || busy || !AppSettings.enabled(this, "timer", true) || !AppSettings.enabled(this, "timerAllowReset", true)) return;
    getSharedPreferences("timer-display", 0).edit().putLong(bank.id + "/" + question().id, totalQuestionMs()).apply();
    updateStatus(); toast("当前题计时已归零，学习统计保留");
  }

  private void updateStatus() {
    if (status == null || home) return;
    status.setText(busy ? streamingRequest ? "模型处理中 · " + livePhase : "正在识别笔迹并检查推理…" : bank.title + " · 自动保存");
    if (timerText == null) return;
    timerText.setVisibility(AppSettings.enabled(this, "timer", true) ? View.VISIBLE : View.GONE);
    long offset = getSharedPreferences("timer-display", 0).getLong(bank.id + "/" + question().id, 0);
    timerText.setText("用时 " + LearningDb.duration(Math.max(0, totalQuestionMs() - offset))
        + (answerStopped ? " · 已暂停" : busy ? " · 等待中" : ""));
    timerText.setEnabled(!busy);
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          long now = SystemClock.elapsedRealtime();
          if (clockStart >= 0 && now - clockStart >= 15000) {
            stopClock();
            startClock();
          }
          if (stayStart >= 0 && now - stayStart >= 15000) {
            stopResidence();
            startResidence();
          }
          updateStatus();
          handler.removeCallbacks(this);
          if (clockStart >= 0 || stayStart >= 0) handler.postDelayed(this, 1000);
        }
      };

  private void startClock() {
    if (resumed && !home && !busy && !answerStopped && clockStart < 0) {
      clockStart = SystemClock.elapsedRealtime();
      clockWall = System.currentTimeMillis();
      handler.removeCallbacks(tick);
      handler.post(tick);
    }
  }

  private void startResidence() {
    if (resumed && !home && stayStart < 0) {
      stayStart = SystemClock.elapsedRealtime();
      stayWall = System.currentTimeMillis();
      handler.removeCallbacks(tick);
      handler.post(tick);
    }
  }

  private void stopResidence() {
    if (stayStart >= 0) {
      long duration = SystemClock.elapsedRealtime() - stayStart;
      stayStart = -1;
      if (bank != null && !home) learning.residence(bank.id, question().id, stayWall, duration);
    }
  }

  private void stopClock() {
    handler.removeCallbacks(tick);
    if (stayStart >= 0) handler.postDelayed(tick, 1000);
    if (clockStart >= 0) {
      long duration = SystemClock.elapsedRealtime() - clockStart;
      clockStart = -1;
      if (bank != null && !home) {
        learning.time(bank.id, question().id, clockWall, duration);
        questionMs += duration;
      }
    }
  }

  private boolean handleOpen(Intent intent) {
    if (!intent.hasExtra("bankLocation")) return false;
    stopResidence();
    openBank(intent.getStringExtra("bankLocation"));
    String id = intent.getStringExtra("questionId");
    if (!home && id != null)
      for (int i = 0; i < bank.questions.size(); i++)
        if (bank.questions.get(i).id.equals(id)) {
          stopClock();
          stopResidence();
          save();
          index = i;
          loadQuestion();
          break;
        }
    return true;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (busy) return;
    stopClock();
    save();
    setIntent(intent);
    handleOpen(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (stylus != null) stylus.resume();
    PlanReminder.schedule(this);
    if (home) {
      showHome();
      return;
    }
    if(renderedBatchSize!=BatchPractice.size(this,bank.id)){stopClock();stopResidence();save();loadQuestion();return;}
    ProviderUi.refresh(this, root);
    refreshFeedback();
    if (ink != null) {
      AppSettings.apply(ink);
      if (paper != null) AppSettings.apply(paper.ink);
      ink.finger = getSharedPreferences("study", 0).getBoolean("finger", false);
      if (paper != null) paper.ink.finger = ink.finger;
      ink.invalidate();
      updateTools();
    }
    startResidence();
    startClock();
    updateStatus();
  }

  private void error(Exception e) {
    lastError = e.getMessage() == null ? "操作失败" : e.getMessage();
    new AlertDialog.Builder(this)
        .setTitle("暂时无法完成")
        .setMessage(e.getMessage() == null ? "操作失败，请重试" : e.getMessage())
        .setPositiveButton("知道了", null)
        .show();
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (stylus != null) stylus.focusChanged(focused);
  }

  @Override
  protected void onPause() {
    resumed = false;
    if (stylus != null) stylus.pause();
    if (ink != null) ink.cancelContact();
    if (paper != null) paper.ink.cancelContact();
    if (hintPaper != null) hintPaper.ink.cancelContact();
    if (resultPaper != null) resultPaper.ink.cancelContact();
    stopResidence();
    stopClock();
    save();
    super.onPause();
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration configuration) {
    super.onConfigurationChanged(configuration);
    if (ink != null) ink.commitText();
    hideLoading();
    if (home) {
      showHome();
      return;
    }
    Memory m = new Memory();
    m.document = ink.freeze();
    m.cx = ink.worldX(ink.getWidth() / 2f);
    m.cy = ink.worldY(ink.getHeight() / 2f);
    m.zoom = ink.zoom;
    try {
      m.annotations = paper.ink.freeze().json(0, 0, 1);
    } catch (Exception ignored) {
    }
    build();
    try {
      ink.restore(m.document.json(m.cx, m.cy, m.zoom));
      if (m.annotations != null) paper.restore(m.annotations);
      AppSettings.apply(ink);
      refreshFeedback();
      if (busy) showLoading();
    } catch (Exception e) {
      error(e);
    }
  }

  @Override
  protected void onDestroy() {
    stopClock();
    hideLoading();
    save();
    handler.removeCallbacks(tick);
    if (learning != null) learning.close();
    handler.removeCallbacks(saveTask);
    if (stylus != null) stylus.detach();
    if (bankImportDialog != null) { bankImportDialog.dismiss(); bankImportDialog = null; }
    worker.shutdownNow();
    saver.shutdown();
    if (root != null) MathView.releaseTree(root);
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (busy) {
      toast("请求处理中，草稿已保留");
      return;
    }
    if (!home) {
      showHome();
      return;
    }
    super.onBackPressed();
  }
}
