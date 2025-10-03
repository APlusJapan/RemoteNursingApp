package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.helper.ApiClientHelper;
import com.aplus.remotenursing.helper.CityPickerHelper;
import com.aplus.remotenursing.models.Project;
import com.aplus.remotenursing.models.Team;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.UserInfo;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserInfoRegisterFragment extends Fragment {
    private static final String ARG_USER_ID = "userId";
    private static final String ARG_PROJECT_ID = "projectId";
    private static final String ARG_TEAM_ID = "teamId";
    private static final String TAG = "UserInfoRegister";

    private EditText etName, etPhone;
    private TextView tvGender, tvBirth, tvMarital, tvEducation, tvLiving, tvJob, tvIncome, tvInsurance, tvCity;
    private Spinner spinnerProject, spinnerTeam;
    private final Gson gson = new Gson();
    private MaterialDatePicker<Long> birthdayPicker;
    private AlertDialog progressDialog;
    private TextView loadingTextView;
    private boolean isRequesting = false;

    private String currentUserId;
    private String passedProjectId;
    private String passedTeamId;

    private String currentAdminId;
    private List<Project> projectList = new ArrayList<>();
    private List<Team> teamList = new ArrayList<>();
    private ArrayAdapter<Project> projectAdapter;
    private ArrayAdapter<Team> teamAdapter;

    private OkHttpClient client = ApiClientHelper.get();
    private boolean isLoadingSpinners = false;

    // 新增:是否为查看详情模式
    private boolean isViewMode = false;

    // 新增:保存原始姓名,用于重置时恢复
    private String originalUserName = "";

    // 城市信息
    private String selectedProvince = "";
    private String selectedCity = "";
    private String selectedDistrict = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount != null) {
            currentAdminId = userAccount.getAdminId();
        }

        if (getArguments() != null) {
            currentUserId = getArguments().getString(ARG_USER_ID);
            passedProjectId = getArguments().getString(ARG_PROJECT_ID);
            passedTeamId = getArguments().getString(ARG_TEAM_ID);
            Log.d(TAG, "接收参数 - userId: " + currentUserId + ", projectId: " + passedProjectId + ", teamId: " + passedTeamId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_userinfo_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        setupSpinners();
        setPickerListeners();

        loadProjects();

        if (currentUserId != null && !currentUserId.isEmpty()) {
            Log.d(TAG, "查看详情模式");
            isViewMode = true;  // 设置为查看模式
            fetchUserInfoById(currentUserId);
        } else {
            Log.d(TAG, "新增用户模式");
            isViewMode = false;  // 设置为新增模式
        }
    }

    private void initViews(View view) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (!isRequesting)
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
        tvCity = view.findViewById(R.id.tv_city);
        spinnerProject = view.findViewById(R.id.spinner_project);
        spinnerTeam = view.findViewById(R.id.spinner_team);

        view.findViewById(R.id.btn_save).setOnClickListener(v -> saveInfo());

        view.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            if (isRequesting) {
                showErrorSafe("操作进行中，请稍候");
                return;
            }
            showResetConfirmDialog();
        });
    }

    private void showResetConfirmDialog() {
        new AlertDialog.Builder(getActivitySafe())
                .setTitle("确认重置")
                .setMessage("确定要重置所有内容吗？")
                .setPositiveButton("确定", (dialog, which) -> resetForm())
                .setNegativeButton("取消", null)
                .show();
    }

    private void resetForm() {
        Log.d(TAG, "重置表单,isViewMode: " + isViewMode);

        // 只有在新增模式下才清空姓名
        if (!isViewMode) {
            etName.setText("");
        } else {
            // 查看模式下恢复原始姓名
            etName.setText(originalUserName);
        }

        // 清空手机号
        etPhone.setText("");

        // 重置所有TextView为默认状态
        resetTextView(tvGender, "请选择");
        resetTextView(tvBirth, "请选择");
        resetTextView(tvMarital, "请选择");
        resetTextView(tvEducation, "请选择");
        resetTextView(tvLiving, "请选择");
        resetTextView(tvJob, "请选择");
        resetTextView(tvIncome, "请选择");
        resetTextView(tvInsurance, "请选择");
        resetTextView(tvCity, "请选择");

        // 重置城市信息
        selectedProvince = "";
        selectedCity = "";
        selectedDistrict = "";

        // 重置Spinner到初始状态
        isLoadingSpinners = true;

        // 如果有默认课题,选择默认课题
        boolean hasDefaultProject = false;
        for (int i = 0; i < projectList.size(); i++) {
            if (projectList.get(i).getDefaultFlg()) {
                spinnerProject.setSelection(i);
                loadTeamsAndSelect(projectList.get(i).getProjectId(), null);
                hasDefaultProject = true;
                break;
            }
        }

        // 如果没有默认课题,重置为"请选择课题"
        if (!hasDefaultProject) {
            spinnerProject.setSelection(0);
            teamList.clear();
            teamList.add(new Team("", "请先选择课题"));
            teamAdapter.notifyDataSetChanged();
            spinnerTeam.setSelection(0);
            isLoadingSpinners = false;
        }

        InfoPopup.showSuccess(getActivitySafe(), "已重置");
    }

    private void resetTextView(TextView textView, String defaultText) {
        textView.setText(defaultText);
        textView.setTextColor(getResources().getColor(android.R.color.darker_gray));
    }

    private void setupSpinners() {
        projectAdapter = new ArrayAdapter<Project>(requireContext(),
                android.R.layout.simple_spinner_item, projectList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(projectList.get(position).getDisplayName());
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(projectList.get(position).getDisplayName());
                return view;
            }
        };
        projectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProject.setAdapter(projectAdapter);

        teamAdapter = new ArrayAdapter<Team>(requireContext(),
                android.R.layout.simple_spinner_item, teamList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(teamList.get(position).getDisplayName());
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(teamList.get(position).getDisplayName());
                return view;
            }
        };
        teamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTeam.setAdapter(teamAdapter);

        teamList.add(new Team("", "请先选择课题"));
        teamAdapter.notifyDataSetChanged();

        spinnerProject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isLoadingSpinners) return;

                if (position > 0) {
                    Project selectedProject = projectList.get(position);
                    loadTeamsByProject(selectedProject.getProjectId());
                } else {
                    teamList.clear();
                    teamList.add(new Team("", "请先选择课题"));
                    teamAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadProjects() {
        if (TextUtils.isEmpty(currentAdminId)) return;

        String url = ApiConfig.API_PROJECT + currentAdminId;

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runUiSafe(() -> InfoPopup.showError(requireContext(), "加载课题列表失败"));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!isAdded() || getActivity() == null) {
                            response.close();
                            return;
                        }

                        try {
                            String resp = response.body().string();
                            Type listType = new TypeToken<List<Project>>() {}.getType();
                            List<Project> projects = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                projectList.clear();
                                projectList.add(new Project("", "请选择课题"));
                                if (projects != null) {
                                    projectList.addAll(projects);
                                }
                                projectAdapter.notifyDataSetChanged();
                                selectInitialProject();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "解析课题列表异常: " + e.getMessage());
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void selectInitialProject() {
        isLoadingSpinners = true;

        if (!TextUtils.isEmpty(passedProjectId)) {
            for (int i = 0; i < projectList.size(); i++) {
                if (projectList.get(i).getProjectId().equals(passedProjectId)) {
                    spinnerProject.setSelection(i);
                    loadTeamsAndSelect(passedProjectId, passedTeamId);
                    return;
                }
            }
        } else {
            for (int i = 0; i < projectList.size(); i++) {
                if (projectList.get(i).getDefaultFlg()) {
                    spinnerProject.setSelection(i);
                    loadTeamsAndSelect(projectList.get(i).getProjectId(), null);
                    return;
                }
            }
        }

        isLoadingSpinners = false;
    }

    private void loadTeamsAndSelect(String projectId, String teamId) {
        String url = ApiConfig.API_PROJECT_TEAM + projectId;

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runUiSafe(() -> {
                            isLoadingSpinners = false;
                            InfoPopup.showError(requireContext(), "加载分组列表失败");
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!isAdded() || getActivity() == null) {
                            response.close();
                            return;
                        }

                        try {
                            String resp = response.body().string();
                            Type listType = new TypeToken<List<Team>>() {}.getType();
                            List<Team> teams = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                teamList.clear();
                                teamList.add(new Team("", "请选择分组"));
                                if (teams != null) {
                                    teamList.addAll(teams);
                                }
                                teamAdapter.notifyDataSetChanged();

                                if (!TextUtils.isEmpty(teamId)) {
                                    for (int i = 0; i < teamList.size(); i++) {
                                        if (teamList.get(i).getTeamId().equals(teamId)) {
                                            spinnerTeam.setSelection(i);
                                            break;
                                        }
                                    }
                                }

                                isLoadingSpinners = false;
                            });
                        } catch (Exception e) {
                            runUiSafe(() -> isLoadingSpinners = false);
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void loadTeamsByProject(String projectId) {
        String url = ApiConfig.API_PROJECT_TEAM + projectId;

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runUiSafe(() -> InfoPopup.showError(requireContext(), "加载分组列表失败"));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!isAdded() || getActivity() == null) {
                            response.close();
                            return;
                        }

                        try {
                            String resp = response.body().string();
                            Type listType = new TypeToken<List<Team>>() {}.getType();
                            List<Team> teams = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                teamList.clear();
                                teamList.add(new Team("", "请选择分组"));
                                if (teams != null) {
                                    teamList.addAll(teams);
                                }
                                teamAdapter.notifyDataSetChanged();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "解析分组列表异常: " + e.getMessage());
                        } finally {
                            response.close();
                        }
                    }
                });
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
        tvBirth.setOnClickListener(v -> showDate());
        tvCity.setOnClickListener(v -> showCityPicker());
    }

    private void showCityPicker() {
        CityPickerHelper.showCityPicker(requireContext(), (province, city, district) -> {
            selectedProvince = province;
            selectedCity = city;
            selectedDistrict = district;

            String cityText = province + " " + city + " " + district;
            tvCity.setText(cityText);
            tvCity.setTextColor(getResources().getColor(R.color.colorPrimary));
        });
    }

    private void showSingle(TextView target, int arrayRes) {
        String[] items = getResources().getStringArray(arrayRes);
        new AlertDialog.Builder(getActivitySafe())
                .setItems(items, (d, which) -> {
                    target.setText(items[which]);
                    target.setTextColor(getResources().getColor(R.color.colorPrimary));
                })
                .show();
    }

    private void showMulti(TextView target, int arrayRes) {
        String[] items = getResources().getStringArray(arrayRes);
        boolean[] checks = new boolean[items.length];

        String currentText = target.getText().toString();
        if (!currentText.equals("请选择") && !TextUtils.isEmpty(currentText)) {
            String[] selected = currentText.split(",");
            for (String s : selected) {
                for (int i = 0; i < items.length; i++) {
                    if (items[i].equals(s.trim())) {
                        checks[i] = true;
                    }
                }
            }
        }

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
                    if (sb.length() > 0) {
                        target.setText(sb.toString());
                        target.setTextColor(getResources().getColor(R.color.colorPrimary));
                    }
                })
                .show();
    }

    private void initBirthdayPicker() {
        if (birthdayPicker == null) {
            birthdayPicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("请选择生日")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();
            birthdayPicker.addOnPositiveButtonClickListener(selection -> {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String dateStr = sdf.format(new Date(selection));
                tvBirth.setText(dateStr);
                tvBirth.setTextColor(getResources().getColor(R.color.colorPrimary));
            });
        }
    }

    private void showDate() {
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
        int projectPosition = spinnerProject.getSelectedItemPosition();
        int teamPosition = spinnerTeam.getSelectedItemPosition();

        if (projectPosition == 0) {
            showErrorSafe("请选择课题");
            return false;
        }
        if (teamPosition == 0) {
            showErrorSafe("请选择分组");
            return false;
        }
        if (info.getUserName() == null || info.getUserName().trim().isEmpty()) {
            showErrorSafe("请填写姓名");
            return false;
        }
        if (info.getUserName().length() > 5) {
            showErrorSafe("姓名不能大于5个字");
            return false;
        }
        if (TextUtils.isEmpty(selectedProvince) ||
                TextUtils.isEmpty(selectedCity) ||
                TextUtils.isEmpty(selectedDistrict)) {
            showErrorSafe("请选择所属城市");
            return false;
        }

        if (info.getPhone() == null || info.getPhone().trim().isEmpty()) {
            showErrorSafe("请填写手机号");
            return false;
        }
        if (info.getPhone().length() != 11) {
            showErrorSafe("手机号必须为11位");
            return false;
        }
        String gender = tvGender.getText().toString();
        if (TextUtils.isEmpty(gender) || gender.equals("请选择")) {
            showErrorSafe("请选择性别");
            return false;
        }
        String birth = tvBirth.getText().toString();
        if (TextUtils.isEmpty(birth) || birth.equals("请选择")) {
            showErrorSafe("请选择生日");
            return false;
        }
        return true;
    }

    private void fetchUserInfoById(String userId) {
        showLoading("正在加载用户信息...");
        String url = ApiConfig.API_USER_INFO + userId;
        Log.d(TAG, "===== 开始加载用户信息 =====");
        Log.d(TAG, "URL: " + url);

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "加载失败: " + e.getMessage());
                        runUiSafe(() -> {
                            hideLoading();
                            showErrorSafe("加载用户信息失败: " + e.getMessage());
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!isAdded() || getActivity() == null) {
                            response.close();
                            return;
                        }

                        try {
                            if (response.isSuccessful()) {
                                String resp = response.body().string();
                                Log.d(TAG, "===== API原始响应 =====");
                                Log.d(TAG, resp);

                                UserInfo info = gson.fromJson(resp, UserInfo.class);

                                Log.d(TAG, "===== 解析后的对象 =====");
                                Log.d(TAG, "userId: " + info.getUserId());
                                Log.d(TAG, "userName: " + info.getUserName());
                                Log.d(TAG, "projectId: " + info.getProjectId());
                                Log.d(TAG, "teamId: " + info.getTeamId());

                                runUiSafe(() -> {
                                    hideLoading();
                                    if (info != null && info.getUserId() != null) {
                                        passedProjectId = info.getProjectId();
                                        passedTeamId = info.getTeamId();

                                        Log.d(TAG, "更新 passedProjectId: " + passedProjectId);
                                        Log.d(TAG, "更新 passedTeamId: " + passedTeamId);

                                        fillUserInfo(info);
                                        selectProjectAndTeam();

                                        Log.d(TAG, "成功填充用户信息");
                                    } else {
                                        showErrorSafe("未找到用户信息");
                                    }
                                });
                            } else {
                                runUiSafe(() -> {
                                    hideLoading();
                                    showErrorSafe("加载失败: " + response.code());
                                });
                            }
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void selectProjectAndTeam() {
        Log.d(TAG, "===== 开始选择课题和分组 =====");
        Log.d(TAG, "projectList size: " + projectList.size());
        Log.d(TAG, "passedProjectId: " + passedProjectId);
        Log.d(TAG, "passedTeamId: " + passedTeamId);

        if (TextUtils.isEmpty(passedProjectId)) {
            Log.d(TAG, "passedProjectId 为空,跳过选择");
            return;
        }

        isLoadingSpinners = true;

        for (int i = 0; i < projectList.size(); i++) {
            if (projectList.get(i).getProjectId().equals(passedProjectId)) {
                Log.d(TAG, "找到匹配的课题,position: " + i);
                final int position = i;

                spinnerProject.post(() -> {
                    spinnerProject.setSelection(position);
                    Log.d(TAG, "已设置课题 Spinner position: " + position);

                    if (!TextUtils.isEmpty(passedTeamId)) {
                        loadTeamsAndSelect(passedProjectId, passedTeamId);
                    } else {
                        loadTeamsByProject(passedProjectId);
                        isLoadingSpinners = false;
                    }
                });
                return;
            }
        }

        Log.d(TAG, "未找到匹配的课题");
        isLoadingSpinners = false;
    }

    private void fillUserInfo(UserInfo info) {
        Log.d(TAG, "[DEBUG] 填充用户信息, isViewMode: " + isViewMode);

        etName.setText(info.getUserName());
        etPhone.setText(info.getPhone());

        // 如果是查看详情模式,保存原始姓名并禁用编辑
        if (isViewMode) {
            originalUserName = info.getUserName() != null ? info.getUserName() : "";
            etName.setEnabled(false);
            etName.setTextColor(getResources().getColor(android.R.color.darker_gray));
            etName.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            Log.d(TAG, "查看模式: 姓名设为只读,保存原始姓名: " + originalUserName);
        }

        if (!TextUtils.isEmpty(info.getGender())) {
            tvGender.setText(info.getGender());
            tvGender.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getBirthDate())) {
            tvBirth.setText(info.getBirthDate());
            tvBirth.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getMaritalStatus())) {
            tvMarital.setText(info.getMaritalStatus());
            tvMarital.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getEducationLevel())) {
            tvEducation.setText(info.getEducationLevel());
            tvEducation.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getLivingStatus())) {
            tvLiving.setText(info.getLivingStatus());
            tvLiving.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getJobStatus())) {
            tvJob.setText(info.getJobStatus());
            tvJob.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getIncomePerCapita())) {
            tvIncome.setText(info.getIncomePerCapita());
            tvIncome.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        if (!TextUtils.isEmpty(info.getInsuranceType())) {
            tvInsurance.setText(info.getInsuranceType());
            tvInsurance.setTextColor(getResources().getColor(R.color.colorPrimary));
        }

        if (!TextUtils.isEmpty(info.getProvince()) &&
                !TextUtils.isEmpty(info.getCity()) &&
                !TextUtils.isEmpty(info.getDistrict())) {

            selectedProvince = info.getProvince();
            selectedCity = info.getCity();
            selectedDistrict = info.getDistrict();

            String cityText = selectedProvince + " " + selectedCity + " " + selectedDistrict;
            tvCity.setText(cityText);
            tvCity.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
    }

    private void saveInfo() {
        UserInfo info = new UserInfo();
        info.setUserName(etName.getText().toString().trim());
        info.setPhone(etPhone.getText().toString().trim());

        String gender = tvGender.getText().toString().trim();
        info.setGender(gender.equals("请选择") ? null : gender);

        String birth = tvBirth.getText().toString().trim();
        info.setBirthDate(birth.equals("请选择") ? null : birth);

        String marital = tvMarital.getText().toString().trim();
        info.setMaritalStatus(marital.equals("请选择") ? null : marital);

        String education = tvEducation.getText().toString().trim();
        info.setEducationLevel(education.equals("请选择") ? null : education);

        String living = tvLiving.getText().toString().trim();
        info.setLivingStatus(living.equals("请选择") ? null : living);

        String job = tvJob.getText().toString().trim();
        info.setJobStatus(job.equals("请选择") ? null : job);

        String income = tvIncome.getText().toString().trim();
        info.setIncomePerCapita(income.equals("请选择") ? null : income);

        String insurance = tvInsurance.getText().toString().trim();
        info.setInsuranceType(insurance.equals("请选择") ? null : insurance);

        info.setAdminId(currentAdminId);

        if (!TextUtils.isEmpty(selectedProvince) &&
                !TextUtils.isEmpty(selectedCity) &&
                !TextUtils.isEmpty(selectedDistrict) &&
                !selectedProvince.equals("请选择") &&
                !selectedCity.equals("请选择") &&
                !selectedDistrict.equals("请选择")) {
            info.setProvince(selectedProvince);
            info.setCity(selectedCity);
            info.setDistrict(selectedDistrict);
        } else {
            info.setProvince(null);
            info.setCity(null);
            info.setDistrict(null);
        }

        int projectPosition = spinnerProject.getSelectedItemPosition();
        int teamPosition = spinnerTeam.getSelectedItemPosition();

        if (projectPosition > 0) {
            Project selectedProject = projectList.get(projectPosition);
            info.setProjectId(selectedProject.getProjectId());
            info.setProjectName(selectedProject.getProjectName());
        }

        if (teamPosition > 0) {
            Team selectedTeam = teamList.get(teamPosition);
            info.setTeamId(selectedTeam.getTeamId());
            info.setTeamName(selectedTeam.getTeamName());
        }

        if (currentUserId != null && !currentUserId.isEmpty()) {
            info.setUserId(currentUserId);
        }

        if (!checkInput(info)) return;

        showLoading("正在保存...");

        String json = gson.toJson(info);
        Log.d(TAG, "发送数据: " + json);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request request;
        if (currentUserId != null && !currentUserId.isEmpty()) {
            request = new Request.Builder()
                    .url(ApiConfig.API_UPDATE_USER_INFO + currentUserId)
                    .put(body)
                    .build();
            Log.d(TAG, "更新模式, URL: " + ApiConfig.API_UPDATE_USER_INFO + currentUserId);
        } else {
            request = new Request.Builder()
                    .url(ApiConfig.API_CREATE_USER_INFO)
                    .post(body)
                    .build();
            Log.d(TAG, "新增模式, URL: " + ApiConfig.API_CREATE_USER_INFO);
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "网络请求失败: " + e.getMessage());
                runUiSafe(() -> {
                    hideLoading();
                    showErrorSafe("保存失败，请检查网络");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded() || getActivity() == null) {
                    response.close();
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    Log.d(TAG, "服务器响应: " + responseBody);

                    if (response.isSuccessful()) {
                        try {
                            org.json.JSONObject jsonObject = new org.json.JSONObject(responseBody);
                            String message = jsonObject.optString("message", "保存成功");

                            runUiSafe(() -> {
                                hideLoading();

                                if (message.contains("成功")) {
                                    showSuccessSafe(message);
                                    new android.os.Handler().postDelayed(() -> {
                                        if (isAdded() && getActivity() != null) {
                                            getActivity().getSupportFragmentManager().popBackStack();
                                        }
                                    }, 1000);
                                } else {
                                    showErrorSafe(message);
                                }
                            });
                        } catch (org.json.JSONException e) {
                            Log.e(TAG, "解析响应失败: " + e.getMessage());
                            runUiSafe(() -> {
                                hideLoading();
                                showSuccessSafe("保存成功");
                                if (isAdded() && getActivity() != null) {
                                    getActivity().getSupportFragmentManager().popBackStack();
                                }
                            });
                        }
                    } else {
                        Log.e(TAG, "HTTP错误: " + response.code());
                        runUiSafe(() -> {
                            hideLoading();

                            try {
                                org.json.JSONObject jsonObject = new org.json.JSONObject(responseBody);
                                String message = jsonObject.optString("message", "服务器错误，保存失败");
                                showErrorSafe(message);
                            } catch (org.json.JSONException e) {
                                showErrorSafe("服务器错误(代码: " + response.code() + ")");
                            }
                        });
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private Context getActivitySafe() {
        if (getActivity() != null) return getActivity();
        if (getContext() != null) return getContext();
        throw new IllegalStateException("Fragment已分离");
    }

    private void runUiSafe(Runnable runnable) {
        if (!isAdded() || getActivity() == null) {
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
            } catch (Throwable e) {
                Log.e(TAG, "runUiSafe执行异常", e);
            }
        });
    }

    private void showErrorSafe(String msg) {
        runUiSafe(() -> InfoPopup.showError(getActivitySafe(), msg));
    }

    private void showSuccessSafe(String msg) {
        runUiSafe(() -> InfoPopup.showSuccess(getActivitySafe(), msg));
    }
}