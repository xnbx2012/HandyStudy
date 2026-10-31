package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** v0.1 baseline retained ONLY in the test APK for comparative drawing benchmarks. */
final class LegacyInkView extends View {
    static final int PAGE_WIDTH=1000,PAGE_HEIGHT=1400;
    static final class Dot { float x,y,pressure; Dot(float x,float y,float p) { this.x=x; this.y=y; pressure=p; } }
    static final class Stroke { int page,color=0xff22313f; boolean erase; float width; final List<Dot> dots=new ArrayList<>(); }
    final List<Stroke> strokes=new ArrayList<>();
    private final List<Stroke> redo=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Stroke current;
    private int pointer=-1;
    int page=0,pages=1;
    boolean eraser=false,finger=false,locked=false;
    float penWidth=3f;
    int penColor=0xff22313f;
    Runnable changed=()->{};
    private final StylusAdapter adapter;
    LegacyInkView(Context context,StylusAdapter adapter) { super(context); this.adapter=adapter; setBackgroundColor(Color.WHITE); setLayerType(View.LAYER_TYPE_HARDWARE,null); setContentDescription("横线手写答题纸，默认只接受手写笔输入"); }
    public LegacyInkView(Context context,android.util.AttributeSet attrs) { this(context,StylusAdapter.Registry.select()); }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/(float)PAGE_WIDTH,getHeight()/(float)PAGE_HEIGHT);
        paper(canvas);
        int layer=canvas.saveLayer(0,0,PAGE_WIDTH,PAGE_HEIGHT,null);
        for(Stroke s:strokes) if(s.page==page) drawStroke(canvas,s);
        if(current!=null) drawStroke(canvas,current);
        canvas.restoreToCount(layer);
        canvas.restore();
    }
    private void paper(Canvas c) {
        c.drawColor(Color.WHITE); paint.setXfermode(null); paint.setColor(Color.rgb(226,234,238)); paint.setStrokeWidth(1); paint.setStyle(Paint.Style.STROKE);
        for(int y=80;y<PAGE_HEIGHT;y+=60) c.drawLine(32,y,PAGE_WIDTH-32,y,paint);
        paint.setColor(Color.rgb(242,220,218)); c.drawLine(82,30,82,PAGE_HEIGHT-30,paint);
    }
    private void drawStroke(Canvas canvas,Stroke s) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(s.color); paint.setXfermode(s.erase?new PorterDuffXfermode(PorterDuff.Mode.CLEAR):null); paint.setStrokeCap(Paint.Cap.ROUND);
        Dot previous=null;
        for(Dot dot:s.dots) {
            paint.setStrokeWidth(s.width*(s.erase?1:(.45f+dot.pressure*.75f)));
            if(previous==null) canvas.drawCircle(dot.x,dot.y,paint.getStrokeWidth()/2,paint);
            else canvas.drawLine(previous.x,previous.y,dot.x,dot.y,paint);
            previous=dot;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(locked) return true;
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            if(current!=null) return true;
            int i=e.getActionIndex(),tool=e.getToolType(i);
            if(tool!=MotionEvent.TOOL_TYPE_STYLUS && tool!=MotionEvent.TOOL_TYPE_ERASER && !finger) return true;
            pointer=e.getPointerId(i); current=new Stroke(); current.page=page; current.color=penColor; current.erase=eraser||adapter.eraser(e,i); current.width=current.erase?38:penWidth;
            if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true); add(e,i,-1); invalidate(); return true;
        }
        if(current==null) return true;
        int i=e.findPointerIndex(pointer);
        if(i<0 || action==MotionEvent.ACTION_CANCEL) { current=null; pointer=-1; invalidate(); return true; }
        if(action==MotionEvent.ACTION_MOVE) { for(int h=0;h<e.getHistorySize();h++) add(e,i,h); add(e,i,-1); }
        if((action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP)&&e.getPointerId(e.getActionIndex())==pointer) {
            add(e,i,-1); strokes.add(current); current=null; pointer=-1; redo.clear(); changed.run(); performClick(); if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        invalidate(); return true;
    }
    private void add(MotionEvent e,int i,int history) {
        float x=history<0?e.getX(i):e.getHistoricalX(i,history), y=history<0?e.getY(i):e.getHistoricalY(i,history);
        float pressure=history<0?adapter.pressure(e,i):Math.max(.1f,Math.min(1.5f,e.getHistoricalPressure(i,history)));
        current.dots.add(new Dot(x*PAGE_WIDTH/Math.max(1,getWidth()),y*PAGE_HEIGHT/Math.max(1,getHeight()),pressure));
    }
    boolean hasInk() { for(Stroke s:strokes) if(!s.erase) return true; return false; }
    void undo() { if(!strokes.isEmpty()) { redo.add(strokes.remove(strokes.size()-1)); invalidate(); changed.run(); } }
    void redo() { if(!redo.isEmpty()) { strokes.add(redo.remove(redo.size()-1)); invalidate(); changed.run(); } }
    void clearPage() { strokes.removeIf(s->s.page==page); redo.clear(); invalidate(); changed.run(); }
    void nextPage() { if(page<pages-1) page++; else if(pages<4) { pages++; page++; } invalidate(); changed.run(); }
    void previousPage() { page=Math.max(0,page-1); invalidate(); changed.run(); }
    Bitmap snapshot() {
        Bitmap b=Bitmap.createBitmap(PAGE_WIDTH,PAGE_HEIGHT*pages,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b);
        for(int i=0;i<pages;i++) { c.save(); c.translate(0,i*PAGE_HEIGHT); c.clipRect(0,0,PAGE_WIDTH,PAGE_HEIGHT); paper(c); int layer=c.saveLayer(0,0,PAGE_WIDTH,PAGE_HEIGHT,null); for(Stroke s:strokes) if(s.page==i) drawStroke(c,s); c.restoreToCount(layer); c.restore(); }
        return b;
    }
    JSONObject serialize() throws JSONException {
        JSONArray array=new JSONArray();
        for(Stroke s:strokes) { JSONArray dots=new JSONArray(); for(Dot d:s.dots) dots.put(new JSONArray().put(d.x).put(d.y).put(d.pressure));
            array.put(new JSONObject().put("page",s.page).put("color",s.color).put("erase",s.erase).put("width",s.width).put("dots",dots)); }
        return new JSONObject().put("pages",pages).put("page",page).put("strokes",array);
    }
    void restore(JSONObject o) throws JSONException {
        strokes.clear(); redo.clear(); current=null; pages=Math.max(1,Math.min(4,o.optInt("pages",1))); page=Math.max(0,Math.min(pages-1,o.optInt("page",0)));
        JSONArray array=o.optJSONArray("strokes");
        if(array!=null) for(int i=0;i<array.length();i++) { JSONObject v=array.getJSONObject(i); Stroke s=new Stroke(); s.page=v.getInt("page"); s.color=v.optInt("color",0xff22313f); s.erase=v.getBoolean("erase"); s.width=(float)v.getDouble("width"); JSONArray ds=v.getJSONArray("dots");
            for(int j=0;j<ds.length();j++) { JSONArray d=ds.getJSONArray(j); s.dots.add(new Dot((float)d.getDouble(0),(float)d.getDouble(1),(float)d.getDouble(2))); } strokes.add(s); }
        invalidate();
    }
}
