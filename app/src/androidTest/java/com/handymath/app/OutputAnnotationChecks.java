package com.handymath.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.*;
import java.util.concurrent.*;
import org.json.*;

final class OutputAnnotationChecks {
  static Object field(Object o,String n)throws Exception{return AnswerRetentionChecks.field(o,n);}
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static void draw(AnnotatedPaper page)throws Exception{
    check(page.getWidth()>0&&page.getHeight()>0,"annotation page laid out");
    for(int action:new int[]{0,2,1}){
      MotionEvent e=StylusChecks.event(action,0,new int[]{7},new int[]{MotionEvent.TOOL_TYPE_STYLUS},new float[]{30+action*5},new float[]{.5f});
      e.setLocation(30+action*5,Math.min(15,page.getHeight()-1));
      try{check(page.dispatchTouchEvent(e),"output page consumes pen events");}finally{e.recycle();}
    }
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();BankRepository bank=new BankRepository(c,null);String id=bank.questions.get(bank.questions.size()-1).id;
    StudyStore store=new StudyStore(c,bank.id);JSONObject before=store.load(id);
    JSONObject result=new JSONObject().put("score",5).put("correct",false).put("studentWork","x=2").put("feedback","答案解析：检查第二步。\n$$x=2$$");
    JSONObject hint=new JSONObject().put("hint","思路提示：先移项。\n$$2x=4$$").put("feedback","");
    Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()).putExtra("questionId",id);
    MainActivity main=null;
    try{
      store.save(id,new JSONObject().put("result",result).put("history",new JSONArray().put(hint)).put("hints",1));
      main=(MainActivity)test.startActivitySync(intent);test.waitForIdleSync();SystemClock.sleep(1000);final MainActivity first=main;
      AnswerRetentionChecks.ui(test,()->{
        AnnotatedPaper output=(AnnotatedPaper)field(first,"resultPaper"),tips=(AnnotatedPaper)field(first,"hintPaper");
        draw(output);check(output.ink.strokes.size()==1&&field(first,"activeInk")==output.ink,"answer overlay receives handwriting and becomes active tool target");
        output.ink.undo();check(!output.ink.hasInk(),"answer annotations undo");output.ink.redo();
        draw(tips);check(tips.ink.strokes.size()==1&&field(first,"activeInk")==tips.ink,"hint overlay receives handwriting");
        JSONObject current=(JSONObject)field(first,"lastResult");
        check(current.getString("feedback").equals(result.getString("feedback")),"annotating does not alter generated explanation");
        check(current.getJSONObject("inkAnnotations").getJSONArray("strokes").length()==1,"result stores its own annotations");
        JSONArray history=(JSONArray)field(first,"history");
        check(history.getJSONObject(0).getJSONObject("inkAnnotations").getJSONArray("strokes").length()==1,"hint stores separate annotations");
        AnswerRetentionChecks.invoke(first,"refreshFeedback");
        check(output.ink.strokes.size()==1&&tips.ink.strokes.size()==1,"feedback refresh retains marks");
        output.ink.undo();output.ink.redo();check(output.ink.strokes.size()==1,"feedback refresh retains undo redo stack");
        AnswerRetentionChecks.invoke(first,"save");
      });
      ((ExecutorService)field(first,"saver")).submit(()->{}).get(10,TimeUnit.SECONDS);
      AnswerRetentionChecks.ui(test,first::finish);test.waitForIdleSync();main=null;
      main=(MainActivity)test.startActivitySync(intent);test.waitForIdleSync();SystemClock.sleep(1000);final MainActivity reopened=main;
      AnswerRetentionChecks.ui(test,()->{
        AnnotatedPaper output=(AnnotatedPaper)field(reopened,"resultPaper"),tips=(AnnotatedPaper)field(reopened,"hintPaper");
        check(output.ink.strokes.size()==1&&tips.ink.strokes.size()==1,"answer and hint handwriting survive reopening");
        float x=output.ink.strokes.get(0).dots.get(0).x;
        Configuration rotated=new Configuration(reopened.getResources().getConfiguration());
        rotated.orientation=rotated.orientation==Configuration.ORIENTATION_LANDSCAPE?Configuration.ORIENTATION_PORTRAIT:Configuration.ORIENTATION_LANDSCAPE;
        reopened.onConfigurationChanged(rotated);
        AnnotatedPaper rebuilt=(AnnotatedPaper)field(reopened,"resultPaper");
        check(rebuilt.ink.strokes.size()==1&&rebuilt.ink.strokes.get(0).dots.get(0).x==x,"layout rebuild preserves logical annotation coordinates");
        check(((AnnotatedPaper)field(reopened,"hintPaper")).ink.strokes.size()==1,"hint marks survive layout rebuild");
      });
    }finally{
      if(main!=null){MainActivity last=main;ExecutorService saver=(ExecutorService)field(last,"saver");AnswerRetentionChecks.ui(test,last::finish);saver.awaitTermination(10,TimeUnit.SECONDS);}
      store.save(id,before);
    }
  }
}
