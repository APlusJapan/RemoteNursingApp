package com.aplus.remotenusing.common;

import android.content.Context;
import android.content.SharedPreferences;
import com.aplus.remotenursing.models.UserAccount;
import com.google.gson.Gson;

public class UserUtil {

    public static void saveUserAccount(Context context, UserAccount account) {
        SharedPreferences sp = context.getSharedPreferences(Contants.LOCAL_FILE_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(Contants.LOCAL_FILE_JSON_KEY, new Gson().toJson(account)).apply();
    }

    public static UserAccount getUserAccount(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Contants.LOCAL_FILE_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(Contants.LOCAL_FILE_JSON_KEY, null);
        if (json != null) {
            return new Gson().fromJson(json, UserAccount.class);
        }
        return null;
    }

    public static String loadUserId(Context context) {
        UserAccount account = getUserAccount(context);
        return account != null ? account.getUserId() : null;
    }

    public static void logout(Context context) {
        SharedPreferences sp = context.getSharedPreferences(Contants.LOCAL_FILE_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }
}
