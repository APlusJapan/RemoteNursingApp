package com.aplus.remotenursing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.aplus.remotenusing.common.ApiConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.*;
import com.veepoo.protocol.listener.data.*;
import com.veepoo.protocol.model.datas.*;
import com.veepoo.protocol.model.enums.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import okhttp3.*;

import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenusing.common.UserUtil;

public class SmartwatchCheckupFragment extends Fragment {
    private static final String TARGET_NAME = "F57L";
    private static final int REQUEST_LOCATION = 1;
    private enum CheckupStatus { BEFORE, IN_PROGRESS, FINISHED }
    private CheckupStatus checkupStatus = CheckupStatus.BEFORE;

    private TextView tvHeart, tvSteps, tvSpo2, tvBp, tvBloodGlucose, tvSleep, tvStatus;
    private Button btnSync;
    private BluetoothLeScanner scanner;
    private Handler uiHandler;
    private String targetMac;
    private boolean isConnected;
    private boolean isScanning;
    private boolean hasRetrieved;

    private CardView cardResult;
    private TextView tvResult;

    private VPOperateManager mgr;
    private List<Integer> heartRateList = new ArrayList<>();
    private List<Integer> spo2hList = new ArrayList<>();

    private ProgressBar progressSteps, progressHeartrate, progressSpo2, progressBp, progressBg, progressSleep;
    private TextView tvMeasureStatus;

    private int lastSteps = 0, lastHeart = 0, lastSpo2 = 0, lastBpHigh = 0, lastBpLow = 0, lastSleep = 0;
    private float lastBloodGlucose = 0f;

    // 标准
    private List<CheckupStandard> standardList = new ArrayList<>();
    private String userId = null; // 动态获取

    // 标准Bean
    public static class CheckupStandard {
        public String itemCode;
        public String itemName;
        public String minValue;
        public String maxValue;
        public String valueType;
        public String unit;
    }

    private final IABluetoothStateListener mBleStateListener = new IABluetoothStateListener() {
        @Override
        public void onBluetoothStateChanged(boolean openOrClosed) {
            log("系统蓝牙：" + (openOrClosed ? "打开" : "关闭"));
        }
    };

