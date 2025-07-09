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
    private static final String ARG_IS_LOGGED_IN = "isLoggedIn";
    private EditText etName, etPhone;
    private TextView tvGender, tvBirth, tvMarital, tvEducation, tvLiving, tvJob, tvIncome, tvInsurance;
    private final Gson gson = new Gson();
    private MaterialDatePicker<Long> birthdayPicker;
    private boolean isLoggedIn;
    private AlertDialog progressDialog;

    // 用户上次本地数据（只为onPause用）
    private UserInfo lastUserInfo;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        isLoggedIn = args != null && args.getBoolean(ARG_IS_LOGGED_IN, false);
    }

    public static UserInfoRegisterFragment newInstance(boolean isLoggedIn) {
        UserInfoRegisterFragment f = new UserInfoRegisterFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_LOGGED_IN, isLoggedIn);
        f.setArguments(args);
        return f;
    }

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
            saveInfoLocalAndNotify();
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
        loadLocalInfo();
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
        new AlertDialog.Builder(requireContext())
                .setItems(items, (d, which) -> target.setText(items[which]))
                .show();
    }

    private void showMulti(TextView target, int arrayRes) {
        String[] items = getResources().getStringArray(arrayRes);
        boolean[] checks = new boolean[items.length];
        new AlertDialog.Builder(requireContext())
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

    private void loadLocalInfo() {
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        String json = sp.getString("data", null);
        if (json != null) {
            UserInfo info = gson.fromJson(json, UserInfo.class);
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
            lastUserInfo = info;
        }
    }

    // --- 新增：loading弹窗 ---
    private void showLoading() {
        if (progressDialog == null) {
            progressDialog = new AlertDialog.Builder(requireContext())
                    .setView(R.layout.dialog_loading)
                    .setCancelable(false)
                    .create();
        }
        progressDialog.show();
    }
    private void hideLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // 核心方法1：保存到本地并通知前一个Fragment刷新（无论保存还是返回都会用到）
    private void saveInfoLocalAndNotify() {
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

        if (isLoggedIn) {
            SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
            String local = sp.getString("data", null);
            if (local != null) {
                UserInfo localInfo = gson.fromJson(local, UserInfo.class);
                info.setUserId(localInfo.getUserId());
            }
        }

        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        sp.edit().putString("data", gson.toJson(info)).apply();

        // 通知MyInfoFragment刷新
        Bundle bundle = new Bundle();
        bundle.putBoolean("user_updated", true);
        bundle.putString("latest_user_json", gson.toJson(info));
        getParentFragmentManager().setFragmentResult("user_info_changed", bundle);
    }

    // 核心方法2：页面即将消失时自动保存并通知，保证即使“返回”也刷新
    @Override
    public void onPause() {
        super.onPause();
        if (getView() != null && isAdded()) {
            saveInfoLocalAndNotify();
        }
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

        if (isLoggedIn) {
            SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
            String local = sp.getString("data", null);
            if (local != null) {
                UserInfo localInfo = gson.fromJson(local, UserInfo.class);
                info.setUserId(localInfo.getUserId());
            }
        }

        showLoading();
        syncToServer(info);
    }

    private void syncToServer(UserInfo info) {
        OkHttpClient client = new OkHttpClient();
        String json = gson.toJson(info);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request;

        if (isLoggedIn) {
            String url = "http://192.168.2.9:8080/api/user/" + info.getUserId();
            request = new Request.Builder().url(url).put(body).build();
        } else {
            request = new Request.Builder().url("http://192.168.2.9:8080/api/user").post(body).build();
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(requireContext(), "保存失败，请检查网络", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    String resp = response.body().string();
                    UserInfo saved = gson.fromJson(resp, UserInfo.class);

                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        if (!isAdded()) return;
                        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                        sp.edit().putString("data", gson.toJson(saved)).apply();
                        // 通知MyInfoFragment刷新
                        Bundle bundle = new Bundle();
                        bundle.putBoolean("user_updated", true);
                        bundle.putString("latest_user_json", gson.toJson(saved));
                        getParentFragmentManager().setFragmentResult("user_info_changed", bundle);
                        // 返回上一级
                        requireActivity().getSupportFragmentManager().popBackStack();
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(requireContext(), "服务器错误，保存失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}
