package com.aplus.remotenursing;

import android.app.DatePickerDialog;
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
import com.aplus.remotenursing.helper.ApiClientHelper;
import com.aplus.remotenursing.models.Project;
import com.aplus.remotenursing.models.Team;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UserFilterFragment extends Fragment {

    private static final String ARG_FILTER = "filter";
    private static final String ARG_ADMIN_ID = "admin_id";
    private static final String TAG = "UserFilterFragment";

    public static UserFilterFragment newInstance(UserSearchFragment.FilterCondition filter, String adminId) {
        UserFilterFragment fragment = new UserFilterFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_FILTER, filter);
        args.putString(ARG_ADMIN_ID, adminId);
        fragment.setArguments(args);
        return fragment;
    }

    private MaterialToolbar toolbar;
    private Spinner spinnerProject, spinnerTeam, spinnerGender, spinnerLoginStatus;
    private EditText etUsername, etPhone, etDateStart, etDateEnd;
    private MaterialButton btnReset, btnApply;
    private ChipGroup chipGroupQuickFilter;
    private Chip chipActivated, chipNotActivated;

    private UserSearchFragment.FilterCondition currentFilter;
    private String adminId;
    private OnFilterAppliedListener listener;

    private List<Project> projectList = new ArrayList<>();
    private List<Team> teamList = new ArrayList<>();
    private ArrayAdapter<Project> projectAdapter;
    private ArrayAdapter<Team> teamAdapter;
    private ArrayAdapter<String> genderAdapter;
    private ArrayAdapter<String> loginStatusAdapter;

    private OkHttpClient client = ApiClientHelper.get();
    private Gson gson = new Gson();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public interface OnFilterAppliedListener {
        void onFilterApplied(UserSearchFragment.FilterCondition filter);
    }

    public void setOnFilterAppliedListener(OnFilterAppliedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupToolbar();
        setupSpinners();
        setupDatePickers();
        setupClickListeners();
        loadFilterData();
        loadProjects();
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        spinnerProject = view.findViewById(R.id.spinner_project);
        spinnerTeam = view.findViewById(R.id.spinner_team);
        spinnerGender = view.findViewById(R.id.spinner_gender);
        spinnerLoginStatus = view.findViewById(R.id.spinner_login_status);
        etUsername = view.findViewById(R.id.et_username);
        etPhone = view.findViewById(R.id.et_phone);
        etDateStart = view.findViewById(R.id.et_date_start);
        etDateEnd = view.findViewById(R.id.et_date_end);
        btnReset = view.findViewById(R.id.btn_reset);
        btnApply = view.findViewById(R.id.btn_apply);
        chipGroupQuickFilter = view.findViewById(R.id.chip_group_quick_filter);
        chipActivated = view.findViewById(R.id.chip_activated);
        chipNotActivated = view.findViewById(R.id.chip_not_activated);
    }

    private void setupToolbar() {
        toolbar.setTitle("用户筛选");
        toolbar.setNavigationIcon(R.drawable.ic_close_24);
        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    private void setupSpinners() {
        // 设置课题下拉列表
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

        // 设置分组下拉列表
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

        // 初始化团队列表
        teamList.clear();
        teamList.add(new Team("", "请先选择课题"));
        teamAdapter.notifyDataSetChanged();

        // 设置性别下拉列表
        List<String> genderOptions = Arrays.asList("全部", "男", "女");
        genderAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, genderOptions);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        // 设置激活状态下拉列表
        List<String> loginStatusOptions = Arrays.asList("全部", "已激活", "未激活");
        loginStatusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, loginStatusOptions);
        loginStatusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLoginStatus.setAdapter(loginStatusAdapter);

        // 课题选择监听器
        spinnerProject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    Project selectedProject = projectList.get(position);
                    loadTeamsByProject(selectedProject.getProjectId());
                } else {
                    teamList.clear();
                    teamList.add(new Team("", "全部分组"));
                    teamAdapter.notifyDataSetChanged();
                    spinnerTeam.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupDatePickers() {
        etDateStart.setOnClickListener(v -> showDatePicker(etDateStart));
        etDateEnd.setOnClickListener(v -> showDatePicker(etDateEnd));
    }

    private void showDatePicker(EditText editText) {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    editText.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    private void setupClickListeners() {
        btnReset.setOnClickListener(v -> resetAllFilters());
        btnApply.setOnClickListener(v -> applyFilters());

        // 快速筛选Chip监听
        chipGroupQuickFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                spinnerLoginStatus.setSelection(0);
            } else if (checkedId == R.id.chip_activated) {
                spinnerLoginStatus.setSelection(1);
                applyQuickFilter("已激活");
            } else if (checkedId == R.id.chip_not_activated) {
                spinnerLoginStatus.setSelection(2);
                applyQuickFilter("未激活");
            }
        });
    }

    private void applyQuickFilter(String loginStatus) {
        // ===== 输入验证 =====
        String username = getText(etUsername);
        if (!TextUtils.isEmpty(username) && username.length() > 6) {
            InfoPopup.showError(requireContext(), "姓名不能超过6个字");
            return;
        }

        String phone = getText(etPhone);
        if (!TextUtils.isEmpty(phone) && phone.length() > 11) {
            InfoPopup.showError(requireContext(), "电话号码不能超过11位");
            return;
        }

        String dateStart = getText(etDateStart);
        String dateEnd = getText(etDateEnd);

        if (!TextUtils.isEmpty(dateStart) && !isValidDateFormat(dateStart)) {
            InfoPopup.showError(requireContext(), "开始日期格式不正确,请使用yyyy-MM-dd格式");
            return;
        }

        if (!TextUtils.isEmpty(dateEnd) && !isValidDateFormat(dateEnd)) {
            InfoPopup.showError(requireContext(), "结束日期格式不正确,请使用yyyy-MM-dd格式");
            return;
        }

        if (!TextUtils.isEmpty(dateStart) && !TextUtils.isEmpty(dateEnd) && dateStart.compareTo(dateEnd) > 0) {
            InfoPopup.showError(requireContext(), "开始日期不能大于结束日期");
            return;
        }
        // ====================

        UserSearchFragment.FilterCondition filter = new UserSearchFragment.FilterCondition();

        if (spinnerProject.getSelectedItemPosition() > 0) {
            Project selectedProject = projectList.get(spinnerProject.getSelectedItemPosition());
            filter.projectId = selectedProject.getProjectId();
            filter.projectName = selectedProject.getProjectName();
        }

        if (spinnerTeam.getSelectedItemPosition() > 0 && teamList.size() > spinnerTeam.getSelectedItemPosition()) {
            Team selectedTeam = teamList.get(spinnerTeam.getSelectedItemPosition());
            filter.teamId = selectedTeam.getTeamId();
            filter.teamName = selectedTeam.getTeamName();
        }

        filter.loginStatus = loginStatus;
        filter.username = TextUtils.isEmpty(username) ? null : username;
        filter.phone = TextUtils.isEmpty(phone) ? null : phone;

        if (spinnerGender.getSelectedItemPosition() > 0) {
            filter.gender = (String) spinnerGender.getSelectedItem();
        }

        filter.dateStart = TextUtils.isEmpty(dateStart) ? null : dateStart;
        filter.dateEnd = TextUtils.isEmpty(dateEnd) ? null : dateEnd;

        if (listener != null) {
            listener.onFilterApplied(filter);
        }

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private void loadFilterData() {
        if (getArguments() != null) {
            currentFilter = (UserSearchFragment.FilterCondition) getArguments().getSerializable(ARG_FILTER);
            adminId = getArguments().getString(ARG_ADMIN_ID);
        }

        if (currentFilter == null) {
            currentFilter = new UserSearchFragment.FilterCondition();
        }
    }

    private void fillExistingFilter() {
        if (!TextUtils.isEmpty(currentFilter.username)) {
            etUsername.setText(currentFilter.username);
        }
        if (!TextUtils.isEmpty(currentFilter.phone)) {
            etPhone.setText(currentFilter.phone);
        }
        if (!TextUtils.isEmpty(currentFilter.gender)) {
            int genderPosition = currentFilter.gender.equals("男") ? 1 : 2;
            spinnerGender.setSelection(genderPosition);
        }
        if (!TextUtils.isEmpty(currentFilter.loginStatus)) {
            int statusPosition = currentFilter.loginStatus.equals("已激活") ? 1 : 2;
            spinnerLoginStatus.setSelection(statusPosition);
        }
        if (!TextUtils.isEmpty(currentFilter.dateStart)) {
            etDateStart.setText(currentFilter.dateStart);
        }
        if (!TextUtils.isEmpty(currentFilter.dateEnd)) {
            etDateEnd.setText(currentFilter.dateEnd);
        }
    }

    private void loadProjects() {
        if (TextUtils.isEmpty(adminId)) return;

        String url = ApiConfig.API_PROJECT + adminId;
        Log.d(TAG, "loadProjects URL: " + url);

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "加载课题列表失败: " + e.getMessage());
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
                            Log.d(TAG, "loadProjects response: " + resp);

                            Type listType = new TypeToken<List<Project>>() {
                            }.getType();
                            List<Project> projects = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                projectList.clear();
                                projectList.add(new Project("", "全部课题"));
                                if (projects != null) {
                                    projectList.addAll(projects);
                                }
                                projectAdapter.notifyDataSetChanged();

                                fillExistingFilterForProjectAndTeam();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "解析课题列表异常: " + e.getMessage());
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void fillExistingFilterForProjectAndTeam() {
        if (!TextUtils.isEmpty(currentFilter.projectId)) {
            for (int i = 0; i < projectList.size(); i++) {
                if (projectList.get(i).getProjectId().equals(currentFilter.projectId)) {
                    final int position = i;

                    spinnerProject.post(() -> {
                        spinnerProject.setSelection(position);
                        Log.d(TAG, "已设置课题position: " + position);

                        loadTeamsByProjectAndFillSelection(currentFilter.projectId, currentFilter.teamId);
                    });
                    break;
                }
            }
        }

        fillExistingFilter();
    }

    private void loadTeamsByProject(String projectId) {
        String url = ApiConfig.API_PROJECT_TEAM + projectId;
        Log.d(TAG, "loadTeams URL: " + url);

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "加载分组列表失败: " + e.getMessage());
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
                            Log.d(TAG, "loadTeams response: " + resp);

                            Type listType = new TypeToken<List<Team>>() {
                            }.getType();
                            List<Team> teams = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                teamList.clear();
                                teamList.add(new Team("", "全部分组"));
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

    private void loadTeamsByProjectAndFillSelection(String projectId, String teamId) {
        String url = ApiConfig.API_PROJECT_TEAM + projectId;

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "加载分组列表失败: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!isAdded() || getActivity() == null) {
                            response.close();
                            return;
                        }

                        try {
                            String resp = response.body().string();
                            Type listType = new TypeToken<List<Team>>() {
                            }.getType();
                            List<Team> teams = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                teamList.clear();
                                teamList.add(new Team("", "全部分组"));
                                if (teams != null) {
                                    teamList.addAll(teams);
                                }
                                teamAdapter.notifyDataSetChanged();

                                if (!TextUtils.isEmpty(teamId)) {
                                    for (int i = 0; i < teamList.size(); i++) {
                                        if (teamList.get(i).getTeamId().equals(teamId)) {
                                            final int position = i;
                                            spinnerTeam.post(() -> {
                                                spinnerTeam.setSelection(position);
                                                Log.d(TAG, "已设置团队position: " + position);
                                            });
                                            break;
                                        }
                                    }
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "解析分组列表异常: " + e.getMessage());
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void resetAllFilters() {
        spinnerProject.setSelection(0);
        spinnerTeam.setSelection(0);
        spinnerGender.setSelection(0);
        spinnerLoginStatus.setSelection(0);
        etUsername.setText("");
        etPhone.setText("");
        etDateStart.setText("");
        etDateEnd.setText("");
        chipGroupQuickFilter.clearCheck();
    }

    private void applyFilters() {
        // ===== 输入验证 =====
        String username = getText(etUsername);
        if (!TextUtils.isEmpty(username) && username.length() > 6) {
            InfoPopup.showError(requireContext(), "姓名不能超过6个字");
            etUsername.requestFocus();
            return;
        }

        String phone = getText(etPhone);
        if (!TextUtils.isEmpty(phone) && phone.length() > 11) {
            InfoPopup.showError(requireContext(), "电话号码不能超过11位");
            etPhone.requestFocus();
            return;
        }

        String dateStart = getText(etDateStart);
        String dateEnd = getText(etDateEnd);

        if (!TextUtils.isEmpty(dateStart) && !isValidDateFormat(dateStart)) {
            InfoPopup.showError(requireContext(), "开始日期格式不正确,请使用yyyy-MM-dd格式");
            etDateStart.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(dateEnd) && !isValidDateFormat(dateEnd)) {
            InfoPopup.showError(requireContext(), "结束日期格式不正确,请使用yyyy-MM-dd格式");
            etDateEnd.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(dateStart) && !TextUtils.isEmpty(dateEnd)) {
            if (dateStart.compareTo(dateEnd) > 0) {
                InfoPopup.showError(requireContext(), "开始日期不能大于结束日期");
                etDateStart.requestFocus();
                return;
            }
        }
        // ====================

        UserSearchFragment.FilterCondition filter = new UserSearchFragment.FilterCondition();

        if (spinnerProject.getSelectedItemPosition() > 0) {
            Project selectedProject = projectList.get(spinnerProject.getSelectedItemPosition());
            filter.projectId = selectedProject.getProjectId();
            filter.projectName = selectedProject.getProjectName();
        }

        if (spinnerTeam.getSelectedItemPosition() > 0 && teamList.size() > spinnerTeam.getSelectedItemPosition()) {
            Team selectedTeam = teamList.get(spinnerTeam.getSelectedItemPosition());
            filter.teamId = selectedTeam.getTeamId();
            filter.teamName = selectedTeam.getTeamName();
        }

        filter.username = TextUtils.isEmpty(username) ? null : username;
        filter.phone = TextUtils.isEmpty(phone) ? null : phone;

        if (spinnerGender.getSelectedItemPosition() > 0) {
            filter.gender = (String) spinnerGender.getSelectedItem();
        }

        if (spinnerLoginStatus.getSelectedItemPosition() > 0) {
            filter.loginStatus = (String) spinnerLoginStatus.getSelectedItem();
        }

        filter.dateStart = TextUtils.isEmpty(dateStart) ? null : dateStart;
        filter.dateEnd = TextUtils.isEmpty(dateEnd) ? null : dateEnd;

        Log.d(TAG, "应用筛选条件 - 课题: " + filter.projectName + ", 分组: " + filter.teamName +
                ", 激活状态: " + filter.loginStatus);
        Log.d(TAG, "projectId: " + filter.projectId + ", teamId: " + filter.teamId);

        if (listener != null) {
            listener.onFilterApplied(filter);
        }

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    private boolean isValidDateFormat(String date) {
        if (TextUtils.isEmpty(date)) {
            return false;
        }

        if (date.length() != 10) {
            return false;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getText(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void runUiSafe(Runnable action) {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }
}