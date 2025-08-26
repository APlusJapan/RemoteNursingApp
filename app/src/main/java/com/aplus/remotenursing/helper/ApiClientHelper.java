package com.aplus.remotenursing.helper;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/**
 * 全局复用的 OkHttp 客户端。
 * - 统一超时/连接池配置
 * - 便于挂拦截器（日志、鉴权等）
 * - 避免每次请求都创建新实例
 */
public final class ApiClientHelper {

    private static volatile OkHttpClient CLIENT;

    private ApiClientHelper() { /* no-op */ }

    public static OkHttpClient get() {
        if (CLIENT == null) {
            synchronized (ApiClientHelper.class) {
                if (CLIENT == null) {
                    CLIENT = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            // .addInterceptor(new HttpLoggingInterceptor().setLevel(Level.BASIC)) // 需要日志就开
                            // .cache(new Cache(new File(context.getCacheDir(), "okhttp"), 50L * 1024 * 1024)) // 如需HTTP缓存
                            .build();
                }
            }
        }
        return CLIENT;
    }
}
