package com.aplus.remotenursing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.cardview.widget.CardView;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.manager.LoginCheckerManager;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.common.UserUtils;
import com.google.gson.Gson;

public class AppManageFragment extends Fragment {

    private TextView tvUserName;
    private TextView tvLoginState;
    private ImageView ivAvatar;
    private Button btnLogin, btnLogout;
    private View cardUserInfoRegister;
    private CardView cardUserFunctions;
    private CardView cardAdminFunctions;
    private CardView cardHealthReport;
    private CardView cardAbout;

    private final Gson gson = new Gson();
    private boolean isLoggedIn = false;
    private UserAccount userAccount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_manage, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次恢复时重新检查权限并更新UI
        updateUIBasedOnUserType();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        setupClickListeners();
        setupFragmentResultListener();
        loadAndDisplayUserInfo();
    }

    private void initViews(View view) {
        // 用户信息相关视图
        tvUserName = view.findViewById(R.id.tv_username);
        tvLoginState = view.findViewById(R.id.tv_login_state);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        btnLogin = view.findViewById(R.id.btn_login);
        btnLogout = view.findViewById(R.id.btn_logout);

        // 功能卡片相关视图
        cardUserFunctions = view.findViewById(R.id.card_user_functions);
        cardAdminFunctions = view.findViewById(R.id.card_admin_functions);
        cardHealthReport = view.findViewById(R.id.card_health_report);
        cardAbout = view.findViewById(R.id.card_about);
        cardUserInfoRegister = view.findViewById(R.id.userinfo_register);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> openLogin());
        btnLogout.setOnClickListener(v -> logout());

        // 健康报告点击事件
        if (cardHealthReport != null) {
            cardHealthReport.setOnClickListener(v -> {
                Log.d("AppManageFragment", "点击健康报告");

                // 检查登录状态，但不要强制跳转到登录页面
                if (!isLoggedIn || userAccount == null) {
                    InfoPopup.showError(getContext(), "请先登录后再使用此功能");
                    return;
                }

                // 检查权限
                if (!isNormalUser() && !isAdmin()) {
                    InfoPopup.showError(getContext(), "您的账户类型暂时无法使用此功能");
                    return;
                }

                // TODO: 跳转到健康报告页面
                InfoPopup.showInfo(getContext(), "健康报告功能开发中，敬请期待");
            });
        }

        // 添加管理员微信点击事件
        if (cardAbout != null) {
            cardAbout.setOnClickListener(v -> {
                Log.d("AppManageFragment", "点击添加管理员微信");

                // 检查登录状态，但不要强制跳转到登录页面
                if (!isLoggedIn || userAccount == null) {
                    InfoPopup.showError(getContext(), "请先登录后再使用此功能");
                    return;
                }

                // 检查权限
                if (!isNormalUser() && !isAdmin()) {
                    InfoPopup.showError(getContext(), "您的账户类型暂时无法使用此功能");
                    return;
                }

                // TODO: 跳转到添加微信页面或显示二维码
                InfoPopup.showInfo(getContext(), "添加管理员微信功能开发中，敬请期待");
            });
        }

        // 用户信息录入点击事件（仅管理员可见）
        if (cardUserInfoRegister != null) {
            cardUserInfoRegister.setOnClickListener(v -> {
                Log.d("AppManageFragment", "点击用户信息录入");

                // 检查登录状态，但不要强制跳转到登录页面
                if (!isLoggedIn || userAccount == null) {
                    InfoPopup.showError(getContext(), "请先登录后再使用此功能");
                    return;
                }

                // 检查管理员权限
                if (!isAdmin()) {
                    InfoPopup.showError(getContext(), "您没有权限访问此功能，仅限管理员使用");
                    return;
                }

                // 跳转到用户信息录入页面
                try {
                    UserSearchFragment frag = new UserSearchFragment();
                    // 进入“用户检索页”
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, UserSearchFragment.newInstance(/* 可传 adminId 或 null */ null))
                            .addToBackStack("UserSearch")
                            .commit();
                    Log.d("AppManageFragment", "成功跳转到用户信息录入页面");
                } catch (Exception e) {
                    Log.e("AppManageFragment", "跳转失败: " + e.getMessage());
                    InfoPopup.showError(getContext(), "页面跳转失败，请重试");
                }
            });
        }
    }

    private void setupFragmentResultListener() {
        // Fragment间通讯：如注册/登录结果回调
        getParentFragmentManager().setFragmentResultListener("user_account_changed", this, (key, bundle) -> {
            String userJson = bundle.getString("latest_user_json", null);
            if (userJson != null) {
                userAccount = gson.fromJson(userJson, UserAccount.class);
                Log.d("AppManageFragment", "FragmentResultListener, set user: " + userJson);
                showLoggedIn(userAccount);
                // 使用UserUtil保存
                UserUtils.saveUserAccount(requireContext(), userAccount);
                // 更新权限控制的UI
                updateUIBasedOnUserType();
            } else {
                showNotLoggedIn();
                hideAllFunctionCards();
            }
        });
    }

    private void loadAndDisplayUserInfo() {
        // 初始化：从UserUtil加载
        userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount != null) {
            Log.d("AppManageFragment", "onViewCreated, load user: " + gson.toJson(userAccount));
            showLoggedIn(userAccount);
            updateUIBasedOnUserType();
        } else {
            showNotLoggedIn();
            hideAllFunctionCards();
        }
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

    private void logout() {
        UserUtils.logout(requireContext());
        InfoPopup.showSuccess(getContext(), "已退出登录");
        showNotLoggedIn();
        hideAllFunctionCards();
    }

    private void showLoggedIn(UserAccount account) {
        isLoggedIn = true;

        tvUserName.setText(account.getNickName());
        tvLoginState.setText(getString(R.string.myinfo_logged_in));
        btnLogin.setVisibility(View.GONE);
        btnLogout.setVisibility(View.VISIBLE);

        // 输出完整的用户信息用于调试
        Log.d("AppManageFragment", "显示已登录状态, 用户信息: userId=" + account.getUserId()
                + ", loginName=" + account.getLoginName()
                + ", nickName=" + account.getNickName()
                + ", adminId=" + account.getAdminId()
                + ", userType=" + account.getUserType());
    }

    private void showNotLoggedIn() {
        isLoggedIn = false;
        tvUserName.setText("未登录");
        tvLoginState.setText(getString(R.string.myinfo_not_logged_in));
        btnLogin.setVisibility(View.VISIBLE);
        btnLogout.setVisibility(View.GONE);
    }

    /**
     * 根据用户类型更新UI显示
     * userType 10-29: 普通用户，只显示功能card
     * userType 30-49: 管理员，显示功能card和管理员card
     */
    private void updateUIBasedOnUserType() {
        if (!isLoggedIn || userAccount == null) {
            hideAllFunctionCards();
            return;
        }

        int userType = parseUserType(userAccount.getUserType());
        Log.d("AppManageFragment", "用户类型: " + userType + ", 原始值: " + userAccount.getUserType());

        if (userType >= 10 && userType <= 29) {
            // 普通用户：只显示功能card
            showUserFunctions(true);
            showAdminFunctions(false);
            Log.d("AppManageFragment", "显示普通用户功能");
        } else if (userType >= 30 && userType <= 49) {
            // 管理员：显示功能card和管理员card
            showUserFunctions(true);
            showAdminFunctions(true);
            Log.d("AppManageFragment", "显示管理员功能");
        } else {
            // 其他情况：隐藏所有功能
            hideAllFunctionCards();
            Log.d("AppManageFragment", "用户类型不在范围内，隐藏所有功能: " + userType);
        }
    }

    private void showUserFunctions(boolean show) {
        if (cardUserFunctions != null) {
            cardUserFunctions.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showAdminFunctions(boolean show) {
        if (cardAdminFunctions != null) {
            cardAdminFunctions.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void hideAllFunctionCards() {
        showUserFunctions(false);
        showAdminFunctions(false);
    }

    /**
     * 解析用户类型，支持字符串和数字格式
     */
    private int parseUserType(String userTypeStr) {
        if (userTypeStr == null || userTypeStr.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(userTypeStr.trim());
        } catch (NumberFormatException e) {
            Log.w("AppManageFragment", "无法解析用户类型: " + userTypeStr);
            return 0;
        }
    }

    /**
     * 检查当前用户是否为管理员
     */
    private boolean isAdmin() {
        if (userAccount == null) return false;
        int userType = parseUserType(userAccount.getUserType());
        return userType >= 30 && userType <= 49;
    }

    /**
     * 检查当前用户是否为普通用户
     */
    private boolean isNormalUser() {
        if (userAccount == null) return false;
        int userType = parseUserType(userAccount.getUserType());
        return userType >= 10 && userType <= 29;
    }
}