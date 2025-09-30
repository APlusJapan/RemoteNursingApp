package com.aplus.remotenursing.helper;


import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;


public final class ApiClientHelper {
    private static volatile OkHttpClient CLIENT;

    private ApiClientHelper() { }

    public static OkHttpClient get() {
        if (CLIENT == null) {
            synchronized (ApiClientHelper.class) {
                if (CLIENT == null) {
                    CLIENT = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                }
            }
        }
        return CLIENT;
    }
}
