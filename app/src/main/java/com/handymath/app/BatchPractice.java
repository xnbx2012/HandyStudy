package com.handymath.app;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.text.InputType;
import android.util.AtomicFile;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bank-scoped group sizes; individual question identities and drafts remain stable. */
final class BatchPractice {
  static int size(Context c, String bank) {return Math.max(1, Math.min(20, c.getSharedPreferences("bank-batch",0).getInt(bank,1)));}
  static List<Integer> group(BankRepository bank, StudyFilter filter, int current, int size) {
    List<Integer> matching=new ArrayList<>();
    for(int i=0;i<bank.questions.size();i++)if(filter.matches(bank,bank.questions.get(i)))matching.add(i);
    int position=matching.indexOf(current);if(position<0)return Collections.singletonList(current);
    int start=position/size*size;return new ArrayList<>(matching.subList(start,Math.min(start+size,matching.size())));
  }
  static AlertDialog edit(Activity activity, String id, String title, Runnable changed) {
    EditText count=new EditText(activity);count.setInputType(InputType.TYPE_CLASS_NUMBER);count.setSingleLine(true);count.setText(String.valueOf(size(activity,id)));count.selectAll();
    LinearLayout box=AppUi.column(activity);box.setPadding(AppUi.dp(activity,24),AppUi.dp(activity,8),AppUi.dp(activity,24),0);
    box.addView(AppUi.text(activity,"每组 1–20 题，逐题作答后统一批改。只修改当前题库；最后一组不足题数时按实际题数提交。",14,AppUi.MUTED));box.addView(count);
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title+" · 几合1").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
    dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      try{int value=Integer.parseInt(count.getText().toString().trim());if(value<1||value>20)throw new NumberFormatException();
        activity.getSharedPreferences("bank-batch",0).edit().putInt(id,value).apply();dialog.dismiss();changed.run();
      }catch(NumberFormatException invalid){count.setError("请输入 1–20 之间的整数");}
    });return dialog;
  }
  static void choose(Activity activity) {
    try{List<BankRepository.Entry> entries=BankRepository.catalog(activity);String[] labels=new String[entries.size()];
      for(int i=0;i<labels.length;i++)labels[i]=entries.get(i).title+" · "+size(activity,entries.get(i).id)+"合1";
      new AlertDialog.Builder(activity).setTitle("各题库合并提交").setItems(labels,(d,i)->{BankRepository.Entry e=entries.get(i);edit(activity,e.id,e.title,()->{});}).setNegativeButton("关闭",null).show();
    }catch(Exception e){AppUi.message(activity,"无法读取题库",e.getMessage());}
  }
  static boolean hasInk(JSONObject document) {
    if(document==null)return false;JSONArray strokes=document.optJSONArray("strokes");
    if(strokes!=null)for(int i=0;i<strokes.length();i++){JSONObject s=strokes.optJSONObject(i);if(s!=null&&!s.optBoolean("erase")&&s.optJSONArray("dots")!=null&&s.optJSONArray("dots").length()>0)return true;}
    return false;
  }
  static final class Item {
    final BankRepository.Question question;final JSONObject state;final InkView.Document document;final JSONObject annotations;final String choice;
    Item(BankRepository.Question question,JSONObject state)throws Exception{
      this.question=question;this.state=state;document=InkView.Document.parse(state.optJSONObject("ink")==null?new JSONObject():state.getJSONObject("ink"));
      annotations=state.optJSONObject("annotations")==null?new JSONObject():state.getJSONObject("annotations");choice=TutorClient.normalizeChoice(state.optString("batchChoice"));
    }
    boolean ink() {return !document.strokes.isEmpty()&&InkView.contentBounds(document.strokes)!=null||hasInk(annotations);}
  }
  static String fingerprint(Item item)throws Exception {
    String source=item.document.json(0,0,1).toString()+"\n"+item.annotations+"\n"+item.choice;
    byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
    StringBuilder out=new StringBuilder();for(byte value:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();
  }
  private static File snapshotDir(Context c,String bank,String question)throws IOException {
    if(!bank.matches("[a-zA-Z0-9_-]{1,64}")||!question.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("题目位置无效");
    return new File(c.getFilesDir(),"batch-snapshots/"+bank+"/"+question);
  }
  static void writeSnapshot(Context c,String bank,String question,JSONObject annotations,List<Bitmap> images)throws Exception {
    File dir=snapshotDir(c,bank,question);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法保存批注截图");
    for(int i=0;i<images.size();i++)try(OutputStream out=new FileOutputStream(new File(dir,i+".png"))){if(!images.get(i).compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("批注截图保存失败");}
    AtomicFile meta=new AtomicFile(new File(dir,"meta.json"));FileOutputStream out=meta.startWrite();
    try{out.write(new JSONObject().put("annotations",annotations).put("count",images.size()).toString().getBytes(StandardCharsets.UTF_8));meta.finishWrite(out);}catch(Exception e){meta.failWrite(out);throw e;}
  }
  static List<Bitmap> readSnapshot(Context c,BankRepository bank,Item item)throws Exception {
    if(!hasInk(item.annotations))return Collections.emptyList();File dir=snapshotDir(c,bank.id,item.question.id);
    JSONObject meta;
    try{meta=new JSONObject(new String(new AtomicFile(new File(dir,"meta.json")).readFully(),StandardCharsets.UTF_8));}
    catch(IOException missing){throw new IOException("「"+item.question.title+"」的题面批注截图不可用，请重新打开该题后提交",missing);}
    if(!meta.getJSONObject("annotations").toString().equals(item.annotations.toString()))throw new IOException("题面批注截图尚未更新，请回到「"+item.question.title+"」后再提交");
    int count=meta.getInt("count");if(count<1||count>64)throw new IOException("题面批注截图无效");List<Bitmap> images=new ArrayList<>();
    try{for(int i=0;i<count;i++){Bitmap bitmap=BitmapFactory.decodeFile(new File(dir,i+".png").getPath());if(bitmap==null)throw new IOException("请重新打开「"+item.question.title+"」以更新题面批注截图");images.add(bitmap);}return images;}
    catch(Exception e){for(Bitmap b:images)b.recycle();throw e;}
  }
}
