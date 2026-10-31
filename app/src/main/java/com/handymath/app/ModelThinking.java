package com.handymath.app;

import android.app.*;
import android.widget.*;
import java.net.URI;
import java.util.*;
import org.json.*;

/** Provider-specific thinking parameters shared by every model request. */
final class ModelThinking {
  static final String[] VALUES = {"default", "none", "minimal", "low", "medium", "high", "xhigh", "max"};
  static final String[] LABELS = {"模型默认", "关闭思考", "最低", "低", "中", "高", "更高", "最高"};
  static final int[] OUTPUT_LIMITS = {0, 4096, 8192, 16384, 32768, 65536, 131072};

  static boolean host(ProviderSettings config, String expected) {
    try { return expected.equalsIgnoreCase(new URI(config.baseUrl).getHost()); }
    catch (Exception invalid) { return false; }
  }

  static void validate(String effort) {
    if (!Arrays.asList(VALUES).contains(effort)) throw new IllegalArgumentException("思考强度无效");
  }

  static String label(String effort) {
    int i = Arrays.asList(VALUES).indexOf(effort);
    return i < 0 ? LABELS[0] : LABELS[i];
  }

  static String[] choices(ProviderSettings config) {
    if (miniMaxM31(config)) return new String[]{"default", "low", "medium", "high", "xhigh", "max"};
    return host(config, "api.deepseek.com")
        ? new String[]{"default", "none", "low", "high", "max"} : VALUES.clone();
  }

  static boolean needsBudget(ProviderSettings config) {
    return !config.reasoningEffort.equals("none")
        && (!config.reasoningEffort.equals("default")
            || host(config, "api.deepseek.com") || host(config, "api.openai.com")
            || miniMaxM31(config));
  }

  static boolean miniMaxM31(ProviderSettings config) {
    String model = config.model.toLowerCase(Locale.ROOT);
    return model.contains("minimax-m3.1-flash") || model.endsWith("/m3.1-flash");
  }

  static int outputLimit(ProviderSettings config) {
    if (config.maxOutputTokens > 0) return config.maxOutputTokens;
    if (miniMaxM31(config)) return 65536;
    if (!needsBudget(config)) return 2500;
    return host(config, "api.deepseek.com") ? (config.reasoningEffort.equals("max") ? 131072 : 65536)
        : (config.reasoningEffort.equals("max") || config.reasoningEffort.equals("xhigh") ? 65536 : 32768);
  }

  static void validate(ProviderSettings config) {
    validate(config.reasoningEffort);
    if (miniMaxM31(config) && !Arrays.asList(choices(config)).contains(config.reasoningEffort))
      throw new IllegalArgumentException("MiniMax-M3.1-Flash 不支持关闭思考或最低档，请在设置中选择低、中、高、更高、最高或模型默认");
    if (config.maxOutputTokens < 0 || config.maxOutputTokens > 131072)
      throw new IllegalArgumentException("输出长度上限须为 0（自动）至 131072");
  }

  static void apply(ProviderSettings config, JSONObject body) throws Exception {
    String effort = config.reasoningEffort;
    validate(config);
    if (!effort.equals("default")) {
      if (host(config, "api.deepseek.com")) {
        body.put("thinking", new JSONObject().put("type", effort.equals("none") ? "disabled" : "enabled"));
        if (!effort.equals("none")) body.put("reasoning_effort", effort);
      } else if (host(config, "openrouter.ai")) {
        body.put("reasoning", effort.equals("none")
            ? new JSONObject().put("enabled", false) : new JSONObject().put("effort", effort));
      } else {
        body.put("reasoning_effort", effort);
      }
    }
    // Reasoning shares the output budget; 2,500 tokens could leave no final JSON.
    int budget = outputLimit(config);
    body.remove("max_tokens");
    body.put(host(config, "api.openai.com") || host(config, "api.minimax.io")
        || host(config, "api.minimaxi.com") || host(config, "api.minimax.chat")
        ? "max_completion_tokens" : "max_tokens", budget);
  }

