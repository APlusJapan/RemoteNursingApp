package com.aplus.remotenursing.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.R;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

    /** 点击设备回调接口 */
    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    private final List<BluetoothDevice> devices;
    private final OnDeviceClickListener listener;

    public DeviceAdapter(List<BluetoothDevice> devices,
                         OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 用 TextView 简单展示列表项
        TextView tv = new TextView(parent.getContext());
        int padding = (int)(16 * parent.getResources().getDisplayMetrics().density);
        tv.setPadding(padding, padding, padding, padding);
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        BluetoothDevice device = devices.get(position);
        Context ctx = holder.tv.getContext();

        String name = "";
        String address = "";
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            name = device.getName();
            address = device.getAddress();
        } else {
            // 权限没给，你可以显示一个占位文本
            name = ctx.getString(R.string.permission_denied);
            address = "";
        }

        String label = name + (address.isEmpty() ? "" : " (" + address + ")");
        holder.tv.setText(label);
        holder.tv.setOnClickListener(v -> listener.onDeviceClick(device));
    }


    @Override
    public int getItemCount() {
        return devices.size();
    }

    /**
     * 向列表新增一个设备（去重）
     */
    public void addDevice(BluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }

    /**
     * 清空所有设备
     */
    public void clear() {
        int oldSize = devices.size();
        devices.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    /** ViewHolder 内部类 */
    public static class VH extends RecyclerView.ViewHolder {
        final TextView tv;
        public VH(@NonNull View itemView) {
            super(itemView);
            this.tv = (TextView)itemView;
        }
    }
}
