package com.aplus.remotenursing.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.UserTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserTaskAdapter extends RecyclerView.Adapter<UserTaskAdapter.ViewHolder> {
    private List<UserTask> tasks = new ArrayList<>();
    private OnTaskClickListener listener;
    private Map<String, Integer> taskPointRuleMap;

    public void setTaskPointRuleMap(Map<String, Integer> ruleMap) {
        this.taskPointRuleMap = ruleMap;
        notifyDataSetChanged();
    }

    public void setTasks(List<UserTask> list) {
        if (list != null) {
            this.tasks = list;
        } else {
            this.tasks = new ArrayList<>();
        }
        notifyDataSetChanged();
    }

    public void setOnTaskClickListener(OnTaskClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_usertask_text, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserTask task = tasks.get(position);
        int taskPoint = 0;
        if (taskPointRuleMap != null && task != null) {
            String type = task.getTaskType();
            if (type.length() == 1) type = "0" + type;
            if (taskPointRuleMap.containsKey(type)) {
                taskPoint = taskPointRuleMap.get(type);
            }
        }
        holder.bind(task, taskPoint);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && task != null) {
                listener.onTaskClick(task);
            }
        });
        holder.tvTaskName.setText(tasks.get(position).getTaskName());

    }

    @Override
    public int getItemCount() {
        return tasks == null ? 0 : tasks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTaskName;
        private final TextView tvTaskPoint;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskName = itemView.findViewById(R.id.tv_task_name);
            tvTaskPoint = itemView.findViewById(R.id.tv_task_point);
        }

        public void bind(UserTask task, int taskPoint) {
            if (task != null) {
                tvTaskName.setText(task.getTaskName());
                String status = "未完成";
                if ("1".equals(task.getActionStatus())) {
                    status = "已完成";
                }
                tvTaskPoint.setText(status + " | 积分：" + taskPoint);
            } else {
                tvTaskName.setText("");
                tvTaskPoint.setText("");
            }
        }
    }

    public interface OnTaskClickListener {
        void onTaskClick(UserTask task);
    }
}
