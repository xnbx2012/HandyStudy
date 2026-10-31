package com.handymath.app;

import android.app.*;
import android.content.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

final class AnswerRetentionChecks {
  static Object field(Object owner,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
  static void invoke(Object owner,String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);}
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static void retained(MainActivity main,String expected)throws Exception{
    check(field(main,"lastResult")!=null&&expected.equals(field(main,"lastResult").toString()),"annotations must preserve the exact generated answer");
    check(((View)field(main,"feedbackBox")).getVisibility()==View.VISIBLE,"answer explanation must remain visible");
    check((Boolean)field(main,"solved"),"annotations must preserve solved state");
  }
  static void ui(Instrumentation test,Checked action)throws Exception{
    AtomicReference<Throwable> error=new AtomicReference<>();test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});
    if(error.get()!=null)throw new AssertionError(error.get());
  }
  interface Checked{void run()throws Exception;}
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();BankRepository bank=new BankRepository(c,null);String id=bank.questions.get(0).id;
    StudyStore store=new StudyStore(c,bank.id);JSONObject before=store.load(id);
    JSONObject answer=new JSONObject().put("score",10).put("correct",true).put("studentWork","x = 2")
        .put("feedback","保持原样的答案解析：两边同除以 2。\n$$x=2$$").put("weaknesses",new JSONArray());
    String expected=answer.toString();
    Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()).putExtra("questionId",id);
    MainActivity main=null;
    try{
      store.save(id,new JSONObject().put("result",answer).put("solved",true).put("answerStopped",true));
      main=(MainActivity)test.startActivitySync(intent);test.waitForIdleSync();final MainActivity first=main;
      ui(test,()->{
        retained(first,expected);
        InkView ink=(InkView)field(first,"ink");
        StylusChecks.pen(ink,MotionEvent.ACTION_DOWN,700,.4f);StylusChecks.pen(ink,MotionEvent.ACTION_MOVE,730,.5f);StylusChecks.pen(ink,MotionEvent.ACTION_UP,750,.5f);
        retained(first,expected);
        ink.undo();retained(first,expected);ink.redo();retained(first,expected);
        AnnotatedPaper paper=(AnnotatedPaper)field(first,"paper");
        StylusChecks.pen(paper.ink,MotionEvent.ACTION_DOWN,700,.4f);StylusChecks.pen(paper.ink,MotionEvent.ACTION_UP,750,.5f);retained(first,expected);
        ink.putText(null,"结合解析补充的提醒",30,300,300);retained(first,expected);
        invoke(first,"save");
      });
      ((ExecutorService)field(first,"saver")).submit(()->{}).get(10,TimeUnit.SECONDS);
      check(store.load(id).getJSONObject("result").toString().equals(expected),"saved draft preserves explanation");
      ui(test,first::finish);test.waitForIdleSync();main=null;
      main=(MainActivity)test.startActivitySync(intent);test.waitForIdleSync();final MainActivity reopened=main;
      ui(test,()->{retained(reopened,expected);invoke(reopened,"clearCurrentArea");check(field(reopened,"lastResult")==null,"explicit clear removes answer");invoke(reopened,"save");});
      ((ExecutorService)field(reopened,"saver")).submit(()->{}).get(10,TimeUnit.SECONDS);
      check(!store.load(id).has("result"),"cleared answer is removed from saved draft");
    }finally{
      if(main!=null){final MainActivity last=main;ExecutorService saver=(ExecutorService)field(last,"saver");ui(test,last::finish);saver.awaitTermination(10,TimeUnit.SECONDS);}
      store.save(id,before);
    }
  }
}
