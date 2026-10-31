package com.handymath.app;

import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.*;
import java.util.*;
import org.json.*;

/** Photo decoding, transcription and explicitly separated crop regions. No answer generation. */
final class PhotoQuestion {
  static File directory(Context c){File dir=new File(c.getFilesDir(),"question-photos");dir.mkdirs();return dir;}
  static File file(Context c,String name)throws IOException {
    if(!name.matches("photo-[a-f0-9-]{36}\\.jpg"))throw new IOException("照片文件名无效");
    return new File(directory(c),name);
  }
  static File importImage(Context c,Uri uri)throws Exception {
    File raw=File.createTempFile("incoming-",".image",c.getCacheDir());
    try {
      try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(raw)){
        if(in==null)throw new IOException("无法读取照片");byte[] buffer=new byte[32768];int count;long size=0;
        while((count=in.read(buffer))!=-1){size+=count;if(size>32L*1024*1024)throw new IOException("照片超过32 MB，请选择较小的图片");out.write(buffer,0,count);}
      }
      Bitmap image=decode(raw);File normalized=file(c,"photo-"+UUID.randomUUID()+".jpg");
      try(FileOutputStream out=new FileOutputStream(normalized)){if(!image.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("照片保存失败");out.getFD().sync();}
      finally {image.recycle();}
      return normalized;
    } finally {raw.delete();}
  }
  static Bitmap decode(File source)throws IOException {
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),opts);
    if(opts.outWidth<1||opts.outHeight<1||opts.outWidth>30000||opts.outHeight>30000)throw new IOException("照片格式不支持或尺寸异常");
    int sample=1;while(Math.max(opts.outWidth,opts.outHeight)/sample>3200)sample*=2;
    opts.inJustDecodeBounds=false;opts.inSampleSize=sample;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
    Bitmap bitmap=BitmapFactory.decodeFile(source.getPath(),opts);if(bitmap==null)throw new IOException("无法解码照片，请改用JPEG或PNG");
    int rotation=ExifInterface.ORIENTATION_NORMAL;
    try{rotation=new ExifInterface(source.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
    Matrix matrix=new Matrix();
    switch(rotation){
      case 2: matrix.setScale(-1,1);break;
      case 3: matrix.setRotate(180);break;
      case 4: matrix.setScale(1,-1);break;
      case 5: matrix.setRotate(90);matrix.postScale(-1,1);break;
      case 6: matrix.setRotate(90);break;
      case 7: matrix.setRotate(270);matrix.postScale(-1,1);break;
      case 8: matrix.setRotate(270);break;
      default: break;
    }
    if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
    if(Math.max(bitmap.getWidth(),bitmap.getHeight())>2600){float ratio=2600f/Math.max(bitmap.getWidth(),bitmap.getHeight());Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*ratio)),Math.max(1,Math.round(bitmap.getHeight()*ratio)),true);if(scaled!=bitmap)bitmap.recycle();bitmap=scaled;}
    return bitmap;
  }
  private static JSONObject photoPart(Bitmap image)throws Exception {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,94,bytes);
    return new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",
        "data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)));
  }
  static JSONObject recognize(ProviderSettings config,Bitmap image)throws Exception {
    config.validate();if(!config.visionVerified)throw new IOException("请先在模型提供商设置中检测并保存多模态模型");
    JSONObject schema=new JSONObject().put("type","object").put("additionalProperties",false);
    JSONObject properties=new JSONObject();JSONArray required=new JSONArray();
    for(String name:new String[]{"title","questionMarkdown","referenceMarkdown","studentMarkdown","warnings"}){
      properties.put(name,new JSONObject().put("type","string"));required.put(name);
    }
    JSONObject box=new JSONObject().put("type","object").put("additionalProperties",false);JSONObject coords=new JSONObject();JSONArray names=new JSONArray();
    for(String name:new String[]{"left","top","right","bottom"}){coords.put(name,new JSONObject().put("type","integer").put("minimum",0).put("maximum",1000));names.put(name);}
    box.put("properties",coords).put("required",names);
    for(String name:new String[]{"questionRegions","referenceRegions","studentRegions"}){
      properties.put(name,new JSONObject().put("type","array").put("items",box).put("maxItems",8));required.put(name);
    }
    schema.put("properties",properties).put("required",required);
    String prompt="你是教材照片转录助手，只忠实识别图片，不解题、不补造答案。将图片按三类分开：questionMarkdown是题目印刷正文、条件与选项；referenceMarkdown是图片实际印刷或明确标为标准答案/解析的内容，未出现必须为空；studentMarkdown仅是用户手写的作答过程或答案，保留错误和不会的说明，绝不替换成正确答案。手写的题号或题面补充不是用户的答案。无法确定是参考解析还是学生笔迹时不要擅自归类，在warnings说明并要求校对。看不清的文字标注[看不清]，不要猜。多道独立题目出现在照片中时只识别主要/居中题目并在warnings说明范围。title简短。公式用LaTeX。三类Regions各自是对应内容（包括图形）的紧贴边界框，坐标以整张图左上为(0,0)、右下为(1000,1000)，整数。必须把题目与参考答案和学生作答分离，不能把整张混合照片作为题目框；区域可多个，禁止不同类别重叠。不存在的类别返回空字符串与空数组。不要将手写作答放进题目或参考答案。若手写覆盖印刷题目，使用文字转录分离，题目区域只保留不含作答或参考答案的图形；无法分离的区域不要框入题目，并在warnings说明。";
    return TutorClient.structured(config,prompt,
        new JSONArray().put(TutorClient.text("请转录并分区这张照片。" )).put(photoPart(image)),
        schema, PhotoQuestion::validate);
  }
  static void validate(JSONObject result)throws Exception {
    for(String name:new String[]{"title","questionMarkdown","referenceMarkdown","studentMarkdown","warnings"}){
      if(!(result.opt(name) instanceof String)||result.getString(name).length()>30000)throw new IOException("识别文字格式异常，请重试");
    }
    JSONArray flat=regions(result);
    for(int i=0;i<flat.length();i++)for(int j=i+1;j<flat.length();j++){
      JSONObject a=flat.getJSONObject(i),b=flat.getJSONObject(j);
      if(a.getInt("role")==b.getInt("role"))continue;
      double overlap=Math.max(0,Math.min(a.getInt("right"),b.getInt("right"))-Math.max(a.getInt("left"),b.getInt("left")))
          *Math.max(0,Math.min(a.getInt("bottom"),b.getInt("bottom"))-Math.max(a.getInt("top"),b.getInt("top")));
      if(overlap>0)throw new IOException("AI返回的题目、参考答案与作答区域发生重叠，请重试或手动划分区域");
    }
    if(result.getString("questionMarkdown").trim().isEmpty()&&result.getJSONArray("questionRegions").length()==0)throw new IOException("没有识别到题目，请拍清晰完整的题目后重试");
  }
  static JSONArray regions(JSONObject result)throws Exception {
    JSONArray all=new JSONArray();String[] categories={"questionRegions","referenceRegions","studentRegions"};
    for(int role=0;role<categories.length;role++){
      JSONArray a=result.getJSONArray(categories[role]);if(a.length()>8)throw new IOException("识别区域过多，请一次拍一道题");
      for(int i=0;i<a.length();i++){
        JSONObject b=a.getJSONObject(i);JSONObject copy=new JSONObject().put("role",role);
        for(String key:new String[]{"left","top","right","bottom"}){
          if(!(b.opt(key) instanceof Number)||b.getDouble(key)!=b.getInt(key)||b.getInt(key)<0||b.getInt(key)>1000)throw new IOException("照片区域坐标无效");
          copy.put(key,b.getInt(key));
        }
        if(copy.getInt("right")-copy.getInt("left")<2||copy.getInt("bottom")-copy.getInt("top")<2)throw new IOException("照片区域太小");
        all.put(copy);
      }
    }return all;
  }
  static Bitmap crop(Bitmap image,JSONObject box)throws Exception {
    int l=Math.max(0,(int)Math.floor(image.getWidth()*box.getDouble("left")/1000)),t=Math.max(0,(int)Math.floor(image.getHeight()*box.getDouble("top")/1000));
    int r=Math.min(image.getWidth(),(int)Math.ceil(image.getWidth()*box.getDouble("right")/1000)),b=Math.min(image.getHeight(),(int)Math.ceil(image.getHeight()*box.getDouble("bottom")/1000));
    if(r<=l||b<=t)throw new IOException("区域为空，请重新划分");
    return Bitmap.createBitmap(image,l,t,r-l,b-t);
  }
  static JSONArray[] saveCrops(Context c,JSONObject draft,File bank,String id,java.util.List<File> created)throws Exception {
    JSONArray[] files={new JSONArray(),new JSONArray(),new JSONArray()};
    String name=draft.optString("photo");if(name.isEmpty())return files;
    Bitmap image=decode(file(c,name));
    try{
      JSONArray boxes=draft.optJSONArray("regions");if(boxes==null)return files;
      if(boxes.length()>32)throw new IOException("照片区域过多");
      JSONObject check=new JSONObject();
      for(String key:new String[]{"title","questionMarkdown","referenceMarkdown","studentMarkdown","warnings"}) check.put(key,"手动核对");
      String[] keys={"questionRegions","referenceRegions","studentRegions"};
      for(String key:keys) check.put(key,new JSONArray());
      for(int i=0;i<boxes.length();i++){
        JSONObject box=boxes.getJSONObject(i);int role=box.getInt("role");
        if(role<0||role>2)throw new IOException("照片类别无效");
        check.getJSONArray(keys[role]).put(box);
      }
      validate(check);
      for(int i=0;i<boxes.length();i++){
        JSONObject box=boxes.getJSONObject(i);int role=box.getInt("role");if(role<0||role>2)throw new IOException("照片类别无效");
        Bitmap cropped=crop(image,box);String path=id+"-photo-"+role+"-"+i+".jpg";File target=new File(bank,path);created.add(target);
        try(FileOutputStream out=new FileOutputStream(target)){if(!cropped.compress(Bitmap.CompressFormat.JPEG,96,out))throw new IOException("区域保存失败");out.getFD().sync();}
        finally{if(cropped!=image)cropped.recycle();}
        files[role].put(path);
      }
    }finally{image.recycle();}return files;
  }
}
