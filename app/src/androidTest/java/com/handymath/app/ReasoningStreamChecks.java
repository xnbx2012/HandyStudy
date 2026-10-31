package com.handymath.app;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Pauses a real streamed submit before/after reasoning to inspect the live UI. */
final class ReasoningStreamChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static String event(JSONObject delta,String finish)throws Exception{
    JSONObject choice=new JSONObject().put("delta",delta);if(finish!=null)choice.put("finish_reason",finish);
    return "data: "+new JSONObject().put("choices",new JSONArray().put(choice))+"\n\n";
  }
  static final class Gate {
    final CountDownLatch waiting=new CountDownLatch(1),release=new CountDownLatch(1);
    void await()throws Exception{check(waiting.await(15,TimeUnit.SECONDS),"stream reached paused boundary");}
  }
  static InputStream paused(String head,String middle,String tail,Gate first,Gate second){
    return new InputStream(){
      final byte[][] chunks={head.getBytes(StandardCharsets.UTF_8),middle.getBytes(StandardCharsets.UTF_8),tail.getBytes(StandardCharsets.UTF_8)};
      int chunk,position;
      public int read()throws IOException{byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;}
      public int read(byte[] bytes,int offset,int length)throws IOException{
        while(chunk<chunks.length&&position==chunks[chunk].length){
          Gate gate=chunk==0?first:chunk==1?second:null;
          if(gate!=null){gate.waiting.countDown();try{if(!gate.release.await(30,TimeUnit.SECONDS))throw new IOException("fixture gate timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
          chunk++;position=0;
        }
        if(chunk==chunks.length)return -1;int count=Math.min(length,chunks[chunk].length-position);
        System.arraycopy(chunks[chunk],position,bytes,offset,count);position+=count;return count;
      }
    };
  }
  static HttpURLConnection connection(URL url,InputStream body){
    return new HttpURLConnection(url){
      public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
      public int getResponseCode(){return 200;}public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
      public InputStream getInputStream(){return body;}
    };
  }
  static ThinkingPanel.State state(MainActivity main)throws Exception{return (ThinkingPanel.State)StudyToolsChecks.get(main,"thinkingState");}
  static void click(ThinkingPanel panel,int step){
    View header=findDescription(panel,"思考阶段 "+step+"：");check(header!=null,"stage header exists");header.performClick();
  }
  static View findDescription(View view,String prefix){
    if(view.getContentDescription()!=null&&view.getContentDescription().toString().startsWith(prefix))return view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),prefix);if(found!=null)return found;}
    return null;
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();String id="reasoning-stream-"+System.nanoTime();
    SharedPreferences prefs=AppSettings.prefs(c),profiles=c.getSharedPreferences("provider-profiles",0),study=c.getSharedPreferences("study",0);
    Map<String,?> oldPrefs=new HashMap<>(prefs.getAll()),oldProfiles=new HashMap<>(profiles.getAll()),oldStudy=new HashMap<>(study.getAll());
    LlmTransport.ConnectionFactory original=LlmTransport.connections;BankRepository bank=null;MainActivity main=null;
    Gate recognition=new Gate(),grading=new Gate(),answerEnd=new Gate();
    try{
      // Verify provider aliases, duplicate fields and encrypted details stay separate from final output.
      List<String> reasoning=new ArrayList<>(),text=new ArrayList<>();
      String aliases=event(new JSONObject().put("reasoning_content","A").put("reasoning","duplicate"),null)
          +event(new JSONObject().put("reasoning","B"),null)
          +event(new JSONObject().put("reasoning_details",new JSONArray().put(new JSONObject().put("type","reasoning.text").put("text","C")).put(new JSONObject().put("type","reasoning.encrypted").put("data","secret"))),null)
          +event(new JSONObject().put("content","{}"),"stop")+"data: [DONE]\n\n";
      LlmTransport.connections=url->connection(url,new ByteArrayInputStream(aliases.getBytes(StandardCharsets.UTF_8)));
      ProviderSettings cfg=new ProviderSettings();cfg.key="fixture";cfg.reasoningEffort="none";
      String finalJson=LlmTransport.call(cfg,new JSONObject().put("stream",true),new LlmTransport.Partial(){
        public void text(String value){text.add(value);}public void reasoning(String value,boolean finished){reasoning.add(value+":"+finished);}
      });
      check(finalJson.equals("{}")&&text.toString().equals("[{}]")&&reasoning.get(reasoning.size()-1).equals("ABC:true"),"reasoning aliases combine without leaking into JSON or duplicating encrypted data");
      prefs.edit().putBoolean("stream",true).putBoolean("logs",false).putBoolean("resultAi",true).commit();
      cfg.id=id;cfg.model="MiniMax-M3.1-Flash";cfg.baseUrl="https://api.minimax.io/v1";cfg.reasoningEffort="default";cfg.visionVerified=true;cfg.save(c);
      bank=NestedZipChecks.read(c,NestedZipChecks.archive(new String[]{"bank.json"},new byte[][]{NestedZipChecks.bank(id)})).get(0);
      try(LearningDb db=new LearningDb(c)){db.tags(id,"q1",new JSONObject().put("knowledgePoints",new JSONArray().put("基础概念")));}
      String recognized=new JSONObject().put("transcription","B").put("choice","B").put("finalAnswer","B").put("readable",true).put("confidence",.99).toString();
      String graded=new JSONObject().put("score",2).put("correct",false).put("feedback","正式解析唯一标记").put("weaknesses",new JSONArray()).toString();
      AtomicInteger calls=new AtomicInteger();
      LlmTransport.connections=url->{try{
        if(calls.getAndIncrement()==0)return connection(url,paused(event(new JSONObject().put("reasoning_content","识别思考唯一标记：先定位学生写下的选项。"),null),
            event(new JSONObject().put("content",recognized),"stop")+"data: [DONE]\n\n","",recognition,null));
        return connection(url,paused(event(new JSONObject().put("reasoning_content","批改思考唯一标记：核对条件，并检查解题步骤。"),null),
            event(new JSONObject().put("content",graded.substring(0,graded.length()-1)),null),event(new JSONObject().put("content","}"),"stop")+"data: [DONE]\n\n",grading,answerEnd));
      }catch(Exception e){throw new IOException(e);}};
      main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()));MainActivity screen=main;
      test.waitForIdleSync();SystemClock.sleep(500);
      JSONObject previous=new JSONObject().put("score",1).put("correct",false).put("feedback","上次解析保留").put("studentWork","B");
      AnswerRetentionChecks.ui(test,()->{
        StudyToolsChecks.set(screen,"lastResult",previous);AnswerRetentionChecks.invoke(screen,"refreshFeedback");
        ((InkView)StudyToolsChecks.get(screen,"ink")).putText(null,"B",30,50,100);
        StudyToolsChecks.call(screen,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE);
      });
      recognition.await();test.waitForIdleSync();
      AnswerRetentionChecks.ui(test,()->{
        check(StudyToolsChecks.get(screen,"loadingDialog")==null,"streaming has no blocking loading dialog");
        check(state(screen).parts.get(0).text.contains("识别思考唯一标记")&&state(screen).parts.get(0).expanded,"recognition reasoning streams expanded before final JSON");
        check(StudyToolsChecks.get(screen,"lastResult")==previous,"old generated answer survives reasoning");
        screen.onConfigurationChanged(screen.getResources().getConfiguration());
        check(state(screen).parts.get(0).expanded,"layout rebuild preserves active thinking");
      });
      StudyToolsChecks.awaitText(test,(ThinkingPanel)StudyToolsChecks.get(screen,"thinkingPanel"),"识别思考唯一标记");
      AnswerRetentionChecks.ui(test,()->check(((ThinkingPanel)StudyToolsChecks.get(screen,"thinkingPanel")).getHeight()<AppUi.dp(c,200),"short reasoning uses its actual content height"));
      PhotoInputChecks.screenshot(test,c,"reasoning-stream-active.png");
      recognition.release.countDown();grading.await();test.waitForIdleSync();
      AnswerRetentionChecks.ui(test,()->{
        check(state(screen).parts.get(0).finished&&!state(screen).parts.get(0).expanded,"first stage automatically folds after reasoning");
        check(state(screen).parts.get(1).expanded,"next thinking stage expands independently");
        click((ThinkingPanel)StudyToolsChecks.get(screen,"thinkingPanel"),1);check(state(screen).parts.get(0).expanded,"completed stage can be reopened while busy");
      });
      grading.release.countDown();answerEnd.await();test.waitForIdleSync();
      AnswerRetentionChecks.ui(test,()->{
        check((Boolean)StudyToolsChecks.get(screen,"busy")&&!state(screen).parts.get(1).expanded,"thinking auto-folds when answer starts, before request finishes");
        click((ThinkingPanel)StudyToolsChecks.get(screen,"thinkingPanel"),2);check(state(screen).parts.get(1).expanded,"user may reopen reasoning during final output");
      });
      answerEnd.release.countDown();((ExecutorService)StudyToolsChecks.get(screen,"worker")).submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();
      ((ExecutorService)StudyToolsChecks.get(screen,"saver")).submit(()->{}).get(20,TimeUnit.SECONDS);
      JSONObject result=(JSONObject)StudyToolsChecks.get(screen,"lastResult");
      check(result.getJSONArray("thinking").length()==2&&!result.getString("feedback").contains("思考唯一标记"),"trace is kept separately from final answer");
      check(new StudyStore(c,id).load("q1").getJSONObject("result").getJSONArray("thinking").length()==2,"thinking persists in answer draft");
      AnswerRetentionChecks.ui(test,()->{
        check(state(screen).parts.get(1).expanded,"completion respects manually reopened completed stage");
        AnswerRetentionChecks.invoke(screen,"loadQuestion");check(state(screen).parts.size()==2&&!state(screen).parts.get(0).expanded&&!state(screen).parts.get(1).expanded,"reload restores collapsed trace");
        click((ThinkingPanel)StudyToolsChecks.get(screen,"thinkingPanel"),2);
      });
      StudyToolsChecks.awaitText(test,(ThinkingPanel)StudyToolsChecks.get(screen,"thinkingPanel"),"批改思考唯一标记");
      PhotoInputChecks.screenshot(test,c,"reasoning-stream-expanded.png");
      try(LearningDb db=new LearningDb(c)){check(db.attempt(result.getLong("attemptId")).getJSONArray("thinking").length()==2,"answer history preserves reasoning");}
      // An incomplete later stream cannot replace the valid answer or become a saved grade.
      JSONObject retained=(JSONObject)StudyToolsChecks.get(screen,"lastResult");
      String cut=event(new JSONObject().put("reasoning_content","中断思考唯一标记"),null)+event(new JSONObject(),"length")+"data: [DONE]\n\n";
      LlmTransport.connections=url->connection(url,new ByteArrayInputStream(cut.getBytes(StandardCharsets.UTF_8)));
      AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE));
      ((ExecutorService)StudyToolsChecks.get(screen,"worker")).submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();
      AnswerRetentionChecks.ui(test,()->check(StudyToolsChecks.get(screen,"lastResult")==retained&&state(screen).failed,"failed reasoning remains inspectable and preserves previous answer"));
    }finally{
      recognition.release.countDown();grading.release.countDown();answerEnd.release.countDown();LlmTransport.connections=original;
      if(main!=null){MainActivity screen=main;test.runOnMainSync(screen::finish);((ExecutorService)StudyToolsChecks.get(screen,"saver")).awaitTermination(10,TimeUnit.SECONDS);}
      if(bank!=null)BankRepository.deleteImported(c,bank.location());
      try(LearningDb db=new LearningDb(c)){
        JSONArray attempts=db.attempts(false);for(int i=0;i<attempts.length();i++){JSONObject a=attempts.getJSONObject(i);if(a.optString("bank").equals(id))new File(c.getFilesDir(),"learning-ink/"+a.getLong("id")+".json").delete();}
        for(String table:new String[]{"attempts","tags","time_log","stay_log","favorites"})db.getWritableDatabase().delete(table,"bank=?",new String[]{id});
      }
      File draft=new File(c.getFilesDir(),"study/"+id);File[] files=draft.listFiles();if(files!=null)for(File file:files)file.delete();draft.delete();
      ProviderSettings.storage(c,id).edit().clear().commit();StudyToolsChecks.restore(profiles,oldProfiles);StudyToolsChecks.restore(study,oldStudy);StudyToolsChecks.restore(prefs,oldPrefs);
      try(UsageLedger db=new UsageLedger(c)){db.getWritableDatabase().delete("usage","provider=?",new String[]{id});}
    }
  }
}
