package com.handymath.app;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.widget.Spinner;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Encrypted profile persistence, settings interaction and actual HTTP/SSE payloads. */
final class ThinkingChecks {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  static JSONObject request(ProviderSettings config,List<JSONObject> requests)throws Exception{
    config.context=null;
    TutorClient.request(config,new JSONArray().put(new JSONObject().put("role","user").put("content","test")),true);
    return requests.get(requests.size()-1);
  }
  static void choose(AlertDialog dialog,String value,ProviderSettings config){
    int index=Arrays.asList(ModelThinking.choices(config)).indexOf(value);
    check(index>=0,"UI choice exists: "+value);
    dialog.getListView().performItemClick(null,index,index);
  }
  static void run(Instrumentation test)throws Exception{
    Context c=test.getTargetContext();String id="thinking-test-"+System.nanoTime();
    SharedPreferences profiles=c.getSharedPreferences("provider-profiles",0),prefs=AppSettings.prefs(c);
    Map<String,?> savedProfiles=new HashMap<>(profiles.getAll()),savedPrefs=new HashMap<>(prefs.getAll());
    LlmTransport.ConnectionFactory original=LlmTransport.connections;List<JSONObject> requests=new ArrayList<>();
    SettingsActivity settings=null;ProvidersActivity editor=null;
    try{
      prefs.edit().putBoolean("stream",false).putBoolean("logs",false).commit();
      LlmTransport.connections=url->new StudyToolsChecks.Fake(url,"{}",requests);
      ProviderSettings cfg=new ProviderSettings();cfg.id=id;cfg.name="思考设置测试";cfg.key="fixture-key";cfg.visionVerified=true;cfg.reasoningEffort="none";cfg.save(c);
      ProviderSettings.storage(c,id).edit().remove("reasoningEffort").commit();
      check(ProviderSettings.load(c,id).reasoningEffort.equals("none"),"legacy DeepSeek preserves previous non-thinking behavior");
      ProviderSettings.storage(c,id).edit().putString("url","https://example.test/v1").commit();
      check(ProviderSettings.load(c,id).reasoningEffort.equals("default"),"legacy generic provider keeps model default");
      cfg.baseUrl="https://api.deepseek.com";
      for(String effort:ModelThinking.choices(cfg)){
        cfg.reasoningEffort=effort;cfg.save(c);ProviderSettings loaded=ProviderSettings.load(c,id);
        check(loaded.reasoningEffort.equals(effort)&&loaded.key.equals("fixture-key")&&loaded.visionVerified,"thinking saved without losing key or vision verification");
        JSONObject body=request(loaded,requests);
        if(effort.equals("default"))check(!body.has("thinking")&&!body.has("reasoning_effort"),"default sends no thinking override");
        else if(effort.equals("none"))check(body.getJSONObject("thinking").getString("type").equals("disabled")&&!body.has("reasoning_effort"),"off disables thinking, not merely hiding it");
        else check(body.getJSONObject("thinking").getString("type").equals("enabled")&&body.getString("reasoning_effort").equals(effort),"DeepSeek sends selected thinking effort");
        check(body.getInt("max_tokens")== (effort.equals("none")?2500:effort.equals("max")?131072:65536),"thinking receives enough output budget");
      }
      cfg.baseUrl="https://example.test/v1";
      for(String effort:ModelThinking.VALUES){
        cfg.reasoningEffort=effort;JSONObject body=request(cfg,requests);
        check(!body.has("thinking")&&!body.has("reasoning"),"provider-specific fields not leaked to compatible endpoint");
        check(effort.equals("default")?!body.has("reasoning_effort"):body.getString("reasoning_effort").equals(effort),"compatible API effort parameter");
      }
      cfg.baseUrl="https://api.openai.com/v1";cfg.reasoningEffort="high";
      JSONObject openai=request(cfg,requests);
      check(openai.has("max_completion_tokens")&&!openai.has("max_tokens"),"OpenAI uses reasoning-compatible output budget field");
      cfg.baseUrl="https://openrouter.ai/api/v1";
      for(String effort:new String[]{"none","high","default"}){
        cfg.reasoningEffort=effort;JSONObject body=request(cfg,requests);
        check(!body.has("thinking")&&!body.has("reasoning_effort"),"OpenRouter uses own reasoning object");
        if(effort.equals("none"))check(!body.getJSONObject("reasoning").getBoolean("enabled"),"OpenRouter disables reasoning");
        else if(effort.equals("high"))check(body.getJSONObject("reasoning").getString("effort").equals("high"),"OpenRouter effort forwarded");
        else check(!body.has("reasoning"),"OpenRouter default sends no override");
      }
      // Both response formats expose only final content; reasoning must not become feedback.
      cfg.baseUrl="https://api.deepseek.com";cfg.reasoningEffort="high";
      JSONObject streamed=request(cfg,requests).put("stream",true);List<String> partials=new ArrayList<>();
      LlmTransport.connections=url->new HttpURLConnection(url){
        public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
        public int getResponseCode(){return 200;}public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
        public InputStream getInputStream(){String s="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"internal-reasoning\"}}]}\n\n"
            +"data: {\"choices\":[{\"delta\":{\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
          return new ByteArrayInputStream(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
      };
      check(LlmTransport.call(cfg,streamed,partials::add).equals("{}")&&!partials.toString().contains("internal-reasoning"),"streaming keeps final JSON separate from reasoning");
      LlmTransport.connections=url->new StudyToolsChecks.Fake(url,"{}",requests);
      cfg.context=c;cfg.reasoningEffort="none";cfg.save(c);
      settings=(SettingsActivity)test.startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      SettingsActivity screen=settings;AtomicReference<Throwable> failure=new AtomicReference<>();
      test.runOnMainSync(()->{try{
        check(InputSettingsChecks.find(screen.getWindow().getDecorView(),"模型思考强度 / 关闭思考  ›")!=null,"settings entry visible");
        choose(ModelThinking.editCurrent(screen),"high",cfg);
        check(ProviderSettings.load(c,id).reasoningEffort.equals("high"),"settings selection persists immediately");
        choose(ModelThinking.editCurrent(screen),"none",cfg);
        check(ProviderSettings.load(c,id).reasoningEffort.equals("none"),"settings can turn thinking off again");
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
      editor=(ProvidersActivity)test.startActivitySync(new Intent(c,ProvidersActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ProvidersActivity editScreen=editor;
      test.runOnMainSync(()->{try{
        java.lang.reflect.Method edit=ProvidersActivity.class.getDeclaredMethod("edit",ProviderSettings.class);edit.setAccessible(true);
        edit.invoke(editScreen,ProviderSettings.load(c,id));
        Spinner spinner=(Spinner)BankDeleteChecks.byDescription(editScreen.getWindow().getDecorView(),"模型思考强度");
        check(ModelThinking.selected(spinner).equals("none"),"provider editor restores selected setting");
      }catch(Throwable e){failure.set(e);}});
      if(failure.get()!=null)throw new AssertionError(failure.get());
    }finally{
      if(editor!=null){ProvidersActivity screen=editor;test.runOnMainSync(screen::finish);}
      if(settings!=null){SettingsActivity screen=settings;test.runOnMainSync(screen::finish);}
      LlmTransport.connections=original;ProviderSettings.storage(c,id).edit().clear().commit();
      StudyToolsChecks.restore(profiles,savedProfiles);StudyToolsChecks.restore(prefs,savedPrefs);
    }
  }
}
