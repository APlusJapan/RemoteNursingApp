package com.aplus.remotenursing.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.UserTask;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class UserTaskAdapter extends RecyclerView.Adapter<UserTaskAdapter.ViewHolder> {
    private List<UserTask> tasks = new ArrayList<>();
    private OnTaskClickListener listener;

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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_videotask_text, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserTask task = tasks.get(position);
        holder.bind(task);

        holder.itemView.setOnClickListener(v -> {
            Log.d("UserTaskAdapter", "Item 被点击: " + task.getTask_name());
            if (listener != null) {
                listener.onTaskClick(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks == null ? 0 : tasks.size();
    }

    // ================== 关键修改点 =====================
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTaskName;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskName = itemView.findViewById(R.id.tv_task_name);
        }
        public void bind(UserTask task) {
            if (task != null) {
                tvTaskName.setText(task.getTask_name());
                Log.d("UserTaskAdapter", "bind: " + task.getTask_name());
            }
        }
    }
    // ===================================================

    public interface OnTaskClickListener {
        void onTaskClick(UserTask task);
    }
}
