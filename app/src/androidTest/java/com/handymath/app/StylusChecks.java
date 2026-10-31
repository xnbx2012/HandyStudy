package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Real MotionEvent flows: pointer identity, cancellation, axes and prediction isolation. */
final class StylusChecks {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static MotionEvent event(int action,int flags,int[] ids,int[] tools,float[] xs,float[] pressures){
    MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
    for(int i=0;i<ids.length;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=tools[i];coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=100;coords[i].pressure=pressures[i];coords[i].setAxisValue(MotionEvent.AXIS_TILT,.8f);coords[i].setAxisValue(MotionEvent.AXIS_ORIENTATION,.3f);}
    long time=SystemClock.uptimeMillis();return MotionEvent.obtain(time,time,action,ids.length,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,flags);
  }
  static void pen(InkView view,int action,float x,float pressure){MotionEvent e=event(action,0,new int[]{7},new int[]{MotionEvent.TOOL_TYPE_STYLUS},new float[]{x},new float[]{pressure});try{view.onTouchEvent(e);}finally{e.recycle();}}
  static void send(InkView view,MotionEvent e){try{view.onTouchEvent(e);}finally{e.recycle();}}
  static final class Adapter implements StylusAdapter {
    int starts,stops;
    public String name(){return "test";}public boolean supports(String maker){return true;}
    public void writing(boolean active){if(active)starts++;else stops++;}
  }
  static void run(android.app.Instrumentation test)throws Exception{
    check(StylusAdapter.Registry.select("Xiaomi","Redmi") instanceof XiaomiStylusAdapter,"Xiaomi and Redmi routed explicitly");
    check(StylusAdapter.Registry.select("BBK","iQOO") instanceof VivoStylusAdapter,"iQOO routed to vivo engine");
    check(StylusAdapter.Registry.select("vivo","vivo") instanceof VivoStylusAdapter,"vivo routed explicitly");
    check(StylusAdapter.Registry.select("Google","google") instanceof StylusAdapter.Standard,"unknown vendor gets standard fallback");
    AtomicReference<Throwable> failure=new AtomicReference<>();Context c=test.getTargetContext();
    test.runOnMainSync(()->{try{
      Adapter adapter=new Adapter();InkView ink=new InkView(c,adapter);ink.layout(0,0,1000,1000);
      java.lang.reflect.Field prediction=InkView.class.getDeclaredField("prediction");prediction.setAccessible(true);
      AtomicInteger seenPointer=new AtomicInteger(-1);
      prediction.set(ink,(StylusPrediction)e->{seenPointer.set(e.getPointerId(0));return e.getActionMasked()==MotionEvent.ACTION_MOVE?new PointF(e.getX()+20,e.getY()):null;});
      pen(ink,MotionEvent.ACTION_DOWN,100,.4f);pen(ink,MotionEvent.ACTION_MOVE,150,.7f);
      check(seenPointer.get()==7,"predictor receives correct non-zero pointer ID");
      java.lang.reflect.Field preview=InkView.class.getDeclaredField("predicted");preview.setAccessible(true);check(preview.get(ink)!=null,"prediction preview generated");
      check(ink.serialize().getJSONArray("strokes").length()==0,"uncommitted prediction excluded from saved state");
      pen(ink,MotionEvent.ACTION_UP,200,.4f);
      check(ink.strokes.size()==1&&ink.strokes.get(0).dots.size()==3,"only actual points committed");
      check(Math.abs(ink.strokes.get(0).dots.get(1).x-ink.worldX(150))<.01f&&preview.get(ink)==null,"predicted point never becomes ink");
      check(adapter.starts==1&&adapter.stops>0,"haptics stop on pen up");
      InkView.Dot dot=ink.strokes.get(0).dots.get(1);check(Math.abs(dot.tilt-.8)<.001&&Math.abs(dot.orientation-.3)<.001,"tilt and orientation captured");
      InkView.Document parsed=InkView.Document.parse(ink.serialize());check(Math.abs(parsed.strokes.get(0).dots.get(1).tilt-.8)<.001,"axis values round trip");
      InkView.Stroke shade=ink.strokes.get(0).copy();float plain=InkView.radius(dot,shade);shade.shade=true;check(InkView.radius(dot,shade)>plain,"opt-in tilt broadens shading while round pen remains default");
      ink.clear();ink.pressureEraser=true;ink.pressureThreshold=.8f;
      ink.pressureEraserDelayMillis=200;ink.pressureEraserRadius=12;
      pen(ink,0,100,.3f);pen(ink,2,150,1);
      check(ink.strokes.isEmpty(),"brief high pressure does not switch to eraser");
      pen(ink,2,250,1);
      check(ink.strokes.isEmpty(),"moving outside hold radius restarts the delay");
      java.lang.reflect.Field pressureSince=InkView.class.getDeclaredField("pressureHoldSince");
      pressureSince.setAccessible(true);
      pressureSince.setLong(ink,SystemClock.uptimeMillis()-201);
      pen(ink,2,250,1);
      check(ink.strokes.size()==1,"stationary high pressure after delay creates provisional fragment");
      check(ink.serialize().getJSONArray("strokes").length()==0,"provisional pressure fragments never enter a snapshot");
      pen(ink,MotionEvent.ACTION_CANCEL,150,.5f);check(ink.strokes.isEmpty(),"cancel rolls back all fragments of the contact");
      if(Build.VERSION.SDK_INT>=33){
        pen(ink,0,100,.4f);
        send(ink,event(MotionEvent.ACTION_UP,MotionEvent.FLAG_CANCELED,new int[]{7},new int[]{2},new float[]{200},new float[]{.4f}));
        check(ink.strokes.isEmpty(),"FLAG_CANCELED pen lift discards contact");
      }
      ink.pressureEraser=false;ink.finger=true;
      // Reset recent-stylus timestamp only to simulate a fresh finger-first sequence.
      java.lang.reflect.Field recent=InkView.class.getDeclaredField("lastStylus");recent.setAccessible(true);recent.setLong(ink,0);
      send(ink,event(0,0,new int[]{2},new int[]{1},new float[]{30},new float[]{.4f}));
      send(ink,event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),0,new int[]{2,7},new int[]{1,2},new float[]{30,500},new float[]{.4f,.5f}));
      send(ink,event(2,0,new int[]{2,7},new int[]{1,2},new float[]{50,550},new float[]{.4f,.5f}));
      if(Build.VERSION.SDK_INT>=33)send(ink,event(MotionEvent.ACTION_POINTER_UP,MotionEvent.FLAG_CANCELED,new int[]{2,7},new int[]{1,2},new float[]{50,550},new float[]{.4f,.5f}));
      send(ink,event(MotionEvent.ACTION_POINTER_UP|(1<<8),0,new int[]{2,7},new int[]{1,2},new float[]{50,600},new float[]{.4f,.5f}));
      check(ink.strokes.size()==1,"pen takes ownership from finger/palm");
      for(InkView.Dot d:ink.strokes.get(0).dots)check(d.x>=ink.worldX(500)-.01f,"palm coordinates excluded and pointer index one retained");
      int before=ink.strokes.size();send(ink,event(0,0,new int[]{2},new int[]{1},new float[]{40},new float[]{.4f}));send(ink,event(1,0,new int[]{2},new int[]{1},new float[]{60},new float[]{.4f}));check(ink.strokes.size()==before,"palm suppressed immediately after pen lift");
      ink.cancelContact();
      MotionEvent mouse=event(MotionEvent.ACTION_HOVER_MOVE,0,new int[]{0},new int[]{MotionEvent.TOOL_TYPE_MOUSE},new float[]{40},new float[]{0});ink.onHoverEvent(mouse);mouse.recycle();
      java.lang.reflect.Field cursor=InkView.class.getDeclaredField("cursorVisible");cursor.setAccessible(true);check(!(Boolean)cursor.get(ink),"mouse hover does not activate stylus cursor or palm lock");
      MotionEvent hover=event(MotionEvent.ACTION_HOVER_MOVE,0,new int[]{7},new int[]{2},new float[]{100},new float[]{0});ink.onHoverEvent(hover);hover.recycle();check((Boolean)cursor.get(ink),"pen hover shows brush preview");
      ink.cancelContact();ink.finger=false;
      StylusPrediction fallback=new StylusPrediction.Vivo(ink);pen(ink,0,100,.5f);pen(ink,1,150,.5f);fallback.close();
      check(ink.hasInk(),"unavailable vivo engine leaves ordinary input usable");
      MotionEvent abnormal=event(2,0,new int[]{7},new int[]{2},new float[]{40},new float[]{2});check(adapter.pressure(abnormal,0)<=1,"out-of-range pressure normalized");abnormal.recycle();
    }catch(Throwable e){failure.set(e);}});
    if(failure.get()!=null)throw new AssertionError(failure.get());
    String[] permissions=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
    check(permissions==null||!Arrays.asList(permissions).contains("android.permission.QUERY_ALL_PACKAGES"),"SDK does not add broad package visibility permission");
    MainActivity main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation","@builtin/sample"));test.waitForIdleSync();SystemClock.sleep(300);
    AtomicInteger toggle=new AtomicInteger(),palette=new AtomicInteger();XiaomiStylusAdapter gesture=new XiaomiStylusAdapter();
    boolean old=AppSettings.enabled(c,"doubleTap",true);AppSettings.prefs(c).edit().putBoolean("doubleTap",true).commit();
    try{test.runOnMainSync(()->{
      gesture.attach(main,toggle::incrementAndGet,palette::incrementAndGet);gesture.resume();gesture.focusChanged(true);gesture.setGestureAllowed(()->true);
      check(gesture.gesture(2)&&toggle.get()==1,"foreground double tap maps to tool switch");
      check(gesture.gesture(4)&&toggle.get()==2,"single button gesture uses configured action");
      if(Build.VERSION.SDK_INT>=34){
        check(gesture.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY)),"standard stylus key down consumed");
        check(gesture.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY)),"standard stylus key up consumed before tap disambiguation");
      }
      check(!gesture.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP)),"ordinary keyboard and volume keys pass through");
      int switches=toggle.get();gesture.pause();check(!gesture.gesture(2)&&toggle.get()==switches,"background gesture not consumed");
      gesture.resume();gesture.setGestureAllowed(()->false);check(!gesture.gesture(2),"busy or unavailable canvas refuses gesture");gesture.setGestureAllowed(()->true);
      AppSettings.prefs(c).edit().putBoolean("doubleTap",false).commit();check(!gesture.gesture(2),"user switch disables vendor gesture");
      gesture.detach();main.finish();
    });}finally{AppSettings.prefs(c).edit().putBoolean("doubleTap",old).commit();}
  }
}
