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

import com.aplus.remotenursing.models.UserInfo;
import com.google.gson.Gson;

public class MyInfoFragment extends Fragment {

    private TextView tvUserName;
    private TextView tvLoginState;
    private final Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 这里引用你稍后要创建的 fragment_me.xml
        return inflater.inflate(R.layout.fragment_myinfo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvUserName = view.findViewById(R.id.tv_username);
        tvLoginState = view.findViewById(R.id.tv_login_state);
        view.findViewById(R.id.layout_user_bar).setOnClickListener(v -> openRegister());
        loadUserInfo();
    }

    private void openRegister() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new UserInfoRegisterFragment())
                .addToBackStack(null)
                .commit();
    }

    private void loadUserInfo() {
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String json = sp.getString("data", null);
        if (json != null) {
            UserInfo info = gson.fromJson(json, UserInfo.class);
            tvUserName.setText(info.getUser_name());
            tvLoginState.setText(getString(R.string.myinfo_logged_in));
        } else {
            tvLoginState.setText(getString(R.string.myinfo_not_logged_in));
        }
    }
}