package com.handymath.app;

import android.os.Build;
import android.view.*;
import java.util.*;

/** Standard input semantics stay valid when a vendor engine is unavailable. */
public interface StylusAdapter {
    String name();
    boolean supports(String manufacturer);
    static boolean isPen(MotionEvent e,int i){return e.getToolType(i)==MotionEvent.TOOL_TYPE_STYLUS||e.getToolType(i)==MotionEvent.TOOL_TYPE_ERASER;}
    default float pressure(MotionEvent event,int index){return normalizePressure(event,event.getPressure(index));}
    default float historicalPressure(MotionEvent event,int index,int history){return normalizePressure(event,event.getHistoricalPressure(index,history));}
    static float normalizePressure(MotionEvent event,float value){
        if(!Float.isFinite(value))return .5f;
        InputDevice device=event.getDevice();
        InputDevice.MotionRange range=device==null?null:device.getMotionRange(MotionEvent.AXIS_PRESSURE,event.getSource());
        if(range!=null&&range.getRange()>0)value=(value-range.getMin())/range.getRange();
        return Math.max(.05f,Math.min(1,value));
    }
    default boolean eraser(MotionEvent event,int index){
        return isPen(event,index)&&event.getToolType(index)==MotionEvent.TOOL_TYPE_ERASER;
    }
    default void setActionHandler(java.util.function.IntConsumer handler){}
    default void heldButton(){}
    default void attach(android.app.Activity activity,Runnable toggleEraser,Runnable showPalette){}
    default void setGestureAllowed(java.util.function.BooleanSupplier allowed){}
    default void resume(){}
    default void pause(){}
    default boolean key(KeyEvent event){return false;}
    default void focusChanged(boolean focused){}
    default void detach(){}
    default void writing(boolean active){}
    default StylusPrediction prediction(View view){return new StylusPrediction.Android(view);}

    final class Registry {
        private static final List<StylusAdapter> adapters=new ArrayList<>();
        public static void register(StylusAdapter adapter){adapters.add(0,adapter);}
        public static StylusAdapter select(){return select(Build.MANUFACTURER,Build.BRAND);}
        static StylusAdapter select(String manufacturer,String brand){
            String maker=(manufacturer+" "+brand).toLowerCase(Locale.ROOT);
            for(StylusAdapter a:adapters)if(a.supports(maker))return a;
            if(maker.contains("huawei"))return new HuaweiStylusAdapter();
            if(maker.contains("vivo")||maker.contains("iqoo"))return new VivoStylusAdapter();
            if(maker.contains("xiaomi")||maker.contains("redmi")||maker.contains("poco"))return new XiaomiStylusAdapter();
            return new Standard(false);
        }
    }
    final class Standard extends ManagedStylusAdapter {
        private final boolean huawei;
        Standard(boolean huawei){this.huawei=huawei;}
        public String name(){return huawei?"华为 M-Pencil · 标准压感 / 侧键":"Android 标准手写笔 · 压感 / 侧键 / 预测";}
        public boolean supports(String maker){return true;}
    }
}
