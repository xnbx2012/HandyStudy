package com.handymath.app;

import android.graphics.PointF;
import android.view.*;
import androidx.input.motionprediction.MotionEventPredictor;
import com.vivo.penengine.impl.VivoAlgorithmManagerImpl;

/** Per-canvas predictor. Predicted coordinates are previews, never document samples. */
interface StylusPrediction {
  PointF record(MotionEvent event);
  static MotionEvent singlePointer(MotionEvent event,int index) {
    MotionEvent.PointerProperties properties=new MotionEvent.PointerProperties();
    event.getPointerProperties(index,properties);
    MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_POINTER_DOWN)action=MotionEvent.ACTION_DOWN;
    if(action==MotionEvent.ACTION_POINTER_UP)action=MotionEvent.ACTION_UP;
    int history=action==MotionEvent.ACTION_MOVE?event.getHistorySize():0;
    if(history>0)event.getHistoricalPointerCoords(index,0,coords);else event.getPointerCoords(index,coords);
    MotionEvent result=MotionEvent.obtain(event.getDownTime(),history>0?event.getHistoricalEventTime(0):event.getEventTime(),action,1,
        new MotionEvent.PointerProperties[]{properties},new MotionEvent.PointerCoords[]{coords},
        event.getMetaState(),event.getButtonState(),event.getXPrecision(),event.getYPrecision(),event.getDeviceId(),event.getEdgeFlags(),event.getSource(),event.getFlags());
    for(int h=1;h<history;h++){event.getHistoricalPointerCoords(index,h,coords);result.addBatch(event.getHistoricalEventTime(h),new MotionEvent.PointerCoords[]{coords},event.getMetaState());}
    if(history>0){event.getPointerCoords(index,coords);result.addBatch(event.getEventTime(),new MotionEvent.PointerCoords[]{coords},event.getMetaState());}
    return result;
  }
  default void close(){}
  final class Android implements StylusPrediction {
    private MotionEventPredictor predictor;
    Android(View view){try{predictor=MotionEventPredictor.newInstance(view);}catch(RuntimeException|LinkageError ignored){}}
    public PointF record(MotionEvent e){
      if(predictor==null)return null;
      try{
        predictor.record(e);
        if(e.getActionMasked()!=MotionEvent.ACTION_MOVE)return null;
        MotionEvent next=predictor.predict();if(next==null)return null;
        try{return new PointF(next.getX(),next.getY());}finally{next.recycle();}
      }catch(RuntimeException|LinkageError unsupported){predictor=null;return null;}
    }
    @Override public void close(){predictor=null;}
  }
  final class Vivo implements StylusPrediction {
    private VivoAlgorithmManagerImpl engine;
    private final Android fallback;
    Vivo(View view){fallback=new Android(view);try{engine=new VivoAlgorithmManagerImpl(view.getContext().getApplicationContext());}catch(RuntimeException|LinkageError ignored){}}
    public PointF record(MotionEvent e){
      PointF standard=fallback.record(e);
      try{if(engine!=null&&engine.isEstimateEnable()){
        PointF point=engine.computeEstimatePoint(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&point!=null&&Float.isFinite(point.x)&&Float.isFinite(point.y))return new PointF(point.x,point.y);
      }}catch(RuntimeException|LinkageError unsupported){closeEngine();}
      return standard;
    }
    private void closeEngine(){try{if(engine!=null)engine.release();}catch(RuntimeException|LinkageError ignored){}engine=null;}
    @Override public void close(){closeEngine();fallback.close();}
  }
}
