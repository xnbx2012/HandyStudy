package com.handymath.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.*;
import android.util.Base64;

import org.json.*;

import java.net.URI;
import java.security.KeyStore;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials stay on-device, encrypted by an Android Keystore non-exportable key. */
final class ProviderSettings {
    String baseUrl = "https://api.deepseek.com", model = "deepseek-flash", key = "";
    String id = "legacy", name = "当前提供商", currency = "CNY", balanceUrl = "";
    String reasoningEffort = "default";
    int maxOutputTokens = 0;
    double inputPrice = 0, outputPrice = 0, cachePrice = 0, cacheWritePrice = 0;
    boolean priceConfigured = false;
    boolean visionVerified;
    Context context;
    private static final String ALIAS = "handymath-provider-v1";

    private static javax.crypto.SecretKey secret() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator g =
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(
                    new KeyGenParameterSpec.Builder(
                                    ALIAS,
                                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey) ks.getKey(ALIAS, null);
    }

    static ProviderSettings load(Context c) throws Exception {
        return load(
                c, c.getSharedPreferences("provider-profiles", 0).getString("active", "legacy"));
    }

    static SharedPreferences storage(Context c, String id) {
        return c.getSharedPreferences(id.equals("legacy") ? "provider" : "provider-" + id, 0);
    }

    static List<String> ids(Context c) {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray a =
                    new JSONArray(
                            c.getSharedPreferences("provider-profiles", 0)
                                    .getString("ids", "[\"legacy\"]"));
            for (int i = 0; i < a.length(); i++) ids.add(a.getString(i));
        } catch (Exception ignored) {
            ids.add("legacy");
        }
        return ids;
    }

    static void activate(Context c, String id) {
        if (ids(c).contains(id))
            c.getSharedPreferences("provider-profiles", 0).edit().putString("active", id).apply();
    }

    static void delete(Context c, String id) {
        List<String> list = ids(c);
        list.remove(id);
        storage(c, id).edit().clear().apply();
        if (list.isEmpty()) list.add("legacy");
        c.getSharedPreferences("provider-profiles", 0)
                .edit()
                .putString("ids", new JSONArray(list).toString())
                .putString("active", list.get(0))
                .apply();
    }

    static ProviderSettings load(Context c, String id) throws Exception {
        SharedPreferences p = storage(c, id);
        ProviderSettings s = new ProviderSettings();
        s.context = c.getApplicationContext();
        s.id = id;
        s.name = p.getString("name", s.name);
        s.currency = p.getString("currency", "CNY");
        s.balanceUrl = p.getString("balanceUrl", "");
        s.priceConfigured = p.getBoolean("priced", false);
        s.inputPrice = Double.longBitsToDouble(p.getLong("inputPrice", 0));
        s.outputPrice = Double.longBitsToDouble(p.getLong("outputPrice", 0));
        s.cachePrice = Double.longBitsToDouble(p.getLong("cachePrice", 0));
        s.cacheWritePrice = Double.longBitsToDouble(p.getLong("cacheWritePrice", 0));
        s.baseUrl = p.getString("url", s.baseUrl);
        s.model = p.getString("model", s.model);
        // Existing DeepSeek profiles used a fixed non-thinking request; preserve that choice.
        s.reasoningEffort = p.getString("reasoningEffort",
                ModelThinking.host(s, "api.deepseek.com") ? "none" : "default");
        s.maxOutputTokens = p.getInt("maxOutputTokens", 0);
        s.visionVerified = p.getBoolean("vision", false);
        String encrypted = p.getString("key", "");
        if (!encrypted.isEmpty()) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secret(),
                    new GCMParameterSpec(
                            128, Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)));
            s.key =
                    new String(
                            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                            java.nio.charset.StandardCharsets.UTF_8);
        }
        return s;
    }

    void validate() throws Exception {
        ModelThinking.validate(this);
        URI u = new URI(baseUrl);
        if (!"https".equalsIgnoreCase(u.getScheme())
                || u.getHost() == null
                || u.getUserInfo() != null
                || u.getQuery() != null
                || u.getFragment() != null)
            throw new IllegalArgumentException("Base URL 必须是完整 HTTPS 地址（不能含查询参数）");
        if (!id.matches("[A-Za-z0-9_-]{1,80}") || name.trim().isEmpty())
            throw new IllegalArgumentException("请填写提供商名称");
        for (double value : new double[] {inputPrice, outputPrice, cachePrice, cacheWritePrice})
            if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("费率须为非负有限数值");
        if (!currency.matches("[A-Z]{3,8}"))
            throw new IllegalArgumentException("币种请填写 CNY 或 USD 等代码");
        if (!balanceUrl.isEmpty()) {
            URI b = new URI(balanceUrl);
            if (!"https".equalsIgnoreCase(b.getScheme())
                    || !u.getHost().equalsIgnoreCase(b.getHost())
                    || b.getUserInfo() != null
                    || b.getFragment() != null
                    || b.getQuery() != null)
                throw new IllegalArgumentException("余额接口需为同一提供商主机的 HTTPS 地址");
        }
        if (key.trim().isEmpty() || model.trim().isEmpty())
            throw new IllegalArgumentException("请填写 API Key 和模型名称");
    }

    String balanceEndpoint() throws Exception {
        if (!balanceUrl.isEmpty()) return balanceUrl;
        java.net.URL u = new java.net.URL(baseUrl);
        return u.getHost().equalsIgnoreCase("api.deepseek.com")
                ? "https://api.deepseek.com/user/balance"
                : "";
    }

    String endpoint() {
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    void save(Context c) throws Exception {
        validate();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret());
        String encrypted =
                Base64.encodeToString(
                        cipher.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        Base64.NO_WRAP);
        context = c.getApplicationContext();
        List<String> profiles = ids(c);
        if (!profiles.contains(id)) profiles.add(id);
        c.getSharedPreferences("provider-profiles", 0)
                .edit()
                .putString("ids", new JSONArray(profiles).toString())
                .putString("active", id)
                .apply();
        storage(c, id)
                .edit()
                .putString("name", name)
                .putString("currency", currency)
                .putString("balanceUrl", balanceUrl)
                .putBoolean("priced", priceConfigured)
                .putLong("inputPrice", Double.doubleToLongBits(inputPrice))
                .putLong("outputPrice", Double.doubleToLongBits(outputPrice))
                .putLong("cachePrice", Double.doubleToLongBits(cachePrice))
                .putLong("cacheWritePrice", Double.doubleToLongBits(cacheWritePrice))
                .putString("url", baseUrl)
                .putString("model", model)
                .putString("reasoningEffort", reasoningEffort)
                .putInt("maxOutputTokens", maxOutputTokens)
                .putBoolean("vision", visionVerified)
                .putString("key", encrypted)
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }
}
