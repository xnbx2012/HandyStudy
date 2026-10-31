package com.handymath.app;

import android.app.Activity;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;
import org.json.*;

final class ProviderUi {
  static TextView picker(Activity activity) {
    TextView picker = new TextView(activity);
    picker.setContentDescription("快速切换模型提供商");
    picker.setTextSize(13);
    picker.setTextColor(0xff197d78);
    picker.setGravity(Gravity.CENTER_VERTICAL);
    picker.setSingleLine(true);
    picker.setEllipsize(android.text.TextUtils.TruncateAt.END);
    int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
    picker.setPadding(pad, pad / 2, pad, pad / 2);
    picker.setMinHeight(pad * 3);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xffe5f0ee);
    bg.setCornerRadius(pad);
    picker.setBackground(bg);
    try {
      ProviderSettings p = ProviderSettings.load(activity);
      picker.setText(p.name + " · " + p.model + "  ▾");
    } catch (Exception e) {
      picker.setText("选择模型  ▾");
    }
    picker.setOnClickListener(
        v -> {
          List<String> ids = ProviderSettings.ids(activity);
          List<String> names = new ArrayList<>();
          String active =
              activity.getSharedPreferences("provider-profiles", 0).getString("active", "legacy");
          int selected = 0;
          for (int i = 0; i < ids.size(); i++) {
            try {
              ProviderSettings p = ProviderSettings.load(activity, ids.get(i));
              names.add(p.name + " · " + p.model);
            } catch (Exception e) {
              names.add("配置不可读");
            }
            if (ids.get(i).equals(active)) selected = i;
          }
          new android.app.AlertDialog.Builder(activity)
              .setTitle("选择模型提供商")
              .setSingleChoiceItems(
                  names.toArray(new String[0]),
                  selected,
                  (d, n) -> {
                    ProviderSettings.activate(activity, ids.get(n));
                    picker.setText(names.get(n) + "  ▾");
                    d.dismiss();
                  })
              .setNegativeButton("取消", null)
              .show();
        });
    return picker;
  }

  static void refresh(Activity activity, ViewGroup group) {
    if (group == null) return;
    for (int i = group.getChildCount() - 1; i >= 0; i--) {
      View child = group.getChildAt(i);
      if ("快速切换模型提供商".equals(child.getContentDescription())) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        group.removeViewAt(i);
        group.addView(picker(activity), i, params);
      } else if (child instanceof ViewGroup) refresh(activity, (ViewGroup) child);
    }
  }

  static String balance(ProviderSettings config) throws Exception {
    String endpoint = config.balanceEndpoint();
    if (endpoint.isEmpty()) return "此提供商未配置可用余额接口";
    HttpURLConnection c = LlmTransport.connections.open(new URL(endpoint));
    c.setInstanceFollowRedirects(false);
    c.setConnectTimeout(15000);
    c.setReadTimeout(20000);
    c.setRequestProperty("Authorization", "Bearer " + config.key);
    try {
      if (c.getResponseCode() != 200)
        throw new java.io.IOException("余额接口不可用（HTTP " + c.getResponseCode() + "）");
      JSONObject reply =
          new JSONObject(
              new String(
                  BankRepository.read(c.getInputStream()),
                  java.nio.charset.StandardCharsets.UTF_8));
      JSONArray balances = reply.optJSONArray("balance_infos");
      if (balances == null) throw new java.io.IOException("余额接口格式未被识别；不会估算账户余额");
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < balances.length(); i++) {
        JSONObject b = balances.getJSONObject(i);
        text.append(b.getString("currency"))
            .append(" ")
            .append(b.getString("total_balance"))
            .append("\n");
      }
      String value = text.toString().trim();
      ProviderSettings.storage(config.context, config.id)
          .edit()
          .putString("balance", value)
          .putLong("balanceAt", System.currentTimeMillis())
          .apply();
      return value;
    } finally {
      c.disconnect();
    }
  }
}
