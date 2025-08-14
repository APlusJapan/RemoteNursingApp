package com.aplus.remotenursing.manager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.method.LinkMovementMethod;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

public class PrivacyConsentManager {
    private static final String SP_NAME = "privacy_consent";
    private static final String KEY_ACCEPTED = "accepted";
    private static final String KEY_VERSION  = "version";

    // 如更新隐私政策，改这个版本号即可触发再次同意
    private static final int POLICY_VERSION = 1;

    public static boolean needsConsent(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        boolean accepted = sp.getBoolean(KEY_ACCEPTED, false);
        int ver = sp.getInt(KEY_VERSION, 0);
        return !accepted || ver != POLICY_VERSION;
    }

    public static void showConsentIfNeeded(Activity act, Runnable onAccepted, Runnable onDeclined) {
        if (!needsConsent(act)) { if (onAccepted != null) onAccepted.run(); return; }

        SpannableStringBuilder ssb = new SpannableStringBuilder(
                "欢迎使用本应用！请阅读并同意《隐私政策》和《用户协议》以继续使用。"
        );
        // 这两个链接可以指向你 asset/html 或在线页
        ssb.append("\n\n")
                .append("《隐私政策》：")
                .append("点击查看");
        int start1 = ssb.length() - "点击查看".length();
        ssb.setSpan(new URLSpan("https://your.domain/privacy.html"), start1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("\n")
                .append("《用户协议》：")
                .append("点击查看");
        int start2 = ssb.length() - "点击查看".length();
        ssb.setSpan(new URLSpan("https://your.domain/terms.html"), start2, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        AlertDialog dialog = new AlertDialog.Builder(act)
                .setTitle("隐私保护指引")
                .setMessage(ssb)
                .setCancelable(false)
                .setPositiveButton("同意并继续", (d, w) -> {
                    SharedPreferences sp = act.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
                    sp.edit().putBoolean(KEY_ACCEPTED, true).putInt(KEY_VERSION, POLICY_VERSION).apply();
                    if (onAccepted != null) onAccepted.run();
                })
                .setNegativeButton("不同意并退出", (d, w) -> {
                    if (onDeclined != null) onDeclined.run();
                    act.finish();
                })
                .create();

        dialog.setOnShowListener(v -> {
            // 让超链接可点击
            TextView tv = dialog.findViewById(android.R.id.message);
            if (tv != null) tv.setMovementMethod(LinkMovementMethod.getInstance());
        });
        dialog.show();
    }
}
