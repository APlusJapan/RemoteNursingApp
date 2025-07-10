package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

import com.aplus.remotenursing.models.UserInfo;

public class UserLoginFragment extends Fragment {
    private EditText etUsername, etPassword;
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
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
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
                    .setView(R.layout.dialog_loading)
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

    // ... 省略 import 和 class 头部

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (password.length() < 6) {
            Toast.makeText(requireContext(), getString(R.string.error_password_format), Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading();
        OkHttpClient client = new OkHttpClient();
        JsonObject obj = new JsonObject();
        obj.addProperty("username", username);
        obj.addProperty("password", password);
        RequestBody body = RequestBody.create(obj.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("http://192.168.2.9:8080/api/account/login")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) return;
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    // 推荐后端返回完整UserInfo json，否则这里需要拼装UserInfo对象
                    UserInfo userInfo = gson.fromJson(resp, UserInfo.class);
                    if (userInfo == null) {
                        requireActivity().runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(requireContext(), "登录失败，数据异常", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                    sp.edit().putString("data", gson.toJson(userInfo)).apply();
                    Bundle bundle = new Bundle();
                    bundle.putString("latest_user_json", gson.toJson(userInfo));
                    getParentFragmentManager().setFragmentResult("user_info_changed", bundle);
                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        requireActivity().getSupportFragmentManager().popBackStack();
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