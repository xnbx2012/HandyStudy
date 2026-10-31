package com.handymath.app;

import java.util.*;

/**
 * Reads completed characters from incomplete JSON string fields, never displays the JSON envelope.
 */
final class StreamJson {
  static String field(String raw, String key) {
    raw = ModelJson.escapes(raw);
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"")
            .matcher(raw);
    if (!m.find()) return "";
    StringBuilder out = new StringBuilder();
    for (int i = m.end(); i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '"') break;
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (++i >= raw.length()) break;
      c = raw.charAt(i);
      if (c == 'u') {
        if (i + 4 >= raw.length()) break;
        try {
          out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
          i += 4;
        } catch (Exception e) {
          break;
        }
      } else
        out.append(
            switch (c) {
              case 'n' -> '\n';
              case 'r' -> '\r';
              case 't' -> '\t';
              case 'b' -> '\b';
              case 'f' -> '\f';
              default -> c;
            });
    }
    // Keep a split UTF-16 surrogate pair out of the rendered text until the low surrogate
    // arrives.
    if (out.length() > 0 && Character.isHighSurrogate(out.charAt(out.length() - 1)))
      out.setLength(out.length() - 1);
    return out.toString();
  }

  static String markdown(String raw, TutorClient.Mode mode) {
    String feedback = field(raw, "feedback"), hint = field(raw, "hint");
    String text =
        mode == TutorClient.Mode.GRADE
            ? feedback
            : (hint + (feedback.isEmpty() ? "" : "\n\n**已做部分点评**\n\n" + feedback));
    return FormulaFormat.preview(text);
  }
}
