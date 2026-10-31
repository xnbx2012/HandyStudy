package com.handymath.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;

/** Dependency-free instrumentation checks for the important behavior boundaries. */
public final class MathInstrumentation extends Instrumentation {
    private boolean batchPractice,reasoningStream,miniMax,thinking,bankDelete,studyTools,xiaoBank,splitLayout,outputAnnotations,answerRetention,inputSettings,stylusInput,nestedZip,photoInput,customInput,escape,v6,v5live,v5, live, regression,v3,v4,v4live,practiceOnly;
    private String measurements="";
    private void check(boolean condition,String description) { if(!condition) throw new AssertionError(description); }
    private void rejects(RunnableWithException action,String description) throws Exception {
        boolean thrown=false; try { action.run(); } catch(Exception e) { thrown=true; } check(thrown,description);
    }
    interface RunnableWithException { void run() throws Exception; }
    @Override public void onCreate(Bundle args) { super.onCreate(args);batchPractice=args!=null&&"true".equals(args.getString("batchPractice"));reasoningStream=args!=null&&"true".equals(args.getString("reasoningStream"));miniMax=args!=null&&"true".equals(args.getString("miniMax"));thinking=args!=null&&"true".equals(args.getString("thinking"));bankDelete=args!=null&&"true".equals(args.getString("bankDelete"));studyTools=args!=null&&"true".equals(args.getString("studyTools"));xiaoBank=args!=null&&"true".equals(args.getString("xiaoBank"));splitLayout=args!=null&&"true".equals(args.getString("splitLayout"));outputAnnotations=args!=null&&"true".equals(args.getString("outputAnnotations"));answerRetention=args!=null&&"true".equals(args.getString("answerRetention"));inputSettings=args!=null&&"true".equals(args.getString("inputSettings"));stylusInput=args!=null&&"true".equals(args.getString("stylusInput"));nestedZip=args!=null&&"true".equals(args.getString("nestedZip"));photoInput=args!=null&&"true".equals(args.getString("photoInput"));customInput=args!=null&&"true".equals(args.getString("customInput"));escape=args!=null&&"true".equals(args.getString("escape"));v6=args!=null&&"true".equals(args.getString("v6"));v5live=args!=null&&"true".equals(args.getString("v5live")); v5=args!=null&&"true".equals(args.getString("v5")); live=args!=null&&"true".equals(args.getString("live")); regression=args!=null&&"true".equals(args.getString("regression")); v3=args!=null&&"true".equals(args.getString("v3"));v4live=args!=null&&"true".equals(args.getString("v4live"));v4=args!=null&&"true".equals(args.getString("v4"));practiceOnly=args!=null&&"true".equals(args.getString("practice"));start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try { if(batchPractice){BatchPracticeChecks.run(this);measurements="Bank-scoped N-in-one sequential practice, combined grading, annotated inputs and per-question persistence passed.";}else if(reasoningStream){ReasoningStreamChecks.run(this);measurements="Live reasoning stages, auto-collapse, expand, retention, rotation and stream failure checks passed.";}else if(miniMax){MiniMaxChecks.run(this);measurements="MiniMax default reasoning budget and final answer completion passed.";}else if(thinking){ThinkingChecks.run(this);measurements="Per-provider thinking effort/off settings, persistence, actual API payloads, budgets and streaming passed.";}else if(bankDelete){BankDeleteChecks.run(this);measurements="Imported bank deletion, confirmation/cancel, built-in protection, duplicate cleanup and retained history passed.";}else if(studyTools){StudyToolsChecks.run(this);measurements="Composed question upload, result display controls, read-only bank browsing, navigation and timer controls passed.";}else if(xiaoBank){XiaoBankChecks.run(getTargetContext());measurements="Xiao1000 actual ZIP imported: five banks, 1148 questions, 528 single choice, 620 multi choice, 52 chapters and valid knowledge tree.";}else if(splitLayout){SplitLayoutChecks.run(this);measurements="Long press resize, swap, cancel, bank/orientation persistence and compact command row passed.";}else if(outputAnnotations){OutputAnnotationChecks.run(this);measurements="Generated answer/hint pen overlays, separate persistence, undo/redo and layout rebuild passed.";}else if(answerRetention){AnswerRetentionChecks.run(this);measurements="Answer retention after annotations and edits, undo/redo, draft reload and explicit clear passed.";}else if(inputSettings){InputSettingsChecks.run(this);measurements="Input settings, multi finger undo/redo, configurable stylus actions and persistent favorites passed.";}else if(stylusInput){StylusChecks.run(this);measurements="Vendor routing, fallback, prediction isolation, pressure/tilt/hover, palm cancel and foreground gestures passed.";}else if(nestedZip){NestedZipChecks.run(getTargetContext());measurements="Nested ZIP folders/bundles, rollback, traversal/depth rejection and real three-bank math bundle passed.";}else if(photoInput){PhotoInputChecks.run(this);measurements="Photo picker/camera, automatic vision OCR, category isolation and initial answer flows passed.";}else if(customInput){CustomInputChecks.run(this); measurements="Custom question, editable text, rendering/export, hint and grading fixture flows passed.";}else if(escape){escapeChecks();}else if(v6){v6Checks();}else if(v5live){v5Live();}else if(v5){v5Checks();}else if(v4live){fullBankLive();}else if(v4){v4Checks();}else if(practiceOnly){practiceOnly();}else if(v3){v3Checks();} else if(regression) {inkRegression(); benchmark();} else { runChecks(); if(live) runLiveChecks(); } result.putString("stream","PASS: bank assets, grade schema, hint ladder, credentials, pen/palm input, undo/redo, draft restore, page export, safe ZIP import, review scheduling"+(live?", live Android provider + UI four hints/partial/grade":"")+"\n"+measurements); finish(Activity.RESULT_OK,result); }
        catch(Throwable e) { result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED,result); }
    }
    private void v5Live()throws Exception{
        Context c=getTargetContext();File file=new File(c.getFilesDir(),"live-provider.json");JSONObject secret;
        try{secret=new JSONObject(new String(BankRepository.read(new FileInputStream(file)),java.nio.charset.StandardCharsets.UTF_8));}finally{file.delete();}
        ProviderSettings config=new ProviderSettings();config.context=c;config.key=secret.getString("key");config.baseUrl=secret.getString("baseUrl");config.model=secret.getString("model");config.visionVerified=true;
        boolean previous=AppSettings.enabled(c,"stream",false);AppSettings.prefs(c).edit().putBoolean("stream",true).commit();
        Bitmap bitmap=Bitmap.createBitmap(800,240,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(64);canvas.drawText("Let t = x - 1",50,130,paint);
        try{BankRepository bank=new BankRepository(c,null);AtomicReference<String> stream=new AtomicReference<>("");TutorClient.Reply reply=TutorClient.ask(config,bank,bank.questions.get(4),java.util.List.of(new InkView.Sheet(bitmap,new RectF(0,0,800,240))),TutorClient.Mode.PARTIAL,0,new JSONArray(),stream::set);check(!reply.hint.isEmpty()&&!stream.get().isEmpty(),"real provider partial SSE callback");FormulaFormat.checked(reply.hint);FormulaFormat.checked(reply.feedback);check(reply.hint.contains("$$")||reply.feedback.contains("$$"),"real partial math LaTeX delimited");try(UsageLedger ledger=new UsageLedger(c)){JSONArray rows=ledger.rows(bank.id);boolean reported=false;for(int i=0;i<Math.min(rows.length(),5);i++)if(!rows.getJSONObject(i).isNull("prompt"))reported=true;check(reported,"real provider reports actual token usage");}check(!ProviderUi.balance(config).isEmpty(),"real provider account balance queried");measurements="Real API: streaming partial hint, escaped LaTeX validation, actual token usage and balance endpoint passed.\n";
        }finally{bitmap.recycle();AppSettings.prefs(c).edit().putBoolean("stream",previous).commit();}
    }

    private static class FixtureConnection extends java.net.HttpURLConnection {
        final byte[] response; final java.io.ByteArrayOutputStream sent=new java.io.ByteArrayOutputStream();
        FixtureConnection(java.net.URL url,String response){super(url);this.response=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
        public void connect(){} public void disconnect(){} public boolean usingProxy(){return false;}
        public int getResponseCode(){return 200;} public OutputStream getOutputStream(){return sent;} public InputStream getInputStream(){return new ByteArrayInputStream(response);}
    }
    private void escapeChecks() throws Exception {
        String math="$$x \\quad \\underline{\\quad} \\quad \\checkmark \\quad \\underline{\\quad} \\quad B$$";
        String raw="{\"transcription\":\""+math+"\",\"finalAnswer\":\"B\",\"choice\":\"B\",\"readable\":true,\"confidence\":0.9}";
        java.lang.reflect.Method parse=TutorClient.class.getDeclaredMethod("json",String.class);parse.setAccessible(true);
        JSONObject recognition;
        try{recognition=(JSONObject)parse.invoke(null,raw);}catch(java.lang.reflect.InvocationTargetException e){throw (Exception)e.getCause();}
        check(math.equals(recognition.getString("transcription")),"screenshot LaTeX survives recognition JSON parsing exactly");
        String commands="$$\\frac{1}{2} \\begin{aligned} \\beta \\nu \\right) \\text{结果}$$";
        String malformed="{\"feedback\":\""+commands+"\",\"score\":2,\"correct\":false}";
        check(commands.equals(TutorClient.Reply.parse(malformed,TutorClient.Mode.GRADE).feedback),"raw math control escapes preserved");
        check(commands.equals(StreamJson.field(malformed,"feedback")),"streamed math matches final math");
        String ordinary="line\n\t\"quoted\" C:\\notes\\file α";
        JSONObject valid=new JSONObject().put("feedback",commands).put("ordinary",ordinary);
        JSONObject roundtrip=ModelJson.object(valid.toString());
        check(commands.equals(roundtrip.getString("feedback"))&&ordinary.equals(roundtrip.getString("ordinary")),"valid JSON unchanged");
        check("α".equals(ModelJson.object("{\"value\":\"\\u03b1\"}").getString("value")),"Unicode escapes preserved");
        check(StreamJson.field("{\"feedback\":\"\\u03","feedback").isEmpty(),"split Unicode waits");
        rejects(()->ModelJson.object("{\"feedback\":\"unfinished"),"truncation rejected");
        Context c=getTargetContext();BankRepository bank=new BankRepository(c,null);ProviderSettings cfg=new ProviderSettings();cfg.context=c;cfg.key="fixture";cfg.visionVerified=true;
        Bitmap image=Bitmap.createBitmap(300,150,Bitmap.Config.ARGB_8888);new Canvas(image).drawColor(Color.WHITE);
        LlmTransport.ConnectionFactory old=LlmTransport.connections;java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        String grade=new JSONObject().put("score",2).put("correct",false).put("feedback","请检查选项。").put("weaknesses",new JSONArray()).toString();
        try{
            LlmTransport.connections=url->new FixtureConnection(url,new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",calls.getAndIncrement()==0?raw:grade)).put("finish_reason","stop"))).toString());
            TutorClient.Reply reply=TutorClient.ask(cfg,bank,bank.questions.get(0),java.util.List.of(new InkView.Sheet(image,new RectF(0,0,300,150))),TutorClient.Mode.GRADE,0,new JSONArray());
            check(math.equals(reply.studentWork)&&reply.score==2,"malformed recognition escapes complete the grading flow without changing work or score");
        }finally{LlmTransport.connections=old;image.recycle();}
        measurements="Screenshot recognition JSON and end-to-end grading passed.\n";
    }

    private void v6Checks() throws Exception {
        Context c=getTargetContext();
        int supplementalBanks=0,supplementalQuestions=0;
        for(BankRepository.Entry entry:BankRepository.catalog(c)){
            if(!entry.location.startsWith("@builtin/")||java.util.Set.of("wzx-basic","wzx-full","english-ii","politics").contains(entry.id))continue;
            BankRepository loaded=BankRepository.loadLocation(c,entry.location);supplementalBanks++;supplementalQuestions+=loaded.questions.size();
            for(BankRepository.Question sample:java.util.List.of(loaded.questions.get(0),loaded.questions.get(loaded.questions.size()-1))){
                check(sample.source!=null,"supplementary question keeps source metadata");
                for(String path:sample.questionImages){Bitmap bitmap=loaded.image(path);check(bitmap!=null&&bitmap.getWidth()>0,"supplementary source image decodes");bitmap.recycle();}
            }
        }
        if(supplementalBanks>0)check(supplementalBanks==44&&supplementalQuestions==17402,"complete supplementary catalog loads on Android");
        runOnMainSync(()->{
            AdaptiveGrid grid=new AdaptiveGrid(c);float density=c.getResources().getDisplayMetrics().density;
            for(int n=0;n<6;n++){android.widget.TextView tile=new android.widget.TextView(c);tile.setText("矩阵项目");grid.addView(tile);}
            int narrow=(int)(340*density),wide=(int)(700*density);
            grid.measure(android.view.View.MeasureSpec.makeMeasureSpec(narrow,1073741824),0);grid.layout(0,0,narrow,grid.getMeasuredHeight());
            check(grid.getChildAt(0).getTop()==grid.getChildAt(1).getTop()&&grid.getChildAt(2).getTop()>0,"matrix uses two columns on narrow content");
            grid.measure(android.view.View.MeasureSpec.makeMeasureSpec(wide,1073741824),0);grid.layout(0,0,wide,grid.getMeasuredHeight());
            check(grid.getChildAt(3).getTop()==0&&grid.getChildAt(4).getTop()>0,"matrix expands to four columns on wider content");
        });
        ExportFilter filter=new ExportFilter();filter.bank="fixture";filter.start="2026-10-14";filter.end="2026-10-15";
        check(filter.accepts(new JSONObject().put("bank","fixture").put("day","2026-10-14")),"export includes start day");
        check(filter.accepts(new JSONObject().put("bank","fixture").put("day","2026-10-15")),"export includes end day");
        check(!filter.accepts(new JSONObject().put("bank","fixture").put("day","2026-10-16")),"export excludes later dates");
        check(!filter.accepts(new JSONObject().put("bank","other").put("day","2026-10-15")),"export isolates banks");
        check(!filter.accepts(new JSONObject().put("day","2026-10-15")),"scoped export excludes unassigned old logs");
        check(new ExportFilter().accepts(new JSONObject()),"all export includes legacy logs");
        BankRepository bank=new BankRepository(c,null);BankRepository.Question q=bank.questions.get(0);
        MainActivity main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("bankLocation",bank.location()).putExtra("questionId",q.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Thread.sleep(1600);
        AnnotatedPaper paper=(AnnotatedPaper)field(main,"paper");InkView answer=(InkView)field(main,"ink");
        AtomicReference<java.util.List<Bitmap>> pictures=new AtomicReference<>();
        runOnMainSync(()->{try{
            paper.ink.clear();answer.clear();
            event(paper.ink,MotionEvent.TOOL_TYPE_STYLUS,0,40,30);event(paper.ink,MotionEvent.TOOL_TYPE_STYLUS,2,100,60);event(paper.ink,MotionEvent.TOOL_TYPE_STYLUS,1,100,60);
            check(field(main,"activeInk")==paper.ink,"shared toolbar targets last touched paper");
            event(answer,MotionEvent.TOOL_TYPE_STYLUS,0,100,120);event(answer,MotionEvent.TOOL_TYPE_STYLUS,2,180,180);event(answer,MotionEvent.TOOL_TYPE_STYLUS,1,180,180);
            check(field(main,"activeInk")==answer,"shared toolbar targets last touched answer");pictures.set(paper.snapshot());
        }catch(Exception e){throw new RuntimeException(e);}});
        check(!pictures.get().isEmpty(),"annotated paper snapshot available");
        java.util.List<InkView.Sheet> sheets=answer.freeze().export(),notes=paper.ink.freeze().export();for(InkView.Sheet note:notes)note.annotation=true;sheets.addAll(notes);
        ProviderSettings cfg=new ProviderSettings();cfg.context=c;cfg.id="fixture-v6";cfg.key="fixture";cfg.visionVerified=true;
        LlmTransport.ConnectionFactory previous=LlmTransport.connections;
        java.util.List<FixtureConnection> calls=new java.util.ArrayList<>();
        JSONObject common=new JSONObject().put("score",2).put("correct",false).put("feedback","请检查符号。").put("weaknesses",new JSONArray().put("符号"))
            .put("transcription","B").put("choice","B").put("finalAnswer","B").put("readable",true).put("confidence",.99)
            .put("knowledgePoints",new JSONArray().put("极限")).put("examPoints",new JSONArray().put("计算")).put("questionType","选择题").put("difficulty",2);
        String activeProfile=c.getSharedPreferences("provider-profiles",0).getString("active","legacy");
        try {
            LlmTransport.connections=url->{FixtureConnection f=new FixtureConnection(url,new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",common.toString())).put("finish_reason","stop"))).toString());calls.add(f);return f;};
            TutorClient.ask(cfg,bank,q,sheets,TutorClient.Mode.GRADE,0,new JSONArray(),pictures.get(),null);
            check(calls.size()>=2,"independent recognition and grading requests");
            String recognize=calls.get(0).sent.toString("UTF-8");
            check(recognize.contains("学生题面批注")&&recognize.contains("学生答题纸"),"recognizer receives annotations and answer sheets");
            boolean combined=false;for(FixtureConnection call:calls)if(call.sent.toString("UTF-8").contains("带学生批注的完整题面"))combined=true;
            check(combined,"grader receives composited annotated paper");
            cfg.name="批注集成测试";cfg.save(c);
            int before=calls.size();
            runOnMainSync(()->{try{answer.clear();java.lang.reflect.Method request=MainActivity.class.getDeclaredMethod("request",TutorClient.Mode.class);request.setAccessible(true);request.invoke(main,TutorClient.Mode.GRADE);}catch(Exception e){throw new RuntimeException(e);}});
            for(int n=0;n<200&&(boolean)field(main,"busy");n++)Thread.sleep(50);
            waitForIdleSync();check(calls.size()>before&&field(main,"lastResult")!=null,"annotation-only submission goes through full UI grading flow");
            try(OutputStream out=new FileOutputStream(new File(c.getCacheDir(),"annotated-paper-v6.png"))){pictures.get().get(0).compress(Bitmap.CompressFormat.PNG,100,out);}

        } finally {LlmTransport.connections=previous;ProviderSettings.activate(c,activeProfile);ProviderSettings.delete(c,cfg.id);for(InkView.Sheet sheet:sheets)sheet.bitmap.recycle();for(Bitmap bitmap:pictures.get())bitmap.recycle();}
        runOnMainSync(()->main.finish());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();filter.bank="no-matching-bank";LearningExport.write(c,bytes,filter);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {java.util.zip.ZipEntry entry;boolean learning=false;while((entry=zip.getNextEntry())!=null){check(!entry.getName().startsWith("llm-logs/"),"unrelated logs excluded");if(entry.getName().equals("learning.json")){JSONObject data=new JSONObject(new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));check(data.getJSONArray("attempts").length()==0&&data.getJSONArray("modelUsage").length()==0,"empty filtered export isolates records");learning=true;}}check(learning,"filtered zip contains learning metadata");}
        StatisticsActivity stats=(StatisticsActivity)startActivitySync(new Intent(c,StatisticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();capture("statistics-v6.png");runOnMainSync(()->stats.finish());
        measurements="v6: inclusive date/bank filtering, scoped ZIP, shared toolbar focus, annotation-only recognition and composited paper grading passed.\n";
    }

    private void v5Checks() throws Exception {
        Context c=getTargetContext();
        BankRepository math=BankRepository.loadLocation(c,"@builtin/wzx-full"), english=BankRepository.loadLocation(c,"@builtin/english-ii"),politics=BankRepository.loadLocation(c,"@builtin/politics");
        check(math.questions.size()==330&&english.questions.size()==99&&politics.questions.size()==718,"all subject banks load with complete asset validation");
        check(english.subject.equals("英语")&&politics.subject.equals("政治")&&!math.knowledge.nodes.isEmpty(),"subjects and hierarchy available");
        for(BankRepository.Question q:politics.questions)check(q.referencePolicy.equals("independent"),"politics does not claim absent official answers");
        StudyFilter filter=new StudyFilter();filter.knowledge=math.knowledge.children(math.knowledge.children("").get(0).id).get(0).id;int count=0;for(BankRepository.Question q:math.questions)if(filter.matches(math,q))count++;check(count>0&&count<330,"descendant knowledge filter");
        rejects(()->FormulaFormat.checked("利用 x/(1+x/2) 计算"),"naked nested fraction rejected");
        String partial=new JSONObject().put("hint","先计算 $$\\frac{x}{1+\\frac{x}{2}}$$。").put("partialValid",true).put("feedback","已做部分正确").toString();
        TutorClient.Reply reply=TutorClient.Reply.parse(partial,TutorClient.Mode.PARTIAL);check(reply.hint.contains("\\frac"),"partial hint retains LaTeX");
        String prefix=new JSONObject().put("hint","继续思考 $$x^2$$").toString();check(StreamJson.markdown(prefix.substring(0,prefix.length()-1),TutorClient.Mode.HINT).contains("继续思考"),"incremental JSON hint decoding");
        final String profile="fixture-v5";String active=c.getSharedPreferences("provider-profiles",0).getString("active","legacy");
        ProviderSettings cfg=new ProviderSettings();cfg.context=c;cfg.id=profile;cfg.name="测试提供商";cfg.key="test-key";cfg.visionVerified=true;cfg.inputPrice=2;cfg.outputPrice=4;cfg.cachePrice=.2;cfg.priceConfigured=true;cfg.save(c);
        check(ProviderSettings.load(c).key.equals("test-key"),"encrypted profile round trip");check(!ProviderSettings.storage(c,profile).getAll().toString().contains("test-key"),"key absent from plain preferences");
        LlmTransport.ConnectionFactory old=LlmTransport.connections;
        JSONObject usage=new JSONObject().put("prompt_tokens",100).put("completion_tokens",20).put("prompt_cache_hit_tokens",40);
        String chunk=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",partial)).put("finish_reason","stop"))).put("usage",usage).toString();
        try {
            UsageLedger.scope.set(new String[]{math.id,math.questions.get(0).id});
            LlmTransport.connections=url->new FixtureConnection(url,"data: "+chunk+"\n\ndata: [DONE]\n\n");
            AtomicReference<String> streamed=new AtomicReference<>();String result=LlmTransport.call(cfg,new JSONObject().put("stream",true),streamed::set);
            check(result.equals(partial)&&streamed.get().equals(partial),"SSE content and usage final chunk decoded");
            try(UsageLedger ledger=new UsageLedger(c)){JSONObject row=ledger.rows(math.id).getJSONObject(0);check(row.getLong("prompt")==100&&row.getLong("cache")==40&&Math.abs(row.getDouble("cost")-.000208)<.00000001,"actual tokens cache cost snapshot");}
            LlmTransport.connections=url->new FixtureConnection(url,"data: {\"choices\":[{\"delta\":{\"content\":\"unfinished\"}}]}\n\n");rejects(()->LlmTransport.call(cfg,new JSONObject().put("stream",true),null),"truncated stream never accepted");
            LlmTransport.connections=url->new FixtureConnection(url,"{\"balance_infos\":[{\"currency\":\"CNY\",\"total_balance\":\"12.34\"}]}");check(ProviderUi.balance(cfg).contains("12.34"),"provider balance is parsed not estimated");
        } finally {LlmTransport.connections=old;UsageLedger.scope.remove();ProviderSettings.activate(c,active);ProviderSettings.delete(c,profile);}
        MainActivity main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("bankLocation",math.location()).putExtra("questionId",math.questions.get(0).id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{try{java.lang.reflect.Field stop=MainActivity.class.getDeclaredField("answerStopped");stop.setAccessible(true);stop.setBoolean(main,false);invoke(main,"startClock");}catch(Exception e){throw new RuntimeException(e);}});
        Thread.sleep(250);runOnMainSync(()->{try{invoke(main,"stopClock");java.lang.reflect.Field stop=MainActivity.class.getDeclaredField("answerStopped");stop.setAccessible(true);stop.setBoolean(main,true);invoke(main,"startClock");}catch(Exception e){throw new RuntimeException(e);}});
        check((long)field(main,"clockStart")==-1,"submitted clock cannot restart");check((long)field(main,"stayStart")>=0,"residence continues after solving clock pauses");
        InkView ink=(InkView)field(main,"ink");runOnMainSync(()->{ink.clear();ink.shapes=true;ink.shapeDistance=15;ink.shapeHoldMillis=200;event(ink,MotionEvent.TOOL_TYPE_STYLUS,0,100,100);for(int i=1;i<=20;i++)event(ink,MotionEvent.TOOL_TYPE_STYLUS,2,100+i*10,100+(i%2));event(ink,MotionEvent.TOOL_TYPE_STYLUS,2,302,102);});Thread.sleep(350);waitForIdleSync();check((boolean)field(ink,"shapeApplied"),"jitter-tolerant shape triggers before pen up");runOnMainSync(()->event(ink,MotionEvent.TOOL_TYPE_STYLUS,1,302,102));
        check((long)field(main,"clockStart")==-1,"editing does not resume stopped clock");
        AnnotatedPaper paper=(AnnotatedPaper)field(main,"paper");runOnMainSync(()->{event(paper.ink,MotionEvent.TOOL_TYPE_STYLUS,0,60,30);event(paper.ink,MotionEvent.TOOL_TYPE_STYLUS,1,160,35);});check(paper.ink.hasInk(),"question annotation accepts stylus vector ink");capture("question-v5.png");
        String savedActive=c.getSharedPreferences("provider-profiles",0).getString("active","legacy");boolean previousStream=AppSettings.enabled(c,"stream",false);
        ProviderSettings ui=new ProviderSettings();ui.id="ui-fixture-v5";ui.name="流式测试";ui.key="fixture";ui.context=c;ui.visionVerified=true;ui.save(c);AppSettings.prefs(c).edit().putBoolean("stream",true).commit();
        JSONObject common=new JSONObject().put("score",2).put("correct",false).put("partialValid",false).put("feedback","**符号有误**，请检查 $$\\frac{x}{1+\\frac{x}{2}}$$。").put("hint","下一步先通分 $$\\frac{x}{1+\\frac{x}{2}}$$。").put("weaknesses",new JSONArray().put("符号判断")).put("transcription","B").put("choice","B").put("finalAnswer","B").put("readable",true).put("confidence",.99).put("knowledgePoints",new JSONArray().put("极限")).put("examPoints",new JSONArray().put("等价替换")).put("questionType","选择题").put("difficulty",2);
        String complete=common.toString();String sseFirst="data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",complete)))).toString()+"\n\n";
        String sseLast="data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject()).put("finish_reason","stop"))).put("usage",usage).toString()+"\n\ndata: [DONE]\n\n";
        try {
            LlmTransport.connections=url->new FixtureConnection(url,sseFirst+sseLast){public InputStream getInputStream(){return new InputStream(){final byte[] bytes=(sseFirst+sseLast).getBytes(java.nio.charset.StandardCharsets.UTF_8);final int boundary=sseFirst.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;int pos=0;boolean delayed=false;public int read(){if(pos==boundary&&!delayed){delayed=true;try{Thread.sleep(1200);}catch(Exception ignored){}}return pos<bytes.length?bytes[pos++]&255:-1;}public int read(byte[] b,int off,int len){if(pos==boundary&&!delayed){delayed=true;try{Thread.sleep(1200);}catch(Exception ignored){}}if(pos>=bytes.length)return -1;int n=Math.min(len,(pos<boundary?boundary:bytes.length)-pos);System.arraycopy(bytes,pos,b,off,n);pos+=n;return n;}};}};
            runOnMainSync(()->{try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("answerStopped");f.setAccessible(true);f.setBoolean(main,false);invoke(main,"startClock");java.lang.reflect.Method request=MainActivity.class.getDeclaredMethod("request",TutorClient.Mode.class);request.setAccessible(true);request.invoke(main,TutorClient.Mode.GRADE);}catch(Exception e){throw new RuntimeException(e);}});
            check((boolean)field(main,"answerStopped")&&(long)field(main,"clockStart")==-1,"actual submit immediately pauses solving timer");check(ink.locked,"canvas locked before first model text");
            long deadline=SystemClock.uptimeMillis()+12000;while(!(boolean)field(main,"streamStarted")&&(boolean)field(main,"busy")&&SystemClock.uptimeMillis()<deadline)Thread.sleep(40);
            waitForIdleSync();check((boolean)field(main,"streamStarted")&&(boolean)field(main,"busy"),"first visible stream text arrives before completion");check(field(main,"loadingDialog")==null&&ink.locked,"first visible character hides modal but canvas remains locked");
            MathView liveView=(MathView)field(main,"hintText");Thread.sleep(200);check("true".equals(evaluate(liveView,"document.querySelectorAll('.katex').length>0 && document.querySelectorAll('.stream-spinner').length===1")),"stream live LaTeX and trailing spinner rendered");capture("stream-v5.png");
            while((boolean)field(main,"busy")&&SystemClock.uptimeMillis()<deadline)Thread.sleep(100);waitForIdleSync();check(((String)field(main,"lastError")).isEmpty(),"fixture grade completed: "+field(main,"lastError"));check((long)field(main,"clockStart")==-1&&!ink.locked,"completion unlocks canvas while solving timer remains paused");
            runOnMainSync(()->{try{java.lang.reflect.Method request=MainActivity.class.getDeclaredMethod("request",TutorClient.Mode.class);request.setAccessible(true);request.invoke(main,TutorClient.Mode.PARTIAL);}catch(Exception e){throw new RuntimeException(e);}});
            deadline=SystemClock.uptimeMillis()+12000;while((boolean)field(main,"busy")&&SystemClock.uptimeMillis()<deadline)Thread.sleep(100);waitForIdleSync();check(((String)field(main,"lastError")).isEmpty(),"partial fixture finishes");Thread.sleep(200);check("true".equals(evaluate(liveView,"document.querySelectorAll('.katex').length>0 && document.querySelectorAll('.stream-spinner').length===0")),"partial hint final LaTeX rendered with spinner removed");
        } finally {LlmTransport.connections=old;AppSettings.prefs(c).edit().putBoolean("stream",previousStream).commit();ProviderSettings.activate(c,savedActive);ProviderSettings.delete(c,ui.id);}
        String beforeAnnotation=((AnnotatedPaper)field(main,"paper")).ink.freeze().json(0,0,1).getJSONArray("strokes").toString();
        runOnMainSync(()->main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(700);waitForIdleSync();check(beforeAnnotation.equals(((AnnotatedPaper)field(main,"paper")).ink.freeze().json(0,0,1).getJSONArray("strokes").toString()),"source vector coordinates survive rotation");MathView rotated=(MathView)((AnnotatedPaper)field(main,"paper")).body.getChildAt(1);boolean ready=false;for(int j=0;j<40;j++){if("true".equals(evaluate(rotated,"document.querySelectorAll('.katex').length>0"))){ready=true;break;}Thread.sleep(100);}check(ready,"rotated question formulas render");capture("landscape-v5.png");
        runOnMainSync(()->main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(600);waitForIdleSync();
        android.content.SharedPreferences plan=LearningPlan.prefs(c);java.util.Map<String,?> planSaved=new java.util.HashMap<>(plan.getAll());
        try {plan.edit().putBoolean("enabled",true).putString("bank",english.location()).putString("type","writing").putString("knowledge","").commit();int next=LearningPlan.next(c,english);check(next<0||english.questions.get(next).type.equals("writing"),"plan queue respects question type");}finally{restorePrefs(plan,planSaved);}
        runOnMainSync(main::finish);waitForIdleSync();
        StatisticsActivity stats=(StatisticsActivity)startActivitySync(new Intent(c,StatisticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(stats.lastError.isEmpty(),"all-data statistics renders");runOnMainSync(()->((android.widget.Spinner)stats.content.getChildAt(0)).setSelection(1));waitForIdleSync();check(stats.lastError.isEmpty()&&!stats.scope.isEmpty(),"bank-scoped charts render");capture("statistics-v5.png");runOnMainSync(stats::finish);waitForIdleSync();
        KnowledgeExplorerActivity graph=(KnowledgeExplorerActivity)startActivitySync(new Intent(c,KnowledgeExplorerActivity.class).putExtra("bankLocation",math.location()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(graph.lastError.isEmpty(),"knowledge mindmap renders");capture("knowledge-v5.png");runOnMainSync(graph::finish);waitForIdleSync();
        measurements="v0.5: 1147 built-in units, ontologies, multi-provider encryption, tokens/cache/cost/balance, SSE truncation, partial LaTeX, paused solving/residence clock, jitter hold shape, source annotations and scoped statistics passed.\n";
    }

    private void fullBankLive()throws Exception {
        Context c=getTargetContext();File file=new File(c.getFilesDir(),"live-provider.json");JSONObject secret;
        try{secret=new JSONObject(new String(BankRepository.read(new FileInputStream(file)),java.nio.charset.StandardCharsets.UTF_8));}finally{file.delete();}
        ProviderSettings config=new ProviderSettings();config.key=secret.getString("key");config.baseUrl=secret.getString("baseUrl");config.model=secret.getString("model");config.context=c;TutorClient.verifyVision(config);
        BankRepository bank=BankRepository.loadLocation(c,"@builtin/wzx-full");Bitmap paper=Bitmap.createBitmap(700,240,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(paper);Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(100);java.util.List<InkView.Sheet> sheets=java.util.List.of(new InkView.Sheet(paper,new RectF(0,0,700,240)));
        try{canvas.drawColor(Color.WHITE);canvas.drawText("B",100,150,paint);TutorClient.Reply wrong=TutorClient.ask(config,bank,bank.questions.get(0),sheets,TutorClient.Mode.GRADE,0,new JSONArray());check(!wrong.correct&&wrong.score<10&&!wrong.feedback.isEmpty(),"full Markdown bank wrong choice rejected with personal feedback");
            canvas.drawColor(Color.WHITE);canvas.drawText("C",100,150,paint);TutorClient.Reply correct=TutorClient.ask(config,bank,bank.questions.get(0),sheets,TutorClient.Mode.GRADE,0,new JSONArray());check(correct.correct&&correct.score==10,"full Markdown bank correct choice accepted");
            BankRepository.Question supplemented=null;for(BankRepository.Question q:bank.questions)if(q.id.equals("wzx-ch9-solution-2"))supplemented=q;
            TutorClient.Reply hint=TutorClient.ask(config,bank,supplemented,java.util.List.of(),TutorClient.Mode.HINT,0,new JSONArray());check(!hint.hint.isEmpty(),"supplemented Markdown solution generates formatted hint");FormulaFormat.checked(hint.hint);
        }finally{paper.recycle();}
        measurements="Real API: full OCR Markdown question B/C grading and supplemented solution hint passed.\n";
    }
    private void v4Checks() throws Exception {
        Context c=getTargetContext();BankRepository full=BankRepository.loadLocation(c,"@builtin/wzx-full");check(full.questions.size()==330,"330 complete built-in questions");check(full.location().equals("@builtin/wzx-full"),"asset bank stable location");check(c.getAssets().list("banks/wzx-full/sources").length==10,"all ten original OCR documents bundled");for(String name:c.getAssets().list("banks/wzx-full/sources"))check(BankRepository.read(c.getAssets().open("banks/wzx-full/sources/"+name)).length>1000,"Unicode original document opens: "+name);for(BankRepository.Question q:full.questions)check(q.referenceAvailable,"all references available or supplemented");
        check(BankRepository.catalog(c).get(0).id.equals("wzx-full"),"complete bank first on home");rejects(()->BankRepository.loadLocation(c,"../../bad"),"bank traversal rejected");rejects(()->LearningPlan.parseIntervals("1,1,3"),"non-increasing review intervals rejected");
        android.content.SharedPreferences p=LearningPlan.prefs(c),progress=c.getSharedPreferences("progress-wzx-full",0);java.util.Map<String,?> saved=new java.util.HashMap<>(p.getAll()),oldProgress=new java.util.HashMap<>(progress.getAll());
        try {
            p.edit().clear().commit();progress.edit().clear().commit();StudyStore store=new StudyStore(c,full.id);String fresh=full.questions.get(0).id,review=full.questions.get(1).id;
            store.grade(review,3,new JSONArray(),0);progress.edit().putLong(review+"-due",System.currentTimeMillis()-1000).commit();
            p.edit().putBoolean("enabled",true).putString("bank",full.location()).putInt("new",1).putInt("reviews",1).putString("intervals","1,3,7").commit();
            check(LearningPlan.next(c,full)==1,"due review precedes new questions");
            store.grade(review,10,new JSONArray(),0);check(LearningPlan.count(LearningPlan.day(c,LearningPlan.today()),true)==1,"review completion recorded");check(store.due(review)==LearningPlan.dueAfterDays(System.currentTimeMillis(),3),"successful retry uses next interval");
            check(LearningPlan.next(c,full)==0,"new question follows due review");store.grade(fresh,3,new JSONArray(),0);
            JSONObject day=LearningPlan.day(c,LearningPlan.today());check(day.optBoolean("checkedIn"),"both daily goals trigger check-in");check(LearningPlan.count(day,false)==1,"new completion recorded");check(store.due(fresh)==LearningPlan.dueAfterDays(System.currentTimeMillis(),1),"wrong answer uses first configured interval");
            store.grade(fresh,3,new JSONArray(),0);check(LearningPlan.count(LearningPlan.day(c,LearningPlan.today()),false)==1,"same-day repeats not double-counted");check(LearningPlan.next(c,full)==-1,"no forced repeat after goals met");
            p.edit().putBoolean("reminder",true).commit();day.put("checkedIn",false);p.edit().putString("day-"+LearningPlan.today(),day.toString()).commit();
            new PlanReminder().onReceive(c,new Intent("com.handymath.app.PLAN_REMINDER"));
            if(PlanReminder.allowed(c)){boolean notification=false;for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())notification|=n.getId()==73;check(notification,"opt-in local reminder notification posted");}
            day.put("checkedIn",true);p.edit().putString("day-"+LearningPlan.today(),day.toString()).commit();PlanReminder.cancelNotification(c);new PlanReminder().onReceive(c,new Intent("com.handymath.app.PLAN_REMINDER"));for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())check(n.getId()!=73,"completed day suppresses reminder");
            PlanActivity plan=(PlanActivity)startActivitySync(new Intent(c,PlanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(plan.lastError.isEmpty(),"plan settings and check-in calendar render");runOnMainSync(()->findButton(plan.content,"保存学习计划").performClick());waitForIdleSync();check(LearningPlan.count(LearningPlan.day(c,LearningPlan.today()),false)==1&&LearningPlan.count(LearningPlan.day(c,LearningPlan.today()),true)==1,"saving plan preserves today's progress");capture("plan-v4.png");runOnMainSync(()->((android.widget.ScrollView)plan.content.getParent().getParent()).fullScroll(View.FOCUS_DOWN));waitForIdleSync();capture("calendar-v4.png");runOnMainSync(plan::finish);waitForIdleSync();
            p.edit().putBoolean("enabled",false).commit();store.grade(full.questions.get(2).id,10,new JSONArray(),0);check(LearningPlan.count(LearningPlan.day(c,LearningPlan.today()),false)==1,"disabled check-ins do not count ordinary practice");
            MainActivity main=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).putExtra("bankLocation",full.location()).putExtra("questionId",fresh).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check((int)field(main,"index")==0,"full bank opens exact question");runOnMainSync(()->invoke(main,"advance"));waitForIdleSync();check((int)field(main,"index")==1,"ordinary practice ignores future review due date");runOnMainSync(main::finish);waitForIdleSync();
            StatisticsActivity stats=(StatisticsActivity)startActivitySync(new Intent(c,StatisticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();AtomicReference<StudyChart> chart=new AtomicReference<>();runOnMainSync(()->{StudyChart v=findChart(stats.content);check(v!=null,"heat chart exists");float cell=Math.min((v.getWidth()-48*v.density)/12,(v.getHeight()-35*v.density)/7);float x=(v.getWidth()-12*cell)/2+11.5f*cell,y=6.5f*cell;MotionEvent e=MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,x,y,0);v.onTouchEvent(e);e.recycle();check(v.selected==83,"tap selects last heatmap day and draws inline tooltip");chart.set(v);});waitForIdleSync();capture("tooltip-v4.png");runOnMainSync(stats::finish);waitForIdleSync();
        } finally {restorePrefs(p,saved);restorePrefs(progress,oldProgress);PlanReminder.cancelNotification(c);PlanReminder.schedule(c);}
        v3Checks();measurements+="v0.4: full OCR bank, plan queue, distinct check-ins, configurable review stages, disabled ordinary practice, reminder suppression and inline tooltip passed\n";
    }
    private void invoke(Object object,String name){try{java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);}catch(Exception e){throw new RuntimeException(e);}}
    private android.widget.Button findButton(View v,String text){if(v instanceof android.widget.Button&&text.contentEquals(((android.widget.Button)v).getText()))return (android.widget.Button)v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){android.widget.Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
    private StudyChart findChart(View v){if(v instanceof StudyChart)return (StudyChart)v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){StudyChart found=findChart(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private void restorePrefs(android.content.SharedPreferences p,java.util.Map<String,?> data){android.content.SharedPreferences.Editor e=p.edit().clear();for(java.util.Map.Entry<String,?> item:data.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);}e.commit();}
    private void practiceOnly()throws Exception{Context c=getTargetContext();long id;try(LearningDb db=new LearningDb(c)){JSONArray a=db.attempts(false);check(a.length()>0,"existing real provider attempt required");id=a.getJSONObject(0).getLong("id");}PracticeActivity practice=(PracticeActivity)startActivitySync(new Intent(c,PracticeActivity.class).putExtra("attemptId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync(()->((android.widget.Button)practice.panel.getChildAt(practice.panel.getChildCount()-1)).performClick());long deadline=SystemClock.uptimeMillis()+240000;while((boolean)field(practice,"busy")&&SystemClock.uptimeMillis()<deadline)Thread.sleep(200);waitForIdleSync();check(!(boolean)field(practice,"busy"),"generation completes");check(practice.lastError.isEmpty(),"generation error: "+practice.lastError);BankRepository bank=new BankRepository(c,new File(c.getFilesDir(),"banks/smart-practice"));check(bank.questions.size()>0&&!bank.questions.get(bank.questions.size()-1).answerMarkdown.isEmpty(),"practice bank saved with solution");runOnMainSync(practice::finish);waitForIdleSync();measurements="Real provider: targeted solution-type practice generated, audited and saved.\n";}
    private void v3Checks()throws Exception{
      check(!TutorClient.readable(new JSONObject().put("readable",true).put("transcription","C").put("confidence",2)),"invalid OCR confidence rejected");
      Context c=getTargetContext();pressureLatchChecks(c);check(FormulaFormat.checked("先算 $x^2$，再看 $$\\frac{1}{x}$$").contains("$$x^2$$"),"normalize inline formulas");rejects(()->FormulaFormat.checked("裸公式 \\frac{1}{x}"),"bare LaTeX rejected");
      JSONObject tags=new JSONObject().put("knowledgePoints",new JSONArray().put("极限")).put("examPoints",new JSONArray().put("等价无穷小")).put("questionType","解答题").put("difficulty",2);TutorClient.validateTags(tags);rejects(()->TutorClient.validateTags(new JSONObject(tags.toString()).put("difficulty",0)),"invalid classification rejected");
      AtomicReference<InkView.Document> document=new AtomicReference<>();AtomicReference<Throwable> err=new AtomicReference<>();runOnMainSync(()->{try{InkView ink=new InkView(c,new StylusAdapter.Standard(false));ink.layout(0,0,1000,1000);ink.pressure=false;event(ink,MotionEvent.TOOL_TYPE_STYLUS,0,200,200);event(ink,MotionEvent.TOOL_TYPE_STYLUS,2,300,300);event(ink,MotionEvent.TOOL_TYPE_STYLUS,1,400,400);document.set(ink.freeze());int count=ink.strokes.size();ink.locked=true;event(ink,MotionEvent.TOOL_TYPE_STYLUS,0,500,500);event(ink,MotionEvent.TOOL_TYPE_STYLUS,1,600,600);check(ink.strokes.size()==count,"loading lock prevents writing");ink.locked=false;ink.pressureEraser=true;ink.pressureThreshold=.5f;event(ink,MotionEvent.TOOL_TYPE_STYLUS,0,200,200);event(ink,MotionEvent.TOOL_TYPE_STYLUS,1,300,300);check(ink.strokes.get(ink.strokes.size()-1).erase,"pressure activates eraser");Bitmap erased=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);ink.draw(new Canvas(erased));check(Color.red(erased.getPixel(250,250))>200,"vector eraser clears earlier path");erased.recycle();InkView zoomed=new InkView(c,new StylusAdapter.Standard(false));zoomed.layout(0,0,1000,1000);zoomed.zoom=5;zoomed.penWidth=6;event(zoomed,MotionEvent.TOOL_TYPE_STYLUS,0,300,300);event(zoomed,MotionEvent.TOOL_TYPE_STYLUS,1,300,300);Bitmap magnified=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);zoomed.draw(new Canvas(magnified));int blurred=0;for(int x=260;x<340;x++){int red=Color.red(magnified.getPixel(x,300));if(red>80&&red<245)blurred++;}check(blurred<=4,"500% vector edges must stay sharp: "+blurred);magnified.recycle();InkView.Stroke shape=new InkView.Stroke();for(int i=0;i<=30;i++)shape.dots.add(new InkView.Dot(100+i*5,100+i*2,.5f));ShapeSnap.apply(shape);check(shape.dots.size()==2,"hold shape recognizes straight line");}catch(Throwable t){err.set(t);}});if(err.get()!=null)throw new AssertionError(err.get());
      try(LearningDb db=new LearningDb(c)){long beforeMs=db.questionTime("v3-test","q1");int beforeAttempts=db.daily(1).get(0).attempts,beforeWrong=db.attempts(true).length(),beforeCount=db.daily(1).get(0).count;db.time("v3-test","q1",System.currentTimeMillis(),40000);db.time("v3-test","q2",System.currentTimeMillis(),60000);check(db.questionTime("v3-test","q1")==beforeMs+40000,"per-question time recorded");BankRepository bank=new BankRepository(c,null);TutorClient.Reply r=TutorClient.Reply.parse("{\"score\":3,\"correct\":false,\"feedback\":\"符号错误\",\"weaknesses\":[\"极限符号\"]}",TutorClient.Mode.GRADE);r.tags=tags;r.studentWork="$$4/\\pi^2$$";db.grade(c,bank,bank.questions.get(4),r,document.get(),0);db.grade(c,bank,bank.questions.get(4),r,document.get(),0);check(db.daily(1).get(0).count<=beforeCount+1,"daily completed questions deduplicated");check(db.daily(1).get(0).attempts==beforeAttempts+2,"repeated submissions retained");check(db.attempts(true).length()==beforeWrong+2,"wrong answer history retained");db.tags("v3-test","q1",tags);check(db.tags("v3-test","q1").getInt("difficulty")==2,"classification cached");}
      ByteArrayOutputStream archive=new ByteArrayOutputStream();LearningExport.write(c,archive);boolean json=false,png=false,vector=false;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null){json|=e.getName().equals("learning.json");vector|=e.getName().startsWith("vectors/");png|=e.getName().startsWith("answers/")&&e.getName().endsWith(".png");}}check(json&&png&&vector,"ZIP exports structured history and all writing PNGs");
      File template=new File(c.getCacheDir(),"v3-template.zip");try(OutputStream out=new FileOutputStream(template)){BankTemplate.write(out);}String old=c.getSharedPreferences("study",0).getString("bank","");BankRepository imported=BankRepository.importZip(c,android.net.Uri.fromFile(template));check(imported.questions.size()==2,"template imports Markdown and image samples");c.getSharedPreferences("study",0).edit().putString("bank",old).commit();
      c.getSharedPreferences("preferences",0).edit().putBoolean("logs",true).commit();ProviderSettings config=new ProviderSettings();config.context=c;ConversationLog.save(c,new JSONObject().put("model","test").put("messages",new JSONArray().put(TutorClient.text("只作日志测试"))),"$$x^2$$","");check(new File(c.getFilesDir(),"llm-logs").listFiles().length>0,"opt-in log saved");c.getSharedPreferences("preferences",0).edit().putBoolean("logs",false).commit();
      SettingsActivity settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(settings.content.getChildCount()>10,"separate settings page contains grouped controls");check(settings.content.getWidth()<=settings.dp(760),"settings width capped for tablet");capture("settings-v31.png");runOnMainSync(()->{try{java.lang.reflect.Method method=SettingsActivity.class.getDeclaredMethod("colorDialog",String.class,String.class,int.class,View.class);method.setAccessible(true);method.invoke(settings,"answerColor","答题笔颜色",0xff22313f,new View(settings));}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();Thread.sleep(200);capture("color-v31.png");runOnMainSync(settings::finish);waitForIdleSync();StatisticsActivity stats=(StatisticsActivity)startActivitySync(new Intent(c,StatisticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(stats.content.getChildCount()>10,"statistics charts and categories rendered");capture("statistics-v31.png");runOnMainSync(stats::finish);waitForIdleSync();
      uiChecks();inkRegression();measurements="v0.3: formula formats, ink lock, pressure eraser, shape snap, time ledger, daily dedup, wrong history, tags, ZIP/png export, template import, logs and separate settings/stats pages\n";
    }
    private void capture(String name)throws Exception {Thread.sleep(250);Bitmap image=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void pressureLatchChecks(Context c)throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try {
            InkView ink=new InkView(c,new StylusAdapter.Standard(false));ink.layout(0,0,1000,1000);ink.pressureEraser=true;ink.pressureThreshold=.8f;
            pressureEvent(ink,0,100,.4f);pressureEvent(ink,2,150,1f);pressureEvent(ink,2,200,.3f);pressureEvent(ink,1,250,.3f);
            check(ink.strokes.get(ink.strokes.size()-1).erase,"pressure eraser stays latched after pressure drops");
            pressureEvent(ink,0,300,.3f);pressureEvent(ink,1,350,.3f);check(!ink.strokes.get(ink.strokes.size()-1).erase,"new contact resets pressure eraser");
            boolean old=c.getSharedPreferences("study",0).getBoolean("finger",false);c.getSharedPreferences("study",0).edit().putBoolean("finger",true).commit();
            ink.finger=c.getSharedPreferences("study",0).getBoolean("finger",false);int before=ink.strokes.size();event(ink,MotionEvent.TOOL_TYPE_FINGER,0,400,400);event(ink,MotionEvent.TOOL_TYPE_FINGER,1,450,450);check(ink.strokes.size()==before+1,"finger writing enabled without stylus");c.getSharedPreferences("study",0).edit().putBoolean("finger",old).commit();
        }catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());
    }
    private void pressureEvent(InkView ink,int action,float x,float pressure){MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=MotionEvent.TOOL_TYPE_STYLUS;MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=100;coords.pressure=pressure;long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);ink.onTouchEvent(e);e.recycle();}
    private void runChecks() throws Exception {
        Context c=getTargetContext(); BankRepository bank=new BankRepository(c,null); check(bank.questions.size()==5,"5 sample questions");
        for(BankRepository.Question q:bank.questions) { if(!q.questionImage.isEmpty()){Bitmap b=bank.image(q.questionImage); check(b.getWidth()>0,"question bitmap"); b.recycle();} Bitmap b; for(String a:q.answers) { b=bank.image(a); b.recycle(); } }
        check(TutorClient.Reply.parse("{\"score\":10,\"correct\":true,\"feedback\":\"选项正确，方法合理\"}",TutorClient.Mode.GRADE).correct,"full score");
        rejects(()->TutorClient.Reply.parse("{\"score\":10,\"correct\":false,\"feedback\":\"错\"}",TutorClient.Mode.GRADE),"contradictory grade rejected");
        rejects(()->TutorClient.Reply.parse("{\"score\":11,\"correct\":true}",TutorClient.Mode.GRADE),"out of range rejected");
        rejects(()->TutorClient.Reply.parse("{\"score\":7,\"correct\":false}",TutorClient.Mode.GRADE),"missing error explanation rejected");
        rejects(()->TutorClient.Reply.parse("{\"hint\":\"下一步\",\"partialValid\":false}",TutorClient.Mode.PARTIAL),"invalid partial must explain error");
        check(TutorClient.recognizedChoice(new JSONObject().put("choice","").put("finalAnswer","C").put("transcription","C")).equals("C"),"plain handwritten letter is a valid choice");
        check(TutorClient.recognizedChoice(new JSONObject().put("finalAnswer","B")).equals("B"),"wrong choice remains B and cannot become reference C");
        for(int i=1;i<4;i++) check(!TutorClient.prompt(TutorClient.Mode.HINT,i,new JSONArray()).contains("只给第一步的具体"),"early hints don't reveal first step");
        check(TutorClient.prompt(TutorClient.Mode.HINT,4,new JSONArray()).contains("只给第一步的具体"),"fourth hint gives first step");
        ProviderSettings config=new ProviderSettings(); config.key="local-test-placeholder"; config.visionVerified=true; config.save(c);
        check(ProviderSettings.load(c).key.equals(config.key),"keystore roundtrip");
        check(!c.getSharedPreferences("provider",0).getString("key","").contains(config.key),"credential not plaintext");
        config.baseUrl="http://example.com"; rejects(config::validate,"HTTPS required");
        c.getSharedPreferences("provider",0).edit().clear().commit();
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{ try {
            InkView ink=new InkView(c,new StylusAdapter.Standard(false)); ink.layout(0,0,1000,1400);
            event(ink,MotionEvent.TOOL_TYPE_FINGER,MotionEvent.ACTION_DOWN,100,100); event(ink,MotionEvent.TOOL_TYPE_FINGER,MotionEvent.ACTION_UP,200,200);
            check(!ink.hasInk(),"palm/finger ignored by default");
            event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_DOWN,100,100); event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_MOVE,200,200); event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_UP,250,200);
            check(ink.hasInk(),"stylus accepted"); ink.undo(); check(!ink.hasInk(),"undo"); ink.redo(); check(ink.hasInk(),"redo");
            JSONObject data=ink.serialize(); InkView restored=new InkView(c,new StylusAdapter.Standard(false)); restored.restore(data); check(restored.hasInk(),"vector restore");
            restored.panX=-4000; restored.panY=-4000; java.util.List<InkView.Sheet> sheets=restored.freeze().export(); check(!sheets.isEmpty(),"ink outside viewport still exported"); for(InkView.Sheet sheet:sheets)sheet.bitmap.recycle();
            restored.clear(); check(!restored.hasInk(),"clear infinite canvas");
            event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_DOWN,100,100); event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_CANCEL,100,100); check(ink.strokes.size()==1,"cancel discards pending stroke");
            StudyStore store=new StudyStore(c,"test-bank"); store.save("test-q",new JSONObject().put("ink",data).put("hints",3)); check(store.load("test-q").getInt("hints")==3,"atomic draft save");
            store.grade("test-q",10,new JSONArray(),0); check(store.due("test-q")>System.currentTimeMillis()+86400000,"unassisted correct review interval");
            store.grade("test-q",4,new JSONArray().put("极限"),0); check(store.due("test-q")<System.currentTimeMillis()+86400000,"incorrect review soon");
        } catch(Throwable e) { error.set(e); } });
        if(error.get()!=null) throw new AssertionError(error.get());
        // Import rejects traversal and preserves the active bank after failure.
        File zip=new File(c.getCacheDir(),"traversal.zip");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))) { out.putNextEntry(new ZipEntry("../escape.txt")); out.write(1); out.closeEntry(); }
        String before=c.getSharedPreferences("study",0).getString("bank","");
        rejects(()->BankRepository.importZip(c,android.net.Uri.fromFile(zip)),"ZIP traversal rejected");
        check(before.equals(c.getSharedPreferences("study",0).getString("bank","")),"failed import keeps bank");
        check(!new File(c.getFilesDir(),"banks/escape.txt").exists(),"no traversal write"); zip.delete();
        File valid=new File(c.getCacheDir(),"valid-bank.zip");
        java.util.Set<String> paths=new java.util.HashSet<>(); paths.add("bank.json");
        for(BankRepository.Question q:bank.questions) { if(!q.questionImage.isEmpty())paths.add(q.questionImage); paths.addAll(q.answers); }
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(valid))) {
            for(String path:paths) { out.putNextEntry(new ZipEntry(path)); out.write(BankRepository.read(bank.open(path))); out.closeEntry(); }
        }
        BankRepository imported=BankRepository.importZip(c,android.net.Uri.fromFile(valid));
        check(imported.questions.size()==5&&imported.id.equals(bank.id),"valid bank imported");
        Bitmap importedImage=imported.image(imported.questions.get(0).questionImage); check(importedImage.getWidth()>0,"imported image loads"); importedImage.recycle();
        c.getSharedPreferences("study",0).edit().putString("bank",before).commit(); valid.delete();
        uiChecks();
    }
    private void event(InkView ink,int tool,int action,float x,float y) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0; p.toolType=tool;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords(); coords.x=x; coords.y=y; coords.pressure=.6f; coords.size=.01f;
        long now=SystemClock.uptimeMillis(); MotionEvent e=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,tool==MotionEvent.TOOL_TYPE_STYLUS?InputDevice.SOURCE_STYLUS:InputDevice.SOURCE_TOUCHSCREEN,0);
        ink.onTouchEvent(e); e.recycle();
    }
    private void inkRegression() throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try {
            InkView ink=new InkView(getTargetContext(),new StylusAdapter.Standard(false)); ink.layout(0,0,1000,1000); ink.penWidth=50;
            event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_DOWN,400,400); event(ink,MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.ACTION_UP,400,400);
            Bitmap b=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888); ink.draw(new Canvas(b));
            int left=1000,right=0,top=1000,bottom=0;
            for(int y=300;y<500;y++) for(int x=300;x<500;x++) { int pixel=b.getPixel(x,y); if(Color.red(pixel)<90&&Color.green(pixel)<90&&Color.blue(pixel)<90) {left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);} }
            float ratio=(right-left+1f)/(bottom-top+1f); b.recycle();
            check(Math.abs(ratio-1)<.1f,"round tip deformed: width/height="+ratio+" (must remain circular in a square viewport)");
            float originalUnit=ink.unit();ink.layout(0,0,1600,800);check(ink.unit()==originalUnit,"rotation preserves uniform scale");
            InkView.Dot dot=ink.strokes.get(0).dots.get(0);float px=dot.x*ink.unit()+ink.panX,py=dot.y*ink.unit()+ink.panY;
            Bitmap rotated=Bitmap.createBitmap(1600,800,Bitmap.Config.ARGB_8888);ink.draw(new Canvas(rotated));
            int minX=1600,maxX=0,minY=800,maxY=0;
            for(int y=Math.max(0,(int)py-100);y<Math.min(800,(int)py+100);y++)for(int x=Math.max(0,(int)px-100);x<Math.min(1600,(int)px+100);x++){int pixel=rotated.getPixel(x,y);if(Color.red(pixel)<90&&Color.green(pixel)<90&&Color.blue(pixel)<90){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}
            check(Math.abs((maxX-minX+1f)/(maxY-minY+1f)-1)<.1f,"rotation must not turn circular ink elliptical");rotated.recycle();
            InkView gestures=new InkView(getTargetContext(),new StylusAdapter.Standard(false));gestures.layout(0,0,1000,1000);
            event(gestures,MotionEvent.TOOL_TYPE_FINGER,MotionEvent.ACTION_DOWN,100,100);
            twoFingers(gestures,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            twoFingers(gestures,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            event(gestures,MotionEvent.TOOL_TYPE_FINGER,MotionEvent.ACTION_MOVE,100,100);
            check(Math.abs(gestures.panX)<.01f,"lifting one pinch finger must not jump canvas");
        }catch(Throwable e){error.set(e);} });
        if(error.get()!=null) throw new AssertionError(error.get());
    }
    private void twoFingers(InkView ink,int action){MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] points=new MotionEvent.PointerCoords[2];for(int i=0;i<2;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;points[i]=new MotionEvent.PointerCoords();points[i].x=100+i*200;points[i].y=100;points[i].pressure=.5f;}long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,2,properties,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);ink.onTouchEvent(e);e.recycle();}
    private Object field(Object object,String name) throws Exception { java.lang.reflect.Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private String evaluate(android.webkit.WebView web,String javascript)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript(javascript,v->{value.set(v);done.countDown();}));
        check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"local renderer callback");return value.get();
    }
    private void uiChecks()throws Exception{
        Context c=getTargetContext();MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check((boolean)field(activity,"home"),"startup must show library home");
        java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("openBank",String.class);open.setAccessible(true);
        runOnMainSync(()->{try{open.invoke(activity,"");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        runOnMainSync(()->{c.getSharedPreferences("preferences",0).edit().putBoolean("timer",false).commit();try{java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateStatus");update.setAccessible(true);update.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});check(!((android.widget.TextView)field(activity,"status")).getText().toString().contains("用时")&&(long)field(activity,"clockStart")>=0,"hidden timer still records study time");c.getSharedPreferences("preferences",0).edit().putBoolean("timer",true).commit();
        InkView ink=(InkView)field(activity,"ink");android.widget.ScrollView reading=(android.widget.ScrollView)field(activity,"studyScroll");
        if(c.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE){float proportion=ink.getWidth()/(float)(ink.getWidth()+reading.getWidth());check(Math.abs(proportion-.6)<.03,"landscape canvas takes 60%, got "+proportion);}
        java.lang.reflect.Field index=MainActivity.class.getDeclaredField("index");index.setAccessible(true);java.lang.reflect.Method load=MainActivity.class.getDeclaredMethod("loadQuestion");load.setAccessible(true);
        runOnMainSync(()->{try{index.setInt(activity,4);load.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        android.widget.LinearLayout box=(android.widget.LinearLayout)field(activity,"questionBox");MathView math=(MathView)box.getChildAt(1);
        boolean rendered=false;for(int i=0;i<30;i++){if(!"0".equals(evaluate(math,"document.querySelectorAll('.katex').length"))&&"function".equals(new org.json.JSONArray("["+evaluate(math,"typeof setMarkdown")+"]").getString(0))){rendered=true;break;}Thread.sleep(100);}
        check(rendered,"packaged KaTeX renders limit题面 offline");
        runOnMainSync(()->math.setMarkdown("**Markdown** $x^2$ <script>window.UNSAFE=true</script>"));Thread.sleep(100);
        check("true".equals(evaluate(math,"document.querySelectorAll('strong').length>0 && document.querySelectorAll('.katex').length>0 && window.UNSAFE!==true && document.getElementById('content').querySelectorAll('script').length===0")),"Markdown + inline LaTeX + sanitized HTML");
        runOnMainSync(()->math.setMarkdown("短公式 $$x^2$$，长公式 $$\n\\frac{1}{x}\n$$"));Thread.sleep(100);check("true".equals(evaluate(math,"document.querySelectorAll('.katex').length===2 && document.querySelectorAll('.katex-display').length===1")),"double-dollar inline and block rendering");
        runOnMainSync(()->math.setMarkdown(((BankRepository)quietField(activity,"bank")).questions.get(4).questionMarkdown));
        runOnMainSync(activity::finish);waitForIdleSync();
    }
    private Object quietField(Object target,String name){try{return field(target,name);}catch(Exception e){throw new RuntimeException(e);}}
    private void benchmark()throws Exception{
        AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{
            LegacyInkView old=new LegacyInkView(getTargetContext(),new StylusAdapter.Standard(false));old.layout(0,0,1000,1000);
            InkView updated=new InkView(getTargetContext(),new StylusAdapter.Standard(false));updated.layout(0,0,1000,1000);
            JSONArray strokes=new JSONArray();
            for(int i=0;i<100;i++){JSONArray dots=new JSONArray();for(int j=0;j<500;j++)dots.put(new JSONArray().put(110+j*.6).put(110+i*2+(j%17)*.15).put(.6));strokes.put(new JSONObject().put("page",0).put("color",0xff22313f).put("erase",false).put("width",3).put("dots",dots));}
            JSONObject data=new JSONObject().put("version",2).put("pages",1).put("page",0).put("strokes",strokes);old.restore(data);updated.restore(data);
            Bitmap bitmap=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);old.draw(canvas);updated.draw(canvas);
            long t=System.nanoTime();for(int i=0;i<20;i++)old.draw(canvas);double before=(System.nanoTime()-t)/20000000.0;
            t=System.nanoTime();for(int i=0;i<20;i++)updated.draw(canvas);double after=(System.nanoTime()-t)/20000000.0;
            measurements=String.format(java.util.Locale.US,"50,000 samples, warmed software draw: old %.2f ms/frame; new %.2f ms/frame; visible tiles %d\n",before,after,updated.tilesPaintedLastFrame);
            check(after<before*.5,"cached draw must eliminate historical replay: "+measurements);bitmap.recycle();
        }catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());
    }
    private void runLiveChecks() throws Exception {
        Context c=getTargetContext(); File file=new File(c.getFilesDir(),"live-provider.json");
        JSONObject secret;
        try { secret=new JSONObject(new String(BankRepository.read(new FileInputStream(file)),java.nio.charset.StandardCharsets.UTF_8)); }
        finally { file.delete(); }
        ProviderSettings config=new ProviderSettings(); config.key=secret.getString("key"); config.baseUrl=secret.getString("baseUrl"); config.model=secret.getString("model");
        config.context=c; c.getSharedPreferences("preferences",0).edit().putBoolean("logs",true).commit(); TutorClient.verifyVision(config); config.save(c);
        new StudyStore(c,"wzx-basic").save("wzx-c1-1",new JSONObject());
        c.getSharedPreferences("study",0).edit().putString("bank","").putInt("index-wzx-basic",0).commit();
        MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync(); java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("openBank",String.class);open.setAccessible(true);runOnMainSync(()->{try{open.invoke(activity,"");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        // Drive the actual Activity buttons, including async requests and persisted history.
        for(int i=1;i<=4;i++) { clickMode(activity,TutorClient.Mode.HINT); check((int)field(activity,"hints")==i,"live UI hint "+i); }
        InkView ink=(InkView)field(activity,"ink");
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{ try {
            ink.clear(); float sx=ink.unit(),sy=ink.unit();
            float[][] points={{140,280},{140,155},{180,155},{208,171},{209,190},{185,210},{140,213},{188,213},{216,233},{217,252},{196,278},{140,280}};
            for(int i=0;i<points.length;i++) event(ink,MotionEvent.TOOL_TYPE_STYLUS,i==0?MotionEvent.ACTION_DOWN:i==points.length-1?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,points[i][0]*sx,points[i][1]*sy);
        }catch(Throwable e){error.set(e);} });
        if(error.get()!=null) throw new AssertionError(error.get());
        clickMode(activity,TutorClient.Mode.PARTIAL);
        JSONArray h=(JSONArray)field(activity,"history"); check(h.length()==5,"partial history appended"); check(!h.getJSONObject(4).getBoolean("partialValid"),"partial wrong choice detected");
        clickMode(activity,TutorClient.Mode.GRADE); JSONObject grade=(JSONObject)field(activity,"lastResult"); check(grade!=null&&!grade.getBoolean("correct")&&grade.getDouble("score")<10,"UI wrong grade");
        runOnMainSync(()->{ ink.clear(); float sx=ink.unit(),sy=ink.unit(); float[][] points={{235,180},{216,163},{182,154},{153,170},{134,203},{131,235},{140,265},{163,283},{195,285},{219,274},{234,262}};
            for(int i=0;i<points.length;i++) event(ink,MotionEvent.TOOL_TYPE_STYLUS,i==0?MotionEvent.ACTION_DOWN:i==points.length-1?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,points[i][0]*sx,points[i][1]*sy);
        });
        clickMode(activity,TutorClient.Mode.GRADE); grade=(JSONObject)field(activity,"lastResult"); check(grade.getBoolean("correct")&&grade.getDouble("score")==10,"UI correct grade");
        check((boolean)field(activity,"solved"),"reference answer available after full score");
        BankRepository bank=new BankRepository(c,null);Bitmap wrongLimit=Bitmap.createBitmap(700,240,Bitmap.Config.ARGB_8888);Canvas draw=new Canvas(wrongLimit);draw.drawColor(Color.WHITE);Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(52);draw.drawText("Answer: +4 / pi^2",40,120,paint);
        try{TutorClient.Reply wrongReply=TutorClient.ask(config,bank,bank.questions.get(4),java.util.List.of(new InkView.Sheet(wrongLimit,new RectF(0,0,700,240))),TutorClient.Mode.GRADE,0,new JSONArray());check(!wrongReply.correct&&wrongReply.score<10&&!wrongReply.feedback.isEmpty(),"positive limit answer must not be graded as negative correct result");}finally{wrongLimit.recycle();}
        final long attemptId=grade.getLong("attemptId");runOnMainSync(activity::finish);waitForIdleSync();
        PracticeActivity practice=(PracticeActivity)startActivitySync(new Intent(c,PracticeActivity.class).putExtra("attemptId",attemptId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync(()->{((android.widget.Button)practice.panel.getChildAt(practice.panel.getChildCount()-1)).performClick();});long deadline=SystemClock.uptimeMillis()+180000;while((boolean)field(practice,"busy")&&SystemClock.uptimeMillis()<deadline)Thread.sleep(200);waitForIdleSync();check(!(boolean)field(practice,"busy"),"practice generation finishes");File generatedFile=new File(c.getFilesDir(),"banks/smart-practice/bank.json");check(generatedFile.exists(),"live targeted practice saved: "+practice.lastError);BankRepository generated=new BankRepository(c,generatedFile.getParentFile());check(!generated.questions.get(generated.questions.size()-1).answerMarkdown.isEmpty(),"generated problem has reference solution");runOnMainSync(practice::finish);waitForIdleSync();
        StatisticsActivity stats=(StatisticsActivity)startActivitySync(new Intent(c,StatisticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(stats.content.getChildCount()>10,"live scores and taxonomy render");runOnMainSync(stats::finish);waitForIdleSync();c.getSharedPreferences("preferences",0).edit().putBoolean("logs",false).commit();
    }
    @SuppressWarnings("unchecked") private void clickMode(MainActivity activity,TutorClient.Mode mode) throws Exception {
        java.util.List<android.widget.Button> buttons=(java.util.List<android.widget.Button>)field(activity,"actionButtons");
        runOnMainSync(()->buttons.get(mode.ordinal()).performClick());
        if((boolean)field(activity,"busy")){check(((ProgressDialog)field(activity,"loadingDialog")).isShowing(),"modal loading shown");check(((InkView)field(activity,"ink")).locked,"canvas locked during model reasoning");}
        long end=SystemClock.uptimeMillis()+150000;
        while((boolean)field(activity,"busy")&&SystemClock.uptimeMillis()<end) { Thread.sleep(200); }
        waitForIdleSync(); check(!(boolean)field(activity,"busy"),"live request completes");check(((String)field(activity,"lastError")).isEmpty(),"live request failed: "+field(activity,"lastError"));
    }
}
