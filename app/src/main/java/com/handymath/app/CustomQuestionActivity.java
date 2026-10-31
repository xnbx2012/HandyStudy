package com.handymath.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Photo-assisted authoring; review keeps problem, reference and student work separate. */
public final class CustomQuestionActivity extends Activity {
  private static final int PICK=410, CAMERA=411;
  private EditText title, body, reference, student;
  private InkView paper;
  private StylusAdapter stylus;
  private Button save, retry, reviewButton;
  private TextView photoStatus;
  private ImageView photoPreview;
  private ProgressBar progress;
  private LinearLayout root, sections;
  private final LinearLayout[] cropLists=new LinearLayout[3];
  private final java.util.List<Bitmap> thumbnails=new ArrayList<>();
  private final java.util.List<View> inputs=new ArrayList<>();
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private Bitmap photo;
  private String photoName="",captureName="",warnings="";
  private boolean autoFullPhoto;
  private JSONArray regions=new JSONArray();
  private boolean saving, recognizing, importing, completed, reviewConfirmed;
  private File draftFile(){return new File(getFilesDir(),"custom-question-draft.json");}
  private int dp(int value){return AppUi.dp(this,value);}
  private LinearLayout card(String label,String subtitle){
    LinearLayout card=AppUi.card(this);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);sections.addView(card,lp);
    card.addView(AppUi.heading(this,label));
    if(!subtitle.isEmpty()){TextView sub=AppUi.text(this,subtitle,13,AppUi.MUTED);sub.setPadding(0,dp(6),0,dp(12));card.addView(sub);}
    return card;
  }
  private EditText field(LinearLayout parent,String hint,int lines){
    EditText view=new EditText(this);view.setHint(hint);view.setMinLines(lines);view.setMaxLines(6);
    view.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    view.setGravity(Gravity.TOP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);lp.bottomMargin=dp(6);parent.addView(view,lp);inputs.add(view);return view;
  }
  private void pair(LinearLayout parent,Button a,Button b){LinearLayout row=new LinearLayout(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(6),dp(3),dp(6));row.addView(a,new LinearLayout.LayoutParams(lp));row.addView(b,new LinearLayout.LayoutParams(lp));parent.addView(row);if(parent.getParent()==sections){inputs.add(a);inputs.add(b);}}
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    root=AppUi.column(this);root.setBackgroundColor(AppUi.BG);setContentView(root);
    root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(16)+i.getSystemWindowInsetLeft(),dp(8)+i.getSystemWindowInsetTop(),dp(16)+i.getSystemWindowInsetRight(),dp(12)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(AppUi.button(this,"‹ 返回",false,()->onBackPressed()));
    TextView heading=AppUi.heading(this,"自定义题目");heading.setTextSize(24);heading.setPadding(dp(16),dp(8),0,dp(8));header.addView(heading);root.addView(header);
    ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setPadding(0,dp(16),0,0);
    FrameLayout center=new FrameLayout(this);sections=AppUi.column(this);center.addView(sections,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));scroll.addView(center);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.min(r-l,dp(940));if(sections.getLayoutParams().width!=width){sections.getLayoutParams().width=width;sections.requestLayout();}});
    LinearLayout source=card("从教材开始","拍一道题，或选择教材照片。自动区分题目、参考答案和你的手写作答。");
    pair(source,AppUi.button(this,"拍照识题",true,()->choose(true)),AppUi.button(this,"从相册选择",false,()->choose(false)));
    photoPreview=new ImageView(this);photoPreview.setAdjustViewBounds(true);photoPreview.setMaxHeight(dp(250));photoPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);photoPreview.setContentDescription("教材原照片预览，点击可手动框选");photoPreview.setVisibility(View.GONE);source.addView(photoPreview,new LinearLayout.LayoutParams(-1,-2));photoPreview.setOnClickListener(v->showCrop());
    progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(AppUi.TEAL));progress.setVisibility(View.GONE);source.addView(progress,new LinearLayout.LayoutParams(dp(26),dp(26)));
    photoStatus=AppUi.text(this,"照片默认直接作为题目；需要拆分题目、答案和已有作答时，可手动框选或点击识别。",13,AppUi.MUTED);photoStatus.setPadding(0,dp(12),0,dp(6));source.addView(photoStatus);
    retry=AppUi.button(this,"识别照片",false,()->recognizePhoto());retry.setVisibility(View.GONE);
    Button crop=AppUi.button(this,"手动框选区域",false,()->showCrop());pair(source,retry,crop);
    Button configure=AppUi.button(this,"多模态模型设置",false,()->startActivity(new Intent(this,ProvidersActivity.class)));source.addView(configure);inputs.add(configure);
    LinearLayout problem=card("01  题目","检查题干、条件、选项与图形，不要包含参考答案或已有作答。");
    title=field(problem,"题目名称（可选）",1);body=field(problem,"输入、粘贴或识别题目正文，支持 LaTeX 公式",3);cropLists[0]=AppUi.column(this);problem.addView(cropLists[0]);
    LinearLayout solution=card("02  参考答案","只放教材真实提供的答案或解析；留空则由 AI 独立评价。");
    reference=field(solution,"参考答案或评分要求（可选）",2);cropLists[1]=AppUi.column(this);solution.addView(cropLists[1]);
    LinearLayout work=card("03  我的已有作答","照片里的手写答案会转为可编辑文字，并带入答题区。请保留自己的真实过程与错误。");
    student=field(work,"识别出的手写作答（可校对，也可留空重新做）",2);cropLists[2]=AppUi.column(this);work.addView(cropLists[2]);
    LinearLayout hand=card("补充题面","在这里补充题目，不是作答。可手写或打字，保存后再进入答题区。");
    HorizontalScrollView toolScroll=new HorizontalScrollView(this);LinearLayout tools=new LinearLayout(this);toolScroll.addView(tools);hand.addView(toolScroll);
    stylus=StylusAdapter.Registry.select();
    stylus.attach(this,()->{if(!busy()){paper.textMode=false;paper.eraser=!paper.eraser;}},()->{
      if(!busy()) startActivity(new Intent(this,SettingsActivity.class));
    });
    stylus.setGestureAllowed(()->paper!=null&&!busy()&&!paper.locked);
    stylus.setActionHandler(action->{
      if(paper==null||busy()||paper.locked)return;
      paper.cancelContact();
      if(action==AppSettings.UNDO)paper.undo();
      else {
        paper.textMode=false;
        if(action==AppSettings.SWITCH_TOOL)paper.eraser=!paper.eraser;
        else if(action==AppSettings.SWITCH_TYPE){paper.correction=!paper.correction;paper.eraser=false;AppSettings.apply(paper);}
        paper.invalidate();
      }
    });
    paper=new InkView(this,stylus);AppSettings.apply(paper);paper.finger=true;
    String[] labels={"手写","打字","橡皮","撤销","重做","查看全部"};
    for(int i=0;i<labels.length;i++){final int tool=i;Button b=AppUi.button(this,labels[i],false,()->{
      if(busy())return;if(tool==0){paper.textMode=false;paper.eraser=false;}if(tool==1){paper.textMode=true;paper.eraser=false;toast("点击一行输入，长按文字编辑");}if(tool==2){paper.textMode=false;paper.eraser=true;}if(tool==3)paper.undo();if(tool==4)paper.redo();if(tool==5)paper.fitInk();
    });LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(6);tools.addView(b,lp);inputs.add(b);}
    FrameLayout writing=new FrameLayout(this);writing.setBackground(AppUi.surface(this,Color.WHITE,14));writing.addView(paper,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams paperLp=new LinearLayout.LayoutParams(-1,dp(300));paperLp.topMargin=dp(12);hand.addView(writing,paperLp);
    reviewButton=AppUi.button(this,"○  我已核对照片分区和识别文字",false,()->{reviewConfirmed=!reviewConfirmed;updateReview();});reviewButton.setVisibility(View.GONE);sections.addView(reviewButton,new LinearLayout.LayoutParams(-1,-2));inputs.add(reviewButton);
    save=AppUi.button(this,"保存并开始做题",true,()->create());LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(-1,dp(54));saveLp.topMargin=dp(10);root.addView(save,saveLp);
    try{
      JSONObject draft=state!=null&&state.containsKey("draft")?new JSONObject(state.getString("draft")):draftFile().isFile()?new JSONObject(new String(new AtomicFile(draftFile()).readFully(),StandardCharsets.UTF_8)):null;
      if(draft!=null){title.setText(draft.optString("title"));body.setText(draft.optString("body"));reference.setText(draft.optString("reference"));student.setText(draft.optString("student"));if(draft.has("paper"))paper.restore(draft.getJSONObject("paper"));
        captureName=draft.optString("capture");photoName=draft.optString("photo");warnings=draft.optString("warnings");regions=draft.optJSONArray("regions");if(regions==null)regions=new JSONArray();reviewConfirmed=draft.optBoolean("reviewConfirmed");autoFullPhoto=draft.optBoolean("autoFullPhoto");
        if(!photoName.isEmpty()){photo=PhotoQuestion.decode(PhotoQuestion.file(this,photoName));displayPhoto();photoStatus.setText(warnings.isEmpty()?"已恢复照片草稿，请核对后保存。":warnings);}
      }
    }catch(Exception e){photoName="";toast("部分照片草稿未能恢复，请重新选择照片");}
  }
  private boolean busy(){return saving||recognizing||importing;}
  private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
  private void setBusy(){boolean b=busy();paper.locked=b;save.setEnabled(!b);for(View view:inputs){view.setEnabled(!b);view.setAlpha(b?.5f:1);}progress.setVisibility(recognizing||importing?View.VISIBLE:View.GONE);save.setText(saving?"正在保存…":"保存并开始做题");}
  private void choose(boolean camera){
    if(busy())return;
    if(photo!=null||!body.getText().toString().trim().isEmpty()||!reference.getText().toString().trim().isEmpty()||!student.getText().toString().trim().isEmpty()){
      Dialog d=new Dialog(this);LinearLayout box=AppUi.card(this);box.addView(AppUi.heading(this,"换一张照片？"));TextView note=AppUi.text(this,"新照片会替换当前照片和分区，手写补充会保留。",15,AppUi.MUTED);note.setPadding(0,dp(12),0,dp(20));box.addView(note);pair(box,AppUi.button(this,"保留当前",false,d::dismiss),AppUi.button(this,"继续选择",true,()->{d.dismiss();launch(camera);}));showDialog(d,box,520);return;
    }launch(camera);
  }
  private void launch(boolean camera){
    try{
      if(camera){captureName="capture-"+UUID.randomUUID()+".jpg";Uri uri=CaptureProvider.uri(this,captureName);
        Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("教材照片",uri));startActivityForResult(intent,CAMERA);
      }else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),PICK);
    }catch(Exception e){captureName="";failure(new IOException(camera?"未找到可用的相机，请从相册选择照片":"无法打开选图器，请稍后重试",e));}
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);if(request!=PICK&&request!=CAMERA)return;
    Uri uri=null;
    try{
      if(request==CAMERA&&!captureName.isEmpty())uri=CaptureProvider.uri(this,captureName);
      else if(data!=null)uri=data.getData();
      if(result==RESULT_OK&&uri!=null)importPhoto(uri,request==CAMERA);
      else if(request==CAMERA&&uri!=null){CaptureProvider.file(this,uri).delete();revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);captureName="";}
    }catch(Exception e){failure(e);}
  }
  private void importPhoto(Uri uri,boolean camera){
    importing=true;photoStatus.setText("正在准备照片…");setBusy();
    worker.execute(()->{
      try{
        File imported=PhotoQuestion.importImage(this,uri);Bitmap bitmap=PhotoQuestion.decode(imported);
        runOnUiThread(()->{
          if(isDestroyed()){bitmap.recycle();return;}
          clearThumbnails();photoPreview.setImageDrawable(null);if(photo!=null)photo.recycle();
          String old=photoName;photoName=imported.getName();photo=bitmap;regions=new JSONArray();reviewConfirmed=false;warnings="";
          autoFullPhoto=!AppSettings.enabled(this,"autoRecognizeCustomPhoto",false);
          if(autoFullPhoto)try{regions.put(new JSONObject().put("role",0).put("left",0).put("top",0).put("right",1000).put("bottom",1000));}
          catch(Exception e){failure(e);}
          importing=false;displayPhoto();setBusy();
          if(!old.isEmpty())try{PhotoQuestion.file(this,old).delete();}catch(IOException ignored){}
          photoStatus.setText(autoFullPhoto?"照片已作为题目。若包含答案或手写作答，请手动框选区域或点击识别照片后核对。":"照片已导入，正在识别…");
          persistDraft();if(!autoFullPhoto)recognizePhoto();
        });
      }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{importing=false;failure(e);});}
      finally{if(camera)try{CaptureProvider.file(this,uri).delete();captureName="";revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}}
    });
  }
  private void recognizePhoto(){
    if(busy()||photoName.isEmpty())return;
    final ProviderSettings config;
    try{config=ProviderSettings.load(this);config.validate();if(!config.visionVerified)throw new IOException("请先设置并检测多模态模型，再点重新识别");}
    catch(Exception e){photoStatus.setText(e.getMessage());retry.setVisibility(View.VISIBLE);return;}
    recognizing=true;photoStatus.setText("正在使用 "+config.name+" 识别题目、参考答案和手写作答…");setBusy();final String name=photoName;
    worker.execute(()->{
      Bitmap bitmap=null;
      try{bitmap=PhotoQuestion.decode(PhotoQuestion.file(this,name));JSONObject result=PhotoQuestion.recognize(config,bitmap);
        runOnUiThread(()->{if(isDestroyed())return;try{applyRecognition(result);}catch(Exception e){failure(e);}finally{recognizing=false;setBusy();persistDraft();}});
      }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{recognizing=false;failure(e);photoStatus.setText("识别未完成："+e.getMessage()+"。照片已保留，可重试或手动框选并填写文字。");});}
      finally{if(bitmap!=null)bitmap.recycle();}
    });
  }
  void applyRecognition(JSONObject result)throws Exception{
    PhotoQuestion.validate(result);title.setText(result.getString("title"));body.setText(result.getString("questionMarkdown"));reference.setText(result.getString("referenceMarkdown"));student.setText(result.getString("studentMarkdown"));
    regions=PhotoQuestion.regions(result);warnings=result.getString("warnings");reviewConfirmed=false;autoFullPhoto=false;retry.setText("重新识别");
    photoStatus.setText(warnings.isEmpty()?"识别完成。请分别核对下面的题目、参考答案和我的作答。":"需要校对："+warnings);renderCrops();updateReview();
  }
  private void displayPhoto(){photoPreview.setImageBitmap(photo);photoPreview.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);reviewButton.setVisibility(View.VISIBLE);renderCrops();updateReview();}
  private void updateReview(){reviewButton.setText(reviewConfirmed?"✓  已核对照片分区和识别文字":"○  我已核对照片分区和识别文字");reviewButton.setTextColor(reviewConfirmed?AppUi.TEAL:AppUi.MUTED);}
  private void clearThumbnails(){for(LinearLayout list:cropLists)if(list!=null)list.removeAllViews();for(Bitmap b:thumbnails)b.recycle();thumbnails.clear();}
  private void renderCrops(){
    clearThumbnails();if(photo==null)return;
    try{for(int i=0;i<regions.length();i++){
      final int index=i;JSONObject box=regions.getJSONObject(i);int role=box.getInt("role");if(role<0||role>2)continue;
      Bitmap crop=PhotoQuestion.crop(photo,box);float scale=Math.min(1,700f/Math.max(crop.getWidth(),crop.getHeight()));Bitmap preview=Bitmap.createScaledBitmap(crop,Math.max(1,(int)(crop.getWidth()*scale)),Math.max(1,(int)(crop.getHeight()*scale)),true);if(crop!=preview&&crop!=photo)crop.recycle();if(preview==photo)preview=photo.copy(Bitmap.Config.ARGB_8888,false);thumbnails.add(preview);
      ImageView image=new ImageView(this);image.setImageBitmap(preview);image.setAdjustViewBounds(true);image.setMaxHeight(dp(160));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(new String[]{"题目照片区域","参考答案照片区域","原手写作答照片区域"}[role]);LinearLayout list=cropLists[role];list.addView(image,new LinearLayout.LayoutParams(-1,-2));
      pair(list,AppUi.button(this,"更改归类",false,()->moveRegion(index)),AppUi.button(this,"移除此区域",false,()->{if(busy())return;regions.remove(index);reviewConfirmed=false;renderCrops();updateReview();}));
    }}catch(Exception e){photoStatus.setText("区域显示失败，请重新识别或框选："+e.getMessage());}
  }
  private void moveRegion(int index){
    if(busy())return;Dialog d=new Dialog(this);LinearLayout box=AppUi.card(this);box.addView(AppUi.heading(this,"这个区域属于哪一类？"));
    String[] names={"题目","参考答案","我的已有作答"};for(int i=0;i<names.length;i++){final int role=i;Button b=AppUi.button(this,names[i],false,()->{try{regions.getJSONObject(index).put("role",role);reviewConfirmed=false;renderCrops();updateReview();d.dismiss();}catch(Exception e){failure(e);}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);box.addView(b,lp);}showDialog(d,box,460);
  }
  private void showCrop(){
    if(busy())return;if(photo==null){toast("请先拍照或选择照片");return;}
    Dialog d=new Dialog(this);LinearLayout box=AppUi.card(this);box.addView(AppUi.heading(this,"拖动框选，再选择区域类别"));PhotoCropView view=new PhotoCropView(this,photo);box.addView(view,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout buttons=new LinearLayout(this);String[] names={"作为题目","作为参考答案","作为我的作答"};
    for(int i=0;i<3;i++){final int role=i;Button button=AppUi.button(this,names[i],i==0,()->{try{if(regions.length()>=32)throw new IOException("区域已达到上限");if(autoFullPhoto){regions=new JSONArray();autoFullPhoto=false;}
        regions.put(view.region(role));reviewConfirmed=false;renderCrops();updateReview();d.dismiss();}catch(Exception e){toast(e.getMessage());}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),dp(8),dp(2),0);buttons.addView(button,lp);}box.addView(buttons);box.addView(AppUi.button(this,"取消",false,d::dismiss));
    d.setContentView(box);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.show();d.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),getResources().getDisplayMetrics().heightPixels-dp(80));
  }
  private void showDialog(Dialog dialog,LinearLayout box,int width){dialog.setContentView(box);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.getWindow().setLayout(Math.min(dp(width),getResources().getDisplayMetrics().widthPixels-dp(32)),-2);}
  private JSONObject draft()throws Exception{paper.commitText();return new JSONObject().put("title",title.getText().toString()).put("body",body.getText().toString()).put("reference",reference.getText().toString()).put("student",student.getText().toString()).put("paper",paper.serialize()).put("photo",photoName).put("capture",captureName).put("regions",regions).put("warnings",warnings).put("reviewConfirmed",reviewConfirmed).put("autoFullPhoto",autoFullPhoto);}
  private void persistDraft(){if(completed)return;try{writeAtomic(draftFile(),draft().toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){toast("草稿保存失败，请稍后重试");}}
  static void writeAtomic(File target,byte[] data)throws Exception{AtomicFile file=new AtomicFile(target);FileOutputStream out=file.startWrite();try{out.write(data);file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}}
  @Override protected void onSaveInstanceState(Bundle state){try{state.putString("draft",draft().toString());}catch(Exception ignored){}super.onSaveInstanceState(state);}
  @Override protected void onResume(){super.onResume();if(stylus!=null)stylus.resume();if(paper!=null)AppSettings.apply(paper);}
  @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(stylus!=null)stylus.focusChanged(focused);}
  @Override public boolean dispatchKeyEvent(KeyEvent event){return stylus!=null&&stylus.key(event)||super.dispatchKeyEvent(event);}
  @Override protected void onPause(){if(stylus!=null)stylus.pause();if(paper!=null)paper.cancelContact();super.onPause();persistDraft();}
  private void create(){
    if(busy())return;
    if(!photoName.isEmpty()&&!reviewConfirmed){toast("请先核对照片分区和识别文字，再勾选确认");return;}
    boolean photoProblem=false;for(int i=0;i<regions.length();i++)if(regions.optJSONObject(i).optInt("role")==0)photoProblem=true;
    if(body.getText().toString().trim().isEmpty()&&!paper.hasInk()&&!photoProblem){toast("请填写题目、手写题面或框选题目照片区域");return;}
    final JSONObject data;try{data=draft();}catch(Exception e){failure(e);return;}final InkView.Document document=paper.freeze();saving=true;setBusy();
    worker.execute(()->{try{String id=saveQuestion(this,data,document);runOnUiThread(()->{if(isDestroyed())return;completed=true;new AtomicFile(draftFile()).delete();if(!photoName.isEmpty())try{PhotoQuestion.file(this,photoName).delete();}catch(IOException ignored){}
      startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("bankLocation","custom-questions").putExtra("questionId",id));finish();});}
      catch(Exception|OutOfMemoryError e){runOnUiThread(()->{saving=false;failure(e);});}});
  }
  static synchronized String saveQuestion(Context context, JSONObject draft, InkView.Document document) throws Exception {
    File dir = new File(context.getFilesDir(),"banks/custom-questions");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建自定义题库");
    AtomicFile manifest = new AtomicFile(new File(dir,"bank.json"));
    JSONObject bank = manifest.getBaseFile().exists() ? new JSONObject(new String(manifest.readFully(),StandardCharsets.UTF_8))
        : new JSONObject().put("version",2).put("id","custom-questions").put("title","自定义题目").put("subject","自定义").put("questions",new JSONArray());
    JSONArray questions = bank.getJSONArray("questions");
    if (questions.length() >= 1000) throw new IOException("自定义题库已达到1000题上限");
    String id = "custom-" + UUID.randomUUID().toString();
    List<InkView.Sheet> sheets = document.export();
    List<File> created = new ArrayList<>();
    try {
      if (sheets.size() > 16) throw new IOException("手写题面范围过大，请缩小到16张图片以内；草稿保留");

      JSONArray images = new JSONArray();
      for (int i = 0; i < sheets.size(); i++) {
        String name = id + "-" + i + ".png"; File file = new File(dir,name); created.add(file);
        try (FileOutputStream out = new FileOutputStream(file)) {
          if (!sheets.get(i).bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)) throw new IOException("题面图片保存失败");
          out.getFD().sync();
        }
        images.put(name);
      }
      JSONArray[] crops = PhotoQuestion.saveCrops(context, draft, dir, id, created);
      for(int i=0;i<crops[0].length();i++) images.put(crops[0].getString(i));
      if(images.length()>16 || crops[1].length()>8) throw new IOException("题目或参考答案照片区域过多，请精简后保存");
      if(images.length()==0 && draft.optString("body").trim().isEmpty()) throw new IOException("题面为空，请输入正文或划分题目区域");
      String name = draft.optString("title").trim();
      if (name.isEmpty()) name = "自定义题目 " + (questions.length()+1);
      String ref = draft.optString("reference");
      JSONObject q = new JSONObject().put("id",id).put("title",name).put("chapter","自定义题目")
          .put("number",questions.length()+1).put("topic","自定义题目").put("type","解答题")
          .put("questionMarkdown",draft.optString("body")).put("questionImages",images)
          .put("answerMarkdown",ref).put("answerImages",crops[1]).put("initialStudentMarkdown",draft.optString("student")).put("studentSourceImages",crops[2]).put("referenceAvailable",true)
          .put("referencePolicy",ref.trim().isEmpty()&&crops[1].length()==0?"independent":"reference");
      questions.put(q);
      writeAtomic(manifest.getBaseFile(),bank.toString(2).getBytes(StandardCharsets.UTF_8));
      return id;
    } catch (Exception | OutOfMemoryError e) {
      for (File file : created) file.delete();
      throw e;
    } finally { for (InkView.Sheet sheet : sheets) sheet.bitmap.recycle(); }
  }
  private void failure(Throwable e){if(isDestroyed())return;setBusy();AppUi.message(this,"暂时无法完成",e.getMessage()==null?"请稍后重试":e.getMessage());}
  @Override public void onBackPressed(){if(!busy())super.onBackPressed();}
  @Override protected void onDestroy(){if(stylus!=null)stylus.detach();worker.shutdown();if(!recognizing&&!importing){clearThumbnails();photoPreview.setImageDrawable(null);if(photo!=null)photo.recycle();}super.onDestroy();}
}
