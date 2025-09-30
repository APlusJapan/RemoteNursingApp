package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.models.UserInfo;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.google.gson.Gson;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

public class UserInfoRegisterFragment extends Fragment {
    private EditText etName, etPhone;
    private TextView tvGender, tvBirth, tvMarital, tvEducation, tvLiving, tvJob, tvIncome, tvInsurance;
    private final Gson gson = new Gson();
    private MaterialDatePicker<Long> birthdayPicker;
    private DatePickerDialog datePickerDialog; // 添加传统DatePicker作为备选
    private AlertDialog progressDialog;
    private TextView loadingTextView;
    private boolean isRequesting = false;
    private boolean isDatePickerShowing = false; // 防止重复打开

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_userinfo_register, container, false);
    }

    private void initBirthdayPicker() {
        if (birthdayPicker == null) {
            birthdayPicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("请选择生日")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            birthdayPicker.addOnPositiveButtonClickListener(selection -> {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String dateStr = sdf.format(new Date((Long) selection));
                tvBirth.setText(dateStr);
                tvBirth.setTextColor(getResources().getColor(android.R.color.black)); // 设置选中后的颜色
                isDatePickerShowing = false;
                Log.d("DatePicker", "日期选择完成: " + dateStr);
            });

            birthdayPicker.addOnNegativeButtonClickListener(selection -> {
                isDatePickerShowing = false;
                Log.d("DatePicker", "取消日期选择");
            });

            birthdayPicker.addOnCancelListener(dialog -> {
                isDatePickerShowing = false;
                Log.d("DatePicker", "日期选择器取消");
            });

            birthdayPicker.addOnDismissListener(dialog -> {
                isDatePickerShowing = false;
                Log.d("DatePicker", "日期选择器关闭");
            });
        }
    }

    // 初始化传统DatePicker作为备选方案
    private void initTraditionalDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                            selectedYear, selectedMonth + 1, selectedDay);
                    tvBirth.setText(dateStr);
                    tvBirth.setTextColor(getResources().getColor(android.R.color.black));
                    isDatePickerShowing = false;
                    Log.d("DatePicker", "传统日期选择完成: " + dateStr);
                },
                year, month, day
        );

        datePickerDialog.setOnCancelListener(dialog -> {
            isDatePickerShowing = false;
            Log.d("DatePicker", "传统日期选择器取消");
        });

        datePickerDialog.setOnDismissListener(dialog -> {
            isDatePickerShowing = false;
            Log.d("DatePicker", "传统日期选择器关闭");
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (!isRequesting) // 正在请求时不允许pop
                requireActivity().getSupportFragmentManager().popBackStack();
            else
                showErrorSafe("操作进行中，请稍候");
        });

        etName = view.findViewById(R.id.et_name);
        etPhone = view.findViewById(R.id.et_phone);
        tvGender = view.findViewById(R.id.tv_gender);
        tvBirth = view.findViewById(R.id.tv_birth);
        tvMarital = view.findViewById(R.id.tv_marital);
        tvEducation = view.findViewById(R.id.tv_education);
        tvLiving = view.findViewById(R.id.tv_living);
        tvJob = view.findViewById(R.id.tv_job);
        tvIncome = view.findViewById(R.id.tv_income);
        tvInsurance = view.findViewById(R.id.tv_insurance);

        setPickerListeners();
        fetchAndFillUserInfo();
        view.findViewById(R.id.btn_save).setOnClickListener(v -> saveInfo());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isRequesting = false;
        isDatePickerShowing = false;

        // 清理MaterialDatePicker
        if (birthdayPicker != null && birthdayPicker.isVisible()) {
            try {
                birthdayPicker.dismiss();
            } catch (Exception e) {
                Log.w("DatePicker", "MaterialDatePicker dismiss error: " + e.getMessage());
            }
        }
        birthdayPicker = null;

        // 清理传统DatePicker
        if (datePickerDialog != null && datePickerDialog.isShowing()) {
            try {
                datePickerDialog.dismiss();
            } catch (Exception e) {
                Log.w("DatePicker", "DatePickerDialog dismiss error: " + e.getMessage());
            }
        }
        datePickerDialog = null;

        // 清理ProgressDialog
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
        loadingTextView = null;
    }

    private void setPickerListeners() {
        tvGender.setOnClickListener(v -> showSingle(tvGender, R.array.gender_options));
        tvMarital.setOnClickListener(v -> showSingle(tvMarital, R.array.marital_options));
        tvEducation.setOnClickListener(v -> showSingle(tvEducation, R.array.education_options));
        tvJob.setOnClickListener(v -> showSingle(tvJob, R.array.job_options));
        tvIncome.setOnClickListener(v -> showSingle(tvIncome, R.array.income_options));
        tvInsurance.setOnClickListener(v -> showSingle(tvInsurance, R.array.insurance_options));
        tvLiving.setOnClickListener(v -> showMulti(tvLiving, R.array.living_options));

        // 优化生日选择的点击监听
        tvBirth.setOnClickListener(v -> {
            Log.d("DatePicker", "生日TextView被点击");
            showDateOptimized();
        });

        // 为整个生日行添加点击监听，增加点击区域
        View llBirth = getView().findViewById(R.id.ll_birth);
        if (llBirth != null) {
            llBirth.setOnClickListener(v -> {
                Log.d("DatePicker", "生日行被点击");
                showDateOptimized();
            });
        }
    }

    private void showSingle(TextView target, int arrayRes) {
        String[] items = getResources().getStringArray(arrayRes);
        new AlertDialog.Builder(getActivitySafe())
                .setItems(items, (d, which) -> {
                    target.setText(items[which]);
                    target.setTextColor(getResources().getColor(android.R.color.black));
                })
                .show();
    }

    private void showMulti(TextView target, int arrayRes) {
        String[] items = getResources().getStringArray(arrayRes);
        boolean[] checks = new boolean[items.length];
        new AlertDialog.Builder(getActivitySafe())
                .setMultiChoiceItems(items, checks, (d, which, isChecked) -> checks[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < items.length; i++) {
                        if (checks[i]) {
                            if (sb.length() > 0) sb.append(',');
                            sb.append(items[i]);
                        }
                    }
                    target.setText(sb.toString());
                    target.setTextColor(getResources().getColor(android.R.color.black));
                })
                .show();
    }

    // 优化的日期选择方法
    private void showDateOptimized() {
        // 防止重复打开
        if (isDatePickerShowing) {
            Log.d("DatePicker", "日期选择器已在显示，忽略重复点击");
            return;
        }

        isDatePickerShowing = true;
        Log.d("DatePicker", "开始显示日期选择器");

        // 优先使用MaterialDatePicker，如果失败则使用传统DatePicker
        try {
            initBirthdayPicker();
            if (birthdayPicker != null && !birthdayPicker.isAdded()) {
                birthdayPicker.show(getParentFragmentManager(), "MATERIAL_DATE_PICKER");
                Log.d("DatePicker", "MaterialDatePicker显示成功");
            } else {
                Log.w("DatePicker", "MaterialDatePicker已添加或为null，使用传统方式");
                showTraditionalDatePicker();
            }
        } catch (Exception e) {
            Log.e("DatePicker", "MaterialDatePicker显示失败: " + e.getMessage());
            showTraditionalDatePicker();
        }
    }

    // 显示传统DatePicker
    private void showTraditionalDatePicker() {
        try {
            initTraditionalDatePicker();
            if (datePickerDialog != null) {
                datePickerDialog.show();
                Log.d("DatePicker", "传统DatePicker显示成功");
            }
        } catch (Exception e) {
            Log.e("DatePicker", "传统DatePicker显示失败: " + e.getMessage());
            isDatePickerShowing = false;
            showErrorSafe("日期选择器打开失败，请重试");
        }
    }

    private void showDate(TextView target) {
        showDateOptimized();
    }

    private void showLoading(String text) {
        isRequesting = true;
        if (progressDialog == null) {
            View dialogView = LayoutInflater.from(getActivitySafe()).inflate(R.layout.dialog_loading, null);
            loadingTextView = dialogView.findViewById(R.id.tv_loading);
            progressDialog = new AlertDialog.Builder(getActivitySafe())
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();
        }
        if (loadingTextView != null) {
            loadingTextView.setText(text);
        }
        progressDialog.show();
    }

    private void hideLoading() {
        isRequesting = false;
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private boolean checkInput(UserInfo info) {
        if (info.getUserName() == null || info.getUserName().trim().isEmpty()) {
            showErrorSafe("请填写姓名");
            return false;
        }
        if (info.getPhone() == null || info.getPhone().trim().isEmpty()) {
            showErrorSafe("请填写手机号");
            return false;
        }
        return true;
    }

    // ----------- 网络请求安全版 -----------
    private void fetchAndFillUserInfo() {
        String userId = UserUtils.loadUserId(requireContext());
        if (userId == null || userId.isEmpty()) return;

        showLoading("正在查询，请稍后");
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_USER_INFO + userId;
        Log.d("fetchAndFillUserInfo", "fetchAndFillUserInfo, URL: " + url);

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.d("UserInfoDebug", "onFailure: " + e.getMessage());
                        runUiSafe(() -> hideLoading());
                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!isAdded() || getActivity() == null) {
                            Log.d("UserInfoDebug", "Fragment已销毁，不再回调UI");
                            response.close();
                            return;
                        }
                        int code = response.code();
                        try {
                            String resp = response.body().string();
                            Log.d("fetchAndFillUserInfo", "HTTP status: " + code + " body: " + resp);

                            UserInfo info = null;
                            try {
                                info = gson.fromJson(resp, UserInfo.class);
                                Log.d("fetchAndFillUserInfo", "解析后 info: " + info);
                            } catch (Exception ignore) {
                                Log.e("fetchAndFillUserInfo", "JSON解析异常: " + ignore.getMessage());
                            }
                            final UserInfo finalInfo = info;
                            runUiSafe(() -> {
                                hideLoading();
                                if (finalInfo != null && finalInfo.getUserId() != null) {
                                    Log.d("UserInfoDebug", "will call fillUserInfo");
                                    fillUserInfo(finalInfo);
                                } else {
                                    showErrorSafe("未查到用户信息（" + code + "）");
                                }
                            });
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void fillUserInfo(UserInfo info) {
        Log.d("UserInfo", "[DEBUG] userId=" + info.getUserId());
        Log.d("UserInfo", "[DEBUG] userName=" + info.getUserName());
        Log.d("UserInfo", "[DEBUG] gender=" + info.getGender());
        Log.d("UserInfo", "[DEBUG] birthDate=" + info.getBirthDate());
        Log.d("UserInfo", "[DEBUG] phone=" + info.getPhone());
        Log.d("UserInfo", "[DEBUG] maritalStatus=" + info.getMaritalStatus());
        Log.d("UserInfo", "[DEBUG] educationLevel=" + info.getEducationLevel());
        Log.d("UserInfo", "[DEBUG] livingStatus=" + info.getLivingStatus());
        Log.d("UserInfo", "[DEBUG] jobStatus=" + info.getJobStatus());
        Log.d("UserInfo", "[DEBUG] incomePerCapita=" + info.getIncomePerCapita());

        etName.setText(info.getUserName());
        etPhone.setText(info.getPhone());
        tvGender.setText(info.getGender());
        tvBirth.setText(info.getBirthDate());
        tvMarital.setText(info.getMaritalStatus());
        tvEducation.setText(info.getEducationLevel());
        tvLiving.setText(info.getLivingStatus());
        tvJob.setText(info.getJobStatus());
        tvIncome.setText(info.getIncomePerCapita());
        tvInsurance.setText(info.getInsuranceType());
    }

    private void saveInfo() {
        UserInfo info = new UserInfo();
        info.setUserName(etName.getText().toString());
        info.setPhone(etPhone.getText().toString());
        info.setGender(tvGender.getText().toString());
        info.setBirthDate(tvBirth.getText().toString());
        info.setMaritalStatus(tvMarital.getText().toString());
        info.setEducationLevel(tvEducation.getText().toString());
        info.setLivingStatus(tvLiving.getText().toString());
        info.setJobStatus(tvJob.getText().toString());
        info.setIncomePerCapita(tvIncome.getText().toString());
        info.setInsuranceType(tvInsurance.getText().toString());

        String userId = UserUtils.loadUserId(requireContext());
        if (userId != null && !userId.isEmpty()) {
            info.setUserId(userId);
        }

        if (!checkInput(info)) return;

        showLoading("正在保存，请稍后");

        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_USER_INFO + userId;
        Request getRequest = new Request.Builder().url(url).get().build();

        client.newCall(getRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runUiSafe(() -> {
                    hideLoading();
                    showErrorSafe("保存失败，请检查网络");
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean exists = false;
                try {
                    if (response.isSuccessful()) {
                        try {
                            String resp = response.body().string();
                            UserInfo infoRemote = gson.fromJson(resp, UserInfo.class);
                            exists = (infoRemote != null && infoRemote.getUserId() != null);
                        } catch (Exception ignore) { }
                    }
                } finally {
                    response.close();
                }
                doSaveOrUpdate(info, exists);
            }
        });
    }

    private void doSaveOrUpdate(UserInfo info, boolean exists) {
        OkHttpClient client = new OkHttpClient();
        String json = gson.toJson(info);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request request;
        if (exists) {
            request = new Request.Builder()
                    .url(ApiConfig.API_UPDATE_USER_INFO + info.getUserId())
                    .put(body)
                    .build();
        } else {
            request = new Request.Builder()
                    .url(ApiConfig.API_CREATE_USER_INFO)
                    .post(body)
                    .build();
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runUiSafe(() -> {
                    hideLoading();
                    showErrorSafe("保存失败，请检查网络");
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean successful;
                try {
                    successful = response.isSuccessful();
                } finally {
                    response.close();
                }
                runUiSafe(() -> {
                    hideLoading();
                    if (successful) {
                        showSuccessSafe("信息保存成功");
                        if (isAdded() && getActivity() != null) {
                            while (getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                                getActivity().getSupportFragmentManager().popBackStackImmediate();
                            }
                        }
                    } else {
                        showErrorSafe("服务器错误，保存失败");
                    }
                });
            }
        });
    }

    // ---- 工具方法 ----
    private Context getActivitySafe() {
        if (getActivity() != null) return getActivity();
        if (getContext() != null) return getContext();
        throw new IllegalStateException("Fragment已分离，getActivity/getContext都为null");
    }

    private void runUiSafe(Runnable runnable) {
        // 进一步优化：Fragment和Activity必须都活着才回调UI
        if (!isAdded() || getActivity() == null) {
            Log.d("UserInfoDebug", "runUiSafe return, isAdded=" + isAdded() + " getActivity=" + getActivity());
            hideLoading();
            return;
        }
        getActivity().runOnUiThread(() -> {
            if (!isAdded() || getActivity() == null) {
                hideLoading();
                return;
            }
            try {
                runnable.run();
            } catch (Throwable ignore) {}
        });
    }

    private void showErrorSafe(String msg) {
        runUiSafe(() -> InfoPopup.showError(getActivitySafe(), msg));
    }

    private void showSuccessSafe(String msg) {
        runUiSafe(() -> InfoPopup.showSuccess(getActivitySafe(), msg));
    }
}