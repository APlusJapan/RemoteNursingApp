package com.aplus.remotenursing.manager;

import android.util.Log;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.models.CodeItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 统一从后端 code-master 接口按 codeType 拉取码表的 Manager。
 * 后端接口：ApiConfig.API_CODE_MASTER
 * 约定：GET ?codeType=XXXX
 * 返回：JSON Array，每项包含 { code, value, value_type }
 */
public class CodeMasterManager {

    private static final String TAG = "CodeMasterManager";

    private final OkHttpClient client;

    public interface CodeListCallback {
        void onSuccess(List<CodeItem> list);
        void onFailure(Throwable t);
    }

    public CodeMasterManager() {
        this.client = new OkHttpClient();
    }

    /**
     * 异步拉取码表列表
     * @param codeType 例如 "SMARTWATCH_TYPE"
     * @param callback 回调
     */
    public void fetchCodeList(String codeType, CodeListCallback callback) {
        try {
            String encoded = URLEncoder.encode(codeType, StandardCharsets.UTF_8.name());

            HttpUrl url = HttpUrl.parse(ApiConfig.API_CODE_MASTER)
                    .newBuilder()
                    .addQueryParameter("codeType", encoded)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "fetchCodeList failed: " + e.getMessage(), e);
                    if (callback != null) callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        IOException ex = new IOException("HTTP " + response.code());
                        Log.e(TAG, "fetchCodeList http error: " + ex.getMessage());
                        if (callback != null) callback.onFailure(ex);
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : "[]";
                    try {
                        JSONArray arr = new JSONArray(body);
                        List<CodeItem> result = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            CodeItem item = new CodeItem(
                                    o.optString("code", ""),
                                    o.optString("value", ""),
                                    o.optString("value_type", "")
                            );
                            result.add(item);
                        }
                        if (callback != null) callback.onSuccess(result);
                    } catch (Exception parseEx) {
                        Log.e(TAG, "parse error: " + parseEx.getMessage(), parseEx);
                        if (callback != null) callback.onFailure(parseEx);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "build request error: " + e.getMessage(), e);
            if (callback != null) callback.onFailure(e);
        }
    }

    /**
     * 空安全返回工具
     */
    public static List<CodeItem> safe(List<CodeItem> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
