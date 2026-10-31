package com.handymath.app;

import android.view.View;
import com.vivo.penengine.impl.*;

/** vivo official SDK 2.0; all service/native failures leave standard Android input intact. */
final class VivoStylusAdapter extends ManagedStylusAdapter {
  private VivoStylusGestureManagerImpl gestures;
  private VivoStylusManagerImpl haptics;
  private final VivoStylusGestureManagerImpl.OnGestureCallback callback=this::gesture;
  public String name(){return "vivo / iQOO 手写笔 · 压感 / SDK 手势 / 预测";}
  public boolean supports(String maker){return maker.contains("vivo")||maker.contains("iqoo");}
  @Override public void attach(android.app.Activity owner,Runnable erase,Runnable colors){
    super.attach(owner,erase,colors);
    try{gestures=VivoStylusGestureManagerImpl.getInstance(owner.getApplicationContext());gestures.registerLifecycle(owner);}
    catch(RuntimeException|LinkageError unavailable){gestures=null;}
  }
  @Override public void resume(){
    super.resume();
    try{if(gestures!=null)gestures.registerGestureCallback(callback);}
    catch(RuntimeException|LinkageError unavailable){/* standard side button remains available */}
    try{if(haptics==null&&activity!=null){haptics=VivoStylusManagerImpl.getInstance(activity.getApplicationContext());haptics.init();}}
    catch(RuntimeException|LinkageError unavailable){haptics=null;}
  }
  @Override public void pause(){
    super.pause();
    try{if(gestures!=null)gestures.unregisterGestureCallback(callback);}catch(RuntimeException|LinkageError ignored){}
  }
  @Override public void writing(boolean active){
    try{if(haptics!=null)haptics.enableWritingVibrate(active&&canGesture()&&AppSettings.enabled(activity,"penHaptics",false));}
    catch(RuntimeException|LinkageError unavailable){/* unsupported pen model */}
  }
  @Override public StylusPrediction prediction(View view){return new StylusPrediction.Vivo(view);}
  @Override public void detach(){
    pause();
    try{if(gestures!=null)gestures.unregisterLifecycle(activity);}catch(RuntimeException|LinkageError ignored){}
    try{if(haptics!=null)haptics.destroy();}catch(RuntimeException|LinkageError ignored){}
    gestures=null;haptics=null;super.detach();
  }
}
