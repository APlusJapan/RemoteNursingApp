package com.aplus.remotenursing;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.helper.ApiClientHelper;
import com.aplus.remotenursing.models.UserAccount;
import com.aplus.remotenursing.models.UserInfoAccount;
import com.aplus.remotenursing.models.Project;
import com.aplus.remotenursing.models.Team;
import com.aplus.remotenursing.adapters.UserSearchAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UserSearchFragment extends Fragment {

    private static final String ARG_ADMIN_ID = "adminId";
    private static final String TAG = "UserSearchFragment";

    public static UserSearchFragment newInstance(@Nullable String adminId) {
        UserSearchFragment f = new UserSearchFragment();
        Bundle b = new Bundle();
        if (!TextUtils.isEmpty(adminId)) b.putString(ARG_ADMIN_ID, adminId);
        f.setArguments(b);
        return f;
    }

    private Spinner spinnerProject, spinnerTeam;
    private MaterialButton btnFilter;
    private RecyclerView rv;
    private View emptyView;
    private TextView tvResultCount;
    private LinearLayout llFilterTags;
    private ChipGroup chipGroupFilters;
    private CircularProgressIndicator progressLoading;
    private UserSearchAdapter adapter;

    private String currentAdminId;
    private List<Project> projectList = new ArrayList<>();
    private List<Team> teamList = new ArrayList<>();
    private ArrayAdapter<Project> projectAdapter;
    private ArrayAdapter<Team> teamAdapter;

    private OkHttpClient client = ApiClientHelper.get();
    private Gson gson = new Gson();

    private List<UserInfoAccount> allUserList = new ArrayList<>();
    private FilterCondition currentFilter = new FilterCondition();

    private boolean isUpdatingSpinners = false;

    @Override
    public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount != null) {
            currentAdminId = userAccount.getAdminId();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        initViews(v);
        setupRecyclerView();
        setupSpinners();
        setupClickListeners();

        if (allUserList.isEmpty()) {
            Log.d(TAG, "首次创建,加载初始数据");
            loadInitialData();
        } else {
            Log.d(TAG, "数据已存在,执行筛选");

            // ===== 修改:先检查projectList是否为空 =====
            if (projectList.isEmpty() || projectList.size() == 1) {
                // projectList为空或只有"全部课题",需要重新加载
                Log.d(TAG, "projectList为空,重新加载");
                loadProjects();
            }
            // ==========================================

            // 恢复Spinner状态
            if (currentFilter.projectId != null || currentFilter.teamId != null) {
                Log.d(TAG, "恢复Spinner状态: projectId=" + currentFilter.projectId + ", teamId=" + currentFilter.teamId);

                // ===== 延迟执行,确保projectList加载完成 =====
                spinnerProject.postDelayed(() -> {
                    if (projectList.size() > 1) {  // 确认已加载
                        syncFilterToSpinners();
                    } else {
                        Log.e(TAG, "projectList仍然为空,无法恢复Spinner");
                    }
                }, 200);
                // ========================================
            }

            doLocalFilter();
        }
    }

    private void initViews(View v) {
        spinnerProject = v.findViewById(R.id.spinner_project);
        spinnerTeam = v.findViewById(R.id.spinner_team);
        btnFilter = v.findViewById(R.id.btn_filter);
        rv = v.findViewById(R.id.rv_users);
        emptyView = v.findViewById(R.id.empty_view);
        tvResultCount = v.findViewById(R.id.tv_result_count);
        llFilterTags = v.findViewById(R.id.ll_filter_tags);
        chipGroupFilters = v.findViewById(R.id.chip_group_filters);
        progressLoading = v.findViewById(R.id.progress_loading);
    }

    private void setupRecyclerView() {
        adapter = new UserSearchAdapter();
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        adapter.setOnActionListener(new UserSearchAdapter.OnActionListener() {
            @Override
            public void onAction1(UserInfoAccount item, int position) {
                // TODO: 实现编辑功能
            }

            @Override
            public void onAction2(UserInfoAccount item, int position) {
                // TODO: 实现查看详情功能
            }
        });
    }

    private void setupSpinners() {
        // 课题 Adapter
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

        // 团队 Adapter
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
        if (teamList.isEmpty()) {
            teamList.add(new Team("", "请先选择课题"));
            teamAdapter.notifyDataSetChanged();
        }

        // 课题选择监听
        spinnerProject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) {
                    Log.d(TAG, "spinnerProject跳过(正在更新)");
                    return;
                }

                if (position > 0) {
                    Project selectedProject = projectList.get(position);
                    currentFilter.projectId = selectedProject.getProjectId();
                    currentFilter.projectName = selectedProject.getProjectName();
                    loadTeamsByProject(selectedProject.getProjectId());
                } else {
                    currentFilter.projectId = null;
                    currentFilter.projectName = null;
                    if (!"NOT_GROUPED".equals(currentFilter.teamId)) {
                        currentFilter.teamId = null;
                        currentFilter.teamName = null;
                    }
                    currentFilter.teamId = null;
                    currentFilter.teamName = null;
                    teamList.clear();
                    teamList.add(new Team("", "全部分组"));
                    teamAdapter.notifyDataSetChanged();
                    spinnerTeam.setSelection(0);
                }
                updateFilterTags();
                doLocalFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 团队选择监听
        spinnerTeam.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) {
                    Log.d(TAG, "spinnerTeam跳过(正在更新)");
                    return;
                }

                if (teamList.size() > position && position > 0) {
                    Team selectedTeam = teamList.get(position);
                    currentFilter.teamId = selectedTeam.getTeamId();
                    currentFilter.teamName = selectedTeam.getTeamName();
                } else {
                    if (!"NOT_GROUPED".equals(currentFilter.teamId)) {
                        currentFilter.teamId = null;
                        currentFilter.teamName = null;
                    }
                }

                if (currentFilter.projectId != null) {
                    updateFilterTags();
                    doLocalFilter();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupClickListeners() {
        btnFilter.setOnClickListener(v -> openFilterFragment());
    }

    private void loadInitialData() {
        showLoading(true);
        loadProjects();
        loadUsersByAdmin();
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
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "解析课题列表异常: " + e.getMessage());
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

    private void loadUsersByAdmin() {
        String url = ApiConfig.API_USERINFO_SEARCH_BY_PARAM + currentAdminId;

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runUiSafe(() -> {
                            showLoading(false);
                            InfoPopup.showError(requireContext(), "网络错误：" + e.getMessage());
                            updateResultUI(null);
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
                            Type listType = new TypeToken<List<UserInfoAccount>>() {
                            }.getType();
                            List<UserInfoAccount> users = gson.fromJson(resp, listType);

                            runUiSafe(() -> {
                                showLoading(false);
                                allUserList.clear();
                                if (users != null) {
                                    allUserList.addAll(users);
                                }
                                doLocalFilter();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "解析用户列表异常: " + e.getMessage());
                            runUiSafe(() -> {
                                showLoading(false);
                                InfoPopup.showError(requireContext(), "解析数据失败");
                                updateResultUI(null);
                            });
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void doLocalFilter() {
        Log.d(TAG, "========== 开始筛选 ==========");
        Log.d(TAG, "筛选条件: projectId=" + currentFilter.projectId + ", teamId=" + currentFilter.teamId);

        List<UserInfoAccount> filteredList = new ArrayList<>();

        for (UserInfoAccount user : allUserList) {
            boolean matchProject = true;
            boolean matchTeam = true;
            boolean matchUsername = true;
            boolean matchPhone = true;
            boolean matchGender = true;
            boolean matchLoginStatus = true;
            boolean matchDate = true;

            // 课题筛选
            if (!TextUtils.isEmpty(currentFilter.projectId)) {
                matchProject = currentFilter.projectName != null &&
                        user.projectName != null &&
                        user.projectName.equals(currentFilter.projectName);
            }

            // 团队筛选
            if (!TextUtils.isEmpty(currentFilter.teamId)) {
                if ("NOT_GROUPED".equals(currentFilter.teamId)) {
                    // 未分组:teamName为null或空
                    matchTeam = TextUtils.isEmpty(user.teamName);
                } else {
                    // 正常分组筛选
                    matchTeam = currentFilter.teamName != null &&
                            user.teamName != null &&
                            user.teamName.equals(currentFilter.teamName);
                }
            }

            // 姓名筛选
            if (!TextUtils.isEmpty(currentFilter.username)) {
                matchUsername = user.userName != null &&
                        user.userName.toLowerCase().contains(currentFilter.username.toLowerCase());
            }

            // 电话筛选
            if (!TextUtils.isEmpty(currentFilter.phone)) {
                matchPhone = user.phone != null &&
                        user.phone.contains(currentFilter.phone);
            }

            // 性别筛选
            if (!TextUtils.isEmpty(currentFilter.gender)) {
                matchGender = user.gender != null &&
                        user.gender.equals(currentFilter.gender);
            }

            // 激活状态筛选
            if (!TextUtils.isEmpty(currentFilter.loginStatus)) {
                matchLoginStatus = user.loginStatus != null &&
                        user.loginStatus.equals(currentFilter.loginStatus);
            }

            // 日期筛选
            if (!TextUtils.isEmpty(currentFilter.dateStart) || !TextUtils.isEmpty(currentFilter.dateEnd)) {
                matchDate = false;
                if (user.createdTime != null) {
                    try {
                        String userDateStr = null;

                        // createdTime 是 String 类型,格式如: "2025-08-08T07:09:05.000+00:00"
                        String dateTimeStr = user.createdTime.toString();

                        // 提取日期部分 yyyy-MM-dd (前10个字符)
                        if (dateTimeStr.length() >= 10) {
                            userDateStr = dateTimeStr.substring(0, 10);
                        }

                        if (userDateStr != null) {
                            boolean afterStart = true;
                            boolean beforeEnd = true;

                            if (!TextUtils.isEmpty(currentFilter.dateStart)) {
                                afterStart = userDateStr.compareTo(currentFilter.dateStart) >= 0;
                            }
                            if (!TextUtils.isEmpty(currentFilter.dateEnd)) {
                                beforeEnd = userDateStr.compareTo(currentFilter.dateEnd) <= 0;
                            }

                            matchDate = afterStart && beforeEnd;

                            // 调试日志
                            Log.d(TAG, "用户 " + user.userName + " 日期: " + userDateStr +
                                    ", 范围: " + currentFilter.dateStart + "~" + currentFilter.dateEnd +
                                    ", 匹配: " + matchDate);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "日期比较失败: " + e.getMessage());
                    }
                }
            }

            if (matchProject && matchTeam && matchUsername && matchPhone &&
                    matchGender && matchLoginStatus && matchDate) {
                filteredList.add(user);
            }
        }

        Log.d(TAG, "筛选完成,结果数量: " + filteredList.size());
        updateResultUI(filteredList);
    }

    private void openFilterFragment() {
        UserFilterFragment filterFragment = UserFilterFragment.newInstance(currentFilter, currentAdminId);

        filterFragment.setOnFilterAppliedListener(filter -> {
            Log.d(TAG, "========== 接收筛选结果 ==========");

            // 保存筛选条件
            currentFilter = filter;

            // 同步到Spinner
            syncFilterToSpinners();

            // 执行筛选
            doLocalFilter();
        });

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                    )
                    .replace(R.id.fragment_container, filterFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    // ========== 核心方法:将筛选条件同步到Spinner ==========
    private void syncFilterToSpinners() {
        Log.d(TAG, "========== 同步筛选条件到Spinner ==========");

        isUpdatingSpinners = true;

        // ===== 特殊处理:未分组 =====
        if ("NOT_GROUPED".equals(currentFilter.teamId)) {
            // 未分组不需要同步Spinner,直接更新标签
            isUpdatingSpinners = false;
            updateFilterTags();
            return;
        }

        // 同步课题
        if (!TextUtils.isEmpty(currentFilter.projectId)) {
            for (int i = 0; i < projectList.size(); i++) {
                if (projectList.get(i).getProjectId().equals(currentFilter.projectId)) {
                    Log.d(TAG, "设置课题position: " + i);
                    spinnerProject.setSelection(i);

                    // 加载并同步团队
                    if (!TextUtils.isEmpty(currentFilter.teamId)) {
                        loadTeamsAndSync(currentFilter.projectId, currentFilter.teamId);
                    } else {
                        isUpdatingSpinners = false;
                    }
                    return;
                }
            }
        }

        // 如果没有课题筛选条件,重置Spinner
        spinnerProject.setSelection(0);
        isUpdatingSpinners = false;
    }

    private void loadTeamsAndSync(String projectId, String teamId) {
        String url = ApiConfig.API_PROJECT_TEAM + projectId;

        client.newCall(new Request.Builder().url(url).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runUiSafe(() -> isUpdatingSpinners = false);
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
                                teamList.add(new Team("", "全部分组"));
                                if (teams != null) {
                                    teamList.addAll(teams);
                                }
                                teamAdapter.notifyDataSetChanged();

                                // 设置团队选中
                                for (int i = 0; i < teamList.size(); i++) {
                                    if (teamList.get(i).getTeamId().equals(teamId)) {
                                        final int position = i;
                                        Log.d(TAG, "设置团队position: " + position);

                                        spinnerTeam.post(() -> {
                                            spinnerTeam.setSelection(position);

                                            spinnerTeam.postDelayed(() -> {
                                                isUpdatingSpinners = false;
                                                Log.d(TAG, "Spinner状态同步完成");

                                                // ===== 新增:同步完成后更新筛选标签 =====
                                                updateFilterTags();
                                                // ========================================
                                            }, 100);
                                        });
                                        return;
                                    }
                                }

                                // 如果没找到teamId,也要重置标志
                                isUpdatingSpinners = false;
                            });
                        } catch (Exception e) {
                            runUiSafe(() -> isUpdatingSpinners = false);
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    private void updateFilterTags() {
        chipGroupFilters.removeAllViews();

        if (!TextUtils.isEmpty(currentFilter.projectName)) {
            addFilterChip("课题: " + currentFilter.projectName, "project");
        }

        if (!TextUtils.isEmpty(currentFilter.teamName)) {
            addFilterChip("分组: " + currentFilter.teamName, "team");
        }

        if (!TextUtils.isEmpty(currentFilter.username)) {
            addFilterChip("姓名: " + currentFilter.username, "username");
        }

        if (!TextUtils.isEmpty(currentFilter.phone)) {
            addFilterChip("电话: " + currentFilter.phone, "phone");
        }

        if (!TextUtils.isEmpty(currentFilter.gender)) {
            addFilterChip("性别: " + currentFilter.gender, "gender");
        }

        if (!TextUtils.isEmpty(currentFilter.loginStatus)) {
            addFilterChip("状态: " + currentFilter.loginStatus, "loginStatus");
        }

        if (!TextUtils.isEmpty(currentFilter.dateStart) || !TextUtils.isEmpty(currentFilter.dateEnd)) {
            String dateRange = "";
            if (!TextUtils.isEmpty(currentFilter.dateStart) && !TextUtils.isEmpty(currentFilter.dateEnd)) {
                dateRange = currentFilter.dateStart + " ~ " + currentFilter.dateEnd;
            } else if (!TextUtils.isEmpty(currentFilter.dateStart)) {
                dateRange = currentFilter.dateStart + " 之后";
            } else {
                dateRange = currentFilter.dateEnd + " 之前";
            }
            addFilterChip("录入日期: " + dateRange, "date");
        }

        llFilterTags.setVisibility(chipGroupFilters.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addFilterChip(String label, String filterType) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCloseIconVisible(true);
        chip.setChipBackgroundColorResource(android.R.color.white);
        chip.setChipStrokeColorResource(R.color.colorPrimary);
        chip.setChipStrokeWidth(2f);
        chip.setTextColor(getResources().getColor(R.color.colorPrimary));
        chip.setCloseIconTintResource(R.color.colorPrimary);

        chip.setOnCloseIconClickListener(v -> removeFilter(filterType));

        chipGroupFilters.addView(chip);
    }

    private void removeFilter(String filterType) {
        isUpdatingSpinners = true;

        switch (filterType) {
            case "project":
                currentFilter.projectId = null;
                currentFilter.projectName = null;
                currentFilter.teamId = null;
                currentFilter.teamName = null;
                spinnerProject.setSelection(0);
                teamList.clear();
                teamList.add(new Team("", "全部分组"));
                teamAdapter.notifyDataSetChanged();
                spinnerTeam.setSelection(0);
                break;
            case "team":
                currentFilter.teamId = null;
                currentFilter.teamName = null;
                spinnerTeam.setSelection(0);
                break;
            case "username":
                currentFilter.username = null;
                break;
            case "phone":
                currentFilter.phone = null;
                break;
            case "gender":
                currentFilter.gender = null;
                break;
            case "loginStatus":
                currentFilter.loginStatus = null;
                break;
            case "date":
                currentFilter.dateStart = null;
                currentFilter.dateEnd = null;
                break;
        }

        spinnerProject.postDelayed(() -> isUpdatingSpinners = false, 100);

        updateFilterTags();
        doLocalFilter();
    }

    private void updateResultUI(List<UserInfoAccount> list) {
        if (adapter != null) {
            adapter.setData(new ArrayList<>());

            rv.postDelayed(() -> {
                adapter.setData(list);
                rv.invalidate();
                rv.requestLayout();
            }, 50);
        }

        if (list == null || list.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
            updateResultCount(0);
        } else {
            emptyView.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            updateResultCount(list.size());
        }
    }

    private void updateResultCount(int count) {
        if (tvResultCount != null) {
            tvResultCount.setText(count + " 条");
        }
    }

    private void showLoading(boolean show) {
        if (progressLoading != null) {
            progressLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void runUiSafe(Runnable action) {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    public static class FilterCondition implements Serializable {
        public String projectId;
        public String projectName;
        public String teamId;
        public String teamName;
        public String username;
        public String phone;
        public String gender;
        public String loginStatus;
        public String dateStart;
        public String dateEnd;
    }
}