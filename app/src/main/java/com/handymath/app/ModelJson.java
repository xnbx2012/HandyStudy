package com.handymath.app;

import org.json.JSONObject;

/** Repairs string escapes in model content only; HTTP envelopes remain strict JSON. */
final class ModelJson {
  static JSONObject object(String value) throws Exception {
    String s = value.trim();
    if (s.startsWith("```")) {
      int start = s.indexOf('\n'), end = s.lastIndexOf("```");
      if (start >= 0 && end > start) s = s.substring(start + 1, end).trim();
    }
    return new JSONObject(escapes(s));
  }

  static String escapes(String s) {
    StringBuilder out = new StringBuilder();
    boolean string = false, math = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"') {
        string = !string;
        math = false;
      }
      if (!string || c != '\\' || i + 1 == s.length()) {
        if (string && c == '$') {
          math = !math;
          if (i + 1 < s.length() && s.charAt(i + 1) == '$') {
            out.append(c);
            i++;
          }
        }
        out.append(c);
        continue;
      }
      char next = s.charAt(i + 1);
      boolean unicode =
          next == 'u' && i + 5 < s.length() && s.substring(i + 2, i + 6).matches("[0-9a-fA-F]{4}");
      int end = i + 1;
      while (end < s.length() && Character.isLetter(s.charAt(end))) end++;
      String command = s.substring(i + 1, end);
      boolean latex =
          math
              && command.matches(
                  "begin|beta|bar|big.*|bold.*|binom|bmod|boxed|breve|backslash|bullet|bot|because|frac|fbox|forall|flat|frak|nu|nabla|neq|ne|not|neg|nonumber|newline|nexists|nmid|nparallel|nolimits|right.*|rho|rfloor|rceil|rangle|rbrace|rbrack|rm|relax|rule|raisebox|rvert|rVert|text.*|tfrac|tbinom|theta|tan|tanh|times|to|top|tilde|triangle.*|tag");
      boolean valid = "\"\\/bfnrt".indexOf(next) >= 0 || unicode;
      // A split Unicode escape must stay incomplete during streaming.
      boolean incompleteUnicode =
          next == 'u' && i + 5 >= s.length() && s.substring(i + 2).matches("[0-9a-fA-F]*");
      if ((!valid && !incompleteUnicode) || (latex && !unicode)) out.append('\\');
      out.append(c).append(next);
      i++;
      if (next == '(' || next == '[') math = true;
      if (next == ')' || next == ']') math = false;
      if (next == '\\' && i + 1 < s.length()) {
        char delimiter = s.charAt(i + 1);
        if (delimiter == '(' || delimiter == '[') math = true;
        if (delimiter == ')' || delimiter == ']') math = false;
      }
    }
    return out.toString();
  }
}
