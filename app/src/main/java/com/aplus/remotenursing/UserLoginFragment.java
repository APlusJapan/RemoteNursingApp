package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.models.PointRuleTaskType;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.helper.DeviceInfoHelper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 手机号 + 短信验证码登录
 * - 验证码为空：发送验证码
 * - 有验证码：校验并登录
 * - 同机快捷登录逻辑保留
 */
public class UserLoginFragment extends Fragment {
    private EditText etPhoneNumber, etActivationCode; // activationCode 复用为“短信验证码”
    private View btnGetOtp;
    private AlertDialog progressDialog;
    private final Gson gson = new Gson();

    // 设备激活相关常量（保留原有快捷登录机制）
    private static final String PREFS_NAME = "device_info";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_LAST_LOGIN_PHONE = "last_login_phone";
    private static final String KEY_DEVICE_ACTIVATED = "device_activated";

    private CountDownTimer otpTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        etPhoneNumber    = view.findViewById(R.id.et_phone_number);
        etActivationCode = view.findViewById(R.id.et_activation_code);
        btnGetOtp        = view.findViewById(R.id.btn_get_otp);

        // 登录按钮（智能分流）
        view.findViewById(R.id.btn_login).setOnClickListener(v -> doLogin());

        // 在 onViewCreated 方法中的按钮点击事件修改为：
        if (btnGetOtp != null) {
            btnGetOtp.setOnClickListener(v -> {
                String phone = etPhoneNumber.getText().toString().trim();
                if (!isValidPhoneNumber(phone)) {
                    InfoPopup.showError(requireContext(), "请输入正确的手机号码");
                    return;
                }
                sendOtp(phone);
                // 注意：不再在这里直接启动倒计时
            });
        }

