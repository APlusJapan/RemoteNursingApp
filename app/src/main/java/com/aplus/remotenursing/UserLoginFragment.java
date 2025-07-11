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

import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

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
            progressDialog = new AlertDialog.Builder(requireContext())
                    .setMessage("请稍候...")
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

    private void doLogin() {
        String loginName = etLoginname.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (password.length() < 6) {
            Toast.makeText(requireContext(), getString(R.string.error_password_format), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(requireContext(), "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.d("Login", "网络错误: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) return;
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    UserAccount userAccount = gson.fromJson(resp, UserAccount.class);
                    if (userAccount == null || userAccount.getUserId() == null || userAccount.getUserId().isEmpty()) {
                        requireActivity().runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(requireContext(), "登录失败，数据异常", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    // 统一用UserUtil保存用户账号（推荐）
                    UserUtil.saveUserAccount(requireContext(), userAccount);

                    // 通知MyInfoFragment刷新
                    Bundle bundle = new Bundle();
                    bundle.putString("latest_user_json", gson.toJson(userAccount));
                    getParentFragmentManager().setFragmentResult("user_account_changed", bundle);

                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        // 跳转到用户信息页（MyInfoFragment会自动联网校验，状态绝不会乱）
                        MyInfoFragment frag = new MyInfoFragment();
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, frag)
                                .commit();
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(requireContext(), getString(R.string.error_username_not_exist), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}
