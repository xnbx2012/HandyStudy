package com.handymath.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.atomic.*;

final class InputSettingsChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static void touch(InkView ink,int action,int count,boolean move){
    int[] ids=new int[count],tools=new int[count];float[] xs=new float[count],pressure=new float[count];
    for(int i=0;i<count;i++){ids[i]=i;tools[i]=MotionEvent.TOOL_TYPE_FINGER;xs[i]=100+i*100+(move?100:0);pressure[i]=.5f;}
    StylusChecks.send(ink,StylusChecks.event(action,0,ids,tools,xs,pressure));
  }
  static void tap(InkView ink,int count,boolean move){
    touch(ink,0,1,false);
    for(int n=2;n<=count;n++)touch(ink,5|((n-1)<<8),n,false);
    if(move)touch(ink,2,count,true);
    for(int n=count;n>1;n--)touch(ink,6|((n-1)<<8),n,move);
    touch(ink,1,1,move);
  }
  static View find(View view,String text){
    if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().equals(text))return view;
    if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}
    return null;
  }
  static KeyEvent key(int action){long now=SystemClock.uptimeMillis();return new KeyEvent(now,now,action,KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY,0);}
  static void hover(InkView ink,int tool){
    MotionEvent e=StylusChecks.event(MotionEvent.ACTION_HOVER_MOVE,0,new int[]{7},new int[]{tool},new float[]{100},new float[]{0});
    try{ink.onHoverEvent(e);}finally{e.recycle();}
  }
  static void cursor(InkView ink,boolean visible,String message){
    android.graphics.Bitmap shown=android.graphics.Bitmap.createBitmap(400,400,android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Bitmap exported=android.graphics.Bitmap.createBitmap(400,400,android.graphics.Bitmap.Config.ARGB_8888);
    try{
      ink.draw(new android.graphics.Canvas(shown));
      ink.exporting=true;ink.draw(new android.graphics.Canvas(exported));
      check(!shown.sameAs(exported)==visible,message);
    }finally{ink.exporting=false;shown.recycle();exported.recycle();}
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext(); SharedPreferences prefs=AppSettings.prefs(c);
    Map<String,?> saved=new HashMap<>(prefs.getAll());
    String[] keys={"doubleTap","penDoubleTapAction","penSingleTapAction","penLongPressAction","twoFingerUndo","threeFingerRedo","penCursor","eraserCursor"};
    try {
      SharedPreferences.Editor edit=prefs.edit();for(String key:keys)edit.remove(key);edit.commit();
      check(!AppSettings.enabled(c,"penCursor",false),"cursor hidden by default");
      AtomicReference<Throwable> failure=new AtomicReference<>();
      test.runOnMainSync(()->{try{
        InkView preview=new InkView(c,new StylusChecks.Adapter());preview.layout(0,0,400,400);preview.annotationSurface=true;
        hover(preview,MotionEvent.TOOL_TYPE_STYLUS);cursor(preview,false,"pen hover hidden by default");
        StylusChecks.pen(preview,MotionEvent.ACTION_DOWN,100,.4f);cursor(preview,false,"pen contact hidden by default");
        preview.cancelContact();preview.eraser=true;
        hover(preview,MotionEvent.TOOL_TYPE_STYLUS);cursor(preview,true,"eraser hover visible independently by default");
        StylusChecks.pen(preview,MotionEvent.ACTION_DOWN,100,.4f);cursor(preview,true,"eraser contact visible by default");
        preview.cancelContact();prefs.edit().putBoolean("eraserCursor",false).putBoolean("penCursor",true).commit();
        hover(preview,MotionEvent.TOOL_TYPE_STYLUS);cursor(preview,false,"eraser switch off does not follow pen switch");
        preview.eraser=false;hover(preview,MotionEvent.TOOL_TYPE_STYLUS);cursor(preview,true,"pen switch on independent of eraser");
        hover(preview,MotionEvent.TOOL_TYPE_ERASER);cursor(preview,false,"physical eraser uses eraser setting");
        prefs.edit().remove("eraserCursor").remove("penCursor").commit();
        hover(preview,MotionEvent.TOOL_TYPE_ERASER);cursor(preview,true,"physical eraser visible by default");
        hover(preview,MotionEvent.TOOL_TYPE_STYLUS);cursor(preview,false,"return to pen resets hover tool");
        preview.pressureEraser=true;preview.pressureThreshold=.8f;
        StylusChecks.pen(preview,MotionEvent.ACTION_DOWN,100,.4f);cursor(preview,false,"pressure starts as pen");
        StylusChecks.pen(preview,MotionEvent.ACTION_MOVE,120,1);cursor(preview,true,"pressure eraser uses eraser setting");
        StylusChecks.pen(preview,MotionEvent.ACTION_MOVE,140,.4f);cursor(preview,true,"pressure eraser remains until lift");
        StylusChecks.pen(preview,MotionEvent.ACTION_UP,140,.4f);cursor(preview,true,"eraser preview retained on lift");
        StylusChecks.pen(preview,MotionEvent.ACTION_DOWN,180,.4f);cursor(preview,false,"next stroke returns to hidden pen cursor");
        preview.cancelContact();
        InkView ink=new InkView(c,new StylusChecks.Adapter());ink.layout(0,0,1000,1000);
        ink.putText(null,"first",10,10,200);ink.putText(null,"second",10,100,200);
        tap(ink,2,false);check(ink.strokes.size()==2,"undo off by default");
        prefs.edit().putBoolean("twoFingerUndo",true).commit();
        tap(ink,2,false);check(ink.strokes.size()==1,"two finger tap undoes once");
        tap(ink,3,false);check(ink.strokes.size()==1,"redo independently disabled");
        prefs.edit().putBoolean("threeFingerRedo",true).commit();
        tap(ink,3,false);check(ink.strokes.size()==2,"three finger tap redoes once");
        tap(ink,2,true);check(ink.strokes.size()==2,"drag does not undo");
        tap(ink,4,false);check(ink.strokes.size()==2,"four fingers do not undo");
        ink.finger=true;tap(ink,2,false);check(ink.strokes.size()==1,"finger drawing canceled before undo");
        ink.locked=true;tap(ink,3,false);check(ink.strokes.size()==1,"locked canvas rejects redo");
        ink.locked=false;ink.viewOnly=true;tap(ink,3,false);check(ink.strokes.size()==1,"read only canvas rejects redo");
        try(LearningDb db=new LearningDb(c)){
          BankRepository bank=new BankRepository(c,null);BankRepository.Question q=bank.questions.get(0);
          boolean was=db.favorite(bank.id,q.id);
          try{
            db.unfavorite(bank.id,q.id);db.favorite(bank,q);db.favorite(bank,q);
            check(db.favorite(bank.id,q.id),"favorite saved");
            int found=0;org.json.JSONArray rows=db.favorites();
            for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("bank").equals(bank.id)&&rows.getJSONObject(i).getString("q").equals(q.id))found++;
            check(found==1,"favorite duplicates prevented");
            try(LearningDb reopened=new LearningDb(c)){check(reopened.favorite(bank.id,q.id),"favorite survives reopening database");}
            db.unfavorite(bank.id,q.id);check(!db.favorite(bank.id,q.id),"unfavorite saved");
          }finally{if(was)db.favorite(bank,q);else db.unfavorite(bank.id,q.id);}
        }
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      MainActivity main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.waitForIdleSync();SystemClock.sleep(400);
      AtomicInteger selected=new AtomicInteger(-1),calls=new AtomicInteger();XiaomiStylusAdapter adapter=new XiaomiStylusAdapter();
      test.runOnMainSync(()->{
        adapter.attach(main,()->{},()->{});adapter.setActionHandler(action->{selected.set(action);calls.incrementAndGet();});adapter.resume();adapter.focusChanged(true);
        prefs.edit().putInt("penDoubleTapAction",AppSettings.UNDO).putInt("penSingleTapAction",AppSettings.SWITCH_TYPE).putInt("penLongPressAction",AppSettings.NONE).commit();
        check(adapter.gesture(2)&&selected.get()==AppSettings.UNDO,"double tap configured undo");
        check(adapter.gesture(3)&&selected.get()==AppSettings.SWITCH_TYPE,"single tap configured type switch");
        adapter.heldButton();check(selected.get()==AppSettings.SWITCH_TYPE,"long press none does nothing");
        prefs.edit().putBoolean("doubleTap",false).commit();check(!adapter.gesture(2),"master switch disables gestures");

      });
      if(Build.VERSION.SDK_INT>=34){
        test.runOnMainSync(()->prefs.edit().putBoolean("doubleTap",true).putInt("penLongPressAction",AppSettings.UNDO).commit());
        int before=calls.get();
        test.runOnMainSync(()->{adapter.key(key(0));adapter.key(key(1));});
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout()+100);
        check(calls.get()==before+1&&selected.get()==AppSettings.SWITCH_TYPE,"key single tap waits and dispatches once");
        before=calls.get();
        test.runOnMainSync(()->{
          adapter.key(key(0));adapter.key(key(1));
          adapter.key(key(0));adapter.key(key(1));
        });
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout()+100);
        check(calls.get()==before+1&&selected.get()==AppSettings.UNDO,"key double tap dispatches without single tap");
        before=calls.get();
        test.runOnMainSync(()->adapter.key(key(0)));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+100);
        test.runOnMainSync(()->adapter.key(key(1)));
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout()+100);
        check(calls.get()==before+1&&selected.get()==AppSettings.UNDO,"key long press dispatches without single tap");
      }
      test.runOnMainSync(()->{adapter.detach();main.finish();});
      SettingsActivity settings=(SettingsActivity)test.startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.runOnMainSync(()->{
        check(find(settings.getWindow().getDecorView(),"显示笔触提示（悬浮 / 落笔圆圈）")!=null,"cursor setting visible");
        check(find(settings.getWindow().getDecorView(),"显示橡皮提示（悬浮 / 擦除圆圈）")!=null,"independent eraser cursor setting visible");
        check(find(settings.getWindow().getDecorView(),"双指单击撤销")!=null&&find(settings.getWindow().getDecorView(),"三指单击重做")!=null,"independent gesture controls visible");
        settings.finish();
      });
      FavoritesActivity favorites=(FavoritesActivity)test.startActivitySync(new Intent(c,FavoritesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.runOnMainSync(()->{check(find(favorites.getWindow().getDecorView(),"我的收藏")!=null,"favorites screen opens");favorites.finish();});
    }finally{
      SharedPreferences.Editor edit=prefs.edit();for(String key:keys){edit.remove(key);Object value=saved.get(key);if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);}edit.commit();
    }
  }
}
