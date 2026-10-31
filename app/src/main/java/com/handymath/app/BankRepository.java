package com.handymath.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Versioned bank contract shared by built-in assets and user ZIP packages. */
public final class BankRepository {
  public static final class Question {
    public String id,
        title,
        topic,
        questionImage,
        questionMarkdown,
        answerMarkdown,
        type,
        expectedChoice,
        choiceOnlyFeedback;
    public final List<String> answers = new ArrayList<>();
    public final List<String> studentSourceImages = new ArrayList<>();
    public String initialStudentMarkdown = "";
    public JSONObject tags;
    public JSONObject source, referenceSource;
    public String chapter = "", referencePolicy = "reference";
    public int number;
    public JSONArray knowledgeIds = new JSONArray();
    public final List<String> questionImages = new ArrayList<>();
    public boolean referenceAvailable = true;
  }

  public final String id, title, subject;
  final KnowledgeSystem knowledge;
  final JSONArray materials;
  final String unitLabel;
  public final List<Question> questions = new ArrayList<>();
  private final Context context;
  private final File directory;
  private final String assets;
  final JSONObject data;

  public BankRepository(Context c, File directory) throws Exception {
    this(c, directory, "sample");
  }

  private BankRepository(Context c, File directory, String builtin) throws Exception {
    context = c;
    this.directory = directory;
    assets = "banks/" + builtin + "/";
    JSONObject obj =
        new JSONObject(
            new String(read(open("bank.json")), java.nio.charset.StandardCharsets.UTF_8));
    data = obj;
    if (obj.getInt("version") != 1 && obj.getInt("version") != 2) throw new IOException("不支持的题库版本");
    id = obj.getString("id");
    title = obj.getString("title");
    subject = obj.optString("subject", "数学");
    materials =
        obj.optJSONArray("materials") == null ? new JSONArray() : obj.getJSONArray("materials");
    unitLabel = obj.optString("unitLabel", "题");
    knowledge = new KnowledgeSystem(obj.optJSONObject("knowledgeSystem"));
    if (!id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IOException("题库 ID 无效");
    JSONArray qs = obj.getJSONArray("questions");
    if (qs.length() < 1 || qs.length() > 1000) throw new IOException("题库须含 1–1000 道题");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < qs.length(); i++) {
      JSONObject o = qs.getJSONObject(i);
      Question q = new Question();
      q.id = o.getString("id");
      q.source = o.optJSONObject("source");
      q.referenceSource = o.optJSONObject("referenceSource");
      q.title = o.getString("title");
      q.chapter = o.optString("chapter", o.optString("topic", "未分章"));
      q.number = o.optInt("number", i + 1);
      q.referencePolicy = o.optString("referencePolicy", "reference");
      q.initialStudentMarkdown = o.optString("initialStudentMarkdown", "");
      JSONArray studentSources = o.optJSONArray("studentSourceImages");
      if (studentSources != null) {
        if (studentSources.length() > 16) throw new IOException("作答来源照片过多");
        for (int j = 0; j < studentSources.length(); j++) q.studentSourceImages.add(studentSources.getString(j));
      }
      if (o.optJSONArray("knowledgeIds") != null) q.knowledgeIds = o.getJSONArray("knowledgeIds");
      for (int n = 0; n < q.knowledgeIds.length(); n++)
        if (!knowledge.nodes.containsKey(q.knowledgeIds.getString(n)))
          throw new IOException("题目引用了未知知识节点");
      q.topic = o.optString("topic", "数学");
      if (!q.id.matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(q.id))
        throw new IOException("题目 ID 无效或重复");
      q.questionImage = o.optString("questionImage", "");
      if (!q.questionImage.isEmpty()) q.questionImages.add(q.questionImage);
      JSONArray pictureList = o.optJSONArray("questionImages");
      if (pictureList != null)
        for (int n = 0; n < pictureList.length(); n++) {
          String path = pictureList.getString(n);
          if (!q.questionImages.contains(path)) q.questionImages.add(path);
        }
      if (q.questionImages.size() > 16) throw new IOException("题面图片过多");
      q.questionMarkdown = o.optString("questionMarkdown", "");
      q.answerMarkdown = o.optString("answerMarkdown", "");
      q.type = o.optString("type", "auto");
      q.referenceAvailable = o.optBoolean("referenceAvailable", true);
      q.expectedChoice = o.optString("expectedChoice", "");
      q.choiceOnlyFeedback = o.optString("choiceOnlyFeedback", "");
      if (o.has("knowledgePoints") || o.has("examPoints")) {
        q.tags =
            new JSONObject()
                .put(
                    "knowledgePoints",
                    o.optJSONArray("knowledgePoints") == null
                        ? new JSONArray().put(q.topic)
                        : o.getJSONArray("knowledgePoints"))
                .put(
                    "examPoints",
                    o.optJSONArray("examPoints") == null
                        ? new JSONArray().put(q.topic)
                        : o.getJSONArray("examPoints"))
                .put(
                    "questionType",
                    o.optString("questionType", q.type.equals("choice") ? "选择题" : "解答题"))
                .put("difficulty", o.optInt("difficulty", 3));
        TutorClient.validateTags(q.tags);
      }
      if (q.questionImages.isEmpty() && q.questionMarkdown.trim().isEmpty())
        throw new IOException("题目缺少题面");
      JSONArray aa = o.optJSONArray("answerImages");
      if (aa == null) aa = new JSONArray();
      if ((aa.length() < 1 && q.answerMarkdown.trim().isEmpty()
              && !q.referencePolicy.equals("independent")) || aa.length() > 8)
        throw new IOException("题目缺少解析或解析图片过多");
      for (int j = 0; j < aa.length(); j++) q.answers.add(aa.getString(j));
      List<String> images = new ArrayList<>(q.answers);
      images.addAll(q.questionImages);
      images.addAll(q.studentSourceImages);
      for (String path : images) {
        try (InputStream in = open(path)) {
          BitmapFactory.Options opts = new BitmapFactory.Options();
          opts.inJustDecodeBounds = true;
          BitmapFactory.decodeStream(in, null, opts);
          if (opts.outWidth < 1
              || opts.outHeight < 1
              || opts.outWidth > 5000
              || opts.outHeight > 8000
              || (long) opts.outWidth * opts.outHeight > 20000000)
            throw new IOException("题库图片无效或过大");
        }
      }
      questions.add(q);
    }
  }

