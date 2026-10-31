package com.handymath.app;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Real import API checks, including all-or-nothing behavior and the user's bundle. */
final class NestedZipChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static byte[] archive(String[] names,byte[][] values)throws Exception{
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    try(ZipOutputStream zip=new ZipOutputStream(bytes)){
      for(int i=0;i<names.length;i++){zip.putNextEntry(new ZipEntry(names[i]));zip.write(values[i]);zip.closeEntry();}
    }return bytes.toByteArray();
  }
  static byte[] bank(String id)throws Exception{
    JSONObject q=new JSONObject().put("id","q1").put("title","测试题").put("questionMarkdown","1+1=?").put("answerMarkdown","2");
    return new JSONObject().put("version",2).put("id",id).put("title",id).put("questions",new JSONArray().put(q)).toString().getBytes(StandardCharsets.UTF_8);
  }
  static List<BankRepository> read(Context c,byte[] bytes)throws Exception{
    File file=new File(c.getCacheDir(),"nested-import-test.zip");try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}
    return BankRepository.importZips(c,Uri.fromFile(file));
  }
  static int installed(Context c){File[] files=new File(c.getFilesDir(),"banks").listFiles(f->f.getName().startsWith("import-"));return files==null?0:files.length;}
  static void fails(Context c,byte[] bytes,String label)throws Exception{
    int count=installed(c);String last=c.getSharedPreferences("study",0).getString("bank","");boolean rejected=false;
    try{read(c,bytes);}catch(Exception e){rejected=true;}
    check(rejected,label);check(installed(c)==count,"failed import leaves no installed banks");
    check(last.equals(c.getSharedPreferences("study",0).getString("bank","")),"failed import preserves selected bank");
    File[] leftover=c.getFilesDir().listFiles(f->f.getName().startsWith("bank-import-"));check(leftover==null||leftover.length==0,"staging cleaned after failure");
  }
  static void run(Context c)throws Exception{
    byte[] one=archive(new String[]{"bank.json"},new byte[][]{bank("nested-test-one")});
    byte[] two=archive(new String[]{"包装/题库/bank.json"},new byte[][]{bank("nested-test-two")});
    check(read(c,one).size()==1,"existing root-level single bank works");
    check(read(c,two).get(0).id.equals("nested-test-two"),"wrapper folders resolve bank root");
    byte[] group=archive(new String[]{"题库/数一.zip","题库/数二.zip"},new byte[][]{one,two});
    List<BankRepository> result=read(c,group);check(result.size()==2,"nested ZIP bundle installs all banks");
    check(result.get(0).questions.size()+result.get(1).questions.size()==2,"all questions retained");
    check(read(c,archive(new String[]{"数学.zip"},new byte[][]{group})).size()==2,"multiple archive nesting levels work");
    byte[] invalid=archive(new String[]{"bank.json"},new byte[][]{"{}".getBytes(StandardCharsets.UTF_8)});
    fails(c,archive(new String[]{"valid.zip","invalid.zip"},new byte[][]{one,invalid}),"one bad bank rejects whole batch");
    fails(c,archive(new String[]{"valid.zip","invalid.zip"},new byte[][]{one,new byte[]{1,2,3}}),"non-ZIP nested payload rejects whole batch");
    fails(c,archive(new String[]{"a.zip","b.zip"},new byte[][]{one,one}),"duplicate bank IDs in bundle rejected");
    fails(c,archive(new String[]{"../escape.json"},new byte[][]{bank("escape")}),"path traversal rejected");
    byte[] escape=archive(new String[]{"../escape.json"},new byte[][]{bank("escape")});
    fails(c,archive(new String[]{"valid.zip","nested.zip"},new byte[][]{one,escape}),"nested traversal rejected");
    byte[] deep=one;for(int i=0;i<6;i++)deep=archive(new String[]{"wrapper/next.zip"},new byte[][]{deep});
    fails(c,deep,"excessive archive depth rejected");
    File actual=new File(c.getExternalFilesDir(null),"handymath-math-bundle.zip");
    check(actual.isFile(),"user math bundle staged on device");
    result=BankRepository.importZips(c,Uri.fromFile(actual));
    Set<String> ids=new HashSet<>();int total=0;for(BankRepository b:result){ids.add(b.id);total+=b.questions.size();}
    check(ids.equals(new HashSet<>(Arrays.asList("math-past-1","math-past-2","math-past-3")))&&total==1155,"actual math bundle imports three banks and 1155 questions");
  }
}
