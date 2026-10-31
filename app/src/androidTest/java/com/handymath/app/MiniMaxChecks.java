package com.handymath.app;

import android.app.Instrumentation;
import android.app.*;
import android.content.*;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Replays a reasoning model exhausting a small shared reasoning/output budget. */
final class MiniMaxChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static final class Response extends HttpURLConnection {
    final ByteArrayOutputStream sent=new ByteArrayOutputStream();
    final List<JSONObject> requests;
    Response(URL url,List<JSONObject> requests){super(url);this.requests=requests;}
    public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
    public int getResponseCode(){return 200;}public OutputStream getOutputStream(){return sent;}
    public InputStream getInputStream()throws IOException{
      try{
        JSONObject request=new JSONObject(sent.toString("UTF-8"));requests.add(request);
        // A deterministic fixture: 9,000 reasoning tokens plus a 1,200-token answer.
        boolean enough=request.optInt("max_completion_tokens",request.optInt("max_tokens"))>=10200;
        String finalAnswer=new JSONObject().put("score",2).put("correct",false).put("feedback","完整解析已生成")
            .put("studentWork","B").put("weaknesses",new JSONArray().put("概念混淆"))
            .put("transcription","B").put("choice","B").put("finalAnswer","B").put("readable",true).put("confidence",.99)
            .put("knowledgePoints",new JSONArray().put("基础概念")).put("examPoints",new JSONArray().put("概念辨析"))
            .put("questionType","选择题").put("difficulty",2).toString();
        JSONObject choice=new JSONObject().put("finish_reason",enough?"stop":"length");
        String body;
        if(request.optBoolean("stream")){
          body="data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("reasoning_content","思考中"))))+"\n\n"
              +"data: "+new JSONObject().put("choices",new JSONArray().put(choice.put("delta",new JSONObject().put("content",enough?finalAnswer:""))))+"\n\ndata: [DONE]\n\n";
        }else{
          body=new JSONObject().put("choices",new JSONArray().put(choice.put("message",new JSONObject().put("reasoning_content","思考中").put("content",enough?finalAnswer:"")))).toString();
        }
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
      }catch(JSONException e){throw new IOException(e);}
    }
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();SharedPreferences prefs=AppSettings.prefs(c);
    Map<String,?> saved=new HashMap<>(prefs.getAll());LlmTransport.ConnectionFactory original=LlmTransport.connections;
    SharedPreferences profiles=c.getSharedPreferences("provider-profiles",0),study=c.getSharedPreferences("study",0);
    Map<String,?> savedProfiles=new HashMap<>(profiles.getAll()),savedStudy=new HashMap<>(study.getAll());
    String profile="minimax-budget-"+System.nanoTime(),bankId=profile;BankRepository bank=null;MainActivity main=null;SettingsActivity settings=null;
    try{
      List<JSONObject> requests=new ArrayList<>();LlmTransport.connections=url->new Response(url,requests);
      ProviderSettings config=new ProviderSettings();config.id=profile;config.key="fixture";
      config.baseUrl="https://api.minimax.io/v1";config.model="MiniMax-M3.1-Flash";config.reasoningEffort="default";config.context=c;
      prefs.edit().putBoolean("logs",false).putBoolean("stream",true).commit();
      TutorClient.Reply reply=TutorClient.formattedReply(config,"仅返回批改JSON",new JSONArray().put(TutorClient.text("请检查我的答案B")),TutorClient.Mode.GRADE);
      check(reply.feedback.equals("完整解析已生成"),"MiniMax default mode must complete its final answer after reasoning");
      prefs.edit().putBoolean("stream",false).commit();
      reply=TutorClient.formattedReply(config,"仅返回批改JSON",new JSONArray().put(TutorClient.text("请检查我的答案B")),TutorClient.Mode.GRADE);
      check(reply.feedback.equals("完整解析已生成"),"non-streaming MiniMax default mode completes too");
      check(requests.get(0).has("max_completion_tokens")&&!requests.get(0).has("max_tokens"),"official MiniMax uses modern length field");
      check(ModelThinking.needsBudget(config),"model default reserves reasoning budget and timeout");
      check(!Arrays.asList(ModelThinking.choices(config)).contains("none")&&!Arrays.asList(ModelThinking.choices(config)).contains("minimal"),"model cannot advertise unsupported thinking modes");
      for(String model:new String[]{"MiniMaxAI/MiniMax-M3.1-Flash","minimax/m3.1-flash"}){
        config.model=model;config.baseUrl="https://proxy.example.test/v1";
        reply=TutorClient.formattedReply(config,"仅返回批改JSON",new JSONArray().put(TutorClient.text("B")),TutorClient.Mode.GRADE);
        check(reply.feedback.equals("完整解析已生成"),"namespaced model on custom endpoint has a reasoning budget");
      }
      config.model="MiniMax-M3.1-Flash";config.baseUrl="https://api.minimax.io/v1";
      for(String effort:new String[]{"none","minimal"}){
        config.reasoningEffort=effort;int count=requests.size();boolean rejected=false;
        try{TutorClient.request(config,new JSONArray(),true);}catch(IllegalArgumentException unsupported){rejected=unsupported.getMessage().contains("不支持关闭思考");}
        check(rejected&&count==requests.size(),"unsupported thinking mode rejected before network request");
      }
      config.reasoningEffort="default";config.maxOutputTokens=4096;
      for(boolean stream:new boolean[]{false,true}){
        prefs.edit().putBoolean("stream",stream).commit();boolean rejected=false;
        try{TutorClient.formattedReply(config,"仅返回批改JSON",new JSONArray().put(TutorClient.text("B")),TutorClient.Mode.GRADE);}
        catch(IOException truncated){rejected=truncated.getMessage().contains("4096")&&truncated.getMessage().contains("模型输出长度上限");}
        check(rejected,"actual truncated answer stays rejected with an actionable message");
      }
      config.maxOutputTokens=131072;config.visionVerified=true;config.save(c);
      ProviderSettings loaded=ProviderSettings.load(c,profile);
      check(loaded.maxOutputTokens==131072&&loaded.reasoningEffort.equals("default")&&loaded.key.equals("fixture"),"custom output cap persists with credentials and thinking");
      settings=(SettingsActivity)test.startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SettingsActivity screen=settings;
      AnswerRetentionChecks.ui(test,()->{
        check(InputSettingsChecks.find(screen.getWindow().getDecorView(),"模型输出长度上限  ›")!=null,"settings exposes output limit");
        AlertDialog output=ModelThinking.editOutput(screen);int selected=Arrays.binarySearch(ModelThinking.OUTPUT_LIMITS,65536);
        output.getListView().performItemClick(null,selected,selected);
        check(ProviderSettings.load(c,profile).maxOutputTokens==65536,"output setting changes active provider");
      });
      // Submit through MainActivity and verify the generated analysis reaches durable storage.
      bank=NestedZipChecks.read(c,NestedZipChecks.archive(new String[]{"bank.json"},new byte[][]{NestedZipChecks.bank(bankId)})).get(0);
      main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()));MainActivity activity=main;
      test.waitForIdleSync();SystemClock.sleep(500);
      AnswerRetentionChecks.ui(test,()->{
        ((InkView)StudyToolsChecks.get(activity,"ink")).putText(null,"B",30,50,100);
        StudyToolsChecks.call(activity,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE);
      });
      ((ExecutorService)StudyToolsChecks.get(activity,"worker")).submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();
      ((ExecutorService)StudyToolsChecks.get(activity,"saver")).submit(()->{}).get(20,TimeUnit.SECONDS);
      JSONObject result=(JSONObject)StudyToolsChecks.get(activity,"lastResult");
      check(result!=null&&result.optString("feedback").contains("完整解析已生成"),"real submit keeps final analysis on screen");
      check(new StudyStore(c,bankId).load("q1").getJSONObject("result").getString("feedback").contains("完整解析已生成"),"generated analysis saved in answer draft");
      try(LearningDb db=new LearningDb(c)){
        JSONArray attempts=db.attempts(false);boolean found=false;
        for(int i=0;i<attempts.length();i++)if(attempts.getJSONObject(i).optString("bank").equals(bankId))found=true;
        check(found,"completed MiniMax analysis saved to answer history");
      }
    }finally{LlmTransport.connections=original;StudyToolsChecks.restore(prefs,saved);
      if(main!=null){MainActivity activity=main;test.runOnMainSync(activity::finish);((ExecutorService)StudyToolsChecks.get(activity,"saver")).awaitTermination(10,TimeUnit.SECONDS);}
      if(settings!=null){SettingsActivity screen=settings;test.runOnMainSync(screen::finish);}
      if(bank!=null)BankRepository.deleteImported(c,bank.location());
      try(LearningDb db=new LearningDb(c)){
        JSONArray attempts=db.attempts(false);for(int i=0;i<attempts.length();i++){JSONObject a=attempts.getJSONObject(i);if(a.optString("bank").equals(bankId))new File(c.getFilesDir(),"learning-ink/"+a.getLong("id")+".json").delete();}
        for(String table:new String[]{"attempts","tags","time_log","stay_log","favorites"})db.getWritableDatabase().delete(table,"bank=?",new String[]{bankId});
      }
      File draft=new File(c.getFilesDir(),"study/"+bankId);File[] files=draft.listFiles();if(files!=null)for(File file:files)file.delete();draft.delete();
      ProviderSettings.storage(c,profile).edit().clear().commit();StudyToolsChecks.restore(profiles,savedProfiles);StudyToolsChecks.restore(study,savedStudy);
      try(UsageLedger db=new UsageLedger(c)){db.getWritableDatabase().delete("usage","provider=?",new String[]{profile});}}
  }
}
