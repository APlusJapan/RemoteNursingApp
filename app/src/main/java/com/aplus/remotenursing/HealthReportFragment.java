package com.aplus.remotenursing;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.manager.FragmentSafetyManager;
import com.aplus.remotenursing.models.UserAccount;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HealthReportFragment extends Fragment {

    private static final String TAG = "HealthReportFragment";
    private static final String PREFS_NAME = "checkup_history";

    private MaterialButton btn7Days, btn30Days, btnAll;
    private int selectedDays = 7;

    private LineChart chartHeartRate, chartSpo2, chartBp, chartGlucose, chartSteps, chartSleep;

    private TextView tvLatestHeart, tvLatestSpo2, tvLatestBpHigh, tvLatestBpLow;
    private TextView tvLatestGlucose, tvLatestSteps, tvLatestSleep;
    private TextView tvLatestDate;

    // 标准值显示
    private TextView tvHeartStandard, tvSpo2Standard, tvStepsStandard;
    private TextView tvBpStandard, tvGlucoseStandard, tvSleepStandard;

    private LinearLayout llEmpty, llContent;

    private String userId;
    private Gson gson = new Gson();

    private List<String> dateLabels = new ArrayList<>();
    private List<CheckupStandard> standardList = new ArrayList<>();

    public static class CheckupStandard {
        public String itemCode;
        public String itemName;
        public String minValue;
        public String maxValue;
        public String valueType;
        public String unit;
    }

    public static class CheckupResultData {
        public int steps;
        public int heartRate;
        public int spo2;
        public int bpHigh;
        public int bpLow;
        public float bloodGlucose;
        public int sleep;
    }

    public static class CheckupHistoryItem {
        public String date;
        public String time;
        public CheckupResultData data;
        public String conclusion;

        public CheckupHistoryItem(String date, String time, CheckupResultData data, String conclusion) {
            this.date = date;
            this.time = time;
            this.data = data;
            this.conclusion = conclusion;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initData();
        loadCheckupStandard();
    }

    private void initViews(View view) {
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                FragmentSafetyManager.safePopBackStack(this));

        btn7Days = view.findViewById(R.id.btn_7days);
        btn30Days = view.findViewById(R.id.btn_30days);
        btnAll = view.findViewById(R.id.btn_all);

        btn7Days.setOnClickListener(v -> selectTimeRange(7));
        btn30Days.setOnClickListener(v -> selectTimeRange(30));
        btnAll.setOnClickListener(v -> selectTimeRange(90));

        chartHeartRate = view.findViewById(R.id.chart_heart_rate);
        chartSpo2 = view.findViewById(R.id.chart_spo2);
        chartBp = view.findViewById(R.id.chart_bp);
        chartGlucose = view.findViewById(R.id.chart_glucose);
        chartSteps = view.findViewById(R.id.chart_steps);
        chartSleep = view.findViewById(R.id.chart_sleep);

        tvLatestHeart = view.findViewById(R.id.tv_latest_heart);
        tvLatestSpo2 = view.findViewById(R.id.tv_latest_spo2);
        tvLatestBpHigh = view.findViewById(R.id.tv_latest_bp_high);
        tvLatestBpLow = view.findViewById(R.id.tv_latest_bp_low);
        tvLatestGlucose = view.findViewById(R.id.tv_latest_glucose);
        tvLatestSteps = view.findViewById(R.id.tv_latest_steps);
        tvLatestSleep = view.findViewById(R.id.tv_latest_sleep);
        tvLatestDate = view.findViewById(R.id.tv_latest_date);

        // 标准值文本
        tvHeartStandard = view.findViewById(R.id.tv_heart_standard);
        tvSpo2Standard = view.findViewById(R.id.tv_spo2_standard);
        tvStepsStandard = view.findViewById(R.id.tv_steps_standard);
        tvBpStandard = view.findViewById(R.id.tv_bp_standard);
        tvGlucoseStandard = view.findViewById(R.id.tv_glucose_standard);
        tvSleepStandard = view.findViewById(R.id.tv_sleep_standard);

        llEmpty = view.findViewById(R.id.ll_empty);
        llContent = view.findViewById(R.id.ll_content);

        initChartStyle(chartHeartRate, "心率 (次/分)", Color.rgb(231, 76, 60));
        initChartStyle(chartSpo2, "血氧 (%)", Color.rgb(52, 152, 219));
        initChartStyle(chartBp, "血压 (mmHg)", Color.rgb(155, 89, 182));
        initChartStyle(chartGlucose, "血糖 (mmol/L)", Color.rgb(230, 126, 34));
        initChartStyle(chartSteps, "步数 (步)", Color.rgb(46, 204, 113));
        initChartStyle(chartSleep, "睡眠 (分钟)", Color.rgb(26, 188, 156));
    }

    private void initData() {
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        userId = userAccount != null ? userAccount.getUserId() : null;

        if (TextUtils.isEmpty(userId)) {
            Log.e(TAG, "用户ID为空");
            showEmpty();
            return;
        }

        selectTimeRange(7);
    }

    private void loadCheckupStandard() {
        if (TextUtils.isEmpty(userId)) {
            loadLocalHealthData();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_CHECKUP_STANDARD + userId;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(
                FragmentSafetyManager.createAutoRetryCallback(
                        this,
                        request,
                        client,
                        (responseBody) -> {
                            try {
                                Type listType = new TypeToken<List<CheckupStandard>>(){}.getType();
                                List<CheckupStandard> standards = gson.fromJson(responseBody, listType);
                                standardList.clear();
                                if (standards != null) {
                                    standardList.addAll(standards);
                                }
                                Log.d(TAG, "加载体检标准成功，共" + standardList.size() + "项");
                                updateStandardTexts();
                            } catch (Exception e) {
                                Log.e(TAG, "解析体检标准失败：" + e.getMessage());
                            } finally {
                                loadLocalHealthData();
                            }
                        },
                        "获取体检标准"
                )
        );
    }

    // 更新标准值文本显示
    private void updateStandardTexts() {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            CheckupStandard stdHeart = findStandard("HEART_RATE");
            CheckupStandard stdSpo2 = findStandard("SPO2");
            CheckupStandard stdBpHigh = findStandard("BLOOD_PRESSURE_HIGH");
            CheckupStandard stdBpLow = findStandard("BLOOD_PRESSURE_LOW");
            CheckupStandard stdGlucose = findStandard("BLOOD_GLUCOSE");
            CheckupStandard stdStep = findStandard("STEP");
            CheckupStandard stdSleep = findStandard("SLEEP_TIME");

            if (stdHeart != null) {
                tvHeartStandard.setText("参考:" + stdHeart.minValue + "-" + stdHeart.maxValue);
                tvHeartStandard.setVisibility(View.VISIBLE);
            }
            if (stdSpo2 != null) {
                tvSpo2Standard.setText("参考:≥" + stdSpo2.minValue);
                tvSpo2Standard.setVisibility(View.VISIBLE);
            }
            if (stdStep != null) {
                tvStepsStandard.setText("参考:≥" + stdStep.minValue);
                tvStepsStandard.setVisibility(View.VISIBLE);
            }
            if (stdBpHigh != null && stdBpLow != null) {
                tvBpStandard.setText("参考:" + stdBpHigh.minValue + "-" + stdBpHigh.maxValue +
                        "/" + stdBpLow.minValue + "-" + stdBpLow.maxValue);
                tvBpStandard.setVisibility(View.VISIBLE);
            }
            if (stdGlucose != null) {
                tvGlucoseStandard.setText("参考:" + stdGlucose.minValue + "-" + stdGlucose.maxValue);
                tvGlucoseStandard.setVisibility(View.VISIBLE);
            }
            if (stdSleep != null) {
                tvSleepStandard.setText("参考:≥" + formatMinutes(Integer.parseInt(stdSleep.minValue)));
                tvSleepStandard.setVisibility(View.VISIBLE);
            }
        });
    }

    private void selectTimeRange(int days) {
        selectedDays = days;

        btn7Days.setBackgroundTintList(days == 7 ?
                getResources().getColorStateList(R.color.colorPrimary) :
                getResources().getColorStateList(android.R.color.darker_gray));
        btn30Days.setBackgroundTintList(days == 30 ?
                getResources().getColorStateList(R.color.colorPrimary) :
                getResources().getColorStateList(android.R.color.darker_gray));
        btnAll.setBackgroundTintList(days == 90 ?
                getResources().getColorStateList(R.color.colorPrimary) :
                getResources().getColorStateList(android.R.color.darker_gray));

        loadLocalHealthData();
    }

    private void loadLocalHealthData() {
        if (TextUtils.isEmpty(userId)) return;

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();

        TreeMap<String, CheckupHistoryItem> historyMap = new TreeMap<>();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith(userId + "_data_")) {
                String dateTimePart = key.substring((userId + "_data_").length());
                String dateStr = dateTimePart.substring(0, 8);
                String timeStr = dateTimePart.substring(9, 15);

                try {
                    String dataJson = (String) entry.getValue();
                    CheckupResultData data = gson.fromJson(dataJson, CheckupResultData.class);

                    String conclusionKey = userId + "_conclusion_" + dateTimePart;
                    String conclusion = prefs.getString(conclusionKey, "");

                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date date = inputFormat.parse(dateStr);
                    String formattedDate = outputFormat.format(date);

                    SimpleDateFormat timeInputFormat = new SimpleDateFormat("HHmmss", Locale.getDefault());
                    SimpleDateFormat timeOutputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    Date time = timeInputFormat.parse(timeStr);
                    String formattedTime = timeOutputFormat.format(time);

                    CheckupHistoryItem newItem = new CheckupHistoryItem(formattedDate, formattedTime, data, conclusion);
                    if (historyMap.containsKey(formattedDate)) {
                        CheckupHistoryItem existingItem = historyMap.get(formattedDate);
                        if (formattedTime.compareTo(existingItem.time) > 0) {
                            historyMap.put(formattedDate, newItem);
                        }
                    } else {
                        historyMap.put(formattedDate, newItem);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "解析体检数据失败: " + key, e);
                }
            }
        }

        if (historyMap.isEmpty()) {
            showEmpty();
            return;
        }

        List<CheckupHistoryItem> historyList = new ArrayList<>(historyMap.values());
        List<CheckupHistoryItem> filteredList = filterByDays(historyList, selectedDays);

        if (filteredList.isEmpty()) {
            showEmpty();
            return;
        }

        displayHealthData(filteredList);
    }

    private List<CheckupHistoryItem> filterByDays(List<CheckupHistoryItem> list, int days) {
        List<CheckupHistoryItem> filtered = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        try {
            Date now = new Date();
            long cutoffTime = now.getTime() - (days * 24L * 60 * 60 * 1000);

            for (CheckupHistoryItem item : list) {
                Date itemDate = sdf.parse(item.date);
                if (itemDate.getTime() >= cutoffTime) {
                    filtered.add(item);
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "日期过滤失败", e);
            return list;
        }

        return filtered;
    }

    private void displayHealthData(List<CheckupHistoryItem> historyList) {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            CheckupHistoryItem latest = historyList.get(historyList.size() - 1);
            displayLatestValues(latest);

            drawCharts(historyList);
            adjustChartWidth(historyList.size());

            llEmpty.setVisibility(View.GONE);
            llContent.setVisibility(View.VISIBLE);
        });
    }

    private void adjustChartWidth(int dataPointCount) {
        int dpPerPoint = 60;
        int minWidthDp = Math.max(400, dataPointCount * dpPerPoint);

        float density = getResources().getDisplayMetrics().density;
        int minWidthPx = (int) (minWidthDp * density);

        setChartWidth(chartHeartRate, minWidthPx);
        setChartWidth(chartSpo2, minWidthPx);
        setChartWidth(chartBp, minWidthPx);
        setChartWidth(chartGlucose, minWidthPx);
        setChartWidth(chartSteps, minWidthPx);
        setChartWidth(chartSleep, minWidthPx);
    }

    private void setChartWidth(LineChart chart, int widthPx) {
        ViewGroup.LayoutParams params = chart.getLayoutParams();
        params.width = widthPx;
        chart.setLayoutParams(params);
    }

    private void displayLatestValues(CheckupHistoryItem item) {
        CheckupResultData data = item.data;

        tvLatestHeart.setText(data.heartRate > 0 ? String.valueOf(data.heartRate) : "-");
        tvLatestSpo2.setText(data.spo2 > 0 ? String.valueOf(data.spo2) : "-");
        tvLatestBpHigh.setText(data.bpHigh > 0 ? String.valueOf(data.bpHigh) : "-");
        tvLatestBpLow.setText(data.bpLow > 0 ? String.valueOf(data.bpLow) : "-");
        tvLatestGlucose.setText(data.bloodGlucose > 0 ?
                String.format(Locale.getDefault(), "%.1f", data.bloodGlucose) : "-");
        tvLatestSteps.setText(data.steps > 0 ? String.valueOf(data.steps) : "-");
        tvLatestSleep.setText(data.sleep > 0 ? formatMinutes(data.sleep) : "-");

        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MM月dd日", Locale.getDefault());
            Date date = inputFormat.parse(item.date);
            tvLatestDate.setText("最新数据：" + outputFormat.format(date) + " " + item.time);
        } catch (Exception e) {
            tvLatestDate.setText("最新数据：" + item.date + " " + item.time);
        }
    }

    private void drawCharts(List<CheckupHistoryItem> historyList) {
        dateLabels.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd", Locale.getDefault());
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (CheckupHistoryItem item : historyList) {
            try {
                Date date = inputFormat.parse(item.date);
                dateLabels.add(sdf.format(date));
            } catch (ParseException e) {
                dateLabels.add(item.date);
            }
        }

        // 更新图表标题显示标准值
        updateChartTitles();

        drawSingleLineChart(chartHeartRate, historyList, "heartRate", Color.rgb(231, 76, 60));
        drawSingleLineChart(chartSpo2, historyList, "spo2", Color.rgb(52, 152, 219));
        drawBpChart(chartBp, historyList);
        drawSingleLineChart(chartGlucose, historyList, "bloodGlucose", Color.rgb(230, 126, 34));
        drawSingleLineChart(chartSteps, historyList, "steps", Color.rgb(46, 204, 113));
        drawSingleLineChart(chartSleep, historyList, "sleep", Color.rgb(26, 188, 156));
    }

    // 更新图表标题,显示标准值
    private void updateChartTitles() {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            TextView tvHeartTitle = getView().findViewById(R.id.tv_heart_title);
            TextView tvSpo2Title = getView().findViewById(R.id.tv_spo2_title);
            TextView tvBpTitle = getView().findViewById(R.id.tv_bp_title);
            TextView tvGlucoseTitle = getView().findViewById(R.id.tv_glucose_title);
            TextView tvStepsTitle = getView().findViewById(R.id.tv_steps_title);
            TextView tvSleepTitle = getView().findViewById(R.id.tv_sleep_title);

            CheckupStandard stdHeart = findStandard("HEART_RATE");
            CheckupStandard stdSpo2 = findStandard("SPO2");
            CheckupStandard stdBpHigh = findStandard("BLOOD_PRESSURE_HIGH");
            CheckupStandard stdBpLow = findStandard("BLOOD_PRESSURE_LOW");
            CheckupStandard stdGlucose = findStandard("BLOOD_GLUCOSE");
            CheckupStandard stdStep = findStandard("STEP");
            CheckupStandard stdSleep = findStandard("SLEEP_TIME");

            if (stdHeart != null) {
                tvHeartTitle.setText("心率趋势 (标准:" + stdHeart.minValue + "-" + stdHeart.maxValue + "次/分)");
            }
            if (stdSpo2 != null) {
                tvSpo2Title.setText("血氧趋势 (标准:大于" + stdSpo2.minValue + "%)");
            }
            if (stdBpHigh != null && stdBpLow != null) {
                tvBpTitle.setText("血压趋势 (标准:" + stdBpHigh.minValue + "-" + stdBpHigh.maxValue +
                        "(高压)/" + stdBpLow.minValue + "-" + stdBpLow.maxValue + "(低压))");
            }
            if (stdGlucose != null) {
                tvGlucoseTitle.setText("血糖趋势 (标准:" + stdGlucose.minValue + "-" + stdGlucose.maxValue + " mmol/L)");
            }
            if (stdStep != null) {
                tvStepsTitle.setText("步数趋势 (标准:大于" + stdStep.minValue + "步)");
            }
            if (stdSleep != null) {
                tvSleepTitle.setText("睡眠趋势 (标准:大于" + formatMinutes(Integer.parseInt(stdSleep.minValue)) + ")");
            }
        });
    }

    private void drawSingleLineChart(LineChart chart, List<CheckupHistoryItem> historyList,
                                     String fieldName, int color) {
        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < historyList.size(); i++) {
            CheckupHistoryItem item = historyList.get(i);
            Float value = getFieldValue(item.data, fieldName);
            if (value != null && value > 0) {
                // 如果是睡眠数据,转换为小时(保留1位小数)
                if ("sleep".equals(fieldName)) {
                    value = value / 60f;
                }
                entries.add(new Entry(i, value));
            }
        }

        if (entries.isEmpty()) {
            chart.setNoDataText("暂无数据");
            chart.setNoDataTextColor(Color.GRAY);
            chart.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(4f);
        dataSet.setCircleRadius(6f);
        dataSet.setDrawCircleHole(false);

        if (entries.size() <= 7) {
            dataSet.setDrawValues(true);
            dataSet.setValueTextSize(16f);
        } else if (entries.size() <= 15) {
            dataSet.setDrawValues(true);
            dataSet.setValueTextSize(12f);
        } else {
            dataSet.setDrawValues(false);
        }

        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        // 如果是睡眠图表,设置数值格式为1位小数
        if ("sleep".equals(fieldName)) {
            dataSet.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%.1f", value);
                }
            });
        }

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        YAxis leftAxis = chart.getAxisLeft();

        if ("bloodGlucose".equals(fieldName)) {
            CheckupStandard stdGlucose = findStandard("BLOOD_GLUCOSE");
            if (stdGlucose != null) {
                try {
                    float maxStandard = Float.parseFloat(stdGlucose.maxValue);
                    leftAxis.setAxisMaximum(maxStandard + 0.5f); // 7.0 + 0.5 = 7.5
                    leftAxis.setAxisMinimum(3f); // 从3开始,更清晰
                    leftAxis.setLabelCount(5, false); // 显示5个标签: 3, 4, 5, 6, 7
                    leftAxis.setGranularity(1f); // 每个刻度间隔1
                } catch (NumberFormatException e) {
                    Log.e(TAG, "解析血糖标准值失败");
                }
            }
        }
        setupXAxis(chart);
        chart.invalidate();
    }

    private void drawBpChart(LineChart chart, List<CheckupHistoryItem> historyList) {
        List<Entry> highEntries = new ArrayList<>();
        List<Entry> lowEntries = new ArrayList<>();

        for (int i = 0; i < historyList.size(); i++) {
            CheckupHistoryItem item = historyList.get(i);
            if (item.data.bpHigh > 0) {
                highEntries.add(new Entry(i, item.data.bpHigh));
            }
            if (item.data.bpLow > 0) {
                lowEntries.add(new Entry(i, item.data.bpLow));
            }
        }

        if (highEntries.isEmpty() && lowEntries.isEmpty()) {
            chart.setNoDataText("暂无数据");
            chart.setNoDataTextColor(Color.GRAY);
            chart.invalidate();
            return;
        }

        LineDataSet highSet = new LineDataSet(highEntries, "高压");
        highSet.setColor(Color.rgb(231, 76, 60));
        highSet.setCircleColor(Color.rgb(231, 76, 60));
        highSet.setLineWidth(4f);
        highSet.setCircleRadius(6f);
        highSet.setDrawCircleHole(false);
        highSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        if (highEntries.size() <= 7) {
            highSet.setDrawValues(true);
            highSet.setValueTextSize(16f);
        } else if (highEntries.size() <= 15) {
            highSet.setDrawValues(true);
            highSet.setValueTextSize(12f);
        } else {
            highSet.setDrawValues(false);
        }

        LineDataSet lowSet = new LineDataSet(lowEntries, "低压");
        lowSet.setColor(Color.rgb(52, 152, 219));
        lowSet.setCircleColor(Color.rgb(52, 152, 219));
        lowSet.setLineWidth(4f);
        lowSet.setCircleRadius(6f);
        lowSet.setDrawCircleHole(false);
        lowSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        if (lowEntries.size() <= 7) {
            lowSet.setDrawValues(true);
            lowSet.setValueTextSize(16f);
        } else if (lowEntries.size() <= 15) {
            lowSet.setDrawValues(true);
            lowSet.setValueTextSize(12f);
        } else {
            lowSet.setDrawValues(false);
        }

        LineData lineData = new LineData(highSet, lowSet);
        chart.setData(lineData);

        Legend legend = chart.getLegend();
        legend.setTextSize(18f);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setFormSize(12f);

        setupXAxis(chart);
        chart.invalidate();
    }

    private CheckupStandard findStandard(String itemCode) {
        for (CheckupStandard s : standardList) {
            if (itemCode.equalsIgnoreCase(s.itemCode)) return s;
        }
        return null;
    }

    private void initChartStyle(LineChart chart, String description, int color) {
        chart.getDescription().setText(description);
        chart.getDescription().setTextSize(18f);
        chart.getDescription().setTextColor(color);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);

        // 增加左边距,避免Y轴标签和数据点数值重叠
        // 格式: setExtraOffsets(left, top, right, bottom)
        chart.setExtraOffsets(5, 5, 5, 5); // 将左边距从10改为20

        chart.setNoDataText("暂无数据");
        chart.setNoDataTextColor(Color.GRAY);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextSize(16f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setAxisLineColor(color);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setGranularityEnabled(true);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setEnabled(false);
    }

    private void setupXAxis(LineChart chart) {
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(16f);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setLabelRotationAngle(-45f);

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dateLabels.size()) {
                    return dateLabels.get(index);
                }
                return "";
            }
        });
    }

    private Float getFieldValue(CheckupResultData data, String fieldName) {
        switch (fieldName) {
            case "heartRate":
                return data.heartRate > 0 ? (float) data.heartRate : null;
            case "spo2":
                return data.spo2 > 0 ? (float) data.spo2 : null;
            case "bloodGlucose":
                return data.bloodGlucose > 0 ? data.bloodGlucose : null;
            case "steps":
                return data.steps > 0 ? (float) data.steps : null;
            case "sleep":
                return data.sleep > 0 ? (float) data.sleep : null;
            default:
                return null;
        }
    }

    private String formatMinutes(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return hours + "时" + mins + "分";
    }

    private void showEmpty() {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            llEmpty.setVisibility(View.VISIBLE);
            llContent.setVisibility(View.GONE);
        });
    }
}