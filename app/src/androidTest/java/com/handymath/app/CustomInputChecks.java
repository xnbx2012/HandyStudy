package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Device-side tests of editable text, real rendering/export, local banks and tutor requests. */
final class CustomInputChecks {
  static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  static View find(View view, String label) {
    if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
    if (view instanceof TextView && label.contentEquals(((TextView)view).getText())) return view;
    if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
      View found = find(((ViewGroup)view).getChildAt(i), label); if (found != null) return found;
    }
    return null;
  }
  static EditText editor(View view) {
    if (view instanceof EditText) return (EditText)view;
    if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
      EditText found = editor(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
    }
    return null;
  }
  static void tap(InkView ink, float x, float y, long hold) {
    long at = android.os.SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0);
    ink.onTouchEvent(down); down.recycle();
    if (hold == 0) {
      MotionEvent up = MotionEvent.obtain(at,at+20,MotionEvent.ACTION_UP,x,y,0); ink.onTouchEvent(up); up.recycle();
    }
  }
  static void run(Instrumentation test) throws Exception {
    Context c=test.getTargetContext();
    new android.util.AtomicFile(new File(c.getFilesDir(),"custom-question-draft.json")).delete();
    AtomicReference<InkView> canvas=new AtomicReference<>();
    AtomicReference<Throwable> uiError=new AtomicReference<>();
    test.runOnMainSync(()->{
      try {
        InkView ink=new InkView(c,StylusAdapter.Registry.select()); canvas.set(ink);
        ink.layout(0,0,1000,700);
        ink.putText(null,"我在这一步不会了 x = 2",40,85,600);
        check(ink.hasInk(),"typed-only answer is submit-ready");
        check(ink.strokes.get(0).dots.get(0).y==80,"text snaps to the selected ruled row");
        InkView.Stroke first=ink.strokes.get(0);
        ink.putText(first,"改为 x = 3",40,85,600);
        ink.undo(); check(ink.strokes.get(0).text.contains("不会"),"undo restores original text");
        ink.redo(); check(ink.strokes.get(0).text.contains("x = 3"),"redo restores edited text");
        JSONObject json=ink.serialize();
        InkView restored=new InkView(c,StylusAdapter.Registry.select()); restored.restore(json);
        check(restored.strokes.get(0).text.equals(ink.strokes.get(0).text),"Unicode text survives draft restore");
        restored.setLineSpacing(48); check(restored.strokes.get(0).width==48,"text follows updated line height");
        check(restored.strokes.get(0).dots.get(0).y==48,"text remains on its row when height changes");
        InkView.Stroke selected=restored.strokes.get(0);
        restored.putText(selected,"",40,48,600);check(!restored.hasInk(),"deleting all text removes answer content");
        restored.undo();check(restored.hasInk(),"undo restores deleted text");
        restored.redo();check(!restored.hasInk(),"redo removes deleted text again");
        InkView.Stroke pen=new InkView.Stroke();pen.width=3;pen.dots.add(new InkView.Dot(30,200,1));pen.dots.add(new InkView.Dot(180,240,1));
        ink.strokes.add(pen);
      } catch(Throwable e) { uiError.set(e); }
    });
    if(uiError.get()!=null)throw new AssertionError(uiError.get());
    InkView ink=canvas.get();
    InkView.Document document=InkView.Document.parse(ink.serialize());
    List<InkView.Sheet> sheets=document.export();
    check(!sheets.isEmpty(),"mixed answer exports images");
    boolean textPixels=false;
    for(InkView.Sheet sheet:sheets) {
      check(sheet.bounds.height()>120,"export includes typed row and handwriting");
      for(int y=0;y<sheet.bitmap.getHeight()&&!textPixels;y++) for(int x=0;x<sheet.bitmap.getWidth();x++) {
        int pixel=sheet.bitmap.getPixel(x,y);
        float worldY=sheet.bounds.top+y;
        if(worldY>=80&&worldY<160&&Color.red(pixel)<120&&Color.green(pixel)<120) {textPixels=true;break;}
      }
    }
    check(textPixels,"actual rendered AI image contains typed glyphs, not only ruling");
    JSONObject draft=new JSONObject().put("title","混合输入回归题").put("body","求 $$x + 1 = 4$$ 的解，并说明理由。").put("reference","");
    String id=CustomQuestionActivity.saveQuestion(c,draft,document);
    BankRepository bank=BankRepository.loadLocation(c,"custom-questions");
    BankRepository.Question question=null; for(BankRepository.Question q:bank.questions)if(q.id.equals(id))question=q;
    check(question!=null&&question.referenceAvailable&&question.referencePolicy.equals("independent"),"custom bank supports independent grading without reference");
    check(question.questionMarkdown.equals(draft.getString("body"))&&!question.questionImages.isEmpty(),"typed question and handwritten images are both retained");
    for(String path:question.questionImages){Bitmap bitmap=bank.image(path);check(bitmap!=null,"custom question image opens via ordinary bank API");bitmap.recycle();}
    ProviderSettings cfg=new ProviderSettings();cfg.context=c;cfg.key="fixture";cfg.visionVerified=true;
    LlmTransport.ConnectionFactory previous=LlmTransport.connections;
    int oldRetries=AppSettings.retries(c);
    AppSettings.prefs(c).edit().putInt("llmFormatRetries",3).commit();
    java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
    List<Fake> gradeCalls=new ArrayList<>();
    String recognition=new JSONObject().put("transcription","改为 x = 3，我在这一步不会了").put("finalAnswer","3").put("choice","").put("readable",true).put("confidence",.95).toString();
    String tags=new JSONObject().put("knowledgePoints",new JSONArray().put("一元一次方程")).put("examPoints",new JSONArray().put("等式性质")).put("questionType","解答题").put("difficulty",1).toString();
    String grade=new JSONObject(recognition).put("score",6).put("correct",false)
        .put("feedback","结果正确，请补充等式变形步骤。").put("weaknesses",new JSONArray())
        .put("tags",new JSONObject(tags)).toString();
    try {
      LlmTransport.connections=url->{int attempt=requests.getAndIncrement();
          String content=attempt==0?"{":attempt==1?"{\"score\":\"bad\"}"
              :attempt==2?new JSONObject(grade).put("feedback","x+1").toString():grade;
          Fake connection=new Fake(url,new JSONObject().put("choices",new JSONArray().put(
              new JSONObject().put("message",new JSONObject().put("content",content))
                  .put("finish_reason","stop"))).toString());
          gradeCalls.add(connection);return connection;};
      TutorClient.Reply reply=TutorClient.ask(cfg,bank,question,sheets,TutorClient.Mode.GRADE,0,new JSONArray());
      check(reply.score==6&&reply.studentWork.contains("不会"),"custom mixed answer reaches unified grading");
      check(reply.tags.getJSONArray("knowledgePoints").length()==1,"unified grading includes classification");
      check(gradeCalls.size()==4,"invalid JSON, score and formula each consume one automatic retry");
      String retryPayload=gradeCalls.get(3).sent.toString("UTF-8");
      check(retryPayload.contains("上一轮输出未通过应用校验")&&retryPayload.contains("image_url")
          &&retryPayload.contains("x+1"),"retry retains original image, prior response and validation error");
      LlmTransport.connections=url->new Fake(url,new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",new JSONObject().put("hint","先用等式性质移项。").put("feedback","").toString())).put("finish_reason","stop"))).toString());
      check(!TutorClient.ask(cfg,bank,question,Collections.emptyList(),TutorClient.Mode.HINT,1,new JSONArray()).hint.isEmpty(),"custom question supports existing hint flow");
      requests.set(0);
      String partial=new JSONObject().put("partialValid",true).put("feedback","").put("hint","把等式两边同时减去1。").toString();
      LlmTransport.connections=url->new Fake(url,new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",requests.getAndIncrement()==0?recognition:partial)).put("finish_reason","stop"))).toString());
      check(TutorClient.ask(cfg,bank,question,sheets,TutorClient.Mode.PARTIAL,0,new JSONArray()).partialValid,"custom mixed answer supports partial-work questions");
    } finally {LlmTransport.connections=previous;AppSettings.prefs(c).edit().putInt("llmFormatRetries",oldRetries).commit();for(InkView.Sheet sheet:sheets)sheet.bitmap.recycle();}
    MainActivity main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bankLocation","custom-questions").putExtra("questionId",id));
    test.waitForIdleSync();
    java.lang.reflect.Field inkField=MainActivity.class.getDeclaredField("ink");inkField.setAccessible(true);
    InkView answer=(InkView)inkField.get(main);
    test.runOnMainSync(()->{
      View button=find(main.getWindow().getDecorView(),"打字：点击答题区某一行输入，长按文字编辑");check(button!=null,"answer toolbar exposes text tool");button.performClick();
      check(answer.textMode,"text tool activates");
      tap(answer,60,80,0);
    });
    test.waitForIdleSync();
    test.runOnMainSync(()->{
      EditText input=editor(main.getWindow().getDecorView());check(input!=null,"row click opens inline editor");input.setText("这是键盘输入的解答，下一步不会了");
      View done=find(main.getWindow().getDecorView(),"完成");check(done!=null,"inline editor has commit control");done.performClick();
      check(answer.hasInk()&&answer.strokes.get(0).text.contains("下一步"),"inline input commits to answer document");
      answer.textMode=false;
      InkView.Dot anchor=answer.strokes.get(0).dots.get(0);
      tap(answer,(anchor.x+20)*answer.unit()+answer.panX,(anchor.y+20)*answer.unit()+answer.panY,600);
    });
    android.os.SystemClock.sleep(700);test.waitForIdleSync();
    test.runOnMainSync(()->{
      EditText input=editor(main.getWindow().getDecorView());check(input!=null,"long press reopens existing text while using pen");
      input.setText("已修改");find(main.getWindow().getDecorView(),"完成").performClick();
      check(answer.strokes.get(0).text.equals("已修改"),"long-press text edit is retained");
    });
    test.waitForIdleSync(); android.os.SystemClock.sleep(300);
    android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();
    try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(c.getExternalFilesDir(null),"custom-answer.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
    test.runOnMainSync(()->{
      main.finish();
    });
    Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
    CustomQuestionActivity author=(CustomQuestionActivity)test.startActivitySync(new Intent(c,CustomQuestionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    test.runOnMainSync(()->{
      try {
        for(String name:new String[]{"title","body","reference"}){
          java.lang.reflect.Field f=CustomQuestionActivity.class.getDeclaredField(name);f.setAccessible(true);
          ((EditText)f.get(author)).setText(name.equals("title")?"现场创建测试":name.equals("body")?"求 2x = 6 的解，写出步骤。":"");
        }
        java.lang.reflect.Field p=CustomQuestionActivity.class.getDeclaredField("paper");p.setAccessible(true);
        InkView writing=(InkView)p.get(author);
        writing.putText(null,"补充条件：x 为实数",30,80,500);
      }catch(Exception e){throw new RuntimeException(e);}
    });
    test.waitForIdleSync(); android.os.SystemClock.sleep(300);
    screenshot=test.getUiAutomation().takeScreenshot();
    try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(c.getExternalFilesDir(null),"custom-author.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
    test.runOnMainSync(()->find(author.getWindow().getDecorView(),"保存并开始做题").performClick());
    Activity opened=monitor.waitForActivityWithTimeout(15000);test.removeMonitor(monitor);
    check(opened instanceof MainActivity,"authoring save opens the ordinary practice screen");
    test.waitForIdleSync();
    java.lang.reflect.Field b=MainActivity.class.getDeclaredField("bank");b.setAccessible(true);
    BankRepository loaded=(BankRepository)b.get(opened);
    java.lang.reflect.Field idx=MainActivity.class.getDeclaredField("index");idx.setAccessible(true);
    check(loaded.questions.get(idx.getInt(opened)).title.equals("现场创建测试"),"newly created question is selected immediately");
    check(!new File(c.getFilesDir(),"custom-question-draft.json").exists(),"successful save clears authoring draft");
    test.runOnMainSync(opened::finish);
  }
  static final class Fake extends javax.net.ssl.HttpsURLConnection {
    final byte[] response; final ByteArrayOutputStream sent=new ByteArrayOutputStream();
    Fake(java.net.URL url,String value){super(url);response=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    public void connect(){} public void disconnect(){} public boolean usingProxy(){return false;}
    public String getCipherSuite(){return "fixture";} public java.security.cert.Certificate[] getLocalCertificates(){return null;}
    public java.security.cert.Certificate[] getServerCertificates(){return null;}
    public int getResponseCode(){return 200;}public OutputStream getOutputStream(){return sent;}public InputStream getInputStream(){return new ByteArrayInputStream(response);}
  }
}
