package com.handymath.app;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.json.*;

final class LearningDb extends SQLiteOpenHelper {
  private final Context context;

  LearningDb(Context c) {
    super(c, "learning.db", null, 3);
    context = c.getApplicationContext();
  }

  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE time_log(id INTEGER PRIMARY KEY,bank TEXT,q TEXT,day TEXT,ms" + " INTEGER)");
    d.execSQL(
        "CREATE TABLE attempts(id INTEGER PRIMARY KEY,bank TEXT,q TEXT,day TEXT,at"
            + " INTEGER,score REAL,correct INTEGER,data TEXT)");
    d.execSQL("CREATE TABLE tags(bank TEXT,q TEXT,data TEXT,PRIMARY KEY(bank,q))");
    d.execSQL(
        "CREATE TABLE stay_log(id INTEGER PRIMARY KEY,bank TEXT,q TEXT,day TEXT,ms" + " INTEGER)");
    d.execSQL("CREATE INDEX attempts_day ON attempts(day)");
    d.execSQL("CREATE INDEX time_day ON time_log(day)");
    createFavorites(d);
  }

  public void onUpgrade(SQLiteDatabase d, int a, int b) {
    if (a < 3) createFavorites(d);
    if (a < 2)
      d.execSQL(
          "CREATE TABLE IF NOT EXISTS stay_log(id INTEGER PRIMARY KEY,bank TEXT,q"
              + " TEXT,day TEXT,ms INTEGER)");
  }

  private static void createFavorites(SQLiteDatabase d) {
    d.execSQL("CREATE TABLE IF NOT EXISTS favorites(bank TEXT,q TEXT,location TEXT,title TEXT,bankTitle TEXT,at INTEGER,PRIMARY KEY(bank,q))");
  }

  boolean favorite(String bank, String question) {
    try (Cursor c = getReadableDatabase().rawQuery(
        "SELECT 1 FROM favorites WHERE bank=? AND q=?", new String[]{bank, question})) {
      return c.moveToFirst();
    }
  }

  void unfavorite(String bank, String question) {
    getWritableDatabase().delete("favorites", "bank=? AND q=?", new String[]{bank, question});
  }

  void favorite(BankRepository bank, BankRepository.Question question) {
    ContentValues v = new ContentValues();
    v.put("bank", bank.id); v.put("q", question.id); v.put("location", bank.location());
    v.put("title", question.title); v.put("bankTitle", bank.title); v.put("at", System.currentTimeMillis());
    getWritableDatabase().insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  JSONArray favorites() throws JSONException {
    JSONArray rows = new JSONArray();
    try (Cursor c = getReadableDatabase().rawQuery(
        "SELECT bank,q,location,title,bankTitle,at FROM favorites ORDER BY at DESC,bank,q", null)) {
      while (c.moveToNext()) rows.put(new JSONObject().put("bank", c.getString(0))
          .put("q", c.getString(1)).put("location", c.getString(2)).put("title", c.getString(3))
          .put("bankTitle", c.getString(4)).put("at", c.getLong(5)));
    }
    return rows;
  }

  static String day(long epoch) {
    return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().toString();
  }

  void time(String bank, String q, long wallStart, long duration) {
    recordTime("time_log", bank, q, wallStart, duration);
  }

  void residence(String bank, String q, long wallStart, long duration) {
    recordTime("stay_log", bank, q, wallStart, duration);
  }

  private void recordTime(String table, String bank, String q, long wallStart, long duration) {
    if (duration <= 0) return;
    long end = wallStart + duration;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      while (wallStart < end) {
        long next =
            Instant.ofEpochMilli(wallStart)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long part = Math.min(end, next) - wallStart;
        ContentValues v = new ContentValues();
        v.put("bank", bank);
        v.put("q", q);
        v.put("day", day(wallStart));
        v.put("ms", part);
        db.insertOrThrow(table, null, v);
        wallStart += part;
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  long questionTime(String bank, String q) {
    try (Cursor cur =
        getReadableDatabase()
            .rawQuery(
                "SELECT COALESCE(SUM(ms),0) FROM time_log WHERE bank=? AND q=?",
                new String[] {bank, q})) {
      cur.moveToFirst();
      return cur.getLong(0);
    }
  }

  JSONObject tags(String bank, String q) throws Exception {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT data FROM tags WHERE bank=? AND q=?", new String[] {bank, q})) {
      return c.moveToFirst() ? new JSONObject(c.getString(0)) : null;
    }
  }

  void tags(String bank, String q, JSONObject tags) {
    ContentValues v = new ContentValues();
    v.put("bank", bank);
    v.put("q", q);
    v.put("data", tags.toString());
    getWritableDatabase().insertWithOnConflict("tags", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  long grade(
      Context context,
      BankRepository bank,
      BankRepository.Question q,
      TutorClient.Reply reply,
      InkView.Document document,
      int hints)
      throws Exception {
    JSONObject data =
        new JSONObject()
            .put("bankLocation", bank.location())
            .put("subject", bank.subject)
            .put("question", snapshot(q))
            .put("feedback", reply.feedback)
            .put("studentWork", reply.studentWork)
            .put("thinking", reply.thinking)
            .put("weaknesses", reply.weaknesses)
            .put("hints", hints)
            .put("tags", reply.tags)
            .put("activeMs", questionTime(bank.id, q.id));
    long now = System.currentTimeMillis();
    ContentValues v = new ContentValues();
    v.put("bank", bank.id);
    v.put("q", q.id);
    v.put("day", day(now));
    v.put("at", now);
    v.put("score", reply.score);
    v.put("correct", reply.correct ? 1 : 0);
    v.put("data", data.toString());
    SQLiteDatabase database = getWritableDatabase();
    database.beginTransaction();
    try {
      long id = database.insertOrThrow("attempts", null, v);
      android.util.AtomicFile file = inkFile(id);
      FileOutputStream out = file.startWrite();
      try {
        out.write(
            document.json(0, 0, 1).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        file.finishWrite(out);
      } catch (Exception e) {
        file.failWrite(out);
        throw e;
      }
      database.setTransactionSuccessful();
      return id;
    } finally {
      database.endTransaction();
    }
  }

  void annotations(long id, JSONObject ink) throws Exception {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT data FROM attempts WHERE id=?", new String[] {Long.toString(id)})) {
      if (c.moveToFirst()) {
        JSONObject data = new JSONObject(c.getString(0));
        data.put("questionAnnotations", ink);
        ContentValues v = new ContentValues();
        v.put("data", data.toString());
        getWritableDatabase().update("attempts", v, "id=?", new String[] {Long.toString(id)});
      }
    }
  }

  static JSONObject snapshot(BankRepository.Question q) throws Exception {
    JSONArray images = new JSONArray();
    for (String s : q.answers) images.put(s);
    return new JSONObject()
        .put("id", q.id)
        .put("title", q.title)
        .put("topic", q.topic)
        .put("chapter", q.chapter)
        .put("knowledgeIds", q.knowledgeIds)
        .put("questionImages", new JSONArray(q.questionImages))
        .put("initialStudentMarkdown", q.initialStudentMarkdown)
        .put("studentSourceImages", new JSONArray(q.studentSourceImages))
        .put("referencePolicy", q.referencePolicy)
        .put("type", q.type)
        .put("questionMarkdown", q.questionMarkdown)
        .put("questionImage", q.questionImage)
        .put("answerMarkdown", q.answerMarkdown)
        .put("expectedChoice", q.expectedChoice)
        .put("choiceOnlyFeedback", q.choiceOnlyFeedback)
        .put("referenceAvailable", q.referenceAvailable)
        .put("source", q.source)
        .put("referenceSource", q.referenceSource)
        .put("answerImages", images);
  }

  JSONArray attempts(boolean wrong) throws Exception {
    return attempts(wrong, false);
  }

  JSONObject attempt(long id) throws Exception {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT bank,q,day,at,score,correct,data FROM attempts WHERE id=?",
                new String[] {Long.toString(id)})) {
      if (!c.moveToFirst()) return null;
      return withInk(new JSONObject(c.getString(6)), id)
          .put("id", id)
          .put("bank", c.getString(0))
          .put("q", c.getString(1))
          .put("day", c.getString(2))
          .put("at", c.getLong(3))
          .put("score", c.getDouble(4))
          .put("correct", c.getInt(5) == 1);
    }
  }

  JSONArray attempts(boolean wrong, boolean includeInk) throws Exception {
    JSONArray result = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,bank,q,day,at,score,correct,data FROM attempts "
                    + (wrong ? "WHERE correct=0 " : "")
                    + "ORDER BY at DESC,id DESC",
                null)) {
      while (c.moveToNext()) {
        JSONObject data = new JSONObject(c.getString(7));
        if (!includeInk) data.remove("ink");
        else withInk(data, c.getLong(0));
        result.put(
            data.put("id", c.getLong(0))
                .put("bank", c.getString(1))
                .put("q", c.getString(2))
                .put("day", c.getString(3))
                .put("at", c.getLong(4))
                .put("score", c.getDouble(5))
                .put("correct", c.getInt(6) == 1));
      }
    }
    return result;
  }

  private android.util.AtomicFile inkFile(long id) {
    File dir = new File(context.getFilesDir(), "learning-ink");
    dir.mkdirs();
    return new android.util.AtomicFile(new File(dir, id + ".json"));
  }

  private JSONObject withInk(JSONObject data, long id) throws Exception {
    if (!data.has("ink"))
      data.put(
          "ink",
          new JSONObject(
              new String(inkFile(id).readFully(), java.nio.charset.StandardCharsets.UTF_8)));
    return data;
  }

  JSONArray times() throws Exception {
    return times(false);
  }

  JSONArray times(boolean residence) throws Exception {
    JSONArray result = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT bank,q,day,SUM(ms) FROM "
                    + (residence ? "stay_log" : "time_log")
                    + " GROUP BY bank,q,day",
                null)) {
      while (c.moveToNext())
        result.put(
            new JSONObject()
                .put("bank", c.getString(0))
                .put("q", c.getString(1))
                .put("day", c.getString(2))
                .put("ms", c.getLong(3)));
    }
    return result;
  }

  static final class Daily {
    String day;
    int count, attempts;
    long ms;
    double score;
  }

  List<Daily> daily(int days) {
    return daily(days, "");
  }

  List<Daily> daily(int days, String bank) {
    String where = bank.isEmpty() ? "" : " WHERE bank=? ";
    String[] args = bank.isEmpty() ? null : new String[] {bank};
    Map<String, Daily> rows = new LinkedHashMap<>();
    LocalDate today = LocalDate.now();
    for (int i = days - 1; i >= 0; i--) {
      Daily d = new Daily();
      d.day = today.minusDays(i).toString();
      rows.put(d.day, d);
    }
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT day,COUNT(*),AVG(score) FROM attempts" + where + " GROUP BY day", args)) {
      while (c.moveToNext()) {
        Daily d = rows.get(c.getString(0));
        if (d != null) {
          d.attempts = c.getInt(1);
          d.score = c.getDouble(2);
        }
      }
    }
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT day,COUNT(*) FROM (SELECT DISTINCT day,bank,q FROM attempts"
                    + where
                    + ") GROUP BY day",
                args)) {
      while (c.moveToNext()) {
        Daily d = rows.get(c.getString(0));
        if (d != null) d.count = c.getInt(1);
      }
    }
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT day,SUM(ms) FROM time_log" + where + " GROUP BY day", args)) {
      while (c.moveToNext()) {
        Daily d = rows.get(c.getString(0));
        if (d != null) d.ms = c.getLong(1);
      }
    }
    return new ArrayList<>(rows.values());
  }

  static String duration(long ms) {
    long seconds = ms / 1000;
    return seconds / 3600 > 0
        ? String.format(
            Locale.CHINA, "%d小时%02d分%02d秒", seconds / 3600, seconds / 60 % 60, seconds % 60)
        : String.format(Locale.CHINA, "%d分%02d秒", seconds / 60, seconds % 60);
  }
}
