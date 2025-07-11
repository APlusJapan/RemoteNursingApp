package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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
            // 保持这里用 requireActivity 没关系（这是同步返回按钮，不是异步回调，不会引起崩溃）
            requireActivity().getSupportFragmentManager().popBackStack();
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

    private String getUserIdFromLocal() {
        SharedPreferences sp = getActivitySafe().getSharedPreferences("user_account", Context.MODE_PRIVATE);
        String userJson = sp.getString("data", null);
        if (userJson != null) {
            UserInfo localInfo = gson.fromJson(userJson, UserInfo.class);
            return localInfo.getUserId();
        }
        return null;
    }

    private void fetchAndFillUserInfo() {
        String userId = getUserIdFromLocal();
        if (userId == null || userId.isEmpty()) return;

        showLoading("正在查询，请稍后");
        OkHttpClient client = new OkHttpClient();
        String url = "http://192.168.2.9:8080/api/userinfo/" + userId;
        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runUiSafe(() -> {
                    hideLoading();
                    // showToastSafe("查询失败，请检查网络");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body().string();
                UserInfo info = null;
                try {
                    info = gson.fromJson(resp, UserInfo.class);
                } catch (Exception ignore) { }
                final UserInfo finalInfo = info;
                runUiSafe(() -> {
                    hideLoading();
                    if (finalInfo != null && finalInfo.getUserId() != null) {
                        fillUserInfo(finalInfo);
                    }
                });
            }
        });
    }

    private void fillUserInfo(UserInfo info) {
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

        String userId = getUserIdFromLocal();
        if (userId != null && !userId.isEmpty()) {
            info.setUserId(userId);
        }

        if (!checkInput(info)) return;

        showLoading("正在保存，请稍后");

        OkHttpClient client = new OkHttpClient();
        String url = "http://192.168.2.9:8080/api/userinfo/" + userId;
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
                    .url("http://192.168.2.9:8080/api/updateUserinfo/" + info.getUserId())
                    .put(body)
                    .build();
        } else {
            request = new Request.Builder()
                    .url("http://192.168.2.9:8080/api/createUserinfo")
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
                        // 只在安全环境下执行 Fragment 操作
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

    // ---- 安全工具方法 ----
    // getActivity的安全封装，保证不会为空
    private Context getActivitySafe() {
        if (getActivity() != null) return getActivity();
        if (getContext() != null) return getContext();
        throw new IllegalStateException("Fragment已分离，getActivity/getContext都为null");
    }

    // UI线程安全运行
    private void runUiSafe(Runnable runnable) {
        if (!isAdded() || getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (!isAdded() || getActivity() == null) return;
            try {
                runnable.run();
            } catch (Throwable ignore) {}
        });
    }

    private void showToastSafe(String msg) {
        runUiSafe(() -> Toast.makeText(getActivitySafe(), msg, Toast.LENGTH_SHORT).show());
    }
}
