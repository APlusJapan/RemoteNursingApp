package com.aplus.remotenursing.helper;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class DeviceInfoHelper {

    private static final String TAG = "DeviceInfoHelper";

    /**
     * 获取设备唯一标识符
     * @param context 上下文
     * @return 设备ID字符串
     */
    public static String getDeviceId(Context context) {
        try {
            String androidId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);

            String manufacturer = android.os.Build.MANUFACTURER;
            String model = android.os.Build.MODEL;
            String serial = android.os.Build.SERIAL;

            String deviceInfo = manufacturer + "_" + model + "_" + serial;

            if (androidId != null && !androidId.isEmpty() && !"9774d56d682e549c".equals(androidId)) {
                return androidId + "_" + deviceInfo.hashCode();
            } else {
                return String.valueOf(deviceInfo.hashCode());
            }

        } catch (Exception e) {
            Log.e(TAG, "获取设备ID失败: " + e.getMessage());
            long timestamp = System.currentTimeMillis();
            int random = (int) (Math.random() * 10000);
            return "device_" + timestamp + "_" + random;
        }
    }

    /**
     * 获取设备IP地址
     * @param context 上下文
     * @return IP地址字符串，失败返回 "unknown"
     */
    public static String getIpAddress(Context context) {
        try {
            // 先尝试获取WiFi IP
            String wifiIp = getWifiIpAddress(context);
            if (wifiIp != null && !wifiIp.isEmpty()) {
                return wifiIp;
            }

            // 如果WiFi未连接，尝试获取移动网络IP
            String mobileIp = getMobileIpAddress();
            if (mobileIp != null && !mobileIp.isEmpty()) {
                return mobileIp;
            }

            return "unknown";

        } catch (Exception e) {
            Log.e(TAG, "获取IP地址失败: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * 获取WiFi IP地址
     * @param context 上下文
     * @return WiFi IP地址
     */
    private static String getWifiIpAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);

            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();

                if (ipAddress != 0) {
                    return String.format("%d.%d.%d.%d",
                            (ipAddress & 0xff),
                            (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff),
                            (ipAddress >> 24 & 0xff));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取WiFi IP失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取移动网络IP地址
     * @return 移动网络IP地址
     */
    private static String getMobileIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // 过滤掉回环地址和IPv6地址
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                        String ip = inetAddress.getHostAddress();

                        // 只返回IPv4地址
                        if (ip != null && ip.indexOf(':') < 0) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取移动网络IP失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取设备完整信息（用于调试）
     * @param context 上下文
     * @return 设备信息字符串
     */
    public static String getDeviceInfo(Context context) {
        StringBuilder info = new StringBuilder();
        info.append("设备ID: ").append(getDeviceId(context)).append("\n");
        info.append("IP地址: ").append(getIpAddress(context)).append("\n");
        info.append("制造商: ").append(android.os.Build.MANUFACTURER).append("\n");
        info.append("型号: ").append(android.os.Build.MODEL).append("\n");
        info.append("Android版本: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        return info.toString();
    }
}