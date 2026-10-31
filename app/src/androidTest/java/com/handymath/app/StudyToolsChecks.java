package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

final class StudyToolsChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
  static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
  static void call(Object owner,String name,Class<?> type,Object value)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(owner,value);}
  static String markdown(View view)throws Exception{
    StringBuilder text=new StringBuilder();
    if(view instanceof MathView)text.append(get(view,"markdown"));
    if(view instanceof TextView)text.append(((TextView)view).getText());
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)text.append(markdown(((ViewGroup)view).getChildAt(i))).append('\n');
    return text.toString();
  }
  static void restore(SharedPreferences p,Map<String,?> saved){
    SharedPreferences.Editor e=p.edit().clear();
    for(Map.Entry<String,?> item:saved.entrySet()){
      String k=item.getKey();Object v=item.getValue();
      if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
      else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);
    }e.commit();
  }
  static final class Fake extends HttpURLConnection {
    final ByteArrayOutputStream sent=new ByteArrayOutputStream();final String answer;final List<JSONObject> payloads;
    Fake(URL url,String answer,List<JSONObject> payloads){super(url);this.answer=answer;this.payloads=payloads;}
    public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}
    public OutputStream getOutputStream(){return sent;}
    public InputStream getInputStream(){try{
      payloads.add(new JSONObject(sent.toString("UTF-8")));
      String body=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",answer)).put("finish_reason","stop"))).toString();
      return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }catch(Exception e){throw new RuntimeException(e);}}
  }
  static JSONArray images(JSONObject request)throws Exception{
    JSONArray found=new JSONArray(),messages=request.getJSONArray("messages");
    for(int i=0;i<messages.length();i++){
      JSONArray content=messages.getJSONObject(i).optJSONArray("content");if(content==null)continue;
      for(int n=0;n<content.length();n++){JSONObject item=content.getJSONObject(n);if(item.optString("type").equals("image_url"))found.put(item.getJSONObject("image_url").getString("url"));}
    }return found;
  }
  static void containsComposed(JSONArray images)throws Exception{
    boolean combined=false;
    for(int i=0;i<images.length();i++){
      String uri=images.getString(i);byte[] png=android.util.Base64.decode(uri.substring(uri.indexOf(',')+1),android.util.Base64.DEFAULT);
      Bitmap b=BitmapFactory.decodeByteArray(png,0,png.length);int red=0,blue=0;
      for(int y=0;y<b.getHeight();y+=2)for(int x=0;x<b.getWidth();x+=2){int c=b.getPixel(x,y);if(Color.red(c)>Color.blue(c)+70)red++;if(Color.blue(c)>Color.red(c)+70)blue++;}
      b.recycle();if(red>5&&blue>40)combined=true;
    }
    check(combined,"one uploaded image contains both printed question background and original red annotations");
  }
  static MathView findMath(View view,String marker)throws Exception {
    if(view instanceof MathView&&((String)get(view,"markdown")).contains(marker))return (MathView)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){MathView found=findMath(((ViewGroup)view).getChildAt(i),marker);if(found!=null)return found;}
    return null;
  }
  static void awaitText(Instrumentation test,View root,String marker)throws Exception {
    AtomicReference<MathView> target=new AtomicReference<>();
    AnswerRetentionChecks.ui(test,()->target.set(findMath(root,marker)));check(target.get()!=null,"rendered section exists: "+marker);
    for(int n=0;n<25;n++){
      CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> text=new AtomicReference<>("");
      test.runOnMainSync(()->target.get().evaluateJavascript("document.getElementById('content') ? document.getElementById('content').innerText : ''",value->{text.set(value);latch.countDown();}));
      latch.await(2,TimeUnit.SECONDS);if(text.get().contains(marker))return;SystemClock.sleep(200);
    }
    throw new AssertionError("actual WebView text did not render: "+marker);
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();SharedPreferences prefs=AppSettings.prefs(c),profiles=c.getSharedPreferences("provider-profiles",0);
    Map<String,?> settings=new HashMap<>(prefs.getAll()),oldProfiles=new HashMap<>(profiles.getAll());
    String providerId="study-tools-fixture";SharedPreferences storage=ProviderSettings.storage(c,providerId);Map<String,?> oldStorage=new HashMap<>(storage.getAll());
    LlmTransport.ConnectionFactory previous=LlmTransport.connections;MainActivity main=null;BankBrowseActivity browser=null;
    try{
      prefs.edit().putBoolean("timer",true).putBoolean("timerAllowPause",true).putBoolean("timerAllowReset",true).putBoolean("stream",false).putBoolean("penCursor",false).commit();
      for(String k:new String[]{"resultAi","resultStudent","resultScore","resultWeaknesses","resultReference","resultKnowledge"})prefs.edit().putBoolean(k,true).commit();
      String id="study-tools-"+SystemClock.uptimeMillis();JSONArray questions=new JSONArray();
      for(int n=1;n<=3;n++)questions.put(new JSONObject().put("id","q"+n).put("title","功能核对题 "+n).put("chapter","第一章 功能核对").put("topic","定位批注")
        .put("type",n==2?"multi-choice":"choice").put("questionMarkdown","请在蓝色题面中标出关键词并判断选项。A.甲 B.乙 C.丙 D.丁")
        .put("questionImages",new JSONArray().put("question.png")).put("answerMarkdown","原版解析唯一标记：正确答案与题面定位。").put("expectedChoice",n==2?"ACD":"A")
        .put("knowledgeIds",new JSONArray().put("topic")).put("source",new JSONObject().put("customSourceField","raw-field-kept")));
      JSONObject data=new JSONObject().put("version",2).put("id",id).put("title","学习工具核对题库").put("subject","政治").put("extraBankField","bank-field-kept")
        .put("knowledgeSystem",new JSONObject().put("nodes",new JSONArray().put(new JSONObject().put("id","topic").put("name","原书定位知识点").put("parentId","")))).put("questions",questions);
      Bitmap picture=Bitmap.createBitmap(600,300,Bitmap.Config.ARGB_8888);picture.eraseColor(0xff3366ff);ByteArrayOutputStream png=new ByteArrayOutputStream();picture.compress(Bitmap.CompressFormat.PNG,100,png);picture.recycle();
      File fixture=new File(c.getCacheDir(),"study-tools.zip");try(OutputStream out=new FileOutputStream(fixture)){out.write(NestedZipChecks.archive(new String[]{"bank.json","question.png"},new byte[][]{data.toString().getBytes(StandardCharsets.UTF_8),png.toByteArray()}));}
      BankRepository bank=BankRepository.importZip(c,Uri.fromFile(fixture));
      check(BankRepository.describe(bank.data).contains("图文题库")&&BankRepository.describe(bank.data).contains("3/3")&&BankRepository.describe(bank.data).contains("含知识树"),"catalog summary reflects actual stored information");
      ProviderSettings cfg=new ProviderSettings();cfg.id=providerId;cfg.key="fixture";cfg.visionVerified=true;cfg.save(c);
      try(LearningDb db=new LearningDb(c)){db.tags(bank.id,"q1",new JSONObject().put("knowledgePoints",new JSONArray().put("原书定位知识点")));}
      main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()).putExtra("questionId","q1"));
      test.waitForIdleSync();SystemClock.sleep(1200);final MainActivity activity=main;
      AtomicInteger requests=new AtomicInteger();List<JSONObject> payloads=Collections.synchronizedList(new ArrayList<>());
      String recognition=new JSONObject().put("transcription","我选择 B，并在题面关键词下划红线").put("choice","B").put("finalAnswer","B").put("readable",true).put("confidence",.99).toString();
      String grade=new JSONObject().put("score",2).put("correct",false).put("feedback","AI解析唯一标记：根据题面红线定位评估。").put("weaknesses",new JSONArray().put("薄弱点唯一标记")).toString();
      LlmTransport.connections=url->new Fake(url,requests.getAndIncrement()%2==0?recognition:grade,payloads);
      AnswerRetentionChecks.ui(test,()->{
        InkView answer=(InkView)get(activity,"ink");answer.putText(null,"B",30,50,100);
        AnnotatedPaper paper=(AnnotatedPaper)get(activity,"paper");paper.ink.penColor=Color.RED;
        StylusChecks.pen(paper.ink,0,40,.5f);StylusChecks.pen(paper.ink,2,90,.5f);StylusChecks.pen(paper.ink,1,150,.5f);
        call(activity,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE);
      });
      ((ExecutorService)get(activity,"worker")).submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();
      check(payloads.size()==2,"actual submit performs recognition and grading without extra annotation-only image");
      check(images(payloads.get(0)).length()==2,"recognition gets answer sheet plus composed question");containsComposed(images(payloads.get(0)));containsComposed(images(payloads.get(1)));
      check(payloads.get(0).toString().contains("印刷内容是题目上下文"),"recognizer distinguishes question print from handwriting");
      AnswerRetentionChecks.ui(test,()->{
        ((InkView)get(activity,"ink")).clear();
        call(activity,"request",TutorClient.Mode.class,TutorClient.Mode.GRADE);
      });
      ((ExecutorService)get(activity,"worker")).submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();
      check(payloads.size()==4&&images(payloads.get(2)).length()==1,"question-only handwriting submits its composed context without requiring answer-sheet ink");
      containsComposed(images(payloads.get(2)));
      AnswerRetentionChecks.ui(test,()->{
        String text=markdown(((AnnotatedPaper)get(activity,"resultPaper")).body);
        check(text.contains("原版解析唯一标记")&&text.contains("AI解析唯一标记")&&text.contains("识别到你的答案")&&text.contains("最近批改")&&text.contains("薄弱点唯一标记")&&text.contains("所属知识点"),"all six result sections include original reference even for wrong answer");
        AnnotatedPaper output=(AnnotatedPaper)get(activity,"resultPaper");output.ink.putText(null,"批注",10,10,100);
        prefs.edit().putBoolean("resultStudent",false).commit();AnswerRetentionChecks.invoke(activity,"refreshFeedback");
        check(!markdown(output.body).contains("识别到你的答案")&&!output.ink.hasInk(),"hiding section does not misalign old annotation marks");
        prefs.edit().putBoolean("resultStudent",true).commit();AnswerRetentionChecks.invoke(activity,"refreshFeedback");check(output.ink.hasInk(),"restoring result layout restores its handwriting");
        String[][] controls={{"resultAi","AI解析唯一标记"},{"resultStudent","识别到你的答案"},{"resultScore","最近批改"},{"resultWeaknesses","薄弱点唯一标记"},{"resultReference","原版解析唯一标记"},{"resultKnowledge","所属知识点"}};
        for(String[] control:controls){
          prefs.edit().putBoolean(control[0],false).commit();AnswerRetentionChecks.invoke(activity,"refreshFeedback");
          String visible=markdown(output.body);check(!visible.contains(control[1]),"individual display option hides its own section: "+control[0]);
          for(String[] other:controls)if(other!=control)check(visible.contains(other[1]),"other result sections stay visible");
          prefs.edit().putBoolean(control[0],true).commit();AnswerRetentionChecks.invoke(activity,"refreshFeedback");
        }
        for(String k:new String[]{"resultAi","resultStudent","resultScore","resultWeaknesses","resultReference","resultKnowledge"})prefs.edit().putBoolean(k,false).commit();
        AnswerRetentionChecks.invoke(activity,"refreshFeedback");check(output.body.getChildCount()==0&&get(activity,"lastResult")!=null,"display settings hide sections without deleting grading result");
        for(String k:new String[]{"resultAi","resultStudent","resultScore","resultWeaknesses","resultReference","resultKnowledge"})prefs.edit().putBoolean(k,true).commit();
        call(activity,"navigateQuestion",int.class,1);check((Integer)get(activity,"index")==1,"next question works without grading next item");
        call(activity,"navigateQuestion",int.class,1);check((Integer)get(activity,"index")==2&&get(activity,"lastResult")==null,"next navigates between entirely ungraded questions");
        check(!((View)get(activity,"nextQuestionButton")).isEnabled(),"next disabled at end of range");
        call(activity,"navigateQuestion",int.class,-1);call(activity,"navigateQuestion",int.class,-1);
        check((Integer)get(activity,"index")==0&&get(activity,"lastResult")!=null,"previous question restores grading draft");
        check(!((View)get(activity,"previousQuestionButton")).isEnabled(),"previous disabled at start of range");
        TextView timer=(TextView)get(activity,"timerText");
        timer.performClick();check(!(Boolean)get(activity,"answerStopped"),"timer click resumes after grade");
        timer.performClick();check((Boolean)get(activity,"answerStopped")&&(Long)get(activity,"clockStart")<0,"timer click pauses");
        prefs.edit().putBoolean("timerAllowPause",false).commit();timer.performClick();check((Boolean)get(activity,"answerStopped"),"pause permission respected");
        set(activity,"questionMs",65000L);timer.performLongClick();check(timer.getText().toString().contains("0秒"),"timer long press zeroes displayed time");
        check((Long)get(activity,"questionMs")==65000L,"timer reset preserves recorded total");
        prefs.edit().putBoolean("timerAllowReset",false).commit();set(activity,"questionMs",125000L);timer.performLongClick();AnswerRetentionChecks.invoke(activity,"updateStatus");check(timer.getText().toString().contains("1分"),"reset permission respected");
        check(TutorClient.recognizedChoice(new JSONObject().put("choice","D,A,C")).equals("ACD"),"multi choice recognition accepts explicit option combination");
      });
      AnswerRetentionChecks.ui(test,()->((ScrollView)get(activity,"studyScroll")).scrollTo(0,((View)get(activity,"feedbackBox")).getTop()));
      awaitText(test,activity.getWindow().getDecorView(),"原版解析唯一标记");
      PhotoInputChecks.screenshot(test,c,"study-tools-results.png");
      browser=(BankBrowseActivity)test.startActivitySync(new Intent(c,BankBrowseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation",bank.location()));
      final BankBrowseActivity view=browser;test.waitForIdleSync();
      AnswerRetentionChecks.ui(test,()->{
        check(((List<?>)get(view,"matches")).size()==3,"browser lists every question");
        ((EditText)get(view,"query")).setText("原书定位知识点");check(((List<?>)get(view,"matches")).size()==3,"knowledge tree paths searchable");
        call(view,"showQuestion",int.class,0);String body=markdown(view.getWindow().getDecorView());
        check(body.contains("原版解析唯一标记")&&body.contains("原书定位知识点"),"read-only details show reference and knowledge");
        check(((BankRepository)get(view,"bank")).data.getJSONArray("questions").getJSONObject(0).getJSONObject("source").has("customSourceField"),"original question extension fields retained");
        view.onBackPressed();check(((EditText)get(view,"query")).getText().toString().equals("原书定位知识点"),"returning from details preserves browse filter");
        call(view,"showQuestion",int.class,0);
      });
      awaitText(test,view.getWindow().getDecorView(),"原版解析唯一标记");
      PhotoInputChecks.screenshot(test,c,"study-tools-browser.png");
    }finally{
      if(browser!=null){BankBrowseActivity b=browser;test.runOnMainSync(b::finish);test.waitForIdleSync();}
      if(main!=null){MainActivity m=main;test.runOnMainSync(m::finish);test.waitForIdleSync();}
      LlmTransport.connections=previous;restore(prefs,settings);restore(profiles,oldProfiles);restore(storage,oldStorage);
    }
  }
}
