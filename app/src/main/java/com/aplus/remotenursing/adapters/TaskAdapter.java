package com.aplus.remotenursing.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;
import com.aplus.remotenursing.models.UserTask;

import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskVH> {
    public interface OnTaskClickListener {
        void onTaskClick(UserTask task);
    }
    private List<UserTask> tasks;
    private OnTaskClickListener listener;
    public void setTasks(List<UserTask> list) {
        this.tasks = list;
    }
    public void setOnTaskClickListener(OnTaskClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public TaskVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskVH holder, int position) {
        UserTask t = tasks.get(position);
        holder.tvName.setText(t.getTask_name());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskClick(t);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks == null ? 0 : tasks.size();
    }

    static class TaskVH extends RecyclerView.ViewHolder {
        TextView tvName;
        TaskVH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_task_name);
        }
    }
}