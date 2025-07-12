package com.aplus.remotenursing;

import android.app.AlertDialog;
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

import com.aplus.remotenursing.models.UserInfo;
import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;
import com.google.gson.Gson;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
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
    private AlertDialog progressDialog;
    private TextView loadingTextView;
    private boolean isRequesting = false; // 防止请求期间页面被pop

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
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (!isRequesting) // 正在请求时不允许pop
                requireActivity().getSupportFragmentManager().popBackStack();
            else
                showToastSafe("操作进行中，请稍候");
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
        tvBirth.setOnClickListener(v -> showDate(tvBirth));
    }

    private void showSingle(TextView target, int arrayRes) {
        String[] items = getResources().getStringArray(arrayRes);
        new AlertDialog.Builder(getActivitySafe())
                .setItems(items, (d, which) -> target.setText(items[which]))
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
                })
                .show();
    }

    private void showDate(TextView target) {
        initBirthdayPicker();
        birthdayPicker.show(getParentFragmentManager(), "MATERIAL_DATE_PICKER");
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
            showToastSafe("请填写姓名");
            return false;
        }
        if (info.getPhone() == null || info.getPhone().trim().isEmpty()) {
            showToastSafe("请填写手机号");
            return false;
        }
        return true;
    }

    // ----------- 网络请求安全版 -----------
    private void fetchAndFillUserInfo() {
        String userId = UserUtil.loadUserId(requireContext());
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
                            return;
                        }
                        String resp = response.body().string();
                        Log.d("fetchAndFillUserInfo", "HTTP status: " + response.code() + " body: " + resp);

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
                                showToastSafe("未查到用户信息（" + response.code() + "）");
                            }
                        });
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

        String userId = UserUtil.loadUserId(requireContext());
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
                    showToastSafe("保存失败，请检查网络");
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean exists = false;
                if (response.isSuccessful()) {
                    try {
                        String resp = response.body().string();
                        UserInfo infoRemote = gson.fromJson(resp, UserInfo.class);
                        exists = (infoRemote != null && infoRemote.getUserId() != null);
                    } catch (Exception ignore) { }
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
                    showToastSafe("保存失败，请检查网络");
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runUiSafe(() -> {
                    hideLoading();
                    if (response.isSuccessful()) {
                        showToastSafe("信息保存成功");
                        if (isAdded() && getActivity() != null) {
                            while (getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                                getActivity().getSupportFragmentManager().popBackStackImmediate();
                            }
                        }
                    } else {
                        showToastSafe("服务器错误，保存失败");
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

    private void showToastSafe(String msg) {
        runUiSafe(() -> Toast.makeText(getActivitySafe(), msg, Toast.LENGTH_SHORT).show());
    }
}
