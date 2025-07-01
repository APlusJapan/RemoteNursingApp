package com.aplus.remotenursing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IABluetoothStateListener;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IConnectResponse;
import com.veepoo.protocol.listener.base.INotifyResponse;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.AbsBloodGlucoseChangeListener;
import com.veepoo.protocol.listener.data.ICustomSettingDataListener;
import com.veepoo.protocol.listener.data.IDeviceFuctionDataListener;
import com.veepoo.protocol.listener.data.IHeartDataListener;
import com.veepoo.protocol.listener.data.IPwdDataListener;
import com.veepoo.protocol.listener.data.ISleepDataListener;
import com.veepoo.protocol.listener.data.ISpo2hDataListener;
import com.veepoo.protocol.listener.data.IBPDetectDataListener;

import com.veepoo.protocol.model.datas.BpData;
import com.veepoo.protocol.model.datas.HeartData;
import com.veepoo.protocol.model.datas.PwdData;
import com.veepoo.protocol.model.datas.PersonInfoData;
import com.veepoo.protocol.model.datas.Spo2hData;
import com.veepoo.protocol.model.datas.SleepData;
import com.veepoo.protocol.model.datas.SleepPrecisionData;
import com.veepoo.protocol.model.enums.EBloodGlucoseRiskLevel;
import com.veepoo.protocol.model.enums.EBPDetectModel;
import com.veepoo.protocol.model.enums.EOprateStauts;
import com.veepoo.protocol.model.enums.ESex;
import com.veepoo.protocol.model.enums.EFunctionStatus;
import com.veepoo.protocol.model.settings.CustomSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataFragment extends Fragment {

    private static final String TARGET_NAME = "F57L";
    private static final int REQUEST_LOCATION = 1;

    private TextView tvHeart, tvSteps, tvSpo2, tvBp, tvBloodGlucose, tvSleep, tvStatus;
    private Button   btnSync;
    private BluetoothLeScanner scanner;
    private Handler uiHandler;

    private String  targetMac;
    private boolean isConnected;
    private boolean isScanning;
    private boolean hasRetrieved;

    private VPOperateManager mgr;
    private List<Integer> heartRateList = new ArrayList<>();
    private List<Integer> spo2hList      = new ArrayList<>();

    // 1️⃣ 系统蓝牙状态监听
    private final IABluetoothStateListener mBleStateListener =
            new IABluetoothStateListener() {
                @Override
                public void onBluetoothStateChanged(boolean openOrClosed) {
                    log("系统蓝牙：" + (openOrClosed ? "打开" : "关闭"));
                }
            };

    // 2️⃣ SDK BLE 连接状态监听
    private final IABleConnectStatusListener mBleConnListener =
            new IABleConnectStatusListener() {
                @Override
                public void onConnectStatusChanged(String mac, int status) {
                    log("BLE 连接状态：" + status);
                    // 如果 SDK 定义 status==1 为已连接，这里设 flag
                    // VPOperateManager 里：16=已连接，32=断开
                    if (status == 16) {
                        isConnected = true;
                    } else if (status == 32) {
                        isConnected = false;
                        targetMac = null; // 断开之后清掉 mac
                    }
                }
            };

    // 3️⃣ connectDevice 回调
    private final IConnectResponse connectCallback = (code, profile, isOad) -> {
        if (code == 0) log("设备连接成功，等待服务就绪…");
        else           log("连接失败，code=" + code);
    };

    // 4️⃣ 通用写响应
    private final IBleWriteResponse writeCallback = code -> {
        if (code != 0) log("命令写入响应 code=" + code);
    };

    // 5️⃣ 密码确认
    private final IPwdDataListener pwdListener = pwd -> {
        log("设备号：" + pwd.getDeviceNumber()
                + "，版本：" + pwd.getDeviceVersion()
                + "，测试版本：" + pwd.getDeviceTestVersion());
        if (pwd.getDeviceNumber() != 0) {
            log("密码确认成功，开始流程 …");
            startSteps();
        } else {
            log("密码确认失败，请检查密码或重试");
        }
    };

    // 6️⃣ 功能支持监听
    private final IDeviceFuctionDataListener functionListener = fs -> {
        if (fs.getHeartDetect() == EFunctionStatus.SUPPORT_OPEN) {
            log("设备支持并已打开自动心率检测");
        }
    };

    // 7️⃣ 自定义设置占位
    private final ICustomSettingDataListener customSettingListener = data -> { /* no-op */ };

    // 8️⃣ 服务就绪通知
    private final INotifyResponse notifyCallback = state -> {
        if (state == 0 && !hasRetrieved) {
            hasRetrieved = true;
            log("服务就绪，开始密码确认…");
            mgr.confirmDevicePwd(
                    writeCallback,
                    pwdListener,
                    functionListener,
                    null,
                    customSettingListener,
                    "0000",
                    false,
                    null
            );
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup container, Bundle saved) {
        View root = inf.inflate(R.layout.fragment_data, container, false);
        tvHeart         = root.findViewById(R.id.tv_heart);
        tvSteps         = root.findViewById(R.id.tv_steps);
        tvSpo2          = root.findViewById(R.id.tv_spo2);
        tvBp            = root.findViewById(R.id.tv_bp);
        tvBloodGlucose = root.findViewById(R.id.tv_bloodGlucose);
        tvSleep         = root.findViewById(R.id.tv_sleep);
        tvStatus        = root.findViewById(R.id.tv_data);
        btnSync         = root.findViewById(R.id.btn_sync);

        uiHandler = new Handler(Looper.getMainLooper());
        mgr       = VPOperateManager.getInstance();
        mgr.init(requireContext());
        mgr.registerBluetoothStateListener(mBleStateListener);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;

        // —— 如果已连接过，回来恢复状态 —— //
        String cur = VPOperateManager.getCurrentDeviceAddress();
        if (!TextUtils.isEmpty(cur) && mgr.isDeviceConnected(cur)) {
            targetMac   = cur;
            isConnected = true;
            log("恢复连接状态：已连接到 " + cur);
        }

        btnSync.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) return;
            resetAll();
            if (!isConnected || targetMac == null) {
                startScanAndConnect();
            } else {
                syncOnce();
            }
        });

        return root;
    }

    /** 运行时申请定位权限，必要才能扫描到结果 */
    private boolean checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_LOCATION);
                return true;
            }
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    private void startScanAndConnect() {
        if (scanner == null) {
            log("不支持 BLE 扫描");
            return;
        }
        if (isScanning) {
            log("扫描已在进行中");
            return;
        }
        isScanning = true;
        log("开始扫描 BLE 设备…");
        scanner.startScan(scanCallback);
    }

    @SuppressLint("MissingPermission")
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int cbType, @NonNull ScanResult res) {
            String name = res.getDevice().getName();
            if (name != null && name.contains(TARGET_NAME)) {
                // 只有匹配到目标时才停止扫描
                scanner.stopScan(this);
                isScanning = false;

                targetMac = res.getDevice().getAddress();
                log("找到设备 " + name + "，连接中…");

                mgr.registerConnectStatusListener(targetMac, mBleConnListener);
                mgr.connectDevice(
                        targetMac, name,
                        connectCallback, notifyCallback
                );
            }
        }
    };

    /** 真机点击一次，同步一次完整流程 */
    private void syncOnce() {
        if (!mgr.isCurrentDeviceConnected()) {
            log("设备未连接，无法同步");
            return;
        }
        mgr.syncPersonInfo(
                writeCallback,
                status -> {
                    log("个人信息同步状态：" + status);
                    if (status == EOprateStauts.OPRATE_SUCCESS) {
                        mgr.confirmDevicePwd(
                                writeCallback,
                                pwdListener,
                                functionListener,
                                null,
                                customSettingListener,
                                "0000",
                                false,
                                null
                        );
                    } else {
                        log("个人信息同步失败，无法继续");
                    }
                },
                new PersonInfoData(ESex.MAN, 178, 60, 20, 8000)
        );
    }

    /** 1️⃣ 读取步数 → 2️⃣ 心率 → 3️⃣ 血氧 → 4️⃣ 血压 → 5️⃣ 血糖 → 6️⃣ 睡眠 */
    private void startSteps() {
        log("开始读取步数…");
        mgr.readSportStep(writeCallback, data -> {
            safeUi(() -> tvSteps.setText("步数：" + data.getStep() + " 步"));
            log("步数读取完成：" + data.getStep());
            startHeart();
        });
    }

    private void startHeart() {
        log("开始心率测量…");
        heartRateList.clear();
        mgr.startDetectHeart(writeCallback, new IHeartDataListener() {
            int count = 0;
            @Override
            public void onDataChange(HeartData hd) {
                if (hd.getData() > 1) {
                    count++;
                    heartRateList.add(hd.getData());
                }
                safeUi(() -> tvHeart.setText(
                        "心率：" + hd.getData() + " 次/分 (正在测量... #" + count + ")"
                ));
                if (count >= 30) {
                    mgr.stopDetectHeart(writeCallback);
                    log("心率测量完成，共 " + count + " 条");
                    // 去掉最大最小，再算平均
                    List<Integer> tmp = new ArrayList<>(heartRateList);
                    if (tmp.size() > 2) {
                        tmp.remove(Collections.max(tmp));
                        tmp.remove(Collections.min(tmp));
                        int sum = 0; for(int v:tmp) sum+=v;
                        int avg = sum / tmp.size();
                        safeUi(() -> tvHeart.setText("心率：" + avg + " 次/分"));
                    }
                    startSpo2();
                }
            }
        });
    }

    private void startSpo2() {
        log("开始血氧测量…");
        spo2hList.clear();
        mgr.startDetectSPO2H(writeCallback, new ISpo2hDataListener() {
            int count = 0;
            @Override
            public void onSpO2HADataChange(Spo2hData data) {
                if (data.getValue() > 50) {
                    count++;
                    spo2hList.add(data.getValue());
                }
                safeUi(() -> tvSpo2.setText(
                        "血氧：" + data.getValue() + "% (正在测量... #" + count + ")"
                ));
                if (count >= 15) {
                    mgr.stopDetectSPO2H(writeCallback, this);
                    log("血氧测量完成，共 " + count + " 条");
                    List<Integer> tmp = new ArrayList<>(spo2hList);
                    if (tmp.size() > 2) {
                        tmp.remove(Collections.max(tmp));
                        tmp.remove(Collections.min(tmp));
                        int sum=0; for(int v:tmp) sum+=v;
                        int avg = sum/tmp.size();
                        safeUi(() -> tvSpo2.setText("血氧：" + avg + "%"));
                    }
                    startBP();
                }
            }
        });
    }

    private void startBP() {
        log("开始血压测量…");
        mgr.startDetectBP(writeCallback, new IBPDetectDataListener() {
            @Override
            public void onDataChange(BpData bpData) {
                safeUi(() -> tvBp.setText(
                        "血压：" + bpData.getHighPressure() + "/" + bpData.getLowPressure()
                                + " (正在测量... #" + bpData.getProgress() + ")"
                ));
                if (bpData.getProgress() >= 100) {
                    mgr.stopDetectBP(writeCallback, EBPDetectModel.DETECT_MODEL_PUBLIC);
                    log("血压测量结束");
                    safeUi(() -> tvBp.setText(
                            "血压：" + bpData.getHighPressure() + "/" + bpData.getLowPressure()
                    ));
                    startBloodGlucose();
                }
            }
        }, EBPDetectModel.DETECT_MODEL_PUBLIC);
    }

    private void startBloodGlucose() {
        log("开始血糖测量…");
        mgr.startBloodGlucoseDetect(writeCallback, new AbsBloodGlucoseChangeListener() {
            @Override
            public void onBloodGlucoseDetect(int progress, float bloodGlucose, EBloodGlucoseRiskLevel riskLevel) {
                safeUi(() -> tvBloodGlucose.setText(
                        "血糖：" + bloodGlucose + " (正在测量... #" + progress + ")"
                ));
                if (progress >= 100) {
                    mgr.stopBloodGlucoseDetect(writeCallback, this);
                    log("血糖测量结束");
                    safeUi(() -> tvBloodGlucose.setText("血糖：" + bloodGlucose));
                    startSleep();
                }
            }
        });
    }

    private void startSleep() {
        log("开始读取睡眠时间…");
        mgr.readSleepData(writeCallback, new ISleepDataListener() {
            @Override public void onSleepDataChange(String day, SleepData sd) {
                if (sd instanceof SleepPrecisionData && false) {
                    safeUi(() -> tvSleep.setText("精准睡眠：" + sd.toString()));
                } else {
                    int total = sd.getAllSleepTime(), deep = sd.getDeepSleepTime();
                    safeUi(() -> tvSleep.setText(
                            "睡眠总：" + formatMinutes(total) + "，深睡：" + formatMinutes(deep)
                    ));
                }
            }
            @Override public void onSleepProgress(float p) {}
            @Override public void onSleepProgressDetail(String d, int p) {}
            @Override public void onReadSleepComplete() {
                log("睡眠数据-读取结束");
            }
        }, 1);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onPause() {
        super.onPause();
        if (scanner != null && isScanning) {
            scanner.stopScan(scanCallback);
            isScanning = false;
            log("停止扫描 BLE（onPause）");
        }
    }
    @SuppressLint("MissingPermission")
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isScanning && scanner != null) {
            scanner.stopScan(scanCallback);
        }
        if (targetMac != null) {
            mgr.unregisterConnectStatusListener(targetMac, mBleConnListener);
        }
    }
    @SuppressLint("MissingPermission")
    private void resetAll() {
        tvStatus.setText("日志\n");
        tvSteps.setText("步数：-");
        tvHeart.setText("心率：-");
        tvSpo2.setText("血氧：-");
        tvBp.setText("血压：-");
        tvBloodGlucose.setText("血糖：-");
        tvSleep.setText("睡眠：-");
        heartRateList.clear();
        spo2hList.clear();
        hasRetrieved = false;
        if (isScanning && scanner != null) {
            scanner.stopScan(scanCallback);
            isScanning = false;
        }
    }

    private void log(String msg) {
        safeUi(() -> tvStatus.append("--" + msg + "\n"));
    }

    private void safeUi(Runnable r) {
        uiHandler.post(() -> { if (isAdded()) r.run(); });
    }

    private String formatMinutes(int min) {
        int h = min / 60, m = min % 60;
        return h + "小时" + m + "分";
    }
}
