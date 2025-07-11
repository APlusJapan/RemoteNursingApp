package com.aplus.remotenursing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MyInfoFragment extends Fragment {

    private TextView tvUserName;
    private TextView tvLoginState;
    private ImageView ivAvatar;
    private Button btnLogin, btnLogout;
    private View  cardUserInfoRegister;
    private final Gson gson = new Gson();
    private boolean isLoggedIn = false;
    private UserAccount userAccount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_myinfo, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserInfo();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvUserName = view.findViewById(R.id.tv_username);
        tvLoginState = view.findViewById(R.id.tv_login_state);
        ivAvatar = view.findViewById(R.id.iv_avatar);

        cardUserInfoRegister = view.findViewById(R.id.userinfo_register);

        btnLogin = view.findViewById(R.id.btn_login);
        btnLogout = view.findViewById(R.id.btn_logout);

        btnLogin.setOnClickListener(v -> openLogin());
        btnLogout.setOnClickListener(v -> {
            UserUtil.logout(requireContext());
            Toast.makeText(getContext(), "已退出登录", Toast.LENGTH_SHORT).show();
            showNotLoggedIn();
        });

        // Fragment间通讯：如注册/登录结果回调
        getParentFragmentManager().setFragmentResultListener("user_account_changed", this, (key, bundle) -> {
            String userJson = bundle.getString("latest_user_json", null);
            if (userJson != null) {
                userAccount = gson.fromJson(userJson, UserAccount.class);
                Log.d("MyAccountFragment", "FragmentResultListener, set user: " + userJson);
                showLoggedIn(userAccount);
                // 使用UserUtil保存
                UserUtil.saveUserAccount(requireContext(), userAccount);
            } else {
                showNotLoggedIn();
            }
        });

        // 初始化：从UserUtil加载
        userAccount = UserUtil.getUserAccount(requireContext());
        if (userAccount != null) {
            Log.d("MyAccountFragment", "onViewCreated, load user: " + gson.toJson(userAccount));
            showLoggedIn(userAccount);
        } else {
            showNotLoggedIn();
        }
        // 跳转到用户信息录入页面
        cardUserInfoRegister.setOnClickListener(v -> {
            if (!LoginChecker.checkLogin(this)) return;
            UserInfoRegisterFragment frag = new UserInfoRegisterFragment();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, frag)
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void openLogin() {
        // 跳转到登录页
        UserLoginFragment frag = new UserLoginFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, frag)
                .addToBackStack(null)
                .commit();
    }

    private void loadUserInfo() {
        userAccount = UserUtil.getUserAccount(requireContext());

        Log.d("MyAccountFragment", "userAccount=" + (userAccount != null ? gson.toJson(userAccount) : "null"));
        Log.d("MyAccountFragment", "userId=" + (userAccount != null ? userAccount.getUserId() : "null"));
        Log.d("MyAccountFragment", "loginName=" + (userAccount != null ? userAccount.getLoginName() : "null"));

        // 本地已登录，联网后台检查有效性
        if (userAccount != null && userAccount.getUserId() != null && !userAccount.getUserId().isEmpty()) {
            showLoggedIn(userAccount);

            String url = ApiConfig.API_USER_ACCOUNT + userAccount.getUserId();
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("MyAccountFragment", "网络不可用，保留本地登录状态，仅提示");
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String resp = response.body().string();
                        UserAccount remote = gson.fromJson(resp, UserAccount.class);
                        if (remote != null && remote.getUserId() != null && !remote.getUserId().isEmpty()) {
                            userAccount = remote;
                            UserUtil.saveUserAccount(requireContext(), remote);
                            if (getActivity() != null) getActivity().runOnUiThread(() -> showLoggedIn(remote));
                            return;
                        }
                    }
                }
            });
        } else {
            showNotLoggedIn();
        }
    }

    private void showLoggedIn(UserAccount account) {
        isLoggedIn = true;

        // 提取手机后4位
        String phoneSuffix = "";
        if (account.getLoginName() != null && account.getLoginName().length() >= 4) {
            phoneSuffix = account.getLoginName().substring(account.getLoginName().length() - 4);
        }
        String displayName = "用户" + phoneSuffix;

        tvUserName.setText(displayName);
        tvLoginState.setText(getString(R.string.myinfo_logged_in));
        btnLogin.setVisibility(View.GONE);
        btnLogout.setVisibility(View.VISIBLE);
    }

    private void showNotLoggedIn() {
        isLoggedIn = false;
        tvUserName.setText("未登录");
        tvLoginState.setText(getString(R.string.myinfo_not_logged_in));
        btnLogin.setVisibility(View.VISIBLE);
        btnLogout.setVisibility(View.GONE);
    }
}
