package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import java.util.*;

final class SplitLayoutChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static void touch(View view,int action,float x,float y){
    long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);
    try{view.onTouchEvent(e);}finally{e.recycle();}
  }
  static void drag(Instrumentation test,StudySplitLayout split,float distance,boolean cancel)throws Exception{
    View divider=split.divider;
    test.runOnMainSync(()->touch(divider,0,divider.getWidth()/2f,divider.getHeight()/2f));
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+100);
    test.runOnMainSync(()->{
      float x=split.horizontal?12+distance:divider.getWidth()/2f;
      float y=split.horizontal?divider.getHeight()/2f:12+distance;
      touch(divider,2,x,y);touch(divider,cancel?3:1,x,y);
    });test.waitForIdleSync();
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();BankRepository bank=new BankRepository(c,null);
    SharedPreferences prefs=c.getSharedPreferences("study-layout-"+bank.id,0);
    Map<String,?> saved=new HashMap<>(prefs.getAll());
    prefs.edit().clear().putFloat("widthShare",.4f).putFloat("heightShare",.35f).commit();
    MainActivity main=null;
    try{
      main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()));
      test.waitForIdleSync();final MainActivity activity=main;
      StudySplitLayout split=(StudySplitLayout)AnswerRetentionChecks.field(main,"studySplit");
      InkView original=(InkView)AnswerRetentionChecks.field(main,"ink");
      float start=split.questionShare();
      test.runOnMainSync(()->{
        float x=split.divider.getWidth()/2f,y=split.divider.getHeight()/2f;
        touch(split.divider,0,x,y);touch(split.divider,2,x+80,y+80);touch(split.divider,1,x+80,y+80);
      });
      check(Math.abs(split.questionShare()-start)<.001,"drag before long press does not resize or swap");
      drag(test,split,90,false);check(split.questionShare()>start,"long press drag resizes question pane");
      float resized=split.questionShare();
      test.runOnMainSync(()->split.divider.performClick());test.waitForIdleSync();
      check(split.swapped()&&Math.abs(split.questionShare()-resized)<.001,"swap preserves question share");
      check(AnswerRetentionChecks.field(main,"ink")==original,"swap retains the existing answer canvas");
      drag(test,split,60,false);check(split.questionShare()<resized,"drag direction follows swapped pane order");
      float stable=split.questionShare();drag(test,split,100,true);
      check(Math.abs(split.questionShare()-stable)<.001,"cancel restores pre-drag size");
      AnswerRetentionChecks.ui(test,()->{
        StudySplitLayout restored=new StudySplitLayout(c,bank.id,split.horizontal);
        check(restored.swapped()&&Math.abs(restored.questionShare()-stable)<.001,"same bank restores ratio and order");
        StudySplitLayout rotated=new StudySplitLayout(c,bank.id,!split.horizontal);
        check(rotated.swapped()&&Math.abs(rotated.questionShare()-(split.horizontal?.35f:.4f))<.001,"width and height shares are stored independently");
        rotated.setPanes(new View(c),new View(c));
        rotated.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY));
        rotated.layout(0,0,1000,1400);
        check(rotated.getChildAt(0).getMeasuredHeight()>0,"portrait panes have allocated height");
        String otherBank=bank.id.equals("wzx-basic")?"wzx-full":"wzx-basic";
        StudySplitLayout other=new StudySplitLayout(c,otherBank,split.horizontal);
        SharedPreferences otherPrefs=c.getSharedPreferences("study-layout-"+otherBank,0);
        check(other.swapped()==otherPrefs.getBoolean("swapped",false),"layout state is read from the selected bank only");
        View row=activity.findViewById(android.R.id.content);
        View submit=InputSettingsChecks.find(row,"提交批改"),hint=InputSettingsChecks.find(row,"没思路"),partial=InputSettingsChecks.find(row,"只能做到这");
        int[] s=new int[2],h=new int[2],p=new int[2];submit.getLocationOnScreen(s);hint.getLocationOnScreen(h);partial.getLocationOnScreen(p);
        check(s[1]==h[1]&&h[1]==p[1],"three actions share one row");
        View favorite=InputSettingsChecks.find(row,"☆ 收藏");
        if(favorite==null)favorite=InputSettingsChecks.find(row,"★ 已收藏");
        View library=InputSettingsChecks.find(row,"选题");
        check(favorite!=null&&favorite.getParent()==library.getParent(),"favorite shares header with question picker");
        check(submit.getWidth()>=88*c.getResources().getDisplayMetrics().density-1,"action buttons have comfortable width");
        check(submit.getParent().getParent() instanceof android.widget.LinearLayout,"actions grouped beside tools");
        android.view.ViewGroup command=(android.view.ViewGroup)submit.getParent().getParent();
        check(command.getChildAt(0) instanceof android.widget.HorizontalScrollView,"tools occupy left side of command row");
        check(submit.getWidth()<activity.getResources().getDisplayMetrics().widthPixels/3,"submit button no longer stretches across a third of the screen");
        check(command.getHeight()<=Math.ceil(50*c.getResources().getDisplayMetrics().density),"single compact command row");
      });
      Bitmap screenshot=test.getUiAutomation().takeScreenshot();
      try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"split-layout.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
    }finally{
      if(main!=null){MainActivity last=main;test.runOnMainSync(last::finish);test.waitForIdleSync();}
      SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:saved.entrySet()){if(e.getValue() instanceof Float)edit.putFloat(e.getKey(),(Float)e.getValue());if(e.getValue() instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)e.getValue());}edit.commit();
    }
  }
}
