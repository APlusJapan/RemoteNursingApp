package com.aplus.remotenursing.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.UserInfoAccount;

import java.util.ArrayList;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.VH> {
    private static final String TAG = "UserSearchAdapter";

    public interface OnActionListener {
        void onAction1(UserInfoAccount item, int position);
        void onAction2(UserInfoAccount item, int position);
    }

    private final List<UserInfoAccount> data = new ArrayList<>();
    private OnActionListener listener;

    public void setOnActionListener(OnActionListener l) {
        this.listener = l;
    }

    public void setData(List<UserInfoAccount> list) {
        Log.d(TAG, "========== setData 被调用 ==========");
        Log.d(TAG, "当前data大小: " + data.size());
        Log.d(TAG, "新list大小: " + (list != null ? list.size() : "null"));

        data.clear();
        if (list != null) {
            data.addAll(list);
            Log.d(TAG, "数据已添加到data,新data大小: " + data.size());
        }

        notifyDataSetChanged();
        Log.d(TAG, "notifyDataSetChanged已调用");
        Log.d(TAG, "========================================");
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        UserInfoAccount it = data.get(pos);
        Log.d(TAG, "绑定第" + pos + "项: " + it.userName + " - " + it.loginStatus);

        h.tvNameGender.setText(safe(it.userName) + " / " + safe(it.gender));
        h.tvPhone.setText("电话：" + safe(it.phone));
        h.tvLoginStatus.setText("登录状态：" + safe(it.loginStatus));
        h.tvProjectTeam.setText("课题/分组：" + safe(it.projectName) + " / " + safe(it.teamName));

        h.btn1.setOnClickListener(v -> {
            if (listener != null) listener.onAction1(it, pos);
        });
        h.btn2.setOnClickListener(v -> {
            if (listener != null) listener.onAction2(it, pos);
        });
    }

    @Override
    public int getItemCount() {
        int count = data.size();
        Log.d(TAG, "getItemCount() 返回: " + count);
        return count;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNameGender, tvPhone, tvLoginStatus, tvProjectTeam;
        Button btn1, btn2;
        VH(@NonNull View itemView) {
            super(itemView);
            tvNameGender = itemView.findViewById(R.id.tv_name_gender);
            tvPhone = itemView.findViewById(R.id.tv_phone);
            tvLoginStatus = itemView.findViewById(R.id.tv_login_status);
            tvProjectTeam = itemView.findViewById(R.id.tv_project_team);
            btn1 = itemView.findViewById(R.id.btn_action1);
            btn2 = itemView.findViewById(R.id.btn_action2);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}