package com.handymath.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;

/** Drag a rectangle on the oriented source photo; stored coordinates are normalized. */
final class PhotoCropView extends View {
  final Bitmap image;final RectF area=new RectF(),selection=new RectF();final Paint paint=new Paint(3);float startX,startY;
  PhotoCropView(Context c,Bitmap bitmap){super(c);image=bitmap;setContentDescription("拖动框选照片中的题目、参考答案或手写作答区域");}
  @Override protected void onDraw(Canvas c){
    float scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;
    area.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);c.drawColor(0xff233446);c.drawBitmap(image,null,area,paint);
    if(!selection.isEmpty()){paint.setColor(0x35197d78);paint.setStyle(Paint.Style.FILL);c.drawRect(selection,paint);paint.setColor(0xff40c7b8);paint.setStrokeWidth(AppUi.dp(getContext(),3));paint.setStyle(Paint.Style.STROKE);c.drawRect(selection,paint);paint.setStyle(Paint.Style.FILL);}
  }
  @Override public boolean onTouchEvent(MotionEvent e){
    float x=Math.max(area.left,Math.min(area.right,e.getX())),y=Math.max(area.top,Math.min(area.bottom,e.getY()));
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=x;startY=y;getParent().requestDisallowInterceptTouchEvent(true);selection.setEmpty();}
    else if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){selection.set(Math.min(startX,x),Math.min(startY,y),Math.max(startX,x),Math.max(startY,y));if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();}
    invalidate();return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
  JSONObject region(int role)throws Exception{
    if(selection.width()<12||selection.height()<12)throw new java.io.IOException("请先在照片上拖动框选内容");
    return new JSONObject().put("role",role).put("left",Math.round((selection.left-area.left)/area.width()*1000))
      .put("top",Math.round((selection.top-area.top)/area.height()*1000)).put("right",Math.round((selection.right-area.left)/area.width()*1000)).put("bottom",Math.round((selection.bottom-area.top)/area.height()*1000));
  }
}
