package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Device-side photo intake, isolation, OCR transport and existing-answer tests. */
final class PhotoInputChecks {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
  static JSONObject box(int top,int bottom)throws Exception{return new JSONObject().put("left",40).put("top",top).put("right",960).put("bottom",bottom);}
  static JSONObject result()throws Exception{return new JSONObject().put("title","教材照片测试").put("questionMarkdown","求方程 $$x+1=4$$ 的解。")
    .put("referenceMarkdown","参考答案：$$x=3$$。").put("studentMarkdown","我的解答：$$x=4$$，这一步不会了。")
    .put("warnings","请核对手写的数字，不自动纠正学生错误。")
    .put("questionRegions",new JSONArray().put(box(40,300))).put("referenceRegions",new JSONArray().put(box(360,560))).put("studentRegions",new JSONArray().put(box(650,950)));}
  static void screenshot(Instrumentation t,Context c,String name)throws Exception{t.waitForIdleSync();SystemClock.sleep(300);Bitmap raw=t.getUiAutomation().takeScreenshot();check(raw!=null,"screenshot available");Bitmap b=raw.copy(Bitmap.Config.ARGB_8888,false);raw.recycle();try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){check(b.compress(Bitmap.CompressFormat.PNG,100,out),"screenshot encoded");}finally{b.recycle();}}
  static void waitRecognized(Instrumentation test,CustomQuestionActivity author)throws Exception {
    for(int i=0;i<180;i++){
      AtomicBoolean done=new AtomicBoolean();test.runOnMainSync(()->{try{done.set(!((Boolean)field(author,"recognizing"))&&!((Boolean)field(author,"importing"))&&((EditText)field(author,"body")).getText().toString().contains("x+1"));}catch(Exception e){throw new RuntimeException(e);}});
      if(done.get())return;SystemClock.sleep(100);
    }throw new AssertionError("automatic photo recognition did not complete");
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();new android.util.AtomicFile(new File(c.getFilesDir(),"custom-question-draft.json")).delete();String active=ProviderSettings.load(c).id;
    boolean oldAuto=AppSettings.enabled(c,"autoRecognizeCustomPhoto",false);
    AppSettings.prefs(c).edit().putBoolean("autoRecognizeCustomPhoto",false).commit();
    ProviderSettings cfg=new ProviderSettings();cfg.context=c;cfg.id="photo-test-"+System.nanoTime();cfg.name="照片测试模型";cfg.key="fixture";cfg.visionVerified=true;cfg.save(c);ProviderSettings.activate(c,cfg.id);
    File source=new File(CaptureProvider.directory(c),"capture-"+UUID.randomUUID()+".jpg");
    Bitmap fixture=Bitmap.createBitmap(900,1200,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(fixture);canvas.drawColor(Color.WHITE);Paint p=new Paint(3);
    p.setColor(0xffffd4d4);canvas.drawRect(0,0,900,390,p);p.setColor(0xffd4ffd4);canvas.drawRect(0,390,900,720,p);p.setColor(0xffd4d4ff);canvas.drawRect(0,720,900,1200,p);
    p.setColor(Color.BLACK);p.setTextSize(48);canvas.drawText("Problem: x + 1 = 4",70,170,p);canvas.drawText("Reference: x = 3",70,530,p);p.setColor(0xff223c8f);canvas.drawText("My answer: x = 4",70,940,p);
    try(OutputStream out=new FileOutputStream(source)){fixture.compress(Bitmap.CompressFormat.JPEG,98,out);}fixture.recycle();
    Uri sourceUri=CaptureProvider.uri(c,source.getName());
    boolean rejected=false;try{CaptureProvider.file(c,Uri.parse("content://"+c.getPackageName()+".camera/capture/../../secret"));}catch(FileNotFoundException e){rejected=true;}check(rejected,"camera provider rejects traversal");
    check(CaptureProvider.file(c,sourceUri).equals(source),"camera URI resolves only to dedicated capture storage");
    JSONObject expected=result();PhotoQuestion.validate(expected);
    JSONObject overlap=new JSONObject(expected.toString());overlap.getJSONArray("referenceRegions").getJSONObject(0).put("top",100);
    rejected=false;try{PhotoQuestion.validate(overlap);}catch(IOException e){rejected=true;}check(rejected,"overlapping question and answer regions are rejected");
    JSONObject bad=new JSONObject(expected.toString());bad.getJSONArray("studentRegions").getJSONObject(0).put("right",1200);
    rejected=false;try{PhotoQuestion.validate(bad);}catch(IOException e){rejected=true;}check(rejected,"out of range crop coordinates are rejected");
    LlmTransport.ConnectionFactory previous=LlmTransport.connections;List<CustomInputChecks.Fake> calls=Collections.synchronizedList(new ArrayList<>());
    String response=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",expected.toString())).put("finish_reason","stop"))).toString();
    LlmTransport.connections=url->{CustomInputChecks.Fake connection=new CustomInputChecks.Fake(url,response);calls.add(connection);return connection;};
    try{
      CustomQuestionActivity author=(CustomQuestionActivity)test.startActivitySync(new Intent(c,CustomQuestionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.runOnMainSync(()->author.onActivityResult(410,Activity.RESULT_OK,new Intent().setData(sourceUri)));
      for(int i=0;i<100&&((Boolean)field(author,"importing"));i++)SystemClock.sleep(50);
      test.runOnMainSync(()->{try{
        check(((EditText)field(author,"body")).getText().length()==0,"default photo import leaves text unrecognized");
        JSONArray regions=(JSONArray)field(author,"regions");
        check(regions.length()==1&&regions.getJSONObject(0).getInt("role")==0,"default photo is the full question image");
      }catch(Exception e){throw new RuntimeException(e);}});
      check(calls.isEmpty(),"default photo import does not contact the model");
      test.runOnMainSync(()->{try{((Button)field(author,"retry")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
      waitRecognized(test,author);check(calls.size()==1,"manual recognition calls the vision model once");
      String payload=calls.get(0).sent.toString("UTF-8");JSONObject requestBody=new JSONObject(payload);
      String photoUrl=requestBody.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url");
      check(photoUrl.startsWith("data:image/jpeg;base64,")&&payload.contains("studentMarkdown")&&payload.contains("referenceRegions"),"vision request includes source photo and typed separation schema");
      test.runOnMainSync(()->{try{
        check(((EditText)field(author,"reference")).getText().toString().contains("x=3"),"reference fills reference field");
        check(((EditText)field(author,"student")).getText().toString().contains("x=4"),"wrong handwritten answer is preserved without correction");
        check(((JSONArray)field(author,"regions")).length()==3,"three separately reviewed crop categories");
      }catch(Exception e){throw new RuntimeException(e);}});
      screenshot(test,c,"photo-author-top.png");
      // Verify actual crop pixels and destination categories before UI save.
      JSONObject draft=new JSONObject().put("title","照片存储隔离测试").put("body",expected.getString("questionMarkdown")).put("reference",expected.getString("referenceMarkdown"))
        .put("student",expected.getString("studentMarkdown")).put("photo",field(author,"photoName")).put("regions",PhotoQuestion.regions(expected));
      InkView.Document empty=new InkView.Document(Collections.emptyList(),80);
      String directId=CustomQuestionActivity.saveQuestion(c,draft,empty);BankRepository bank=BankRepository.loadLocation(c,"custom-questions");
      BankRepository.Question saved=null;for(BankRepository.Question q:bank.questions)if(q.id.equals(directId))saved=q;
      check(saved!=null&&saved.questionImages.size()==1&&saved.answers.size()==1&&saved.studentSourceImages.size()==1,"all photo categories persist as separate files");
      Bitmap q=bank.image(saved.questionImages.get(0)),r=bank.image(saved.answers.get(0)),a=bank.image(saved.studentSourceImages.get(0));
      check(Color.red(q.getPixel(5,5))>Color.green(q.getPixel(5,5))+15,"question crop contains only red question band");
      check(Color.green(r.getPixel(5,5))>Color.red(r.getPixel(5,5))+15,"reference crop contains only green reference band");
      check(Color.blue(a.getPixel(5,5))>Color.red(a.getPixel(5,5))+15,"student source contains only blue handwritten band");q.recycle();r.recycle();a.recycle();
      JSONObject snap=LearningDb.snapshot(saved);check(snap.getJSONArray("studentSourceImages").length()==1,"history and exports retain handwritten source");
      Instrumentation.ActivityMonitor mainMonitor=test.addMonitor(MainActivity.class.getName(),null,false);
      test.runOnMainSync(()->{try{((Button)field(author,"reviewButton")).performClick();((Button)field(author,"save")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
      Activity opened=mainMonitor.waitForActivityWithTimeout(15000);test.removeMonitor(mainMonitor);check(opened instanceof MainActivity,"reviewed photo opens ordinary practice page");test.waitForIdleSync();
      MainActivity main=(MainActivity)opened;InkView answer=(InkView)field(main,"ink");
      check(answer.hasInk()&&answer.strokes.get(0).text.contains("x=4"),"recognized handwritten answer enters answer canvas");
      check(!answer.strokes.get(0).text.contains("x=3"),"reference answer is never seeded into student canvas");
      screenshot(test,c,"photo-practice.png");test.runOnMainSync(main::finish);
      // Exercise the actual camera intent/output URI path with a camera-result fixture.
      Instrumentation.ActivityMonitor cameraMonitor=new Instrumentation.ActivityMonitor(){
        @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
          if(!MediaStore.ACTION_IMAGE_CAPTURE.equals(intent.getAction()))return null;
          try{
            Uri output=intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT);check(output!=null,"camera requests full-resolution output URI");
            check((intent.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0,"camera receives temporary write grant");
            try(InputStream in=new FileInputStream(source);OutputStream out=c.getContentResolver().openOutputStream(output)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
            return new Instrumentation.ActivityResult(Activity.RESULT_OK,null);
          }catch(Exception e){throw new RuntimeException(e);}
        }
      };
      test.addMonitor(cameraMonitor);
      AppSettings.prefs(c).edit().putBoolean("autoRecognizeCustomPhoto",true).commit();
      CustomQuestionActivity camera=(CustomQuestionActivity)test.startActivitySync(new Intent(c,CustomQuestionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.runOnMainSync(()->CustomInputChecks.find(camera.getWindow().getDecorView(),"拍照识题").performClick());
      waitRecognized(test,camera);test.removeMonitor(cameraMonitor);check(calls.size()==2,"camera completion automatically recognizes captured full-resolution photo");
      test.runOnMainSync(camera::finish);
    }finally{test.waitForIdleSync();LlmTransport.connections=previous;ProviderSettings.activate(c,active);ProviderSettings.delete(c,cfg.id);AppSettings.prefs(c).edit().putBoolean("autoRecognizeCustomPhoto",oldAuto).commit();source.delete();new android.util.AtomicFile(new File(c.getFilesDir(),"custom-question-draft.json")).delete();}
  }
}
