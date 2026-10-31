package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import java.util.*;
import org.json.*;

/** Infinite dp world, uniform viewport scale, incremental visible-tile raster cache. */
final class InkView extends View {
  static final int TILE = 256;

  static final class Dot {
    final float x, y, pressure, tilt, orientation;

    Dot(float x, float y, float p) {
      this(x, y, p, 0, 0);
    }

    Dot(float x, float y, float p, float tilt, float orientation) {
      this.x = x; this.y = y; pressure = p;
      this.tilt = tilt; this.orientation = orientation;
    }
  }

  static final class Stroke {
    int color = 0xff22313f;
    boolean erase, shade;
    float width;
    String text = "";
    Stroke previous;
    final List<Dot> dots = new ArrayList<>();

    Stroke copy() {
      Stroke s = new Stroke();
      s.color = color;
      s.erase = erase;
      s.shade = shade;
      s.width = width;
      s.text = text;
      s.dots.addAll(dots);
      return s;
    }
  }

  static final class Segment {
    final Dot a, b;
    final Stroke style;
    final int order;
    final RectF bounds;

    Segment(Dot a, Dot b, Stroke s, int order) {
      this.a = a;
      this.b = b;
      style = s;
      this.order = order;
      float r = Math.max(radius(a, s), radius(b, s)) + 2;
      bounds =
          new RectF(
              Math.min(a.x, b.x) - r,
              Math.min(a.y, b.y) - r,
              Math.max(a.x, b.x) + r,
              Math.max(a.y, b.y) + r);
    }
  }

  static float radius(Dot d, Stroke s) {
    return (s.shade && !s.erase ? 1 + 1.5f * (float)Math.sin(d.tilt) : 1) * s.width * (s.erase ? 1 : (.25f + Math.max(.05f, Math.min(1.5f, d.pressure)) * .9f)) / 2;
  }

  static long key(int x, int y) {
    return ((long) x << 32) | (y & 0xffffffffL);
  }

  static int tx(long k) {
    return (int) (k >> 32);
  }

  static int ty(long k) {
    return (int) k;
  }

  static int tile(float v) {
    return (int) Math.floor(v / TILE);
  }