    private final IABleConnectStatusListener mBleConnListener = new IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            log("BLE 连接状态：" + status);
            if (status == 16) {
                isConnected = true;
            } else if (status == 32) {
                isConnected = false;
                targetMac = null;
            }
        }
    };

    private final IConnectResponse connectCallback = (code, profile, isOad) -> {
        if (code == 0) log("设备连接成功，等待服务就绪…");
        else log("连接失败，code=" + code);
    };
    private final IBleWriteResponse writeCallback = code -> {
        if (code != 0) log("命令写入响应 code=" + code);
    };
    private final IPwdDataListener pwdListener = pwd -> {
        log("设备号：" + pwd.getDeviceNumber() + "，版本：" + pwd.getDeviceVersion());
        if (pwd.getDeviceNumber() != 0) {
            log("密码确认成功，开始流程 …");
            startSteps();
        } else {
            log("密码确认失败，请检查密码或重试");
        }
    };
    private final IDeviceFuctionDataListener functionListener = fs -> {
        if (fs.getHeartDetect() == EFunctionStatus.SUPPORT_OPEN) {
            log("设备支持并已打开自动心率检测");
        }
    };
    private final ICustomSettingDataListener customSettingListener = data -> {};
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
        View root = inf.inflate(R.layout.fragment_smartwatch_checkup, container, false);
        root.findViewById(R.id.smartwatch_btn_back)
                .setOnClickListener(v -> requireActivity().onBackPressed());

        tvSteps = root.findViewById(R.id.tv_steps);
        tvHeart = root.findViewById(R.id.tv_heartrate);
        tvSpo2 = root.findViewById(R.id.tv_spo2);
        tvBp = root.findViewById(R.id.tv_bp);
        tvBloodGlucose = root.findViewById(R.id.tv_bg);
        tvSleep = root.findViewById(R.id.tv_sleep);
        btnSync = root.findViewById(R.id.btn_checkup);
        cardResult = root.findViewById(R.id.card_checkup_result);
        tvResult = root.findViewById(R.id.tv_checkup_result);

        progressSteps = root.findViewById(R.id.progress_steps);
        progressHeartrate = root.findViewById(R.id.progress_heartrate);
        progressSpo2 = root.findViewById(R.id.progress_spo2);
        progressBp = root.findViewById(R.id.progress_bp);
        progressBg = root.findViewById(R.id.progress_bg);
        progressSleep = root.findViewById(R.id.progress_sleep);

        tvMeasureStatus = root.findViewById(R.id.tv_measure_status);
        tvStatus = root.findViewById(R.id.tv_measure_status);

        uiHandler = new Handler(Looper.getMainLooper());
        mgr = VPOperateManager.getInstance();
        mgr.init(requireContext());
        mgr.registerBluetoothStateListener(mBleStateListener);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;

        hasRetrieved = false;
        isConnected = false;
        targetMac = null;

        String cur = VPOperateManager.getCurrentDeviceAddress();
        if (!TextUtils.isEmpty(cur) && mgr.isDeviceConnected(cur)) {
            targetMac = cur;
            isConnected = true;
            log("恢复连接状态：已连接到 " + cur);
        }

        updateCheckupUI(CheckupStatus.BEFORE, null);

        // --------- 获取 userId -----------
        UserAccount userAccount = UserUtil.getUserAccount(requireContext());
        userId = userAccount != null ? userAccount.getUserId() : null;

        // ---加载标准后再设置体检按钮---
        loadCheckupStandard(userId, () -> {
            btnSync.setOnClickListener(v -> {
                if (checkupStatus != CheckupStatus.IN_PROGRESS) {
                    updateCheckupUI(CheckupStatus.IN_PROGRESS, null);
                    resetAll();
                    if (!isConnected || targetMac == null) {
                        startScanAndConnect();
                    } else {
                        syncOnce();
                    }
                }
            });
        });

        return root;
    }

    // --------标准加载--------
    private void loadCheckupStandard(String userId, Runnable afterLoad) {
        if (TextUtils.isEmpty(userId)) {
            // 没有userId则直接回调
            safeUi(() -> { if (afterLoad != null) afterLoad.run(); });
            return;
        }
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_CHECKUP_STANDARD + userId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                safeUi(() -> { if (afterLoad != null) afterLoad.run(); });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    safeUi(() -> { if (afterLoad != null) afterLoad.run(); });
                    return;
                }
                String json = response.body().string();
                Gson gson = new Gson();
                Type listType = new TypeToken<List<CheckupStandard>>(){}.getType();
                List<CheckupStandard> standards = gson.fromJson(json, listType);
                standardList.clear();
                if (standards != null) standardList.addAll(standards);
                safeUi(() -> { if (afterLoad != null) afterLoad.run(); });
            }
        });
    }

    // ========== 下面全保留你原有方法 ==========

    @SuppressLint("MissingPermission")
    private void startScanAndConnect() {
        log("startScanAndConnect: isScanning=" + isScanning + ", isConnected=" + isConnected + ", targetMac=" + targetMac);
        if (scanner == null) {
            log("不支持 BLE 扫描");
            return;
        }
        if (isScanning) {
            log("扫描已在进行中，直接 return");
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

    private void startSteps() {
        log("开始读取步数…");
        tvMeasureStatus.setText("正在读取步数，请等待…");
        showProgressBarFor("steps");
        mgr.readSportStep(writeCallback, data -> {
            lastSteps = data.getStep();
            safeUi(() -> tvSteps.setText("步数：" + lastSteps + " 步"));
            hideAllProgressBars();
            log("步数读取完成：" + lastSteps);
            startHeart();
        });
    }

    private void startHeart() {
        log("开始心率测量…");
        tvMeasureStatus.setText("正在测量心率，请等待…");
        showProgressBarFor("heartrate");
        heartRateList.clear();
        mgr.startDetectHeart(writeCallback, new IHeartDataListener() {
            int count = 0;
            @Override
            public void onDataChange(HeartData hd) {
                if (hd.getData() > 1) {
                    count++;
                    heartRateList.add(hd.getData());
                }
                safeUi(() -> tvHeart.setText("心率：" + hd.getData() + " 次/分"));
                if (count >= 30) {
                    mgr.stopDetectHeart(writeCallback);
                    log("心率测量完成，共 " + count + " 条");
                    List<Integer> tmp = new ArrayList<>(heartRateList);
                    if (tmp.size() > 2) {
                        tmp.remove(Collections.max(tmp));
                        tmp.remove(Collections.min(tmp));
                        int sum = 0; for(int v:tmp) sum+=v;
                        int avg = sum / tmp.size();
                        lastHeart = avg;
                        safeUi(() -> tvHeart.setText("心率：" + avg + " 次/分"));
                    }
                    hideAllProgressBars();
                    startSpo2();
                }
            }
        });
    }

    private void startSpo2() {
        log("开始血氧测量…");
        tvMeasureStatus.setText("正在测量血氧，请等待…");
        showProgressBarFor("spo2");
        spo2hList.clear();
        mgr.startDetectSPO2H(writeCallback, new ISpo2hDataListener() {
            int count = 0;
            @Override
            public void onSpO2HADataChange(Spo2hData data) {
                if (data.getValue() > 50) {
                    count++;
                    spo2hList.add(data.getValue());
                }
                safeUi(() -> tvSpo2.setText("血氧：" + data.getValue() + "%"));
                if (count >= 15) {
                    mgr.stopDetectSPO2H(writeCallback, this);
                    log("血氧测量完成，共 " + count + " 条");
                    List<Integer> tmp = new ArrayList<>(spo2hList);
                    if (tmp.size() > 2) {
                        tmp.remove(Collections.max(tmp));
                        tmp.remove(Collections.min(tmp));
                        int sum=0; for(int v:tmp) sum+=v;
                        int avg = sum/tmp.size();
                        lastSpo2 = avg;
                        safeUi(() -> tvSpo2.setText("血氧：" + avg + "%"));
                    }
                    hideAllProgressBars();
                    startBP();
                }
            }
        });
    }

    private void startBP() {
        log("开始血压测量…");
        tvMeasureStatus.setText("正在测量血压…");
        showProgressBarFor("bp");
        mgr.startDetectBP(writeCallback, new IBPDetectDataListener() {
            @Override
            public void onDataChange(BpData bpData) {
                safeUi(() -> tvBp.setText("血压：" + bpData.getHighPressure() + "/" + bpData.getLowPressure()));
                if (bpData.getProgress() >= 100) {
                    mgr.stopDetectBP(writeCallback, EBPDetectModel.DETECT_MODEL_PUBLIC);
                    log("血压测量结束");
                    lastBpHigh = bpData.getHighPressure();
                    lastBpLow = bpData.getLowPressure();
                    safeUi(() -> tvBp.setText("血压：" + lastBpHigh + "/" + lastBpLow));
                    hideAllProgressBars();
                    startBloodGlucose();
                }
            }
        }, EBPDetectModel.DETECT_MODEL_PUBLIC);
    }

    private void startBloodGlucose() {
        log("开始血糖测量…");
        tvMeasureStatus.setText("正在测量血糖…");
        showProgressBarFor("bg");
        mgr.startBloodGlucoseDetect(writeCallback, new AbsBloodGlucoseChangeListener() {
            @Override
            public void onBloodGlucoseDetect(int progress, float bloodGlucose, EBloodGlucoseRiskLevel riskLevel) {
                safeUi(() -> tvBloodGlucose.setText("血糖：" + bloodGlucose));
                if (progress >= 100) {
                    mgr.stopBloodGlucoseDetect(writeCallback, this);
                    log("血糖测量结束");
                    lastBloodGlucose = bloodGlucose;
                    safeUi(() -> tvBloodGlucose.setText("血糖：" + lastBloodGlucose));
                    hideAllProgressBars();
                    startSleep();
                }
            }
        });
    }

    private void startSleep() {
        log("开始读取睡眠时间…");
        tvMeasureStatus.setText("正在读取睡眠数据…");
        showProgressBarFor("sleep");
        final boolean[] hasSleepData = {false};
        mgr.readSleepData(writeCallback, new ISleepDataListener() {
            @Override
            public void onSleepDataChange(String day, SleepData sd) {
                hasSleepData[0] = true;
                int total = sd.getAllSleepTime();
                lastSleep = total;
                safeUi(() -> tvSleep.setText("睡眠总：" + formatMinutes(total)));
                hideAllProgressBars();
                safeUi(() -> showCheckupResultAndFinish());
            }
            @Override public void onSleepProgress(float p) {}
            @Override public void onSleepProgressDetail(String d, int p) {}
            @Override public void onReadSleepComplete() {
                log("睡眠数据-读取结束");
                uiHandler.postDelayed(() -> {
                    if (!hasSleepData[0]) {
                        lastSleep = 0;
                        safeUi(() -> tvSleep.setText("睡眠时长：-"));
                        hideAllProgressBars();
                        safeUi(() -> showCheckupResultAndFinish());
                    }
                }, 1000);
            }
        }, 1);
    }

    private void showProgressBarFor(String item) {
        progressSteps.setVisibility(View.GONE);
        progressHeartrate.setVisibility(View.GONE);
        progressSpo2.setVisibility(View.GONE);
        progressBp.setVisibility(View.GONE);
        progressBg.setVisibility(View.GONE);
        progressSleep.setVisibility(View.GONE);
        switch(item) {
            case "steps": progressSteps.setVisibility(View.VISIBLE); break;
            case "heartrate": progressHeartrate.setVisibility(View.VISIBLE); break;
            case "spo2": progressSpo2.setVisibility(View.VISIBLE); break;
            case "bp": progressBp.setVisibility(View.VISIBLE); break;
            case "bg": progressBg.setVisibility(View.VISIBLE); break;
            case "sleep": progressSleep.setVisibility(View.VISIBLE); break;
        }
    }

    private void hideAllProgressBars() {
        progressSteps.setVisibility(View.GONE);
        progressHeartrate.setVisibility(View.GONE);
        progressSpo2.setVisibility(View.GONE);
        progressBp.setVisibility(View.GONE);
        progressBg.setVisibility(View.GONE);
        progressSleep.setVisibility(View.GONE);
    }

    private void showCheckupResultAndFinish() {
        hideAllProgressBars();
        tvMeasureStatus.setText("体检完成！");
        String stepsDesc = lastSteps + "步";
        String hrDesc = lastHeart + "次/分";
        String spo2Desc = lastSpo2 + "%";
        String bpDesc = lastBpHigh + "/" + lastBpLow + " mmHg";
        String bgDesc = lastBloodGlucose + " mmol/L";
        String sleepDesc = formatMinutes(lastSleep);

        tvSteps.setText("步数：" + stepsDesc);
        tvHeart.setText("心率：" + hrDesc);
        tvSpo2.setText("血氧：" + spo2Desc);
        tvBp.setText("血压：" + bpDesc);
        tvBloodGlucose.setText("血糖：" + bgDesc);
        tvSleep.setText("睡眠时长：" + sleepDesc);

        StringBuilder sb = new StringBuilder();
        CheckupStandard stdHeart = findStandard("HEART_RATE");
        CheckupStandard stdSpo2 = findStandard("SPO2");
        CheckupStandard stdBpHigh = findStandard("BLOOD_PRESSURE_HIGH");
        CheckupStandard stdBpLow = findStandard("BLOOD_PRESSURE_LOW");
        CheckupStandard stdGlucose = findStandard("BLOOD_GLUCOSE");
        CheckupStandard stdStep = findStandard("STEP");
        CheckupStandard stdSleep = findStandard("SLEEP_TIME");

        // 心率
        if (stdHeart != null) {
            int min = Integer.parseInt(stdHeart.minValue);
            int max = Integer.parseInt(stdHeart.maxValue);
            if (lastHeart < min)
                sb.append(getString(R.string.checkup_heart_rate_low)).append("；");
            else if (lastHeart > max)
                sb.append(getString(R.string.checkup_heart_rate_high)).append("；");
        }
        // 血氧
        if (stdSpo2 != null) {
            int min = Integer.parseInt(stdSpo2.minValue);
            if (lastSpo2 < min)
                sb.append(getString(R.string.checkup_spo2_low)).append("；");
        }
        // 血压
        if (stdBpHigh != null && stdBpLow != null) {
            int stdBpHighMax = Integer.parseInt(stdBpHigh.maxValue);
            int stdBpLowMax = Integer.parseInt(stdBpLow.maxValue);
            boolean hadBeenBpHigh = false;
            // 高血压判断（舒张压和收缩压任何一个大于阈值的情况）
            if(lastBpHigh > stdBpHighMax || lastBpLow > stdBpLowMax){
                sb.append(getString(R.string.checkup_bp_high_high)).append("；");
                hadBeenBpHigh = true;
            }
            // 低血压判断（非高血压，且舒张压和收缩压任何一个小于阈值的情况）
            int stdBpHighMin = Integer.parseInt(stdBpHigh.minValue);
            int stdBpLowMin = Integer.parseInt(stdBpLow.minValue);
            if(!hadBeenBpHigh  && (lastBpLow < stdBpHighMin || lastBpLow < stdBpLowMin)){
                sb.append(getString(R.string.checkup_bp_high_low)).append("；");
            }
        }

        // 血糖
        if (stdGlucose != null) {
            float min = Float.parseFloat(stdGlucose.minValue);
            float max = Float.parseFloat(stdGlucose.maxValue);
            if (lastBloodGlucose < min)
                sb.append(getString(R.string.checkup_blood_glucose_low)).append("；");
            else if (lastBloodGlucose > max)
                sb.append(getString(R.string.checkup_blood_glucose_high)).append("；");
        }
        // 步数
        if (stdStep != null) {
            int min = Integer.parseInt(stdStep.minValue);
            if (lastSteps < min)
                sb.append(getString(R.string.checkup_step_low)).append("；");
        }
        // 睡眠
        if (stdSleep != null) {
            int min = Integer.parseInt(stdSleep.minValue);
            if (lastSleep < min)
                sb.append(getString(R.string.checkup_sleep_time_low)).append("；");
        }
        if (sb.length() == 0) sb.append(getString(R.string.checkup_all_normal));

        tvResult.setText(sb.toString());
        cardResult.setVisibility(View.VISIBLE);

        updateCheckupUI(CheckupStatus.FINISHED, "体检完成！");
    }

    private CheckupStandard findStandard(String itemCode) {
        for (CheckupStandard s : standardList) {
            if (itemCode.equalsIgnoreCase(s.itemCode)) return s;
        }
        return null;
    }

    private void updateCheckupUI(CheckupStatus status, String msg) {
        this.checkupStatus = status;
        switch (status) {
            case BEFORE:
                btnSync.setText("开始体检");
                btnSync.setEnabled(true);
                tvStatus.setText(TextUtils.isEmpty(msg) ? "请点击“开始体检”同步数据" : msg);
                if (cardResult != null) cardResult.setVisibility(View.GONE);
                break;
            case IN_PROGRESS:
                btnSync.setText("正在体检。。");
                btnSync.setEnabled(false);
                tvStatus.setText(TextUtils.isEmpty(msg) ? "正在采集数据…" : msg);
                if (cardResult != null) cardResult.setVisibility(View.GONE);
                break;
            case FINISHED:
                btnSync.setText("重新体检");
                btnSync.setEnabled(true);
                tvStatus.setText(TextUtils.isEmpty(msg) ? "体检完成！" : msg);
                break;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onPause() {
        super.onPause();
        if (scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
        isScanning = false;
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
            mgr.disconnectWatch(writeCallback);
            isConnected = false;
            targetMac = null;
        }
        hasRetrieved = false;
    }

    @SuppressLint("MissingPermission")
    private void resetAll() {
        hideAllProgressBars();
        tvSteps.setText("步数：-");
        tvHeart.setText("心率：-");
        tvSpo2.setText("血氧：-");
        tvBp.setText("血压：-");
        tvBloodGlucose.setText("血糖：-");
        tvSleep.setText("睡眠：-");
        lastSteps = lastHeart = lastSpo2 = lastBpHigh = lastBpLow = lastSleep = 0;
        lastBloodGlucose = 0f;
        heartRateList.clear();
        spo2hList.clear();
        hasRetrieved = false;
        if (cardResult != null) cardResult.setVisibility(View.GONE);
    }

    private void log(String msg) { }
    private void safeUi(Runnable r) { uiHandler.post(() -> { if (isAdded()) r.run(); }); }
    private String formatMinutes(int min) { int h = min / 60, m = min % 60; return h + "小时" + m + "分"; }
}
