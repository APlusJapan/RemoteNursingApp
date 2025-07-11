package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.models.UserAccount;
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

public class UserAccountRegisterFragment extends Fragment {
    private EditText etLoginname, etPassword, etPasswordConfirm;
    private ImageView ivPwdEye, ivPwdEyeConfirm;
    private AlertDialog progressDialog;
    private final Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_account_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 返回
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        etLoginname = view.findViewById(R.id.et_login_name);
        etPassword = view.findViewById(R.id.et_password);
        etPasswordConfirm = view.findViewById(R.id.et_password_confirm);
        ivPwdEye = view.findViewById(R.id.iv_pwd_eye);
        ivPwdEyeConfirm = view.findViewById(R.id.iv_pwd_eye_confirm);
        view.findViewById(R.id.btn_register).setOnClickListener(v -> doRegister());

        // 密码可见性切换（主密码框）
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

        // 密码可见性切换（确认密码框）
        ivPwdEyeConfirm.setOnClickListener(v -> {
            if (etPasswordConfirm.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                etPasswordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivPwdEyeConfirm.setImageResource(R.drawable.ic_pwd_eye_open);
            } else {
                etPasswordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivPwdEyeConfirm.setImageResource(R.drawable.ic_pwd_eye_closed);
            }
            etPasswordConfirm.setSelection(etPasswordConfirm.getText().length());
        });
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

    private void doRegister() {
        String loginName = etLoginname.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm = etPasswordConfirm.getText().toString();
        if (password.length() < 6) {
            Toast.makeText(requireContext(), "密码至少6位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(requireContext(), "两次输入密码不一致", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading();
        OkHttpClient client = new OkHttpClient();
        JsonObject obj = new JsonObject();
        obj.addProperty("login_name", loginName);
        obj.addProperty("password", password);
        RequestBody body = RequestBody.create(obj.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("http://192.168.2.9:8080/api/account/register")
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
                    UserAccount userAccount = gson.fromJson(resp, UserAccount.class);
                    if (userAccount == null || userAccount.getUserId() == null || userAccount.getUserId().isEmpty()) {
                        requireActivity().runOnUiThread(() -> {
                            hideLoading();
                            Toast.makeText(requireContext(), "注册失败，数据异常", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    // 注册成功直接保存
                    SharedPreferences sp = requireContext().getSharedPreferences("user_account", Context.MODE_PRIVATE);
                    sp.edit().putString("data", gson.toJson(userAccount)).apply();

                    // 通知刷新
                    Bundle bundle = new Bundle();
                    bundle.putString("latest_user_json", gson.toJson(userAccount));
                    getParentFragmentManager().setFragmentResult("user_account_changed", bundle);

                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        // 跳转到用户信息页（MyInfoFragment会联网校验userId，不会乱）
                        MyInfoFragment frag = new MyInfoFragment();
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, frag)
                                .commit();
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(requireContext(), "该账号已存在", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

}