  final List<Stroke> strokes = new ArrayList<>();
  private final List<Stroke> redo = new ArrayList<>();
  private final Map<Long, List<Segment>> index = new HashMap<>();
  private final LinkedHashMap<Long, Picture> cache = new LinkedHashMap<>(64, .75f, true);
  private final Set<Long> touched = new HashSet<>();
  private final Map<Stroke, Path> outlines = new IdentityHashMap<>();
  private final Map<Long, List<Segment>> live = new HashMap<>();
  private final Paint brush = new Paint(3),
      bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG),
      guide = new Paint(3);
  private final Paint ruling = new Paint(3);
  private final RectF visible = new RectF(), tileRect = new RectF();
  private final Path taper = new Path();
  private final PorterDuffXfermode clear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
  private Stroke current;
  private int pointer = -1, serial = 0;
  boolean eraser = false,
      finger = false,
      locked = false,
      viewOnly = false,
      pressure = true,
      pressureEraser = false,
      shapes = false,
      correction = false;
  float pressureThreshold = .85f, shapeDistance = 12;
  long shapeHoldMillis = 450;
  boolean annotationSurface = false, tiltShading = false, exporting = false;
  private final android.os.Handler shapeHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean shapeApplied;
  private int stationaryIndex;
  private final Runnable shapeCheck = () -> checkShapeHold();
  private long stationarySince;
  private float stationaryX, stationaryY;
  float penWidth = 2.8f, lineSpacing = 80, zoom = 1, panX = 0, panY = 0;
  int penColor = 0xff22313f;
  Runnable changed = () -> {}, viewportChanged = () -> {}, documentChanged = () -> {};
  private final StylusAdapter adapter;
  private final float density;
  private final ScaleGestureDetector scaleDetector;
  private boolean dragging = false, pendingCenter = false, cursorVisible = false, cursorEraser = false;
  private float lastX, lastY, centerX, centerY, cursorX, cursorY;
  private long lastStylus = 0;
  int tilesPaintedLastFrame = 0;
  private StylusPrediction prediction;
  private Dot predicted;
  private int contactStartIndex;
  private boolean contactPen, predictionContactStarted;
  private final Runnable expirePrediction = () -> { predicted = null; invalidate(); };

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (prediction == null && AppSettings.enabled(getContext(), "penPrediction", true)) prediction = adapter.prediction(this);
    if (android.os.Build.VERSION.SDK_INT >= 33) setAutoHandwritingEnabled(false);
  }

  private void previewPrediction(MotionEvent event, int index) {
    predicted = null;
    shapeHandler.removeCallbacks(expirePrediction);
    if (!contactPen || current == null || current.erase || shapeApplied || prediction == null
        || !AppSettings.enabled(getContext(), "penPrediction", true)) return;
    MotionEvent isolated = StylusPrediction.singlePointer(event, index);
    try {
      if (isolated.getActionMasked() == MotionEvent.ACTION_DOWN) predictionContactStarted = true;
      PointF next = prediction.record(isolated);
      if (event.getActionMasked() == MotionEvent.ACTION_MOVE && next != null
          && Float.isFinite(next.x) && Float.isFinite(next.y)) {
        float x = worldX(next.x), y = worldY(next.y);
        Dot last = current.dots.get(current.dots.size() - 1);
        if (Math.hypot(x - last.x, y - last.y) <= 32) {
          predicted = new Dot(x, y, last.pressure, last.tilt, last.orientation);
          shapeHandler.postDelayed(expirePrediction, 32);
        }
      }
    } finally { isolated.recycle(); }
  }

  void cancelContact() {
    if (current != null) {
      // Pressure-eraser switching may have added fragments from this same contact.
      while (strokes.size() > contactStartIndex) strokes.remove(strokes.size() - 1);
      current = null; pointer = -1; rebuild();
    }
    shapeHandler.removeCallbacks(shapeCheck);
    shapeHandler.removeCallbacks(expirePrediction);
    predicted = null; contactPen = false; predictionContactStarted = false; cursorVisible = false; dragging = false;
    adapter.writing(false);
    if (prediction != null) { prediction.close(); prediction = null; }
    invalidate();
  }

  @Override public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (!focused) { tapValid = false; multiTap = false; cancelContact(); }
  }

  InkView(Context c, StylusAdapter adapter) {
    super(c);
    this.adapter = adapter;
    density = getResources().getDisplayMetrics().density;
    textGestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
      @Override public boolean onDown(MotionEvent e) { return true; }
      @Override public boolean onSingleTapUp(MotionEvent e) {
        if (textMode) editTextAt(textPressX, textPressY);
        return true;
      }
      @Override public void onLongPress(MotionEvent e) {
        // Keyboard animations can move the viewport after ACTION_DOWN.
        if (textTarget != null && strokes.contains(textTarget)) {
          Dot anchor = textTarget.dots.get(0);
          editTextAt(anchor.x + 1, anchor.y + 1);
        }
      }
      @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
        panX -= dx; panY -= dy; invalidate(); viewportChanged.run(); return true;
      }
    });
    setBackgroundColor(Color.WHITE);
    setFocusable(true);
    setContentDescription("无限草稿：手写笔书写，手指拖动，双指缩放");
    scaleDetector =
        new ScaleGestureDetector(
            c,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScaleBegin(ScaleGestureDetector d) {
                return current == null;
              }

              @Override
              public boolean onScale(ScaleGestureDetector d) {
                if (current != null) return false;
                float wx = worldX(d.getFocusX()), wy = worldY(d.getFocusY());
                zoom = Math.max(.2f, Math.min(5, zoom * d.getScaleFactor()));
                panX = d.getFocusX() - wx * unit();
                panY = d.getFocusY() - wy * unit();
                invalidate();
                viewportChanged.run();
                return true;
              }
            });
  }

  public InkView(Context c, android.util.AttributeSet a) {
    this(c, StylusAdapter.Registry.select());
  }

  float unit() {
    return density * zoom;
  }

  float worldX(float p) {
    return (p - panX) / unit();
  }

  float worldY(float p) {
    return (p - panY) / unit();
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    if (annotationSurface) {
      panX = 0;
      panY = 0;
      pendingCenter = false;
      return;
    }
    if (pendingCenter) {
      panX = w / 2f - centerX * unit();
      panY = h / 2f - centerY * unit();
      pendingCenter = false;
    } else if (ow > 0 && oh > 0 && !textEditorOpen) {
      float x = (ow / 2f - panX) / unit(), y = (oh / 2f - panY) / unit();
      panX = w / 2f - x * unit();
      panY = h / 2f - y * unit();
    }
    positionTextEditor();
  }

  private void positionTextEditor() {
    if (inlineEditor == null || !(inlineEditor.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams)) return;
    android.widget.FrameLayout.LayoutParams p = (android.widget.FrameLayout.LayoutParams)inlineEditor.getLayoutParams();
    int height = Math.max(inlineEditor.getMeasuredHeight(), (int)(lineSpacing * unit() + 64 * density));
    float row = editorTop * unit() + panY;
    float limit = Math.max(0, getHeight() - height);
    if (row > limit) panY -= row - limit;
    p.topMargin = Math.max(0, (int)(editorTop * unit() + panY));
    p.width = Math.max((int)(120 * density), getWidth() - p.leftMargin);
    inlineEditor.setLayoutParams(p);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    visible.set(worldX(0), worldY(0), worldX(getWidth()), worldY(getHeight()));
    c.save();
    c.translate(panX, panY);
    c.scale(unit(), unit());
    if (!annotationSurface) paper(c, visible, lineSpacing, ruling);
    tilesPaintedLastFrame = 0;
    for (int y = tile(visible.top); y <= tile(visible.bottom); y++)
      for (int x = tile(visible.left); x <= tile(visible.right); x++) {
        long k = key(x, y);
        if (!index.containsKey(k)) continue;
        tileRect.set(x * TILE, y * TILE, (x + 1) * TILE, (y + 1) * TILE);
        int layer = c.saveLayer(tileRect, null);
        c.save();
        c.clipRect(tileRect);
        c.translate(x * TILE, y * TILE);
        c.drawPicture(tilePicture(k));
        c.restore();
        if (current != null)
          for (Segment seg : live.getOrDefault(k, Collections.emptyList()))
            drawSegment(c, seg, brush, taper, clear);
        c.restoreToCount(layer);
        tilesPaintedLastFrame++;
      }
    if (current != null && predicted != null && !current.dots.isEmpty()) {
      Dot last = current.dots.get(current.dots.size() - 1);
      drawSegment(c, new Segment(last, predicted, current, 0), brush, taper, clear);
    }
    c.restore();
    boolean eraseCursor = current != null ? current.erase : eraser || cursorEraser;
    if (cursorVisible && !exporting
        && AppSettings.enabled(getContext(), eraseCursor ? "eraserCursor" : "penCursor", eraseCursor)) {
      guide.setStyle(Paint.Style.STROKE);
      guide.setStrokeWidth(density * 1.5f);
      float cursorRadius = eraseCursor ? 18 * unit() : Math.max(3 * density, penWidth * unit() / 2);
      guide.setColor(eraseCursor ? 0xff287e79 : penColor);
      c.drawCircle(cursorX, cursorY, cursorRadius, guide);
      guide.setColor(0x33287e79);
      guide.setStyle(Paint.Style.FILL);
      c.drawCircle(cursorX, cursorY, cursorRadius, guide);
    }
  }

  static void paper(Canvas c, RectF r, float spacing) {
    paper(c, r, spacing, new Paint(3));
  }

  static void paper(Canvas c, RectF r, float spacing, Paint p) {
    p.setColor(0xffe0e8ed);
    p.setStrokeWidth(.6f);
    for (float y = (float) Math.floor(r.top / spacing) * spacing; y <= r.bottom; y += spacing)
      c.drawLine(r.left, y, r.right, y, p);
  }

  private Picture tilePicture(long k) {
    Picture picture = cache.get(k);
    if (picture != null) return picture;
    picture = new Picture();
    Canvas c = picture.beginRecording(TILE, TILE);
    c.translate(-tx(k) * TILE, -ty(k) * TILE);
    Set<Stroke> painted = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Segment seg : index.get(k))
      if (seg.style == current) continue;
      else if (seg.style.shade) drawSegment(c, seg, brush, taper, clear);
      else if (painted.add(seg.style)) {
        Path outline = outlines.get(seg.style);
        if (outline == null) {
          outline = outline(seg.style);
          outlines.put(seg.style, outline);
        }
        brush.setStyle(Paint.Style.FILL);
        brush.setColor(seg.style.color);
        brush.setXfermode(seg.style.erase ? clear : null);
        c.drawPath(outline, brush);
      }
    picture.endRecording();
    cache.put(k, picture);
    while (cache.size() > 96) {
      Iterator<Long> it = cache.keySet().iterator();
      it.next();
      it.remove();
    }
    return picture;
  }

  static Path outline(Stroke s) {
    if (!s.text.isEmpty()) return textPath(s);
    Path path = new Path();
    List<Dot> dots = new ArrayList<>();
    for (Dot d : s.dots)
      if (dots.isEmpty()
          || Math.hypot(d.x - dots.get(dots.size() - 1).x, d.y - dots.get(dots.size() - 1).y) > .01)
        dots.add(d);
    if (dots.isEmpty()) return path;
    int n = dots.size();
    float[] nx = new float[n], ny = new float[n];
    for (int i = 0; i < n; i++) {
      Dot before = dots.get(Math.max(0, i - 1)), after = dots.get(Math.min(n - 1, i + 1));
      float dx = after.x - before.x, dy = after.y - before.y, len = (float) Math.hypot(dx, dy);
      if (len > .001) {
        nx[i] = -dy / len;
        ny[i] = dx / len;
      }
    }
    for (int i = 0; i < n; i++) {
      Dot d = dots.get(i);
      float radius = radius(d, s);
      float x = d.x + nx[i] * radius, y = d.y + ny[i] * radius;
      if (i == 0) path.moveTo(x, y);
      else path.lineTo(x, y);
    }
    for (int i = n - 1; i >= 0; i--) {
      Dot d = dots.get(i);
      float radius = radius(d, s);
      path.lineTo(d.x - nx[i] * radius, d.y - ny[i] * radius);
    }
    path.close();
    Dot first = dots.get(0), last = dots.get(n - 1);
    path.addCircle(first.x, first.y, radius(first, s), Path.Direction.CCW);
    path.addCircle(last.x, last.y, radius(last, s), Path.Direction.CCW);
    return path;
  }

  static void drawSegment(Canvas c, Segment s, Paint p, Path path, PorterDuffXfermode clear) {
    p.setStyle(Paint.Style.FILL);
    p.setColor(s.style.color);
    if (s.style.shade) p.setAlpha((int)(Color.alpha(s.style.color) * (1 - .55f * Math.sin(s.b.tilt))));
    p.setXfermode(s.style.erase ? clear : null);
    float r0 = radius(s.a, s.style),
        r1 = radius(s.b, s.style),
        dx = s.b.x - s.a.x,
        dy = s.b.y - s.a.y,
        len = (float) Math.hypot(dx, dy);
    if (len > .01f) {
      float nx = -dy / len, ny = dx / len;
      path.reset();
      path.moveTo(s.a.x + nx * r0, s.a.y + ny * r0);
      path.lineTo(s.b.x + nx * r1, s.b.y + ny * r1);
      path.lineTo(s.b.x - nx * r1, s.b.y - ny * r1);
      path.lineTo(s.a.x - nx * r0, s.a.y - ny * r0);
      path.close();
      c.drawPath(path, p);
    }
    c.drawCircle(s.a.x, s.a.y, r0, p);
    c.drawCircle(s.b.x, s.b.y, r1, p);
  }

  // Text is retained as editable Unicode and rendered as vector glyph outlines.
  // It therefore uses the same eraser, tiles, snapshots and AI image export as ink.
  static Path textPath(Stroke s) {
    Path path = new Path();
    Paint p = new Paint(3);
    p.setTypeface(Typeface.DEFAULT);
    p.setTextSize(s.width * .70f);
    Paint.FontMetrics fm = p.getFontMetrics();
    float baseline = s.dots.get(0).y + (s.width - (fm.descent - fm.ascent)) / 2 - fm.ascent;
    float x = s.dots.get(0).x;
    for (String line : s.text.split("\\n", -1)) {
      Path glyphs = new Path();
      p.getTextPath(line, 0, line.length(), x, baseline, glyphs);
      path.addPath(glyphs);
      baseline += s.width;
    }
    return path;
  }

  Stroke textAt(float x, float y) {
    for (int i = strokes.size() - 1; i >= 0; i--) {
      Stroke s = strokes.get(i);
      if (!s.text.isEmpty() && s.dots.size() == 2) {
        Dot a = s.dots.get(0), b = s.dots.get(1);
        if (x >= a.x - 8 && x <= b.x + 8 && y >= a.y && y <= b.y) return s;
      }
    }
    return null;
  }

  void putText(Stroke old, String value, float x, float y, float maxWidth) {
    if (locked || viewOnly) return;
    Stroke s = new Stroke();
    s.color = old == null ? penColor : old.color;
    s.width = lineSpacing;
    s.previous = old;
    Paint p = new Paint(3);
    p.setTextSize(s.width * .70f);
    StringBuilder wrapped = new StringBuilder();
    for (String paragraph : value.replace("\r", "").split("\n", -1)) {
      if (wrapped.length() > 0) wrapped.append('\n');
      while (!paragraph.isEmpty()) {
        int count = Math.max(1, p.breakText(paragraph, true, Math.max(80, maxWidth), null));
        // Never split a surrogate pair when wrapping Unicode.
        if (count < paragraph.length() && Character.isHighSurrogate(paragraph.charAt(count - 1))) count++;
        wrapped.append(paragraph.substring(0, count));
        paragraph = paragraph.substring(count);
        if (!paragraph.isEmpty()) wrapped.append('\n');
      }
    }
    s.text = wrapped.toString();
    float left = old == null ? x : old.dots.get(0).x;
    float top = old == null ? (float)Math.floor(y / lineSpacing) * lineSpacing : old.dots.get(0).y;
    float width = 1;
    for (String line : s.text.split("\n", -1)) width = Math.max(width, p.measureText(line));
    if (!s.text.trim().isEmpty()) {
      s.dots.add(new Dot(left, top, 1));
      s.dots.add(new Dot(left + width, top + s.width * s.text.split("\n", -1).length, 1));
    }
    int index = old == null ? -1 : strokes.indexOf(old);
    if (index >= 0) strokes.remove(index);
    strokes.add(s);
    redo.clear();
    rebuild();
    documentChanged.run();
    changed.run();
  }

  private android.widget.LinearLayout inlineEditor;
  private float editorTop;
  private boolean textEditorOpen;
  private Runnable commitEditor;

  void commitText() {
    if (commitEditor != null) commitEditor.run();
  }

  private void editTextAt(float x, float y) {
    if (locked || viewOnly || textEditorOpen) return;
    Stroke old = textAt(x, y);
    android.widget.EditText field = new android.widget.EditText(getContext());
    field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    field.setMinLines(3);
    field.setMaxLines(8);
    field.setHint("输入解题过程、说明或疑问，可换行");
    field.setText(old == null ? "" : old.text);
    field.setSelection(field.length());
    if (getParent() instanceof android.widget.FrameLayout) {
      android.widget.FrameLayout frame = (android.widget.FrameLayout)getParent();
      android.widget.LinearLayout editor = new android.widget.LinearLayout(getContext());
      editor.setOrientation(android.widget.LinearLayout.VERTICAL);
      editor.setBackgroundColor(0xffeef8f6);
      field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, lineSpacing * .70f * unit());
      field.setMinLines(1); field.setMaxLines(4); field.setPadding(0, 0, 0, 0);
      field.setIncludeFontPadding(false);
      field.setLineSpacing(0, 1);
      editor.addView(field, new android.widget.LinearLayout.LayoutParams(-1, -2));
      android.widget.LinearLayout actions = new android.widget.LinearLayout(getContext());
      editor.addView(actions);
      float left = old == null ? x : old.dots.get(0).x;
      float top = old == null ? (float)Math.floor(y / lineSpacing) * lineSpacing : old.dots.get(0).y;
      int margin = Math.max(0, Math.min(getWidth() - (int)(120 * density), (int)(left * unit() + panX)));
      android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
          Math.max((int)(120 * density), getWidth() - margin), -2);
      params.leftMargin = margin;
      params.topMargin = Math.max(0, (int)(top * unit() + panY));
      textEditorOpen = true;
      Runnable close = () -> {
        textEditorOpen = false;
        commitEditor = null;
        inlineEditor = null;
        android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        ime.hideSoftInputFromWindow(field.getWindowToken(), 0);
        frame.removeView(editor); requestFocus();
      };
      android.widget.Button done = new android.widget.Button(getContext()); done.setText("完成"); actions.addView(done);
      android.widget.Button cancel = new android.widget.Button(getContext()); cancel.setText("取消"); actions.addView(cancel);
      commitEditor = () -> {
        if (!locked) putText(old, field.getText().toString(), left, top,
            Math.min(1600, Math.max(160, worldX(getWidth()) - left - 24)));
        close.run();
      };
      done.setOnClickListener(v -> commitText());
      cancel.setOnClickListener(v -> close.run());
      frame.addView(editor, params);
      inlineEditor = editor; editorTop = top;
      editor.post(this::positionTextEditor);
      field.requestFocus();
      field.postDelayed(() -> {
        if (!textEditorOpen || !field.isAttachedToWindow()) return;
        android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        ime.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
      }, 100);
      return;
    }
    android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(getContext())
        .setTitle(old == null ? "在这一行打字" : "编辑文字")
        .setView(field).setNegativeButton("取消", null)
        .setPositiveButton("确定", (d, w) -> {
          if (!locked) putText(old, field.getText().toString(), x, y,
              Math.min(1600, Math.max(160, worldX(getWidth()) - (old == null ? x : old.dots.get(0).x) - 24)));
        }).create();
    textEditorOpen = true;
    commitEditor = () -> {
      if (!locked) putText(old, field.getText().toString(), x, y,
          Math.min(1600, Math.max(160, worldX(getWidth()) - (old == null ? x : old.dots.get(0).x) - 24)));
      dialog.dismiss();
    };
    dialog.setOnDismissListener(d -> { textEditorOpen = false; commitEditor = null; });
    dialog.setOnShowListener(d -> {
      field.requestFocus();
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
          | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    });
    dialog.show();
  }

  boolean textMode;
  private GestureDetector textGestures;
  private boolean textGesture;
  private Stroke textTarget;
  private float textPressX, textPressY;

  private void indexSegment(Segment s, boolean update) {
    for (int y = tile(s.bounds.top); y <= tile(s.bounds.bottom); y++)
      for (int x = tile(s.bounds.left); x <= tile(s.bounds.right); x++) {
        long k = key(x, y);
        index.computeIfAbsent(k, z -> new ArrayList<>()).add(s);
        if (update) {
          touched.add(k);
          live.computeIfAbsent(k, z -> new ArrayList<>()).add(s);
        }
      }
  }

  private void rebuild() {
    index.clear();
    cache.clear();
    outlines.clear();
    touched.clear();
    live.clear();
    serial = 0;
    for (Stroke s : strokes) {
      Dot a = null;
      for (Dot d : s.dots) {
        indexSegment(new Segment(a == null ? d : a, d, s, serial++), false);
        a = d;
      }
    }
    invalidate();
  }

  private void checkShapeHold() {
    if (current == null || !shapes || current.erase || locked || shapeApplied) return;
    if (android.os.SystemClock.uptimeMillis() - stationarySince >= shapeHoldMillis) {
      Stroke candidate = new Stroke();
      candidate.color = current.color;
      candidate.width = current.width;
      candidate.erase = current.erase;
      int end = Math.min(current.dots.size(), Math.max(1, stationaryIndex + 1));
      candidate.dots.addAll(current.dots.subList(0, end));
      if (end < current.dots.size()) {
        float x = 0, y = 0, p = 0;
        int n = current.dots.size() - end;
        for (int i = end; i < current.dots.size(); i++) {
          Dot d = current.dots.get(i);
          x += d.x;
          y += d.y;
          p += d.pressure;
        }
        candidate.dots.add(new Dot(x / n, y / n, p / n));
      }
      if (ShapeSnap.apply(candidate)) {
        current.dots.clear();
        current.dots.addAll(candidate.dots);
        shapeApplied = true;
        rebuildLive();
        invalidate();
      }
    }
  }

  private void rebuildLive() {
    for (long k : touched) {
      cache.remove(k);
      List<Segment> parts = index.get(k);
      if (parts != null) {
        parts.removeIf(s -> s.style == current);
        if (parts.isEmpty()) index.remove(k);
      }
    }
    touched.clear();
    live.clear();
    Dot previous = null;
    for (Dot d : current.dots) {
      indexSegment(new Segment(previous == null ? d : previous, d, current, serial++), true);
      previous = d;
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    cancelContact();
    if (prediction != null) { prediction.close(); prediction = null; }
    super.onDetachedFromWindow();
  }

  @Override
  public boolean onHoverEvent(MotionEvent e) {
    if (!StylusAdapter.isPen(e, e.getActionIndex())) return super.onHoverEvent(e);
    lastStylus = SystemClock.uptimeMillis();
    cursorX = e.getX();
    cursorY = e.getY();
    cursorEraser = adapter.eraser(e, e.getActionIndex())
        || (e.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            && AppSettings.penAction(getContext(), "penLongPress") == AppSettings.SWITCH_TOOL;
    cursorVisible = e.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT;
    invalidate();
    return true;
  }

  Runnable activated = () -> {};
  private final Map<Integer, PointF> tapStarts = new HashMap<>();
  private int tapFingers;
  private boolean multiTap, tapValid;
  private long tapStartTime;

  boolean allowFingerGesture() { return SystemClock.uptimeMillis() - lastStylus >= 650; }

  boolean fingerTap(MotionEvent e) {
    int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      tapStarts.clear(); tapFingers = 1; multiTap = false; tapValid = true;
      tapStartTime = e.getEventTime();
    }
    if (action == MotionEvent.ACTION_CANCEL) { multiTap = false; tapValid = false; return false; }
    for (int i = 0; i < e.getPointerCount(); i++) {
      if (e.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) { tapValid = false; return false; }
      int id = e.getPointerId(i);
      PointF start = tapStarts.get(id);
      if (start == null) tapStarts.put(id, new PointF(e.getX(i), e.getY(i)));
      else if (Math.hypot(e.getX(i)-start.x, e.getY(i)-start.y) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) tapValid = false;
      for (int h = 0; h < e.getHistorySize() && start != null; h++)
        if (Math.hypot(e.getHistoricalX(i,h)-start.x,e.getHistoricalY(i,h)-start.y) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) tapValid = false;
    }
    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      // A new finger after any finger has lifted is not one simultaneous tap.
      if (multiTap && e.getPointerCount() <= tapFingers) tapValid = false;
      tapFingers = Math.max(tapFingers, e.getPointerCount()); multiTap = true;
      if (current != null && !contactPen) cancelContact();
    }
    if (tapFingers > 3 || e.getEventTime()-tapStartTime > ViewConfiguration.getTapTimeout()*2L) tapValid = false;
    if (multiTap && action == MotionEvent.ACTION_UP) {
      if (tapValid && !viewOnly) {
        if (tapFingers == 2 && AppSettings.enabled(getContext(), "twoFingerUndo", false)) undo();
        if (tapFingers == 3 && AppSettings.enabled(getContext(), "threeFingerRedo", false)) redo();
      }
      multiTap = false; dragging = false; textGesture = false;
      return true;
    }
    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    int action = e.getActionMasked(), ai = e.getActionIndex();
    boolean pen = StylusAdapter.isPen(e, ai);
    if (action == MotionEvent.ACTION_CANCEL) { fingerTap(e); cancelContact(); return true; }
    boolean canceledUp = android.os.Build.VERSION.SDK_INT >= 33
        && (e.getFlags() & MotionEvent.FLAG_CANCELED) != 0
        && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP);
    if (canceledUp) {
      tapValid = false;
      if (current != null && e.getPointerId(ai) == pointer) cancelContact();
      return true;
    }
    if (pen) lastStylus = SystemClock.uptimeMillis();
    // A real pen arriving after a palm/finger contact owns the document input.
    if (pen && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
        && current != null && !contactPen) cancelContact();
    if (action == MotionEvent.ACTION_DOWN || pen && action == MotionEvent.ACTION_POINTER_DOWN) activated.run();
    if (locked) { cancelContact(); return true; }
    if (!pen && current == null && SystemClock.uptimeMillis() - lastStylus < 650) return true;
    if (!pen && !contactPen && !(annotationSurface && !finger)) {
      if (fingerTap(e)) return true;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      textPressX = worldX(e.getX()); textPressY = worldY(e.getY());
      textTarget = textAt(textPressX, textPressY);
      textGesture = !viewOnly && !eraser && (textMode || textTarget != null);
    }
    if (textGesture && current == null) {
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
      scaleDetector.onTouchEvent(e);
      if (e.getPointerCount() == 1) textGestures.onTouchEvent(e);
      else {
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        textGestures.onTouchEvent(cancel); cancel.recycle();
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP) changed.run();
      return true;
    }
    if (annotationSurface
        && e.getToolType(e.getActionIndex()) == MotionEvent.TOOL_TYPE_FINGER
        && !finger) return false;
    if (current != null) {
      int i = e.findPointerIndex(pointer);
      if (i < 0 || action == MotionEvent.ACTION_CANCEL) {
        cancelContact();
        return true;
      }
      if (action == MotionEvent.ACTION_MOVE) {
        for (int h = 0; h < e.getHistorySize(); h++) add(e, i, h);
        add(e, i, -1);
        if (contactPen) requestUnbufferedDispatch(e);
        previewPrediction(e, i);
      }
      if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
          && e.getPointerId(ai) == pointer) {
        for (int h = 0; h < e.getHistorySize(); h++) add(e, i, h);
        add(e, i, -1);
        predicted = null;
        shapeHandler.removeCallbacks(expirePrediction);
        if (prediction != null && contactPen && predictionContactStarted) {
          MotionEvent isolated = StylusPrediction.singlePointer(e, i);
          try { prediction.record(isolated); } finally { isolated.recycle(); }
        }
        predictionContactStarted = false;
        adapter.writing(false);
        if (shapes
            && !current.erase
            && SystemClock.uptimeMillis() - stationarySince >= shapeHoldMillis) checkShapeHold();
        strokes.add(current);
        for (long k : touched) cache.remove(k);
        touched.clear();
        live.clear();
        boolean snapped = shapes && !current.erase && shapeApplied;
        shapeHandler.removeCallbacks(shapeCheck);
        cursorEraser = current.erase;
        current = null;
        if (snapped) rebuild();
        pointer = -1;
        contactPen = false;
        redo.clear();
        documentChanged.run();
        changed.run();
        performClick();
      }
      cursorX = e.getX(i);
      cursorY = e.getY(i);
      cursorVisible = true;
      invalidate();
      return true;
    }

    if (viewOnly && pen) return true;
    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
        && (pen || !viewOnly && finger && e.getPointerCount() == 1)) {
      if (pen) {
        if (prediction == null && isAttachedToWindow() && AppSettings.enabled(getContext(), "penPrediction", true)) prediction = adapter.prediction(this);
        lastStylus = SystemClock.uptimeMillis();
        requestUnbufferedDispatch(e);
      }
      boolean held = pen && (e.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
      int heldAction = AppSettings.penAction(getContext(), "penLongPress");
      if (held && heldAction != AppSettings.SWITCH_TOOL && heldAction != AppSettings.NONE) {
        adapter.heldButton();
        if (heldAction == AppSettings.UNDO) return true;
      }
      dragging = false;
      pointer = e.getPointerId(ai);
      contactStartIndex = strokes.size();
      contactPen = pen;
      current = new Stroke();
      current.shade = tiltShading && pen;
      current.color = penColor;
      current.erase = eraser || adapter.eraser(e, ai) || held && heldAction == AppSettings.SWITCH_TOOL;
      current.width = current.erase ? 36 : penWidth;
      touched.clear();
      live.clear();
      shapeApplied = false;
      stationaryIndex = 0;
      stationarySince = SystemClock.uptimeMillis();
      shapeHandler.removeCallbacks(shapeCheck);
      shapeHandler.postDelayed(shapeCheck, shapeHoldMillis);
      stationaryX = worldX(e.getX(ai));
      stationaryY = worldY(e.getY(ai));
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
      add(e, ai, -1);
      previewPrediction(e, ai);
      if (pen) adapter.writing(!current.erase);
      cursorX = e.getX(ai);
      cursorY = e.getY(ai);
      cursorVisible = true;
      invalidate();
      return true;
    }
    if (pen || SystemClock.uptimeMillis() - lastStylus < 650) return true;
    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    scaleDetector.onTouchEvent(e);
    float x = 0, y = 0;
    int fingers = 0;
    for (int i = 0; i < e.getPointerCount(); i++) {
      if (action == MotionEvent.ACTION_POINTER_UP && i == ai) continue;
      x += e.getX(i);
      y += e.getY(i);
      fingers++;
    }
    x /= Math.max(1, fingers);
    y /= Math.max(1, fingers);
    if (action == MotionEvent.ACTION_DOWN
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      lastX = x;
      lastY = y;
      dragging = true;
    } else if (action == MotionEvent.ACTION_MOVE && dragging) {
      panX += x - lastX;
      panY += y - lastY;
      lastX = x;
      lastY = y;
      invalidate();
      viewportChanged.run();
    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      dragging = false;
      changed.run();
    }
    return true;
  }

  private void add(MotionEvent e, int i, int h) {
    if (h < 0
        && (e.getActionMasked() == MotionEvent.ACTION_UP
            || e.getActionMasked() == MotionEvent.ACTION_POINTER_UP)
        && e.getPressure(i) <= .05f) return;
    float x = h < 0 ? e.getX(i) : e.getHistoricalX(i, h),
        y = h < 0 ? e.getY(i) : e.getHistoricalY(i, h);
    float p =
        h < 0
            ? adapter.pressure(e, i)
            : adapter.historicalPressure(e, i, h);
    float holdDistance =
        (float) Math.hypot(worldX(x) - stationaryX, worldY(y) - stationaryY) * zoom;
    if (shapeApplied && holdDistance <= shapeDistance && !(pressureEraser && p > pressureThreshold))
      return;
    if (holdDistance > shapeDistance) {
      stationaryIndex = current.dots.size();
      shapeApplied = false;
      shapeHandler.removeCallbacks(shapeCheck);
      shapeHandler.postDelayed(shapeCheck, shapeHoldMillis);
      stationarySince = SystemClock.uptimeMillis();
      stationaryX = worldX(x);
      stationaryY = worldY(y);
    }
    boolean auto = pressureEraser && p > pressureThreshold;
    if (!eraser && !adapter.eraser(e, i) && !current.erase && auto && !current.dots.isEmpty()) {
      strokes.add(current);
      for (long k : touched) cache.remove(k);
      touched.clear();
      live.clear();
      Stroke next = new Stroke();
      next.color = penColor;
      next.erase = auto;
      next.width = auto ? 36 : penWidth;
      current = next;
      predicted = null;
      adapter.writing(false);
    } else if (current.dots.isEmpty() && auto) {
      current.erase = true;
      current.width = 36;
    }
    float tilt = h < 0 ? e.getAxisValue(MotionEvent.AXIS_TILT, i) : e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i, h);
    float orientation = h < 0 ? e.getAxisValue(MotionEvent.AXIS_ORIENTATION, i) : e.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, i, h);
    if (!Float.isFinite(tilt)) tilt = 0;
    if (!Float.isFinite(orientation)) orientation = 0;
    Dot d = new Dot(worldX(x), worldY(y), pressure ? p : 1, Math.max(0, Math.min((float)Math.PI / 2, tilt)), orientation),
        a = current.dots.isEmpty() ? d : current.dots.get(current.dots.size() - 1);
    current.dots.add(d);
    indexSegment(new Segment(a, d, current, serial++), true);
  }

  @Override public boolean onDragEvent(DragEvent event) {
    if (locked || viewOnly || annotationSurface) return false;
    android.content.ClipDescription description = event.getClipDescription();
    if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
      return description != null && description.hasMimeType("text/plain");
    if (event.getAction() == DragEvent.ACTION_DROP) {
      android.content.ClipData data = event.getClipData();
      if (data == null) return false;
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < data.getItemCount(); i++) {
        CharSequence item = data.getItemAt(i).getText();
        if (item == null) continue;
        if (text.length() > 0) text.append('\n');
        text.append(item);
      }
      if (text.toString().trim().isEmpty()) return false;
      activated.run();
      putText(null, text.toString(), worldX(event.getX()), worldY(event.getY()),
          Math.min(1600, Math.max(160, worldX(getWidth()) - worldX(event.getX()) - 24)));
      return true;
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  boolean writing() {
    return current != null || textEditorOpen;
  }

  boolean hasInk() {
    for (Stroke s : strokes) if (!s.erase && !s.dots.isEmpty()) return true;
    return false;
  }

  void undo() {
    if (!strokes.isEmpty()) {
      Stroke removed = strokes.remove(strokes.size() - 1);
      if (removed.previous != null) strokes.add(removed.previous);
      redo.add(removed);
      rebuild();
      documentChanged.run();
      changed.run();
    }
  }

  void redo() {
    if (!redo.isEmpty()) {
      Stroke restored = redo.remove(redo.size() - 1);
      if (restored.previous != null) strokes.remove(restored.previous);
      strokes.add(restored);
      rebuild();
      documentChanged.run();
      changed.run();
    }
  }

  void clear() {
    cancelContact();
    strokes.clear();
    redo.clear();
    current = null;
    pointer = -1;
    rebuild();
    documentChanged.run();
    changed.run();
  }

  void setLineSpacing(float spacing) {
    if (Math.abs(spacing - lineSpacing) < .01f) return;
    Set<Stroke> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    List<Stroke> all = new ArrayList<>(strokes); all.addAll(redo);
    for (int i = 0; i < all.size(); i++) {
      Stroke s = all.get(i);
      if (!seen.add(s)) continue;
      if (s.previous != null) all.add(s.previous);
      if (s.text.isEmpty() || s.dots.size() != 2) continue;
      float ratio = spacing / s.width;
      Dot a = s.dots.get(0), b = s.dots.get(1);
      s.width = spacing;
      s.dots.set(0, new Dot(a.x, a.y * ratio, 1));
      s.dots.set(1, new Dot(a.x + (b.x - a.x) * ratio, b.y * ratio, 1));
    }
    lineSpacing = spacing;
    rebuild();
  }

  void home() {
    zoom = 1;
    panX = 0;
    panY = 0;
    invalidate();
    changed.run();
    viewportChanged.run();
  }

  void fitInk() {
    RectF b = contentBounds(strokes);
    if (b == null) {
      home();
      return;
    }
    zoom =
        Math.max(
            .2f,
            Math.min(
                3,
                Math.min(
                    getWidth() / (b.width() + 80) / density,
                    getHeight() / (b.height() + 80) / density)));
    panX = getWidth() / 2f - b.centerX() * unit();
    panY = getHeight() / 2f - b.centerY() * unit();
    invalidate();
    changed.run();
    viewportChanged.run();
  }

  static RectF contentBounds(List<Stroke> list) {
    RectF b = null;
    for (Stroke s : list)
      if (!s.erase)
        for (Dot d : s.dots) {
          float r = radius(d, s) + 8;
          RectF v = new RectF(d.x - r, d.y - r, d.x + r, d.y + r);
          if (b == null) b = v;
          else b.union(v);
        }
    return b;
  }

  Document freeze() {
    List<Stroke> copy = new ArrayList<>();
    int committed = current == null ? strokes.size() : Math.min(contactStartIndex, strokes.size());
    for (int i = 0; i < committed; i++) copy.add(strokes.get(i).copy());
    return new Document(copy, lineSpacing);
  }

  static final class Sheet {
    boolean annotation;
    final Bitmap bitmap;
    final RectF bounds;

    Sheet(Bitmap b, RectF r) {
      bitmap = b;
      bounds = r;
    }
  }

  static final class Document {
    final List<Stroke> strokes;
    final float spacing;

    Document(List<Stroke> s, float spacing) {
      strokes = s;
      this.spacing = spacing;
    }

    static Document parse(JSONObject o) throws JSONException {
      List<Stroke> strokes = new ArrayList<>();
      boolean legacy = o.optInt("version", 1) < 2;
      JSONArray a = o.optJSONArray("strokes");
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          JSONObject v = a.getJSONObject(i);
          Stroke s = new Stroke();
          s.color = v.optInt("color", 0xff22313f);
          s.erase = v.getBoolean("erase");
          s.shade = v.optBoolean("shade", false);
          s.width = (float) v.getDouble("width");
          s.text = v.optString("text", "");
          JSONArray ds = v.getJSONArray("dots");
          for (int j = 0; j < ds.length(); j++) {
            JSONArray d = ds.getJSONArray(j);
            s.dots.add(
                new Dot(
                    (float) d.getDouble(0),
                    (float) d.getDouble(1) + (legacy ? v.optInt("page", 0) * 1400 : 0),
                    (float) d.getDouble(2), (float)d.optDouble(3, 0), (float)d.optDouble(4, 0)));
          }
          strokes.add(s);
        }
      return new Document(strokes, (float) o.optDouble("spacing", 80));
    }

    JSONObject json(float cx, float cy, float zoom) throws JSONException {
      return serializeDocument(strokes, spacing, cx, cy, zoom);
    }

    List<Sheet> export() throws Exception {
      Map<Long, List<Segment>> spatial = new HashMap<>();
      Set<Long> groups = new TreeSet<>();
      int order = 0;
      for (Stroke stroke : strokes) {
        Dot a = null;
        for (Dot d : stroke.dots) {
          Segment s = new Segment(a == null ? d : a, d, stroke, order++);
          a = d;
          for (int y = tile(s.bounds.top); y <= tile(s.bounds.bottom); y++)
            for (int x = tile(s.bounds.left); x <= tile(s.bounds.right); x++) {
              spatial.computeIfAbsent(key(x, y), v -> new ArrayList<>()).add(s);
              if (!stroke.erase) groups.add(key(Math.floorDiv(x, 4), Math.floorDiv(y, 4)));
            }
        }
      }
      if (groups.size() > 64)
        throw new java.io.IOException("书写范围超过 64 张清晰图片，请缩小空白跨度或分次作答；未截断或提交草稿");
      List<Sheet> result = new ArrayList<>();
      long pixels = 0, budget = Math.min(16000000L, Runtime.getRuntime().maxMemory() / 24);
      Paint p = new Paint(3);
      Map<Stroke, Path> exportPaths = new IdentityHashMap<>();
      PorterDuffXfermode clear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
      try {
        for (long group : groups) {
          int gx = tx(group) * 4, gy = ty(group) * 4;
          TreeMap<Integer, Segment> ordered = new TreeMap<>();
          for (int y = gy; y < gy + 4; y++)
            for (int x = gx; x < gx + 4; x++) {
              List<Segment> list = spatial.get(key(x, y));
              if (list != null) for (Segment s : list) ordered.put(s.order, s);
            }
          RectF area = new RectF(gx * TILE, gy * TILE, (gx + 4) * TILE, (gy + 4) * TILE);
          Bitmap ink = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
          Canvas c = new Canvas(ink);
          c.translate(-area.left, -area.top);
          Set<Stroke> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
          for (Segment seg : ordered.values())
            if (seg.style.shade) drawSegment(c, seg, p, new Path(), clear);
            else if (drawn.add(seg.style)) {
              Path shape = exportPaths.get(seg.style);
              if (shape == null) {
                shape = outline(seg.style);
                exportPaths.put(seg.style, shape);
              }
              p.setStyle(Paint.Style.FILL);
              p.setColor(seg.style.color);
              p.setXfermode(seg.style.erase ? clear : null);
              c.drawPath(shape, p);
            }
          int l = 1024, t = 1024, r = -1, b = -1;
          int[] row = new int[1024];
          for (int y = 0; y < 1024; y++) {
            ink.getPixels(row, 0, 1024, 0, y, 1024, 1);
            for (int x = 0; x < 1024; x++)
              if (Color.alpha(row[x]) > 12) {
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
              }
          }
          if (r < l) {
            ink.recycle();
            continue;
          }
          l = Math.max(0, l - 24);
          r = Math.min(1023, r + 24);
          t = Math.max(0, t - 24);
          b = Math.min(1023, b + 24);
          pixels += (long) Math.max(160, r - l + 1) * Math.max(160, b - t + 1);
          if (pixels > budget) {
            ink.recycle();
            throw new java.io.IOException("书写图片达到清晰导出内存上限，未截断或提交；请分次作答，草稿已保留");
          }
          Bitmap out =
              Bitmap.createBitmap(
                  Math.max(160, r - l + 1), Math.max(160, b - t + 1), Bitmap.Config.ARGB_8888);
          Canvas dest = new Canvas(out);
          dest.drawColor(Color.WHITE);
          RectF cropped =
              new RectF(
                  area.left + l,
                  area.top + t,
                  area.left + l + out.getWidth(),
                  area.top + t + out.getHeight());
          dest.save();
          dest.translate(-cropped.left, -cropped.top);
          paper(dest, cropped, spacing);
          dest.restore();
          dest.drawBitmap(ink, -l, -t, null);
          ink.recycle();
          result.add(new Sheet(out, cropped));
        }
        return result;
      } catch (Exception | OutOfMemoryError e) {
        for (Sheet s : result) s.bitmap.recycle();
        throw e;
      }
    }
  }

  static JSONObject serializeDocument(
      List<Stroke> strokes, float spacing, float cx, float cy, float zoom) throws JSONException {
    JSONArray a = new JSONArray();
    for (Stroke s : strokes) {
      JSONArray dots = new JSONArray();
      for (Dot d : s.dots) dots.put(new JSONArray().put(d.x).put(d.y).put(d.pressure).put(d.tilt).put(d.orientation));
      a.put(
          new JSONObject()
              .put("shade", s.shade)
              .put("text", s.text)
              .put("color", s.color)
              .put("erase", s.erase)
              .put("width", s.width)
              .put("dots", dots));
    }
    return new JSONObject()
        .put("version", 2)
        .put("spacing", spacing)
        .put("centerX", cx)
        .put("centerY", cy)
        .put("zoom", zoom)
        .put("strokes", a);
  }

  JSONObject serialize() throws JSONException {
    return freeze().json(worldX(getWidth() / 2f), worldY(getHeight() / 2f), zoom);
  }

  void restore(JSONObject o) throws JSONException {
    cancelContact();
    strokes.clear();
    redo.clear();
    current = null;
    pointer = -1;
    boolean legacy = o.optInt("version", 1) < 2;
    lineSpacing = (float) o.optDouble("spacing", 80);
    zoom = Math.max(.2f, Math.min(5, (float) o.optDouble("zoom", 1)));
    JSONArray a = o.optJSONArray("strokes");
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject v = a.getJSONObject(i);
        Stroke s = new Stroke();
        s.color = v.optInt("color", 0xff22313f);
        s.erase = v.getBoolean("erase");
          s.shade = v.optBoolean("shade", false);
        s.width = (float) v.getDouble("width");
          s.text = v.optString("text", "");
        JSONArray ds = v.getJSONArray("dots");
        for (int j = 0; j < ds.length(); j++) {
          JSONArray d = ds.getJSONArray(j);
          s.dots.add(
              new Dot(
                  (float) d.getDouble(0),
                  (float) d.getDouble(1) + (legacy ? v.optInt("page", 0) * 1400 : 0),
                  (float) d.getDouble(2), (float)d.optDouble(3, 0), (float)d.optDouble(4, 0)));
        }
        strokes.add(s);
      }
    if (!legacy) {
      centerX = (float) o.optDouble("centerX", 0);
      centerY = (float) o.optDouble("centerY", 0);
      if (getWidth() > 0) {
        panX = getWidth() / 2f - centerX * unit();
        panY = getHeight() / 2f - centerY * unit();
      } else pendingCenter = true;
    }
    rebuild();
  }
}
