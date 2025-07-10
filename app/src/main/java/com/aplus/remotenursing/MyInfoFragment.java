package com.aplus.remotenursing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.aplus.remotenursing.UserLoginFragment;

import com.aplus.remotenursing.models.UserInfo;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.util.Log;

public class MyInfoFragment extends Fragment {

    private TextView tvUserName;
    private TextView tvLoginState;
    private final Gson gson = new Gson();
    private boolean isLoggedIn = false;
    private UserInfo currentInfo;

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

    // onViewCreated 只做一次初始化，监听 user_info_changed
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvUserName = view.findViewById(R.id.tv_username);
        tvLoginState = view.findViewById(R.id.tv_login_state);
        view.findViewById(R.id.layout_user_bar).setOnClickListener(v -> {
            if (isLoggedIn) {
                openRegister();
            } else {
                openLogin();
            }
        });
        tvLoginState.setOnClickListener(v -> {
            if (!isLoggedIn) {
                openLogin();
            }
        });
        // 只监听结果，不在 onResume 里主动刷新
        getParentFragmentManager().setFragmentResultListener("user_info_changed", this, (key, bundle) -> {
            String userJson = bundle.getString("latest_user_json", null);
            if (userJson != null) {
                UserInfo info = gson.fromJson(userJson, UserInfo.class);
                showLoggedIn(info);
            } else {
                loadUserInfo(); // 兼容旧数据流程
            }
        });

        // 首次进入页面时，显示本地数据
        loadUserInfo();
    }


    private void openRegister() {
        // 如果已经登录，传递当前用户信息；否则空
        UserInfoRegisterFragment frag = UserInfoRegisterFragment.newInstance(isLoggedIn);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, frag)
                .addToBackStack(null)
                .commit();
    }
    private void openLogin() {
        UserLoginFragment frag = new UserLoginFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, frag)
                .addToBackStack(null)
                .commit();
    }

    private void loadUserInfo() {
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String json = sp.getString("data", null);
        currentInfo = null;

        if (json != null) {
            currentInfo = gson.fromJson(json, UserInfo.class);
        }
        Log.d("MyInfo", "currentInfo=" + gson.toJson(currentInfo));
        Log.d("MyInfo", "userId=" + (currentInfo != null ? currentInfo.getUserId() : "null"));

        // 只要有userId，且不是空，才判定为本地登录
        if (currentInfo != null && currentInfo.getUserId() != null && !currentInfo.getUserId().isEmpty()) {
            String url = "http://192.168.2.9:8080/api/user/" + currentInfo.getUserId();
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> showOffline());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String resp = response.body().string();
                        UserInfo remote = gson.fromJson(resp, UserInfo.class);
                        if (remote != null && remote.getUserId() != null && !remote.getUserId().isEmpty()) {
                            currentInfo = remote;
                            sp.edit().putString("data", gson.toJson(remote)).apply();
                            if (getActivity() != null) getActivity().runOnUiThread(() -> showLoggedIn(remote));
                            return;
                        }
                    }
                    if (getActivity() != null) getActivity().runOnUiThread(() -> showNotLoggedIn());
                }
            });
        } else {
            showNotLoggedIn();
        }
    }

    private void showLoggedIn(UserInfo info) {
        isLoggedIn = true;
        String displayName = (info.getUserName() != null && !info.getUserName().isEmpty())
                ? info.getUserName()
                : (info.getPhone() != null ? info.getPhone() : "用户");
        tvUserName.setText(displayName);
        tvLoginState.setText(getString(R.string.myinfo_logged_in));
    }

    private void showNotLoggedIn() {
        isLoggedIn = false;
        tvUserName.setText("未登录");
        tvLoginState.setText(getString(R.string.myinfo_not_logged_in));
    }

    private void showOffline() {
        // 网络不可用，仅本地信息
        isLoggedIn = true;
        String displayName = (currentInfo != null && currentInfo.getUserName() != null && !currentInfo.getUserName().isEmpty())
                ? currentInfo.getUserName()
                : (currentInfo != null && currentInfo.getPhone() != null ? currentInfo.getPhone() : "用户");
        tvUserName.setText(displayName);
        tvLoginState.setText(getString(R.string.myinfo_offline));
    }
}
