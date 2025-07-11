package com.aplus.remotenursing;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;

// 可根据你的项目实际情况调整包名和细节
public class LoginChecker {

    // 静态方法，传入fragment和回调
    public static boolean checkLogin(Fragment fragment) {
        Context context = fragment.requireContext();
        SharedPreferences sp = context.getSharedPreferences("user_account", Context.MODE_PRIVATE);
        String userJson = sp.getString("data", null);
        boolean isLoggedIn = userJson != null && !userJson.isEmpty();
        if (!isLoggedIn) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show();
            // 跳转到登录页面
            fragment.requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .addToBackStack(null)
                    .commit();
            return false;
        }
        return true;
    }

    // 也可以加Activity版本
    public static boolean checkLogin(AppCompatActivity activity) {
        SharedPreferences sp = activity.getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String userJson = sp.getString("data", null);
        boolean isLoggedIn = userJson != null && !userJson.isEmpty();
        if (!isLoggedIn) {
            Toast.makeText(activity, "请先登录", Toast.LENGTH_SHORT).show();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new UserLoginFragment())
                    .addToBackStack(null)
                    .commit();
            return false;
        }
        return true;
    }
}
