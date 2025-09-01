package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.models.PointRuleTaskType;
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

public class UserLoginFragment extends Fragment {
    private EditText etLoginname, etPassword;
    private ImageView ivPwdEye;
    private AlertDialog progressDialog;
    private final Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        etLoginname = view.findViewById(R.id.et_login_name);
        etPassword = view.findViewById(R.id.et_password);
        ivPwdEye = view.findViewById(R.id.iv_pwd_eye);

        // 密码可见性切换
        if (ivPwdEye != null && etPassword != null) {
            ivPwdEye.setOnClickListener(v -> {
                if (etPassword.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    ivPwdEye.setImageResource(R.drawable.ic_pwd_eye_open);
                } else {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    ivPwdEye.setImageResource(R.drawable.ic_pwd_eye_closed);
                }
                etPassword.setSelection(etPassword.getText().length());
            });
        }

        view.findViewById(R.id.btn_login).setOnClickListener(v -> doLogin());
        view.findViewById(R.id.btn_register).setOnClickListener(v -> openRegister());
    }

    private void openRegister() {
        UserAccountRegisterFragment frag = new UserAccountRegisterFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, frag)
                .addToBackStack(null)
                .commit();
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
     * 获取并缓存用户积分规则
     * @param userAccount 已登录的用户账号信息
     */
    private void fetchAndCachePointRules(UserAccount userAccount) {
        Log.d("UserLogin", "开始获取积分规则...");

        // 缓存Context，避免Fragment被移除后无法访问Context
        final android.content.Context appContext = requireContext().getApplicationContext();

        OkHttpClient client = new OkHttpClient();

        // GET请求，不需要请求体参数，直接调用findAll()
        Request request = new Request.Builder()
                .url(ApiConfig.API_POINT_RULES) // 请确保在ApiConfig中定义此常量
                .get() // GET请求
                .build();

        Log.d("UserLogin", "积分规则API请求URL: " + ApiConfig.API_POINT_RULES);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("UserLogin", "获取积分规则网络请求失败: " + e.getMessage());
                e.printStackTrace();
                // 积分规则获取失败不影响登录流程，仅记录日志
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("UserLogin", "积分规则API响应状态码: " + response.code());

                boolean successful = response.isSuccessful();
                String responseBody;
                try {
                    responseBody = response.body().string();
                    Log.d("UserLogin", "积分规则API响应内容: " + responseBody);
                } finally {
                    response.close();
                }

                if (successful && responseBody != null && !responseBody.isEmpty()) {
                    try {
                        // 解析积分规则列表
                        Type listType = new TypeToken<List<PointRuleTaskType>>(){}.getType();
                        List<PointRuleTaskType> pointRules = gson.fromJson(responseBody, listType);

                        Log.d("UserLogin", "解析到积分规则数量: " + (pointRules != null ? pointRules.size() : 0));

                        if (pointRules != null && !pointRules.isEmpty()) {
                            // 使用ApplicationContext保存到本地缓存，避免Fragment生命周期问题
                            UserUtils.savePointRules(appContext, pointRules);
                            Log.d("UserLogin", "积分规则缓存成功，共 " + pointRules.size() + " 条规则");

                            // 打印每条规则的详细信息
                            for (int i = 0; i < pointRules.size(); i++) {
                                PointRuleTaskType rule = pointRules.get(i);
                                Log.d("UserLogin", "规则" + i + ": taskType=" + rule.getTaskType() + ", pointAmount=" + rule.getPointAmount());
                            }
                        } else {
                            Log.w("UserLogin", "积分规则列表为空");
                        }
                    } catch (Exception e) {
                        Log.e("UserLogin", "解析积分规则数据失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    Log.w("UserLogin", "积分规则API返回失败，状态码: " + response.code() + ", 响应内容: " + responseBody);
                }
            }
        });
    }

    private void doLogin() {
        String loginName = etLoginname.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (password.length() < 6) {
            InfoPopup.showError(requireContext(), getString(R.string.error_password_format));
            return;
        }
        showLoading();
        OkHttpClient client = new OkHttpClient();
        JsonObject obj = new JsonObject();
        obj.addProperty("login_name", loginName);
        obj.addProperty("password", password);

        RequestBody body = RequestBody.create(obj.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(ApiConfig.API_ACCOUNT_LOGIN)
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    InfoPopup.showError(requireContext(), getString(R.string.error_login_network_fail));
                    Log.d("Login", "网络错误: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) {
                    response.close();
                    return;
                }
                boolean successful = response.isSuccessful();
                String resp;
                try {
                    resp = response.body().string();
                } finally {
                    response.close();
                }
                if (successful) {
                    UserAccount userAccount = gson.fromJson(resp, UserAccount.class);
                    if (userAccount == null || userAccount.getUserId() == null || userAccount.getUserId().isEmpty()) {
                        requireActivity().runOnUiThread(() -> {
                            hideLoading();
                            InfoPopup.showError(requireContext(), getString(R.string.error_login_data_fail));
                        });
                        return;
                    }
                    // 保存用户账号到缓存
                    UserUtils.saveUserAccount(requireContext(), userAccount);

                    // ====== 新增：获取并缓存积分规则 ======
                    fetchAndCachePointRules(userAccount);

                    // 通知MyInfoFragment刷新
                    Bundle bundle = new Bundle();
                    bundle.putString("latest_user_json", gson.toJson(userAccount));
                    getParentFragmentManager().setFragmentResult("user_account_changed", bundle);

                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        // 先缓存当前Activity
                        MainActivity main = (MainActivity) getActivity();

                        // 提前获取字符串，避免Fragment detach后无法获取
                        String successMessage = getString(R.string.success_login);

                        // 先pop（回到主界面）
                        requireActivity().getSupportFragmentManager().popBackStackImmediate(null, 0);

                        // popBackStack后Fragment已Detach，不能再用requireContext()和getActivity()
                        // 所以用Activity缓存变量
                        // 延迟100ms（等待UI稳定），再切tab
                        if (main != null) {
                            main.getWindow().getDecorView().postDelayed(() -> {
                                main.switchToTab(R.id.navigation_myInfo);
                                InfoPopup.showSuccess(main, successMessage);
                            }, 100);
                        }
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        InfoPopup.showError(requireContext(), getString(R.string.error_username_not_exist));
                    });
                }
            }
        });
    }
}