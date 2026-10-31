package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Real sequential answer navigation and joint requests, including annotated question images. */
final class BatchPracticeChecks {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static EditText input(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText e=input(((ViewGroup)view).getChildAt(i));if(e!=null)return e;}return null;}
  static int attempts(Context c,String bank)throws Exception{try(LearningDb db=new LearningDb(c)){int count=0;JSONArray a=db.attempts(false);for(int i=0;i<a.length();i++)if(a.getJSONObject(i).optString("bank").equals(bank))count++;return count;}}
  static void settle(Instrumentation test,MainActivity main)throws Exception{
    ((ExecutorService)StudyToolsChecks.get(main,"worker")).submit(()->{}).get(25,TimeUnit.SECONDS);test.waitForIdleSync();
    ((ExecutorService)StudyToolsChecks.get(main,"saver")).submit(()->{}).get(25,TimeUnit.SECONDS);
  }
  static void pick(Instrumentation test,MainActivity main,String choice)throws Exception{
    AnswerRetentionChecks.ui(test,()->{for(char value:choice.toCharArray()){
      View button=BankDeleteChecks.byDescription(main.getWindow().getDecorView(),"快捷选项 "+value);check(button!=null,"quick choice exists");button.performClick();
    }});
  }
  static void remove(File file)throws Exception{if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);}file.delete();}
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();String id="batch-check-"+System.nanoTime();
    SharedPreferences prefs=AppSettings.prefs(c),profiles=c.getSharedPreferences("provider-profiles",0),study=c.getSharedPreferences("study",0),batchPrefs=c.getSharedPreferences("bank-batch",0);
    Map<String,?> oldPrefs=new HashMap<>(prefs.getAll()),oldProfiles=new HashMap<>(profiles.getAll()),oldStudy=new HashMap<>(study.getAll());
    LlmTransport.ConnectionFactory original=LlmTransport.connections;MainActivity main=null;BankRepository bank=null;
    List<JSONObject> requests=Collections.synchronizedList(new ArrayList<>());AtomicBoolean malformed=new AtomicBoolean();
    ReasoningStreamChecks.Gate background=new ReasoningStreamChecks.Gate();AtomicBoolean pauseOnce=new AtomicBoolean(true);
    try{
      JSONArray qs=new JSONArray();for(int n=1;n<=12;n++){
        JSONObject q=new JSONObject().put("id","q"+n).put("title","政治测试题 "+n).put("chapter",n<=6?"第一章":"第二章").put("topic","基础概念")
            .put("questionMarkdown","第"+n+"题题面：请选择正确选项。A.甲 B.乙 C.丙 D.丁").put("answerMarkdown","原版参考解析-"+n)
            .put("type",n==3?"multi-choice":"choice").put("expectedChoice",n==3?"AC":"A");if(n==1)q.put("questionImage","blue.png");qs.put(q);
      }
      JSONObject data=new JSONObject().put("version",2).put("id",id).put("title","政治合并提交测试").put("subject","政治").put("questions",qs);
      Bitmap picture=Bitmap.createBitmap(400,100,Bitmap.Config.ARGB_8888);picture.eraseColor(0xff3366ff);ByteArrayOutputStream png=new ByteArrayOutputStream();picture.compress(Bitmap.CompressFormat.PNG,100,png);picture.recycle();
      bank=NestedZipChecks.read(c,NestedZipChecks.archive(new String[]{"bank.json","blue.png"},new byte[][]{data.toString().getBytes(StandardCharsets.UTF_8),png.toByteArray()})).get(0);
      check(BatchPractice.size(c,id)==1&&BatchPractice.size(c,"unrelated-bank")==1,"every bank defaults to one question");
      prefs.edit().putBoolean("stream",false).putBoolean("logs",false).putBoolean("resultAi",true).putBoolean("resultReference",true).commit();
      ProviderSettings cfg=new ProviderSettings();cfg.id=id;cfg.name="批量测试提供商";cfg.key="fixture";cfg.visionVerified=true;cfg.save(c);
      LlmTransport.connections=url->new HttpURLConnection(url){
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
        public int getResponseCode(){return 200;}public OutputStream getOutputStream(){return sent;}
        public InputStream getInputStream()throws IOException{try{
          JSONObject payload=new JSONObject(sent.toString("UTF-8"));requests.add(payload);
          if(pauseOnce.getAndSet(false)){background.waiting.countDown();if(!background.release.await(20,TimeUnit.SECONDS))throw new IOException("fixture timeout");}
          JSONArray messages=payload.getJSONArray("messages");String system=messages.getJSONObject(0).getString("content");String response;
          if(system.contains("多题独立识别"))response=new JSONObject().put("answers",new JSONArray().put(new JSONObject().put("id","q1").put("transcription","B").put("choice","B").put("readable",true).put("confidence",.99))).toString();
          else{
            String text=messages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text");JSONObject source=new JSONObject(text.substring(text.indexOf("{\"questions\":")));JSONArray questions=source.getJSONArray("questions"),results=new JSONArray();
            for(int i=0;i<questions.length()-(malformed.get()?1:0);i++){String qid=questions.getJSONObject(i).getString("id");results.put(new JSONObject().put("id",qid).put("studentWork",questions.getJSONObject(i).optString("studentWork")).put("choice",questions.getJSONObject(i).optString("studentChoice")).put("score",2).put("correct",false).put("feedback","批量点评-"+qid).put("weaknesses",new JSONArray()));}
            response=new JSONObject().put("results",results).toString();
          }
          String wire=payload.optBoolean("stream")?ReasoningStreamChecks.event(new JSONObject().put("reasoning_content","本组思考过程"),null)+ReasoningStreamChecks.event(new JSONObject().put("content",response),"stop")+"data: [DONE]\n\n"
              :new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop").put("message",new JSONObject().put("content",response)))).toString();
          return new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){throw new IOException(e);}}
      };
      main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()));MainActivity screen=main;
      test.waitForIdleSync();
      AnswerRetentionChecks.ui(test,()->{
        check(BankDeleteChecks.byDescription(screen.getWindow().getDecorView(),"快捷选项 A")==null,"single mode unchanged by default");
        AlertDialog edit=BatchPractice.edit(screen,id,"测试题库",()->{});EditText count=input(edit.getWindow().getDecorView());count.setText("21");edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();check(edit.isShowing(),"out-of-range size rejected");
        count.setText("10");edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        check(BatchPractice.size(c,id)==10&&BatchPractice.size(c,"unrelated-bank")==1,"group size stored independently per bank");screen.onResume();
      });
      check(((Integer)StudyToolsChecks.get(screen,"renderedBatchSize"))==10,"return from settings updates the current study page");
      check(BatchPractice.group(bank,new StudyFilter(),0,10).size()==10&&BatchPractice.group(bank,new StudyFilter(),11,10).size()==2,"last partial group uses real question count");
      StudyFilter multi=new StudyFilter();multi.type="multi-choice";check(BatchPractice.group(bank,multi,2,10).equals(Collections.singletonList(2)),"group respects current type filter");
      for(int n=1;n<=10;n++){
        pick(test,screen,n==2?"B":n==3?"AC":"A");
        if(n<10)AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"navigateQuestion",int.class,1));
      }
      check(requests.isEmpty(),"answering ten questions makes no network requests");
      AnswerRetentionChecks.ui(test,()->check(!(BankDeleteChecks.byDescription(screen.getWindow().getDecorView(),"下一题")==null)&&((View)StudyToolsChecks.get(screen,"nextQuestionButton")).isEnabled()==false,"last question stops inside current group"));
      AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE));
      background.await();AnswerRetentionChecks.ui(test,()->{screen.onPause();screen.onResume();});background.release.countDown();settle(test,screen);
      check(requests.size()==1,"ten explicit choices share exactly one grading call");check(attempts(c,id)==10,"ten results saved independently");
      StudyStore store=new StudyStore(c,id);
      for(int n=1;n<=10;n++){
        JSONObject state=store.load("q"+n),result=state.getJSONObject("result");
        check(result.getString("feedback").contains("批量点评-q"+n)&&result.getInt("batchCount")==10,"result returned to correct question id");
        check(result.getDouble("score")== (n==2?0:10),"local choice verdict respects exact single/multi expected answers");
        check(state.optString("batchChoice").equals(n==2?"B":n==3?"AC":"A"),"selected choice retained per question");
      }
      check(!store.load("q11").has("result"),"other group remains ungraded");
      StudyToolsChecks.awaitText(test,screen.getWindow().getDecorView(),"批量点评-q10");
      PhotoInputChecks.screenshot(test,c,"batch-practice-results.png");
      AnswerRetentionChecks.ui(test,()->AnswerRetentionChecks.invoke(screen,"advanceBatch"));
      check(((Integer)StudyToolsChecks.get(screen,"index"))==10,"next group starts at following filtered question");
      prefs.edit().putBoolean("stream",true).commit();
      pick(test,screen,"A");AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"navigateQuestion",int.class,1));pick(test,screen,"A");
      AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE));settle(test,screen);
      check(requests.size()==2&&attempts(c,id)==12,"short final group submits its two questions once");
      check(store.load("q11").getJSONObject("result").getJSONArray("thinking").length()==1,"combined grading preserves streamed thinking");
      // Resume a different group size and retain handwriting plus original-position annotations.
      batchPrefs.edit().putInt(id,2).commit();study.edit().putInt("index-"+id,0).commit();final String location=bank.location();
      AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"openBank",String.class,location));
      StudyToolsChecks.awaitText(test,screen.getWindow().getDecorView(),"第1题题面");
      AnswerRetentionChecks.ui(test,()->{
        StudyToolsChecks.set(screen,"batchChoice","");InkView answer=(InkView)StudyToolsChecks.get(screen,"ink");answer.putText(null,"B",30,50,100);
        AnnotatedPaper paper=(AnnotatedPaper)StudyToolsChecks.get(screen,"paper");paper.ink.penColor=Color.RED;StylusChecks.pen(paper.ink,0,40,.5f);StylusChecks.pen(paper.ink,2,100,.5f);StylusChecks.pen(paper.ink,1,150,.5f);
        StudyToolsChecks.call(screen,"navigateQuestion",int.class,1);
      });
      pick(test,screen,"A");int before=requests.size();
      AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE));settle(test,screen);
      check(requests.size()==before+2,"mixed handwriting is recognized once and graded once for whole group");
      check(store.load("q1").getJSONObject("result").getJSONArray("thinking").length()==2,"joint recognition and grading keep independent thinking stages");
      StudyToolsChecks.containsComposed(StudyToolsChecks.images(requests.get(before)));StudyToolsChecks.containsComposed(StudyToolsChecks.images(requests.get(before+1)));
      check(store.load("q1").getJSONObject("result").getDouble("score")==0&&store.load("q2").getJSONObject("result").getDouble("score")==10,"handwritten/typed answers stay assigned to their own questions");
      JSONObject retained=store.load("q2").getJSONObject("result");int historyCount=attempts(c,id);malformed.set(true);
      AnswerRetentionChecks.ui(test,()->StudyToolsChecks.call(screen,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE));settle(test,screen);
      check(StudyToolsChecks.get(screen,"lastError").toString().contains("批量题数不完整")&&attempts(c,id)==historyCount,"incomplete batch response produces no partial history");
      check(store.load("q2").getJSONObject("result").toString().equals(retained.toString()),"invalid response keeps previous generated analysis");
      batchPrefs.edit().putInt(id,1).commit();
      AnswerRetentionChecks.ui(test,()->{screen.onResume();check(((InkView)StudyToolsChecks.get(screen,"ink")).hasInk()&&StudyToolsChecks.get(screen,"batchChoice").equals(""),"returning to single mode keeps quick answer visibly on answer paper");});
    }finally{
      background.release.countDown();LlmTransport.connections=original;if(main!=null){MainActivity screen=main;test.runOnMainSync(screen::finish);((ExecutorService)StudyToolsChecks.get(screen,"saver")).awaitTermination(10,TimeUnit.SECONDS);}
      if(bank!=null)BankRepository.deleteImported(c,bank.location());
      try(LearningDb db=new LearningDb(c)){
        JSONArray all=db.attempts(false);for(int i=0;i<all.length();i++){JSONObject a=all.getJSONObject(i);if(a.optString("bank").equals(id))new File(c.getFilesDir(),"learning-ink/"+a.getLong("id")+".json").delete();}
        for(String table:new String[]{"attempts","tags","time_log","stay_log","favorites"})db.getWritableDatabase().delete(table,"bank=?",new String[]{id});
      }
      remove(new File(c.getFilesDir(),"study/"+id));remove(new File(c.getFilesDir(),"batch-snapshots/"+id));
      c.getSharedPreferences("progress-"+id,0).edit().clear().commit();batchPrefs.edit().remove(id).commit();ProviderSettings.storage(c,id).edit().clear().commit();
      StudyToolsChecks.restore(profiles,oldProfiles);StudyToolsChecks.restore(study,oldStudy);StudyToolsChecks.restore(prefs,oldPrefs);
      try(UsageLedger ledger=new UsageLedger(c)){ledger.getWritableDatabase().delete("usage","provider=?",new String[]{id});}
    }
  }
}
