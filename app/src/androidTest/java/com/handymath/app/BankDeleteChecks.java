package com.handymath.app;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Real ZIP installs, deletion confirmation, protected paths and retained learning data. */
final class BankDeleteChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static BankRepository install(Context c,String id)throws Exception{
    return NestedZipChecks.read(c,NestedZipChecks.archive(new String[]{"bank.json"},new byte[][]{NestedZipChecks.bank(id)})).get(0);
  }
  static BankRepository.Entry entry(Context c,String id)throws Exception{
    for(BankRepository.Entry e:BankRepository.catalog(c))if(e.id.equals(id))return e;
    return null;
  }
  static void reject(Context c,String location)throws Exception{
    boolean rejected=false;try{BankRepository.deleteImported(c,location);}catch(Exception expected){rejected=true;}
    check(rejected,"protected or invalid location rejected: "+location);
  }
  static View byDescription(View view,String description){
    if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
      View found=byDescription(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;
    }return null;
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();SharedPreferences prefs=c.getSharedPreferences("study",0);
    Map<String,?> saved=new HashMap<>(prefs.getAll());
    String id="delete-check-"+System.currentTimeMillis(),otherId=id+"-other";
    List<String> locations=new ArrayList<>();MainActivity main=null;long attempt=-1;
    try{
      BankRepository first=install(c,id);locations.add(first.location());
      BankRepository duplicate=install(c,id);locations.add(duplicate.location());
      BankRepository other=install(c,otherId);locations.add(other.location());
      File sibling=new File(c.getFilesDir(),"delete-check-sentinel");
      try(FileOutputStream out=new FileOutputStream(sibling)){out.write(42);}
      try{
        for(String location:new String[]{null,"","@builtin/wzx-basic","@builtin/wzx-full","@builtin/politics","custom-questions","../delete-check-sentinel","import-../../delete-check-sentinel"})reject(c,location);
        check(sibling.isFile(),"outside file remains intact");
        for(BankRepository.Entry e:BankRepository.catalog(c))if(e.location.isEmpty()||e.location.startsWith("@builtin/")){
          check(!e.imported(),"built-in entry never deletable");BankRepository.loadLocation(c,e.location);
        }
      }finally{sibling.delete();}
      new StudyStore(c,id).save("q1",new JSONObject().put("deleteTestDraft",true));
      try(LearningDb db=new LearningDb(c)){
        db.favorite(first,first.questions.get(0));db.favorite(other,other.questions.get(0));
        db.time(id,"q1",System.currentTimeMillis()-12000,12000);
        TutorClient.Reply reply=new TutorClient.Reply();reply.feedback="删除后历史点评保留";reply.studentWork="我的作答";reply.weaknesses=new JSONArray();
        attempt=db.grade(c,first,first.questions.get(0),reply,new InkView.Document(new ArrayList<>(),80),0);
      }
      prefs.edit().putString("bank",duplicate.location()).commit();
      main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
      MainActivity screen=main;AtomicReference<AlertDialog> dialog=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
      test.runOnMainSync(()->{try{
        View root=screen.getWindow().getDecorView();check(byDescription(root,"删除题库："+id)!=null,"imported card has delete button");
        for(BankRepository.Entry e:BankRepository.catalog(c))if(!e.imported())
          check(byDescription(root,"删除题库："+e.title)==null,"protected card has no delete button");
        java.lang.reflect.Method confirm=MainActivity.class.getDeclaredMethod("confirmDeleteBank",BankRepository.Entry.class);confirm.setAccessible(true);
        dialog.set((AlertDialog)confirm.invoke(screen,entry(c,id)));
        dialog.get().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      check(new File(c.getFilesDir(),"banks/"+first.location()).isDirectory(),"cancel preserves installed bank");
      test.runOnMainSync(()->{try{
        java.lang.reflect.Method confirm=MainActivity.class.getDeclaredMethod("confirmDeleteBank",BankRepository.Entry.class);confirm.setAccessible(true);
        dialog.set((AlertDialog)confirm.invoke(screen,entry(c,id)));dialog.get().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      for(int i=0;i<100;i++){
        AtomicReference<Boolean> busy=new AtomicReference<>(true);
        test.runOnMainSync(()->{try{busy.set((Boolean)StudyToolsChecks.get(screen,"busy"));}catch(Exception e){failure.set(e);}});
        if(!busy.get())break;SystemClock.sleep(100);
      }
      if(failure.get()!=null)throw new AssertionError(failure.get());
      check(entry(c,id)==null,"deleted bank disappears, including duplicate installs");
      check(!new File(c.getFilesDir(),"banks/"+first.location()).exists()&&!new File(c.getFilesDir(),"banks/"+duplicate.location()).exists(),"both imported directories removed");
      check(entry(c,otherId)!=null,"unrelated imported bank retained");
      check(!prefs.contains("bank"),"deleted active bank selection cleared");
      check(new StudyStore(c,id).load("q1").optBoolean("deleteTestDraft"),"draft retained");
      try(LearningDb db=new LearningDb(c)){
        check(db.questionTime(id,"q1")==12000&&db.attempt(attempt)!=null,"learning time and answer history retained");
        check(!db.favorite(id,"q1")&&db.favorite(otherId,"q1"),"only removed bank favorites cleared");
      }
      test.runOnMainSync(()->check(byDescription(screen.getWindow().getDecorView(),"删除题库："+id)==null,"home refreshed after deletion"));
      ReviewActivity review=(ReviewActivity)test.startActivitySync(new Intent(c,ReviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("attemptId",attempt));
      test.runOnMainSync(()->{try{
        check(StudyToolsChecks.markdown(review.getWindow().getDecorView()).contains("删除后历史点评保留"),"answer review works after source bank deletion");
      }catch(Exception e){failure.set(e);}finally{review.finish();}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      BankRepository restored=install(c,id);locations.add(restored.location());
      check(entry(c,id)!=null&&new StudyStore(c,id).load("q1").optBoolean("deleteTestDraft"),"reimport restores bank and retained draft");
      BankRepository.deleteImported(c,other.location());
      check(prefs.getString("bank","").equals(restored.location()),"deleting inactive bank preserves selected bank");
    }finally{
      if(main!=null){MainActivity screen=main;test.runOnMainSync(screen::finish);}
      for(String location:locations)if(new File(c.getFilesDir(),"banks/"+location).exists())BankRepository.deleteImported(c,location);
      try(LearningDb db=new LearningDb(c)){
        for(String table:new String[]{"favorites","time_log","attempts"})db.getWritableDatabase().delete(table,"bank=? OR bank=?",new String[]{id,otherId});
      }
      if(attempt>=0)new File(c.getFilesDir(),"learning-ink/"+attempt+".json").delete();
      new File(c.getFilesDir(),"study/"+id+"/q1.json").delete();new File(c.getFilesDir(),"study/"+id).delete();
      StudyToolsChecks.restore(prefs,saved);
    }
  }
}