        // 同机快捷登录
        checkAutoLogin();
    }

    /**
     * 检查是否可以自动登录（同一设备，已激活）
     */
    private void checkAutoLogin() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDeviceId = prefs.getString(KEY_DEVICE_ID, "");
        String lastLoginPhone = prefs.getString(KEY_LAST_LOGIN_PHONE, "");
        boolean deviceActivated = prefs.getBoolean(KEY_DEVICE_ACTIVATED, false);
        String currentDeviceId = DeviceInfoHelper.getDeviceId(getContext());

        Log.d("UserLogin", "设备状态检查 - 保存的设备ID: " + savedDeviceId + ", 当前设备ID: " + currentDeviceId + ", 已激活: " + deviceActivated);

        if (!TextUtils.isEmpty(savedDeviceId) &&
                savedDeviceId.equals(currentDeviceId) &&
                deviceActivated &&
                !TextUtils.isEmpty(lastLoginPhone)) {
            etPhoneNumber.setText(lastLoginPhone);
            showQuickLoginOption(lastLoginPhone);
        }
    }

    /**
     * 显示自定义大尺寸快捷登录选项
     */
    private void showQuickLoginOption(String phoneNumber) {
        // 创建自定义Dialog - 使用默认样式或者自定义样式
        Dialog customDialog = new Dialog(requireContext());

        // 设置自定义布局
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quick_login, null);

        // 设置手机号显示
        TextView tvMessage = dialogView.findViewById(R.id.tv_quick_login_message);
        tvMessage.setText("检测到您之前在此设备已登录过\n手机号：" + phoneNumber + "\n是否直接登录？");

        // 设置按钮点击事件
        Button btnNo = dialogView.findViewById(R.id.btn_quick_login_no);
        Button btnYes = dialogView.findViewById(R.id.btn_quick_login_yes);

        btnNo.setOnClickListener(v -> {
            customDialog.dismiss();
            etPhoneNumber.setText("");
        });

        btnYes.setOnClickListener(v -> {
            customDialog.dismiss();
            doQuickLogin(phoneNumber);
        });

        // 设置Dialog属性
        customDialog.setContentView(dialogView);
        customDialog.setCancelable(true);

        // 设置Dialog窗口属性 - 关键：让弹窗占据更多屏幕空间
        Window window = customDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            // 设置宽度为屏幕宽度的90%
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            // 高度自适应，但设置最小高度
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);

            // 设置背景为透明，避免默认边框
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        customDialog.show();
    }
    /**
     * 快捷登录（同机免验证码）- 简化错误处理版本
     */
    private void doQuickLogin(String phoneNumber) {
        showLoading();

        OkHttpClient client = new OkHttpClient();
        JsonObject obj = new JsonObject();
        obj.addProperty("phone_number", phoneNumber);
        obj.addProperty("device_id", DeviceInfoHelper.getDeviceId(getContext()));
        obj.addProperty("quick_login", true);

        RequestBody body = RequestBody.create(obj.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(ApiConfig.API_ACCOUNT_QUICK_LOGIN)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    InfoPopup.showError(requireContext(), "网络连接失败，请检查网络后重试");
                    Log.d("QuickLogin", "网络错误: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) { response.close(); return; }

                String resp;
                try {
                    resp = response.body().string();
                } finally {
                    response.close();
                }

                requireActivity().runOnUiThread(() -> {
                    hideLoading();

                    try {
                        JsonObject jsonResponse = gson.fromJson(resp, JsonObject.class);

                        // 检查是否包含success字段且为true
                        if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                            // 登录成功，构造UserAccount对象
                            UserAccount userAccount = new UserAccount();
                            userAccount.setUserId(jsonResponse.has("userId") ? jsonResponse.get("userId").getAsString() : "");
                            userAccount.setLoginName(jsonResponse.has("login_name") ? jsonResponse.get("login_name").getAsString() : "");
                            userAccount.setNickName(jsonResponse.has("nick_name") ? jsonResponse.get("nick_name").getAsString() : "");
                            userAccount.setDeviceId(jsonResponse.has("device_id") ? jsonResponse.get("device_id").getAsString() : "");
                            userAccount.setAdminId(jsonResponse.has("admin_id") ? jsonResponse.get("admin_id").getAsString() : "");
                            userAccount.setUserType(jsonResponse.has("user_type") ? jsonResponse.get("user_type").getAsString() : "");

                            if (userAccount.getUserId() != null && !userAccount.getUserId().isEmpty()) {
                                // 验证后端返回的设备ID与本地设备ID是否一致
                                String currentDeviceId = DeviceInfoHelper.getDeviceId(getContext());
                                String serverDeviceId = jsonResponse.has("device_id") ? jsonResponse.get("device_id").getAsString() : "";

                                Log.d("QuickLogin", "设备ID验证 - 当前设备ID: " + currentDeviceId);
                                Log.d("QuickLogin", "设备ID验证 - 服务端设备ID: " + serverDeviceId);

                                if (!serverDeviceId.isEmpty() && !currentDeviceId.equals(serverDeviceId)) {
                                    Log.w("QuickLogin", "设备ID不匹配，拒绝快捷登录");
                                    clearDeviceActivationAndShowMessage();
                                    return;
                                }

                                handleLoginSuccess(userAccount);
                                return;
                            } else {
                                InfoPopup.showError(requireContext(), "登录数据异常，请重试");
                                return;
                            }
                        } else {
                            // success为false的情况，直接显示后端返回的错误信息
                            if (jsonResponse.has("message")) {
                                String errorMessage = jsonResponse.get("message").getAsString();
                                InfoPopup.showError(requireContext(), errorMessage);
                            } else {
                                InfoPopup.showError(requireContext(), "快捷登录失败，请使用短信验证码登录");
                            }
                        }

                    } catch (Exception e) {
                        Log.e("QuickLogin", "解析响应数据失败: " + e.getMessage());
                        InfoPopup.showError(requireContext(), "数据解析失败，请重试");
                    }

                    Log.d("QuickLogin", "Response code: " + response.code() + ", body: " + resp);
                });
            }
        });
    }

    private void showLoading() {
        if (progressDialog == null) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading, null);
            progressDialog = new AlertDialog.Builder(requireContext())
                    .setView(view)
                    .setCancelable(false)
                    .create();
        }
        progressDialog.show();
    }

    private void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * 获取并缓存用户积分规则（保留原逻辑）
     */
    private void fetchAndCachePointRules(UserAccount userAccount) {
        final Context appContext = requireContext().getApplicationContext();
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(ApiConfig.API_POINT_RULES)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { Log.e("UserLogin", "积分规则请求失败: " + e.getMessage()); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                boolean successful = response.isSuccessful();
                String responseBody;
                try { responseBody = response.body().string(); } finally { response.close(); }

                if (successful && responseBody != null && !responseBody.isEmpty()) {
                    try {
                        Type listType = new TypeToken<List<PointRuleTaskType>>(){}.getType();
                        List<PointRuleTaskType> pointRules = gson.fromJson(responseBody, listType);
                        if (pointRules != null && !pointRules.isEmpty()) {
                            UserUtils.savePointRules(appContext, pointRules);
                        }
                    } catch (Exception e) {
                        Log.e("UserLogin", "解析积分规则失败: " + e.getMessage());
                    }
                } else {
                    Log.w("UserLogin", "积分规则API返回失败: " + response.code());
                }
            }
        });
    }

    /**
     * 登录主逻辑：
     * - 验证码为空：发送验证码
     * - 有验证码：校验并登录
     */
    private void doLogin() {
        String phoneNumber = etPhoneNumber.getText().toString().trim();
        String otp = etActivationCode.getText().toString().trim();

        if (!isValidPhoneNumber(phoneNumber)) {
            InfoPopup.showError(requireContext(), "请输入正确的手机号码");
            return;
        }

        if (TextUtils.isEmpty(otp)) {
            sendOtp(phoneNumber);
            // 注意：不再在这里直接启动倒计时，而是在sendOtp成功后启动
            return;
        }

        if (otp.length() < 4) {
            InfoPopup.showError(requireContext(), "验证码格式不正确，请重新输入");
            return;
        }

        verifyOtp(phoneNumber, otp);
    }

    /**
     * 发送短信验证码 - 更新版本，只在成功时启动倒计时,处理后端统一返回格式
     */
    private void sendOtp(String phoneNumber) {
        showLoading();
        OkHttpClient client = new OkHttpClient();

        JsonObject obj = new JsonObject();
        obj.addProperty("phone", phoneNumber);
        obj.addProperty("scene", "login");

        RequestBody body = RequestBody.create(obj.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(ApiConfig.API_AUTH_OTP_REQUEST)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    InfoPopup.showError(requireContext(), "网络连接失败，请检查网络后重试");
                    Log.d("OTP", "request onFailure: " + e.getMessage());
                    // 发送失败，不启动倒计时
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) { response.close(); return; }

                String respText;
                try {
                    respText = response.body().string();
                } finally {
                    response.close();
                }

                requireActivity().runOnUiThread(() -> {
                    hideLoading();

                    try {
                        JsonObject jsonResponse = gson.fromJson(respText, JsonObject.class);

                        // 检查success字段
                        if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                            // 发送成功
                            InfoPopup.showSuccess(requireContext(), "验证码已发送，请查收短信");
                            // 只在成功时启动倒计时
                            if (btnGetOtp != null) {
                                startOtpCountDown(btnGetOtp);
                            }
                        } else {
                            // 发送失败，显示后端返回的具体错误信息，不启动倒计时
                            if (jsonResponse.has("message")) {
                                String errorMessage = jsonResponse.get("message").getAsString();
                                InfoPopup.showError(requireContext(), errorMessage);
                            } else {
                                InfoPopup.showError(requireContext(), "验证码发送失败，请稍后再试");
                            }
                            // 发送失败，不启动倒计时
                        }

                    } catch (Exception e) {
                        Log.e("OTP", "解析发送验证码响应失败: " + e.getMessage());
                        InfoPopup.showError(requireContext(), "验证码发送失败，请稍后再试");
                        // 解析失败，不启动倒计时
                    }

                    Log.d("OTP", "request response: " + respText);
                });
            }
        });
    }

    /**
     * 校验短信验证码并登录 - 修复版本
     */
    private void verifyOtp(String phoneNumber, String otp) {
        showLoading();
        OkHttpClient client = new OkHttpClient();

        JsonObject obj = new JsonObject();
        obj.addProperty("phone", phoneNumber);
        obj.addProperty("otp", otp);
        obj.addProperty("deviceId", DeviceInfoHelper.getDeviceId(getContext()));

        RequestBody body = RequestBody.create(obj.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(ApiConfig.API_AUTH_OTP_VERIFY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    InfoPopup.showError(requireContext(), "网络连接失败，请检查网络后重试");
                    Log.d("OTP", "verify onFailure: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) { response.close(); return; }

                String resp;
                try {
                    resp = response.body().string();
                } finally {
                    response.close();
                }

                requireActivity().runOnUiThread(() -> {
                    hideLoading();

                    if (response.isSuccessful()) {
                        // 成功响应，解析用户数据
                        try {
                            JsonObject jsonResponse = gson.fromJson(resp, JsonObject.class);

                            // 检查是否包含success字段且为true
                            if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                                // 构造UserAccount对象
                                UserAccount userAccount = new UserAccount();
                                userAccount.setUserId(jsonResponse.has("userId") ? jsonResponse.get("userId").getAsString() : "");
                                userAccount.setLoginName(jsonResponse.has("login_name") ? jsonResponse.get("login_name").getAsString() : "");
                                userAccount.setNickName(jsonResponse.has("nick_name") ? jsonResponse.get("nick_name").getAsString() : "");
                                userAccount.setDeviceId(jsonResponse.has("device_id") ? jsonResponse.get("device_id").getAsString() : "");
                                userAccount.setAdminId(jsonResponse.has("admin_id") ? jsonResponse.get("admin_id").getAsString() : "");
                                userAccount.setUserType(jsonResponse.has("user_type") ? jsonResponse.get("user_type").getAsString() : "");
                                // 可以根据需要添加更多字段

                                if (userAccount.getUserId() != null && !userAccount.getUserId().isEmpty()) {
                                    // 保存设备激活信息
                                    saveDeviceActivation(phoneNumber);
                                    handleLoginSuccess(userAccount);
                                    return;
                                } else {
                                    InfoPopup.showError(requireContext(), "登录数据异常，请重试");
                                    return;
                                }
                            }

                            // success为false的情况，显示后端返回的具体错误信息
                            if (jsonResponse.has("message")) {
                                String errorMessage = jsonResponse.get("message").getAsString();
                                InfoPopup.showError(requireContext(), errorMessage);
                            } else {
                                InfoPopup.showError(requireContext(), "验证码校验失败，请重试");
                            }

                        } catch (Exception e) {
                            Log.e("OTP", "解析响应数据失败: " + e.getMessage());
                            InfoPopup.showError(requireContext(), "数据解析失败，请重试");
                        }
                    } else {
                        // HTTP错误响应，尝试解析错误信息
                        try {
                            JsonObject jsonResponse = gson.fromJson(resp, JsonObject.class);
                            if (jsonResponse.has("message")) {
                                String errorMessage = jsonResponse.get("message").getAsString();
                                InfoPopup.showError(requireContext(), errorMessage);
                            } else {
                                InfoPopup.showError(requireContext(), "验证码校验失败 (HTTP " + response.code() + ")");
                            }
                        } catch (Exception e) {
                            // JSON解析失败，显示HTTP状态码相关的友好信息
                            String friendlyMessage;
                            switch (response.code()) {
                                case 400:
                                    friendlyMessage = "请求参数错误，请检查输入信息";
                                    break;
                                case 401:
                                    friendlyMessage = "验证码无效或已过期，请重新获取";
                                    break;
                                case 404:
                                    friendlyMessage = "用户不存在或已删除，请检查手机号";
                                    break;
                                case 403:
                                    friendlyMessage = "用户未激活，请联系管理员";
                                    break;
                                case 500:
                                    friendlyMessage = "服务器内部错误，请稍后重试";
                                    break;
                                default:
                                    friendlyMessage = "验证码无效或已过期，请重新获取";
                            }
                            InfoPopup.showError(requireContext(), friendlyMessage);
                        }
                    }

                    Log.d("OTP", "verify response code: " + response.code() + ", body: " + resp);
                });
            }
        });
    }

    /**
     * 获取验证码按钮 60s 倒计时 - 适老化改进版本
     */
    private void startOtpCountDown(View btnView) {
        if (!(btnView instanceof android.widget.Button)) return;
        android.widget.Button b = (android.widget.Button) btnView;

        if (otpTimer != null) otpTimer.cancel();

        // 禁用按钮
        b.setEnabled(false);

        otpTimer = new CountDownTimer(60_000, 1000) {
            @Override
            public void onTick(long ms) {
                // 显示倒计时，使用更大更醒目的文字
                int seconds = (int) (ms / 1000);
                b.setText(seconds + "秒后重试");

                // 设置倒计时时的视觉效果
                b.setTextColor(0xFF333333); // 深灰色文字，更容易看清
                b.setBackgroundResource(R.drawable.bg_btn_disabled_round); // 使用禁用状态的背景
            }

            @Override
            public void onFinish() {
                // 恢复按钮状态
                b.setEnabled(true);
                b.setText("获取验证码");
                b.setTextColor(0xFFFFFFFF); // 白色文字
                b.setBackgroundResource(R.drawable.bg_btn_primary_round); // 恢复原背景
            }
        }.start();
    }

    /**
     * 登录成功后的处理
     */
    private void handleLoginSuccess(UserAccount userAccount) {
        UserUtils.saveUserAccount(requireContext(), userAccount);
        fetchAndCachePointRules(userAccount);

        Bundle bundle = new Bundle();
        bundle.putString("latest_user_json", gson.toJson(userAccount));
        getParentFragmentManager().setFragmentResult("user_account_changed", bundle);

        requireActivity().runOnUiThread(() -> {
            hideLoading();
            MainActivity main = (MainActivity) getActivity();
            String successMessage = "登录成功";
            requireActivity().getSupportFragmentManager().popBackStackImmediate(null, 0);
            if (main != null) {
                main.getWindow().getDecorView().postDelayed(() -> {
                    main.switchToTab(R.id.navigation_myInfo);
                    InfoPopup.showSuccess(main, successMessage);
                }, 100);
            }
        });
    }

    /**
     * 保存设备激活信息（用于下次快捷登录）
     */
    private void saveDeviceActivation(String phoneNumber) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_DEVICE_ID, DeviceInfoHelper.getDeviceId(getContext()))
                .putString(KEY_LAST_LOGIN_PHONE, phoneNumber)
                .putBoolean(KEY_DEVICE_ACTIVATED, true)
                .apply();
        Log.d("UserLogin", "设备激活信息已保存: deviceId=" + DeviceInfoHelper.getDeviceId(getContext()) + ", phone=" + phoneNumber + ", activated=true");
    }

    /**
     * 获取设备ID
     */
