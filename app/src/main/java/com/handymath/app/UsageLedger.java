package com.handymath.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

import org.json.*;

import java.util.*;

/**
 * Provider-reported tokens only; estimated fees snapshot per request, currencies never added
 * together.
 */
final class UsageLedger extends SQLiteOpenHelper {
    static final ThreadLocal<String[]> scope = new ThreadLocal<>();

    UsageLedger(Context c) {
        super(c, "api-usage.db", null, 1);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE usage(id INTEGER PRIMARY KEY,at INTEGER,bank TEXT,q TEXT,provider"
                    + " TEXT,name TEXT,model TEXT,prompt INTEGER,output INTEGER,cache"
                    + " INTEGER,cache_write INTEGER,cost REAL,currency TEXT,data TEXT,error TEXT)");
    }

    public void onUpgrade(SQLiteDatabase d, int a, int b) {}

    static void record(ProviderSettings config, JSONObject usage, String error) {
        if (config.context == null) return;
        try (UsageLedger ledger = new UsageLedger(config.context)) {
            JSONObject u = usage == null ? new JSONObject() : usage;
            long prompt = u.optLong("prompt_tokens", u.optLong("input_tokens", -1)),
                    output = u.optLong("completion_tokens", u.optLong("output_tokens", -1));
            JSONObject details = u.optJSONObject("prompt_tokens_details");
            if (details == null) details = u.optJSONObject("input_tokens_details");
            long cache =
                    u.optLong(
                            "prompt_cache_hit_tokens",
                            u.optLong(
                                    "cache_read_input_tokens",
                                    details == null ? -1 : details.optLong("cached_tokens", -1)));
            long write = u.optLong("cache_creation_input_tokens", 0);
            if (!u.has("prompt_tokens") && u.has("cache_read_input_tokens"))
                prompt += Math.max(0, cache) + Math.max(0, write);
            if (cache > prompt && prompt >= 0) cache = -1;
            ContentValues v = new ContentValues();
            String[] context = scope.get();
            v.put("at", System.currentTimeMillis());
            v.put("bank", context == null ? "" : context[0]);
            v.put("q", context == null ? "" : context[1]);
            v.put("provider", config.id);
            v.put("name", config.name);
            v.put("model", config.model);
            v.put("currency", config.currency);
            v.put("data", u.toString());
            v.put("error", error == null ? "" : error);
            if (prompt >= 0) v.put("prompt", prompt);
            if (output >= 0) v.put("output", output);
            if (cache >= 0) v.put("cache", cache);
            v.put("cache_write", write);
            if (config.priceConfigured
                    && prompt >= 0
                    && output >= 0
                    && (cache >= 0 || config.cachePrice == config.inputPrice)) {
                long hit = Math.max(0, cache);
                double cost =
                        (Math.max(0, prompt - hit - write) * config.inputPrice
                                        + hit * config.cachePrice
                                        + write * config.cacheWritePrice
                                        + output * config.outputPrice)
                                / 1000000d;
                v.put("cost", cost);
            }
            ledger.getWritableDatabase().insertOrThrow("usage", null, v);
        } catch (Exception ignored) {
        }
    }

    JSONArray rows(String bank) throws Exception {
        JSONArray out = new JSONArray();
        try (Cursor c =
                getReadableDatabase()
                        .query(
                                "usage",
                                null,
                                bank.isEmpty() ? null : "bank=?",
                                bank.isEmpty() ? null : new String[] {bank},
                                null,
                                null,
                                "at DESC,id DESC")) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String name = c.getColumnName(i);
                    Object value =
                            c.isNull(i)
                                    ? JSONObject.NULL
                                    : switch (c.getType(i)) {
                                        case Cursor.FIELD_TYPE_INTEGER -> c.getLong(i);
                                        case Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i);
                                        default -> c.getString(i);
                                    };
                    row.put(name, value);
                }
                out.put(row);
            }
        }
        return out;
    }
}
