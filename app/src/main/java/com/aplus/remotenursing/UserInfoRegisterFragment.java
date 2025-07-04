package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.models.UserInfo;
import com.google.gson.Gson;

import java.util.Calendar;
import com.google.android.material.datepicker.MaterialDatePicker; // 新增
import java.text.SimpleDateFormat; // 新增
import java.util.Date; // 新增
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.Locale;

public class UserInfoRegisterFragment extends Fragment {

    private EditText etName, etPhone;
    private TextView tvGender, tvBirth, tvMarital, tvEducation, tvLiving, tvJob, tvIncome, tvInsurance;
    private final Gson gson = new Gson();
    private MaterialDatePicker<Long> birthdayPicker;

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
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());
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
            etName.setText(info.getUser_name());
            etPhone.setText(info.getPhone());
            tvGender.setText(info.getGender());
            tvBirth.setText(info.getBirth_date());
            tvMarital.setText(info.getMarital_status());
            tvEducation.setText(info.getEducation_level());
            tvLiving.setText(info.getLiving_status());
            tvJob.setText(info.getJob_status());
            tvIncome.setText(info.getIncome_per_capita());
            tvInsurance.setText(info.getInsurance_type());
        }
    }

    private void saveInfo() {
        UserInfo info = new UserInfo();
        info.setUser_name(etName.getText().toString());
        info.setPhone(etPhone.getText().toString());
        info.setGender(tvGender.getText().toString());
        info.setBirth_date(tvBirth.getText().toString());
        info.setMarital_status(tvMarital.getText().toString());
        info.setEducation_level(tvEducation.getText().toString());
        info.setLiving_status(tvLiving.getText().toString());
        info.setJob_status(tvJob.getText().toString());
        info.setIncome_per_capita(tvIncome.getText().toString());
        info.setInsurance_type(tvInsurance.getText().toString());

        String json = gson.toJson(info);
        SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
        sp.edit().putString("data", json).apply();
        syncToServer(info);
        requireActivity().onBackPressed();
    }

    private void syncToServer(UserInfo info) {
        OkHttpClient client = new OkHttpClient();
        String json = gson.toJson(info);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("http://192.168.2.9:8080/api/user")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String resp = response.body().string();
                    UserInfo saved = gson.fromJson(resp, UserInfo.class);
                    SharedPreferences sp = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE);
                    sp.edit().putString("data", gson.toJson(saved)).apply();
                }
            }
        });
    }
}