package com.handymath.app;

import android.app.Activity;
import android.os.*;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;

/** Gesture callbacks never consume keys outside a focused, usable writing surface. */
abstract class ManagedStylusAdapter implements StylusAdapter {
  protected Activity activity;
  protected Runnable toggle,palette;
  protected boolean resumed,focused;
  private BooleanSupplier allowed=()->true;
  private final Handler main=new Handler(Looper.getMainLooper());
  private int pressedKey=-1;
  private boolean longPressed, secondTap;
  private int firstTapKey=-1;
  private long firstTapUp;
  private IntConsumer actionHandler;
  private final Runnable singleTap=()->{firstTapUp=0;dispatch("penSingleTap");};
  private final Runnable longPress=()->{if(pressedKey!=-1){longPressed=true;firstTapUp=0;main.removeCallbacks(singleTap);dispatch("penLongPress");}};
  @Override public void setActionHandler(IntConsumer handler){actionHandler=handler;}
  @Override public void heldButton(){if(pressedKey==-1)dispatch("penLongPress");}
  private boolean dispatch(String name){
    if(!canGesture())return false;
    int selected=AppSettings.penAction(activity,name);
    if(selected==AppSettings.NONE)return false;
    Runnable action=()->{
      if(!canGesture())return;
      int current=AppSettings.penAction(activity,name);
      if(current==AppSettings.NONE)return;
      if(actionHandler!=null)actionHandler.accept(current);
      else if(current==AppSettings.SWITCH_TOOL&&toggle!=null)toggle.run();
    };
    if(Looper.myLooper()==Looper.getMainLooper())action.run();else main.post(action);
    return true;
  }
  private void cancelKeys(){pressedKey=-1;firstTapUp=0;main.removeCallbacks(singleTap);main.removeCallbacks(longPress);}
  @Override public void attach(Activity owner,Runnable erase,Runnable colors){activity=owner;toggle=erase;palette=colors;}
  @Override public void setGestureAllowed(BooleanSupplier available){allowed=available;}
  @Override public void resume(){resumed=true;}
  @Override public void pause(){resumed=false;cancelKeys();writing(false);}
  @Override public void focusChanged(boolean hasFocus){focused=hasFocus;if(!hasFocus){cancelKeys();writing(false);}}
  protected boolean canGesture(){return activity!=null&&resumed&&focused&&activity.hasWindowFocus()&&!activity.isFinishing()&&!activity.isDestroyed()&&allowed.getAsBoolean();}
  boolean gesture(int function){
    return function==2?dispatch("penDoubleTap"):function==3||function==4?dispatch("penSingleTap"):false;
  }
  @Override public boolean key(KeyEvent event){
    if(Build.VERSION.SDK_INT<34)return false;
    int code=event.getKeyCode();
    if(code!=KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY&&code!=KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY)return false;
    if(event.getAction()==KeyEvent.ACTION_DOWN){
      if(!canGesture()||!AppSettings.enabled(activity,"doubleTap",true))return false;
      if(event.getRepeatCount()==0){
        secondTap=firstTapUp!=0&&firstTapKey==code&&event.getEventTime()-firstTapUp<=ViewConfiguration.getDoubleTapTimeout();
        if(secondTap)main.removeCallbacks(singleTap);
        pressedKey=code;longPressed=false;
        main.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());
      }
      return pressedKey==code;
    }
    if(event.getAction()==KeyEvent.ACTION_UP&&pressedKey==code){
      pressedKey=-1;main.removeCallbacks(longPress);
      if(event.isCanceled()){cancelKeys();return true;}
      if(!longPressed){
        long now=event.getEventTime();
        if(secondTap){
          main.removeCallbacks(singleTap);firstTapUp=0;dispatch("penDoubleTap");
        }else{firstTapUp=now;firstTapKey=code;main.removeCallbacks(singleTap);main.postDelayed(singleTap,ViewConfiguration.getDoubleTapTimeout());}
      }
      return true;
    }
    return false;
  }
  @Override public void detach(){pause();main.removeCallbacksAndMessages(null);activity=null;toggle=null;palette=null;actionHandler=null;focused=false;}
}
