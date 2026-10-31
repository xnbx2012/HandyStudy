package com.handymath.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Multiple encrypted profiles; changes of endpoint/model/key require fresh capability detection.
 */
public final class ProvidersActivity extends PageActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public void onCreate(Bundle state) {
        super.onCreate(state);
        page("模型提供商");
        section("已配置的提供商", "可配置多个模型，题目页下拉切换；每个配置的密钥独立加密保存。");
        for (String id : ProviderSettings.ids(this))
            try {
                ProviderSettings p = ProviderSettings.load(this, id);
                action(p.name + " · " + p.model + "  ›", () -> edit(p));
            } catch (Exception e) {
                label("配置不可读，请重新添加");
            }
        action(
                "＋ 添加提供商",
                () -> {
                    ProviderSettings p = new ProviderSettings();
                    p.id = UUID.randomUUID().toString();
                    p.name = "新提供商";
                    edit(p);
                });
    }

    private EditText field(String title, String value, boolean secret) {
        label(title);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setTextSize(15);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(surface(0xfff1f5f4, 12));
        if (secret)
            e.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(e);
        return e;
    }

    private double price(EditText e) {
        String text = e.getText().toString().trim();
        return text.isEmpty() ? 0 : Double.parseDouble(text);
    }

    private void edit(ProviderSettings old) {
        page("提供商配置");
        section("连接与模型", "修改后保存，支持图片输入和 JSON 结构化输出");
        EditText name = field("显示名称", old.name, false),
                url = field("HTTPS Base URL", old.baseUrl, false),
                model = field("模型名称", old.model, false),
                key = field("API Key（本机加密保存）", old.key, true);
        Spinner thinking = ModelThinking.picker(this, old);
        label("可选择关闭思考或调整强度；可用档位取决于模型，修改后随此配置保存。");
        Spinner outputLimit = ModelThinking.outputPicker(this, old);
        label("思考和正文共享输出额度。长解析被截断时可调高；自动适配会为推理模型预留额度，实际可用上限由服务商决定。");
        section("Token 费率", "每百万 Token 的价格；留空表示未知。实际账单以提供商为准，不将不同币种相加。");
        EditText currency = field("币种代码", old.currency, false),
                input =
                        field(
                                "输入（未命中缓存）",
                                old.priceConfigured ? String.valueOf(old.inputPrice) : "",
                                false),
                output =
                        field(
                                "输出",
                                old.priceConfigured ? String.valueOf(old.outputPrice) : "",
                                false),
                cache =
                        field(
                                "缓存命中输入",
                                old.priceConfigured ? String.valueOf(old.cachePrice) : "",
                                false),
                write =
                        field(
                                "缓存写入输入（留空沿用未缓存输入价）",
                                old.priceConfigured ? String.valueOf(old.cacheWritePrice) : "",
                                false);
        section("余额接口", "DeepSeek 自动使用官方余额接口；其它提供商可填写同一主机的兼容接口，未知格式会明确标注不可查询。");
        EditText balance = field("HTTPS 余额 URL（可留空）", old.balanceUrl, false);
        action(
                "检测并保存 / 设为当前",
                () -> {
                    ProviderSettings next = new ProviderSettings();
                    next.id = old.id;
                    next.name = name.getText().toString().trim();
                    next.baseUrl = url.getText().toString().trim();
                    next.model = model.getText().toString().trim();
                    next.key = key.getText().toString().trim();
                    next.reasoningEffort = ModelThinking.selected(thinking);
                    next.maxOutputTokens = ModelThinking.selectedOutput(outputLimit);
                    next.balanceUrl = balance.getText().toString().trim();
                    next.currency = currency.getText().toString().trim().toUpperCase(Locale.ROOT);
                    try {
                        next.priceConfigured =
                                !input.getText().toString().trim().isEmpty()
                                        && !output.getText().toString().trim().isEmpty()
                                        && !cache.getText().toString().trim().isEmpty();
                        next.inputPrice = price(input);
                        next.outputPrice = price(output);
                        next.cachePrice = price(cache);
                        next.cacheWritePrice =
                                write.getText().toString().trim().isEmpty()
                                        ? next.inputPrice
                                        : price(write);
                        next.validate();
                    } catch (Exception e) {
                        failure(e);
                        return;
                    }
                    loading("正在保存并核对图片识别能力…");
                    worker.execute(
                            () -> {
                                try {
                                    boolean same =
                                            old.visionVerified
                                                    && next.baseUrl.equals(old.baseUrl)
                                                    && next.model.equals(old.model)
                                                    && next.key.equals(old.key);
                                    next.context = getApplicationContext();
                                    if (same) next.visionVerified = true;
                                    else TutorClient.verifyVision(next);
                                    next.save(this);
                                    runOnUiThread(
                                            () -> {
                                                if (!isDestroyed()) {
                                                    loaded();
                                                    Toast.makeText(
                                                                    this,
                                                                    "已保存并切换",
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                    recreate();
                                                }
                                            });
                                } catch (Exception e) {
                                    runOnUiThread(
                                            () -> {
                                                if (!isDestroyed()) failure(e);
                                            });
                                }
                            });
                });
        if (ProviderSettings.ids(this).contains(old.id))
            action(
                    "删除此配置",
                    () ->
                            new AlertDialog.Builder(this)
                                    .setMessage("删除此提供商配置？历史 Token 与费用记录会保留。")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton(
                                            "删除",
                                            (d, w) -> {
                                                ProviderSettings.delete(this, old.id);
                                                recreate();
                                            })
                                    .show());
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
