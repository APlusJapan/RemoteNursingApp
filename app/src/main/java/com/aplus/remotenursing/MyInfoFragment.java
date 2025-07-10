package com.aplus.remotenursing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.models.UserInfo;
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvUserName = view.findViewById(R.id.tv_username);
        tvLoginState = view.findViewById(R.id.tv_login_state);
        // 推荐只保留这一个入口，简洁易维护
        view.findViewById(R.id.layout_user_bar).setOnClickListener(v -> {
            if (!isLoggedIn) {
                openLogin();
            } else {
                openRegister(); // 你想实现个人信息编辑可以在这处理
            }
        });
        // 注册监听 - 只会生效一次（除非Fragment重建）
        getParentFragmentManager().setFragmentResultListener("user_info_changed", this, (key, bundle) -> {
            String userJson = bundle.getString("latest_user_json", null);
            if (userJson != null) {
                currentInfo = gson.fromJson(userJson, UserInfo.class);
                Log.d("MyInfoFragment", "FragmentResultListener, set user: " + userJson);
                showLoggedIn(currentInfo);
                // 一定要写入sp，防止下次进来失效
                SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                sp.edit().putString("data", userJson).apply();
            } else {
                showNotLoggedIn();
            }
        });

        // 第一次进来（没有FragmentResult），从本地加载
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String userJson = sp.getString("data", null);
        if (userJson != null) {
            currentInfo = gson.fromJson(userJson, UserInfo.class);
            Log.d("MyInfoFragment", "onViewCreated, load user: " + userJson);
            showLoggedIn(currentInfo);
        } else {
            showNotLoggedIn();
        }
    }


    private void openRegister() {
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
            try {
                currentInfo = gson.fromJson(json, UserInfo.class);
            } catch (Exception e) {
                Log.e("MyInfoFragment", "Parse userInfo failed: " + e.getMessage());
                currentInfo = null;
            }
        }
        Log.d("MyInfoFragment", "currentInfo=" + gson.toJson(currentInfo));
        Log.d("MyInfoFragment", "userId=" + (currentInfo != null ? currentInfo.getUserId() : "null"));

        // 只刷新本地UI
        if (currentInfo != null && currentInfo.getUserId() != null && !currentInfo.getUserId().isEmpty()) {
            showLoggedIn(currentInfo);
            // 联网检查只作为后续后台刷新，不要直接清除本地登录状态
            String url = "http://192.168.2.9:8080/api/user/" + currentInfo.getUserId();
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("MyInfoFragment", "网络不可用，保留本地登录状态，仅提示");
                    // 可选：在UI上显示"离线状态"
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
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
                    // 只有服务器返回明确无此用户时才清理本地，否则仅提示
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        // showNotLoggedIn(); // 不再主动清理
                        // Optional: Toast.makeText(getContext(), "登录信息已过期，请重新登录", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            showNotLoggedIn();
        }
    }


    // 增加这个方法，彻底清理本地缓存和状态
    private void clearLogin() {
        isLoggedIn = false;
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        sp.edit().remove("data").apply();
        tvUserName.setText("未登录");
        tvLoginState.setText(getString(R.string.myinfo_not_logged_in));
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
}
