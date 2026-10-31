package com.handymath.app;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class ConversationLog {
  static void save(Context context, JSONObject request, String response, String error) {
    if (context == null || !AppSettings.enabled(context, "logs", false)) return;
    try {
      File folder =
          new File(
              context.getFilesDir(),
              "llm-logs/" + System.currentTimeMillis() + "-" + UUID.randomUUID());
      if (!folder.mkdirs()) return;
      JSONObject copy = new JSONObject(request.toString());
      int[] image = {0};
      extract(copy, folder, image);
      JSONObject record =
          new JSONObject()
              .put("at", System.currentTimeMillis())
              .put("request", copy)
              .put("response", response)
              .put("error", error);
      String[] scope = UsageLedger.scope.get();
      if (scope != null) record.put("bank", scope[0]).put("q", scope[1]);
      try (FileOutputStream out = new FileOutputStream(new File(folder, "conversation.json"))) {
        out.write(record.toString(2).getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception ignored) {
      /* Logging must never turn a completed grade into failure. */
    }
  }

  private static void extract(Object node, File folder, int[] count) throws Exception {
    if (node instanceof JSONObject) {
      JSONObject obj = (JSONObject) node;
      List<String> keys = new ArrayList<>();
      obj.keys().forEachRemaining(keys::add);
      for (String key : keys) {
        Object value = obj.get(key);
        if (value instanceof String && ((String) value).startsWith("data:image/png;base64,")) {
          String name = "image-" + (++count[0]) + ".png";
          try (FileOutputStream out = new FileOutputStream(new File(folder, name))) {
            out.write(Base64.decode(((String) value).substring(22), Base64.DEFAULT));
          }
          obj.put(key, name);
        } else extract(value, folder, count);
      }
    } else if (node instanceof JSONArray) {
      JSONArray a = (JSONArray) node;
      for (int i = 0; i < a.length(); i++) extract(a.get(i), folder, count);
    }
  }
}