  static Spinner picker(PageActivity page, ProviderSettings config) {
    page.label("模型思考强度");
    Spinner spinner = new Spinner(page);
    String[] values = choices(config);
    List<String> labels = new ArrayList<>();
    for (String value : values) labels.add(label(value));
    // Preserve any saved extended effort when editing a different endpoint/model.
    if (!miniMaxM31(config) && !Arrays.asList(values).contains(config.reasoningEffort)) {
      values = VALUES.clone(); labels.clear(); for (String value : values) labels.add(label(value));
    }
    ArrayAdapter<String> adapter = new ArrayAdapter<>(page, android.R.layout.simple_spinner_dropdown_item, labels);
    spinner.setAdapter(adapter); spinner.setTag(values);
    spinner.setContentDescription("模型思考强度");
    spinner.setSelection(Math.max(0, Arrays.asList(values).indexOf(config.reasoningEffort)));
    page.panel.addView(spinner, new LinearLayout.LayoutParams(-1, page.dp(52)));
    if (miniMaxM31(config)) page.label("此模型必须开启思考，模型默认对应最高强度；想加快回答可选择低档。");
    return spinner;
  }

  static String outputLabel(int limit) { return limit == 0 ? "自动适配（推荐）" : limit + " Token"; }

  static Spinner outputPicker(PageActivity page, ProviderSettings config) {
    page.label("模型输出长度上限");
    Spinner spinner = new Spinner(page);
    List<Integer> limits = new ArrayList<>(); for (int value : OUTPUT_LIMITS) limits.add(value);
    if (!limits.contains(config.maxOutputTokens)) limits.add(config.maxOutputTokens);
    List<String> labels = new ArrayList<>(); for (int value : limits) labels.add(outputLabel(value));
    spinner.setAdapter(new ArrayAdapter<>(page, android.R.layout.simple_spinner_dropdown_item, labels));
    spinner.setTag(limits); spinner.setContentDescription("模型输出长度上限");
    spinner.setSelection(limits.indexOf(config.maxOutputTokens));
    page.panel.addView(spinner, new LinearLayout.LayoutParams(-1, page.dp(52)));
    return spinner;
  }

  static int selectedOutput(Spinner spinner) { return (Integer)((List<?>)spinner.getTag()).get(spinner.getSelectedItemPosition()); }

  static AlertDialog editOutput(Activity activity) {
    try {
      ProviderSettings config = ProviderSettings.load(activity);
      String[] labels = new String[OUTPUT_LIMITS.length]; int selected = -1;
      for (int i = 0; i < labels.length; i++) {
        labels[i] = outputLabel(OUTPUT_LIMITS[i]); if (OUTPUT_LIMITS[i] == config.maxOutputTokens) selected = i;
      }
      return new AlertDialog.Builder(activity).setTitle("输出长度上限 · " + config.name)
          .setSingleChoiceItems(labels, selected, (dialog, i) -> {
            ProviderSettings.storage(activity, config.id).edit().putInt("maxOutputTokens", OUTPUT_LIMITS[i]).apply();
            dialog.dismiss(); Toast.makeText(activity, "已保存：" + labels[i], Toast.LENGTH_SHORT).show();
          }).setNegativeButton("取消", null).show();
    } catch (Exception error) {
      return new AlertDialog.Builder(activity).setMessage("模型配置无法读取，请到提供商管理中重新配置。")
          .setPositiveButton("知道了", null).show();
    }
  }

  static String selected(Spinner spinner) {
    return ((String[])spinner.getTag())[spinner.getSelectedItemPosition()];
  }

  static AlertDialog editCurrent(Activity activity) {
    try {
      ProviderSettings config = ProviderSettings.load(activity);
      String[] values = choices(config), labels = new String[values.length];
      for (int i = 0; i < values.length; i++) labels[i] = label(values[i]);
      return new AlertDialog.Builder(activity)
          .setTitle(miniMaxM31(config) ? "思考强度 · 此模型不可关闭思考" : "思考强度 · " + config.name)
          .setSingleChoiceItems(labels, Arrays.asList(values).indexOf(config.reasoningEffort), (dialog, i) -> {
            config.reasoningEffort = values[i];
            ProviderSettings.storage(activity, config.id).edit().putString("reasoningEffort", values[i]).apply();
            dialog.dismiss();
            Toast.makeText(activity, "已保存：" + label(values[i]), Toast.LENGTH_SHORT).show();
          })
          .setNegativeButton("取消", null).show();
    } catch (Exception error) {
      return new AlertDialog.Builder(activity).setMessage("模型配置无法读取，请到提供商管理中重新配置。")
          .setPositiveButton("知道了", null).show();
    }
  }
}
