package com.aplus.remotenursing.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.UserInfoAccount;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.ViewHolder> {

    private List<UserInfoAccount> dataList = new ArrayList<>();
    private OnActionListener listener;

    public interface OnActionListener {
        void onDeleteClick(UserInfoAccount item, int position);
        void onDetailClick(UserInfoAccount item, int position);
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<UserInfoAccount> list) {
        this.dataList = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserInfoAccount user = dataList.get(position);
        Context context = holder.itemView.getContext();

        // 姓名 / 性别
        String nameGender = (user.userName != null ? user.userName : "未命名") +
                " / " +
                (user.gender != null ? user.gender : "未知");
        holder.tvNameGender.setText(nameGender);

        // 激活状态 Chip
        if ("已激活".equals(user.loginStatus)) {
            holder.chipStatus.setText("已激活");
            holder.chipStatus.setChipBackgroundColorResource(R.color.chip_background_activated);
            holder.chipStatus.setChipStrokeColorResource(R.color.chip_text_color_activated);
            holder.chipStatus.setTextColor(context.getResources()
                    .getColor(R.color.chip_text_color_activated));
        } else {
            holder.chipStatus.setText("未激活");
            holder.chipStatus.setChipBackgroundColorResource(R.color.chip_background_not_activated);
            holder.chipStatus.setChipStrokeColorResource(R.color.chip_text_color_not_activated);
            holder.chipStatus.setTextColor(context.getResources()
                    .getColor(R.color.chip_text_color_not_activated));
        }

        // 录入日期
        String loginDateText = "录入: 未知";
        if (user.createdTime != null && !TextUtils.isEmpty(user.createdTime.toString())) {
            try {
                String dateTimeStr = user.createdTime.toString();
                if (dateTimeStr.length() >= 10) {
                    String dateStr = dateTimeStr.substring(0, 10);
                    loginDateText = "录入: " + dateStr;
                }
            } catch (Exception e) {
                loginDateText = "录入: 格式错误";
            }
        }
        holder.tvLoginDate.setText(loginDateText);

        // 电话
        holder.tvPhone.setText(user.phone != null ? user.phone : "未填写");

        // 课题 - 始终显示，为空时显示 "-"
        holder.llProject.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(user.projectName)) {
            holder.tvProject.setText(user.projectName);
        } else {
            holder.tvProject.setText("-");
        }

        // 分组 - 始终显示，为空时显示 "-"
        holder.llTeam.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(user.teamName)) {
            holder.tvTeam.setText(user.teamName);
        } else {
            holder.tvTeam.setText("-");
        }

        // 复制电话按钮
        holder.btnCopyPhone.setOnClickListener(v -> {
            if (user.phone != null && !TextUtils.isEmpty(user.phone)) {
                copyToClipboard(context, user.phone);
                Toast.makeText(context, "已复制: " + user.phone, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "无电话号码可复制", Toast.LENGTH_SHORT).show();
            }
        });

        // 操作按钮（始终显示）
        holder.opArea.setVisibility(View.VISIBLE);

        // 删除按钮
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(user, position);
            }
        });

        // 查看详细按钮
        holder.btnDetail.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDetailClick(user, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("phone", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNameGender, tvPhone, tvProject, tvTeam, tvLoginDate;
        Chip chipStatus;
        ImageView btnCopyPhone;
        LinearLayout llProject, llTeam, opArea;
        Button btnDelete, btnDetail;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNameGender = itemView.findViewById(R.id.tv_name_gender);
            tvPhone = itemView.findViewById(R.id.tv_phone);
            tvProject = itemView.findViewById(R.id.tv_project);
            tvTeam = itemView.findViewById(R.id.tv_team);
            tvLoginDate = itemView.findViewById(R.id.tv_login_date);
            chipStatus = itemView.findViewById(R.id.chip_status);
            btnCopyPhone = itemView.findViewById(R.id.btn_copy_phone);
            llProject = itemView.findViewById(R.id.ll_project);
            llTeam = itemView.findViewById(R.id.ll_team);
            opArea = itemView.findViewById(R.id.op_area);
            btnDelete = itemView.findViewById(R.id.btn_action1);
            btnDetail = itemView.findViewById(R.id.btn_action2);
        }
    }
}