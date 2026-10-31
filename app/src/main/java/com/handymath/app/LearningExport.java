package com.handymath.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

final class LearningExport {
  static void write(Context c, OutputStream out) throws Exception {
    write(c, out, new ExportFilter());
  }

  static void write(Context c, OutputStream out, ExportFilter filter) throws Exception {
    try (LearningDb db = new LearningDb(c);
        UsageLedger ledger = new UsageLedger(c);
        ZipOutputStream zip = new ZipOutputStream(out)) {
      JSONArray attempts = filter.apply(db.attempts(false)), times = filter.apply(db.times());
      BankTemplate.entry(
          zip,
          "README.md",
          ("# 手写伴学 学习记录\n"
                  + "统计定义：完成数每天按题目去重，均分按提交，时长排除模型等待和后台。\n"
                  + "learning.json 包含题目、点评、知识点、错题、矢量文件引用和按日期的时长。vectors/ 保留完整矢量笔迹"
                  + " JSON。answers/ 是每次提交的全部书写范围 PNG 分块；以记录中的 worldBounds 拼接。banks/"
                  + " 保留所导出作答对应的原题图片及解析，不重复打包整本题库或听力。筛选导出不包含全局学习计划。llm-logs/ 包含开启日志时保存的请求、模型回复和图片附件。不包含提供商凭据。\n"
                  + "请分析学生实际作答与历史弱项并生成针对性练习，不将参考解析视作学生答案。\n")
              .getBytes(StandardCharsets.UTF_8));
      Set<String> locations = new HashSet<>();
      for (int i = 0; i < attempts.length(); i++) {
        JSONObject a = attempts.getJSONObject(i);
        locations.add(a.optString("bankLocation"));
        JSONObject annotations = a.optJSONObject("questionAnnotations");
        if (annotations != null) {
          String path = "annotations/" + a.getLong("id") + ".json";
          BankTemplate.entry(zip, path, annotations.toString().getBytes(StandardCharsets.UTF_8));
          a.put("annotationVectorPath", path);
          List<InkView.Sheet> notes = InkView.Document.parse(annotations).export();
          JSONArray noteImages = new JSONArray();
          try {
            for (int j = 0; j < notes.size(); j++) {
              InkView.Sheet note = notes.get(j);
              String name = "annotations/" + a.getLong("id") + "-" + j + ".png";
              zip.putNextEntry(new ZipEntry(name));
              note.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zip);
              zip.closeEntry();
              noteImages.put(
                  new JSONObject()
                      .put("path", name)
                      .put(
                          "worldBounds",
                          new JSONArray()
                              .put(note.bounds.left)
                              .put(note.bounds.top)
                              .put(note.bounds.right)
                              .put(note.bounds.bottom)));
            }
          } finally {
            for (InkView.Sheet note : notes) note.bitmap.recycle();
          }
          a.put("annotationImages", noteImages);
        }
        JSONObject ink = db.attempt(a.getLong("id")).getJSONObject("ink");
        String vector = "vectors/" + a.getLong("id") + ".json";
        BankTemplate.entry(zip, vector, ink.toString().getBytes(StandardCharsets.UTF_8));
        a.put("vectorPath", vector);
        List<InkView.Sheet> sheets = InkView.Document.parse(ink).export();
        JSONArray images = new JSONArray();
        try {
          for (int j = 0; j < sheets.size(); j++) {
            InkView.Sheet s = sheets.get(j);
            String name = "answers/" + a.getLong("id") + "/" + j + ".png";
            zip.putNextEntry(new ZipEntry(name));
            s.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zip);
            zip.closeEntry();
            images.put(
                new JSONObject()
                    .put("path", name)
                    .put(
                        "worldBounds",
                        new JSONArray()
                            .put(s.bounds.left)
                            .put(s.bounds.top)
                            .put(s.bounds.right)
                            .put(s.bounds.bottom)));
          }
        } finally {
          for (InkView.Sheet s : sheets) s.bitmap.recycle();
        }
        a.put("answerImages", images);
      }
      BankTemplate.entry(
          zip,
          "learning.json",
          new JSONObject()
              .put("version", 1)
              .put("exportedAt", System.currentTimeMillis())
              .put("filter", filter.json())
              .put("attempts", attempts)
              .put(
                  "learningPlan",
                  filter.all() ? new JSONObject(LearningPlan.prefs(c).getAll()) : new JSONObject())
              .put("timeByQuestionDay", times)
              .put("residenceByQuestionDay", filter.apply(db.times(true)))
              .put("modelUsage", filter.apply(ledger.rows("")))
              .toString(2)
              .getBytes(StandardCharsets.UTF_8));
      for (String location : locations) exportBank(c, zip, location, attempts);
      File[] logs = new File(c.getFilesDir(), "llm-logs").listFiles();
      if (logs != null)
        for (File log : logs) {
          if (!log.isDirectory()) continue;
          if (filter.all()) {
            files(zip, log, "llm-logs/" + log.getName());
            continue;
          }
          JSONObject record;
          try {
            record =
                new JSONObject(
                    new String(
                        BankRepository.read(
                            new FileInputStream(new File(log, "conversation.json"))),
                        StandardCharsets.UTF_8));
          } catch (IOException | JSONException corrupt) {
            continue;
          }
          if (filter.accepts(record)) files(zip, log, "llm-logs/" + log.getName());
        }
    }
  }

  private static void exportBank(Context c, ZipOutputStream zip, String location, JSONArray attempts) throws Exception {
    BankRepository bank = BankRepository.loadLocation(c, location);
    JSONObject metadata = new JSONObject(new String(BankRepository.read(bank.open("bank.json")), StandardCharsets.UTF_8));
    LinkedHashMap<String, JSONObject> questions = new LinkedHashMap<>();
    Set<String> images = new LinkedHashSet<>();
    for (int i=0;i<attempts.length();i++) {
      JSONObject attempt=attempts.getJSONObject(i);
      if (!location.equals(attempt.optString("bankLocation"))) continue;
      JSONObject question=attempt.getJSONObject("question");
      questions.putIfAbsent(question.getString("id"), question);
      if (!question.optString("questionImage").isEmpty()) images.add(question.getString("questionImage"));
      for (String field : new String[]{"questionImages", "answerImages", "studentSourceImages"}) {
        JSONArray paths=question.optJSONArray(field);
        if(paths!=null)for(int j=0;j<paths.length();j++)images.add(paths.getString(j));
      }
    }
    metadata.put("questions", new JSONArray(questions.values()));
    metadata.remove("materials");
    String root="banks/"+bank.id;
    BankTemplate.entry(zip,root+"/bank.json",metadata.toString(2).getBytes(StandardCharsets.UTF_8));
    for (String image:images)BankTemplate.entry(zip,root+"/"+image,BankRepository.read(bank.open(image)));
  }

  static void files(ZipOutputStream zip, File source, String path) throws IOException {
    if (!source.exists()) return;
    if (source.isDirectory()) {
      File[] entries = source.listFiles();
      if (entries != null) for (File f : entries) files(zip, f, path + "/" + f.getName());
    } else {
      zip.putNextEntry(new ZipEntry(path));
      try (InputStream in = new FileInputStream(source)) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
      }
      zip.closeEntry();
    }
  }

  static void assets(Context c, ZipOutputStream zip, String source, String target)
      throws IOException {
    String[] children = c.getAssets().list(source);
    if (children != null && children.length > 0) {
      for (String child : children) assets(c, zip, source + "/" + child, target + "/" + child);
    } else {
      zip.putNextEntry(new ZipEntry(target));
      try (InputStream in = c.getAssets().open(source)) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
      }
      zip.closeEntry();
    }
  }
}