  InputStream open(String path) throws IOException {
    if (path.isEmpty()
        || path.contains("\\")
        || path.startsWith("/")
        || Arrays.asList(path.split("/")).contains("..")) throw new IOException("非法题库路径");
    if (directory == null) return context.getAssets().open(assets + path);
    File file = new File(directory, path);
    if (!file.getCanonicalPath().startsWith(directory.getCanonicalPath() + File.separator))
      throw new IOException("非法题库路径");
    return new FileInputStream(file);
  }

  Bitmap preview(String path, int width, int height) throws IOException {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inJustDecodeBounds = true;
    try (InputStream in = open(path)) { BitmapFactory.decodeStream(in, null, opts); }
    int sample = 1;
    while (opts.outWidth / sample > width || opts.outHeight / sample > height) sample *= 2;
    opts.inJustDecodeBounds = false;
    opts.inSampleSize = sample;
    try (InputStream in = open(path)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in, null, opts);
      if (bitmap == null) throw new IOException("无法读取题库图片");
      return bitmap;
    }
  }

  Bitmap image(String path) throws IOException {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inJustDecodeBounds = true;
    try (InputStream in = open(path)) {
      BitmapFactory.decodeStream(in, null, opts);
    }
    int sample = 1;
    while (opts.outWidth / sample > 1800 || opts.outHeight / sample > 2600) sample *= 2;
    opts.inJustDecodeBounds = false;
    opts.inSampleSize = sample;
    try (InputStream in = open(path)) {
      Bitmap b = BitmapFactory.decodeStream(in, null, opts);
      if (b == null) throw new IOException("无法读取题库图片");
      return b;
    }
  }

  static byte[] read(InputStream in) throws IOException {
    try (InputStream source = in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      long size = 0;
      while ((n = source.read(buf)) != -1) {
        size += n;
        if (size > 40 * 1024 * 1024) throw new IOException("文件过大");
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    }
  }

  static BankRepository loadLocation(Context c, String location) throws Exception {
    if (location == null || location.isEmpty()) return new BankRepository(c, null);
    if (location.matches("@builtin/[a-zA-Z0-9_-]{1,64}"))
      return new BankRepository(c, null, location.substring(9));
    if (!location.matches("[a-zA-Z0-9_-]{1,100}")) throw new IOException("题库位置无效");
    return new BankRepository(c, new File(c.getFilesDir(), "banks/" + location));
  }

  static BankRepository active(Context c) throws Exception {
    return loadLocation(c, c.getSharedPreferences("study", 0).getString("bank", ""));
  }

  String location() {
    return directory == null
        ? (assets.equals("banks/sample/")
            ? ""
            : "@builtin/" + assets.substring(6, assets.length() - 1))
        : directory.getName();
  }

  static final class Entry {
    final String title, id, location;
    String subject = "数学", unitLabel = "题", details = "";
    final int count;

    Entry(String t, String id, String l, int n) {
      title = t;
      this.id = id;
      location = l;
      count = n;
    }

    boolean imported() { return importedLocation(location); }
  }

  private static boolean importedLocation(String location) {
    return location != null && location.matches("import-[a-zA-Z0-9_-]{1,93}");
  }

  /** Delete installed copies only; asset banks and user-created questions are protected. */
  static Set<String> deleteImported(Context c, String location) throws Exception {
    if (!importedLocation(location)) throw new IOException("只能删除已导入的题库，系统内置题库不能删除");
    File root = new File(c.getFilesDir(), "banks").getCanonicalFile();
    File target = new File(root, location);
    if (!target.equals(target.getCanonicalFile()) || !target.isDirectory())
      throw new IOException("题库目录不存在或位置无效");
    String id = new JSONObject(new String(read(new FileInputStream(new File(target, "bank.json"))),
        java.nio.charset.StandardCharsets.UTF_8)).getString("id");
    List<File> copies = new ArrayList<>(); copies.add(target);
    File[] installed = root.listFiles();
    if (installed == null) throw new IOException("无法读取题库目录");
    for (File file : installed) {
      if (file.equals(target) || !importedLocation(file.getName())
          || !file.equals(file.getCanonicalFile()) || !file.isDirectory()) continue;
      try {
        JSONObject data = new JSONObject(new String(read(new FileInputStream(new File(file, "bank.json"))),
            java.nio.charset.StandardCharsets.UTF_8));
        if (id.equals(data.optString("id"))) copies.add(file);
      } catch (IOException | JSONException ignored) { }
    }
    Set<String> removed = new HashSet<>();
    try {
      for (File file : copies) {
        // walkFileTree does not follow symbolic links inside an imported directory.
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
          @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            Files.delete(path); return FileVisitResult.CONTINUE;
          }
          @Override public FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
            if (error != null) throw error;
            Files.delete(path); return FileVisitResult.CONTINUE;
          }
        });
        removed.add(file.getName());
      }
    } finally {
      String active = c.getSharedPreferences("study", 0).getString("bank", "");
      if (removed.contains(active)) c.getSharedPreferences("study", 0).edit().remove("bank").apply();
      try (LearningDb db = new LearningDb(c)) {
        for (String deleted : removed)
          db.getWritableDatabase().delete("favorites", "location=?", new String[]{deleted});
      }
    }
    return removed;
  }

  private static Entry entry(BankRepository bank) {
    Entry e = new Entry(bank.title, bank.id, bank.location(), bank.questions.size());
    e.subject = bank.subject; e.unitLabel = bank.unitLabel; e.details = describe(bank.data); return e;
  }

  static String describe(JSONObject data) {
    JSONArray questions = data.optJSONArray("questions");
    int count = questions == null ? 0 : questions.length(), text = 0, pictures = 0, references = 0;
    for (int i = 0; i < count; i++) {
      JSONObject q = questions.optJSONObject(i); if (q == null) continue;
      if (!q.optString("questionMarkdown").trim().isEmpty()) text++;
      JSONArray images = q.optJSONArray("questionImages"), answers = q.optJSONArray("answerImages");
      if (!q.optString("questionImage").isEmpty() || images != null && images.length() > 0) pictures++;
      if (!q.optString("answerMarkdown").trim().isEmpty() || answers != null && answers.length() > 0) references++;
    }
    JSONObject system = data.optJSONObject("knowledgeSystem");
    JSONArray nodes = system == null ? null : system.optJSONArray("nodes");
    String format = text > 0 && pictures > 0 ? "图文题库" : pictures > 0 ? "图片题库" : "文本题库";
    return format + " · 参考答案/解析 " + references + "/" + count
        + " · " + (nodes != null && nodes.length() > 0 ? "含知识树（" + nodes.length() + "节点）" : "无知识树");
  }

  static List<Entry> catalog(Context c) throws Exception {
    List<Entry> entries = new ArrayList<>();
    for (String builtinName : c.getAssets().list("banks")) {
      try {
        BankRepository builtin = loadLocation(c, "@builtin/" + builtinName);
        entries.add(entry(builtin));
      } catch (Exception missing) {
        // Optional banks are absent from public source builds.
      }
    }
    File[] files = new File(c.getFilesDir(), "banks").listFiles();
    Set<String> seen = new HashSet<>();
    for (Entry e : entries) seen.add(e.id);
    if (files != null) {
      Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
      for (File f : files)
        try {
          JSONObject o =
              new JSONObject(
                  new String(
                      read(new FileInputStream(new File(f, "bank.json"))),
                      java.nio.charset.StandardCharsets.UTF_8));
          String id = o.getString("id");
          if (seen.add(id))
            entries.add(
                new Entry(
                    o.getString("title"), id, f.getName(), o.getJSONArray("questions").length()));
          if (!entries.isEmpty() && entries.get(entries.size() - 1).id.equals(id)) {
            Entry entry = entries.get(entries.size() - 1);
            entry.subject = o.optString("subject", "其它"); entry.unitLabel = o.optString("unitLabel", "题"); entry.details = describe(o);
          }
        } catch (Exception ignored) {
        }
    }
    return entries;
  }

  /** Compatibility entry point for callers importing a single bank. */
  static BankRepository importZip(Context c, Uri uri) throws Exception {
    return importZips(c, uri).get(0);
  }

  /** Validate the entire bundle first, then install all banks or roll back all of them. */
  static List<BankRepository> importZips(Context c, Uri uri) throws Exception {
    File staging = new File(c.getFilesDir(), "bank-import-" + UUID.randomUUID());
    File banks = new File(c.getFilesDir(), "banks");
    List<File> installed = new ArrayList<>();
    try {
      if (!staging.mkdirs() || !banks.isDirectory() && !banks.mkdirs())
        throw new IOException("无法创建题库导入目录");
      ImportBudget budget = new ImportBudget();
      try (InputStream input = c.getContentResolver().openInputStream(uri)) {
        if (input == null) throw new IOException("无法读取题库 ZIP");
        extractArchive(input, staging, budget);
      }
      List<File> candidates = new ArrayList<>();
      discoverBanks(staging, 0, 0, budget, candidates);
      if (candidates.isEmpty()) throw new IOException("ZIP 中未找到题库，请确认内含 bank.json 或独立题库 ZIP");
      Set<String> ids = new HashSet<>();
      for (File candidate : candidates) {
        checkBankSize(candidate, new long[2]);
        BankRepository bank = new BankRepository(c, candidate);
        if (!ids.add(bank.id)) throw new IOException("压缩包中有重复题库 ID：" + bank.id);
      }
      List<BankRepository> result = new ArrayList<>();
      for (File candidate : candidates) {
        File target = new File(banks, "import-" + UUID.randomUUID());
        if (!candidate.renameTo(target)) throw new IOException("题库安装失败，请检查存储空间");
        installed.add(target);
        result.add(new BankRepository(c, target));
      }
      c.getSharedPreferences("study", 0).edit().putString("bank", installed.get(0).getName()).apply();
      return result;
    } catch (Exception | OutOfMemoryError e) {
      for (File target : installed) remove(target);
      throw e;
    } finally {
      remove(staging);
    }
  }

  private static final class ImportBudget {
    long bytes;
    int entries, archives = 1;
  }

  private static void extractArchive(InputStream input, File root, ImportBudget budget) throws Exception {
    try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
      ZipEntry entry;
      byte[] buffer = new byte[32768];
      String prefix = root.getCanonicalPath() + File.separator;
      while ((entry = zip.getNextEntry()) != null) {
        if (++budget.entries > 120000) throw new IOException("题库包文件总数超过 120000");
        String name = entry.getName();
        File target = new File(root, name);
        if (name.contains("\\") || name.startsWith("/") || name.contains(":")
            || !target.getCanonicalPath().startsWith(prefix)) throw new IOException("非法 ZIP 路径");
        if (entry.isDirectory()) {
          if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建题库文件夹");
          continue;
        }
        if (target.exists()) throw new IOException("ZIP 中有重复文件：" + name);
        if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
          throw new IOException("无法创建题库文件夹");
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
          int n;
          while ((n = zip.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("题库导入已取消");
            budget.bytes += n;
            // Count nested ZIP bytes as well as their contents to bound expansion work.
            if (budget.bytes > 4L * 1024 * 1024 * 1024) throw new IOException("题库包累计解压超过 4 GB，请分批导入");
            output.write(buffer, 0, n);
          }
        }
      }
    }
  }

  private static void discoverBanks(File directory, int depth, int archiveDepth, ImportBudget budget, List<File> result) throws Exception {
    if (depth > 32) throw new IOException("题库文件夹层级过深");
    if (new File(directory, "bank.json").isFile()) {
      if (result.size() >= 128) throw new IOException("一次最多导入 128 个题库");
      result.add(directory);
      return;
    }
    File[] files = directory.listFiles();
    if (files == null) throw new IOException("无法读取题库文件夹");
    Arrays.sort(files, Comparator.comparing(File::getName));
    for (File file : files) {
      if (file.isDirectory()) discoverBanks(file, depth + 1, archiveDepth, budget, result);
      else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
        if (++budget.archives > 128) throw new IOException("嵌套 ZIP 数量过多");
        // Archive depth is independent of ordinary wrapper-folder depth.
        if (archiveDepth >= 4) throw new IOException("ZIP 嵌套不能超过 4 层");
        File expanded = new File(directory, "unpacked-" + UUID.randomUUID());
        if (!expanded.mkdirs()) throw new IOException("无法创建嵌套 ZIP 解压目录");
        try (InputStream input = new FileInputStream(file)) { extractArchive(input, expanded, budget); }
        int previousCount = result.size();
        discoverBanks(expanded, depth + 1, archiveDepth + 1, budget, result);
        if (result.size() == previousCount) throw new IOException("嵌套 ZIP 中未找到题库：" + file.getName());
      }
    }
  }

  private static void checkBankSize(File file, long[] totals) throws IOException {
    if (++totals[0] > 10000) throw new IOException("单个题库文件不能超过 10000 个");
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children == null) throw new IOException("无法读取题库文件夹");
      for (File child : children) checkBankSize(child, totals);
    } else {
      totals[1] += file.length();
      if (totals[1] > 100L * 1024 * 1024) throw new IOException("单个题库解压不能超过 100 MB");
    }
  }

  private static void remove(File f) {
    if (f.isDirectory()) {
      File[] list = f.listFiles();
      if (list != null) for (File x : list) remove(x);
    }
    f.delete();
  }
}