//    private String getDeviceId() {
//        try {
//            String androidId = android.provider.Settings.Secure.getString(
//                    requireContext().getContentResolver(),
//                    android.provider.Settings.Secure.ANDROID_ID);
//
//            String manufacturer = android.os.Build.MANUFACTURER;
//            String model = android.os.Build.MODEL;
//            String serial = android.os.Build.SERIAL;
//
//            String deviceInfo = manufacturer + "_" + model + "_" + serial;
//
//            if (androidId != null && !androidId.isEmpty() && !"9774d56d682e549c".equals(androidId)) {
//                return androidId + "_" + deviceInfo.hashCode();
//            } else {
//                return String.valueOf(deviceInfo.hashCode());
//            }
//
//        } catch (Exception e) {
//            Log.e("UserLogin", "获取设备ID失败: " + e.getMessage());
//            long timestamp = System.currentTimeMillis();
//            int random = (int) (Math.random() * 10000);
//            return "device_" + timestamp + "_" + random;
//        }
//    }

    /**
     * 手机号校验（国内）
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^(\\+?86)?1[3-9]\\d{9}$");
    }
    /**
     * 清除设备激活状态并显示安全提示信息（强制重新登录）
     */
    private void clearDeviceActivationAndShowMessage() {
        Log.d("QuickLogin", "清除设备激活状态并强制重新登录");

        // 清除本地设备激活信息
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_DEVICE_ID)
                .remove(KEY_LAST_LOGIN_PHONE)
                .putBoolean(KEY_DEVICE_ACTIVATED, false)
                .apply();

        // 创建自定义Dialog
        Dialog customDialog = new Dialog(requireContext());

        // 设置自定义布局
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quick_login, null);

        // 设置消息内容
        TextView tvMessage = dialogView.findViewById(R.id.tv_quick_login_message);
        tvMessage.setText("检测到您的账号曾在其他设备上登录，为了账户安全，请使用短信验证码重新登录。");

        // 只显示一个确认按钮
        Button btnNo = dialogView.findViewById(R.id.btn_quick_login_no);
        Button btnYes = dialogView.findViewById(R.id.btn_quick_login_yes);

        // 隐藏左侧按钮，只保留右侧确认按钮
        btnNo.setVisibility(View.GONE);
        btnYes.setText("确定");

        btnYes.setOnClickListener(v -> {
            customDialog.dismiss();
            // 清空输入框并显示提示
            etPhoneNumber.setText("");
            etActivationCode.setText("");
            InfoPopup.showError(requireContext(), "请使用短信验证码重新登录");
        });

        // 设置Dialog属性
        customDialog.setContentView(dialogView);
        customDialog.setCancelable(false); // 不允许取消

        // 设置Dialog窗口属性
        Window window = customDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        customDialog.show();
    }
    @Override
    public void onDestroyView() {
        if (otpTimer != null) {
            otpTimer.cancel();
            otpTimer = null;
        }
        super.onDestroyView();
    }
}
