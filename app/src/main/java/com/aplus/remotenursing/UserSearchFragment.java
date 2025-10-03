package com.aplus.remotenursing;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.helper.ApiClientHelper;
import com.aplus.remotenursing.models.PageResult;
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
import java.util.ArrayList;
import java.util.List;

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
    private MaterialButton btnFilter, btnAddUser;
    private RecyclerView rv;
    private View emptyView;
    private TextView tvResultCount;
    private LinearLayout llFilterTags;
    private ChipGroup chipGroupFilters;
    private CircularProgressIndicator progressLoading;
    private SwipeRefreshLayout swipeRefresh;
    private UserSearchAdapter adapter;

    // 分页相关控件 - 顶部
    private LinearLayout llPaginationTop;
    private TextView tvPageInfoTop;
    private Button btnPrevTop, btnNextTop;

    // 分页相关控件 - 底部
    private LinearLayout llPaginationBottom;
    private TextView tvPageInfoBottom;
    private Button btnPrevBottom, btnNextBottom;

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

    // 分页参数
    private int currentPage = 1;
    private int pageSize = 10;
    private int totalCount = 0;
    private int totalPages = 0;

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
        setupSwipeRefresh();
        setupPagination();

        if (allUserList.isEmpty()) {
            Log.d(TAG, "首次创建,加载初始数据");
            loadInitialData();
        } else {
            Log.d(TAG, "数据已存在,执行筛选");

            if (projectList.isEmpty() || projectList.size() == 1) {
                Log.d(TAG, "projectList为空,重新加载");
                loadProjects();
            }

            if (currentFilter.projectId != null || currentFilter.teamId != null) {
                Log.d(TAG, "恢复Spinner状态");
                spinnerProject.postDelayed(() -> {
                    if (projectList.size() > 1) {
                        syncFilterToSpinners();
                    }
                }, 200);
            }

            loadUsersByAdmin();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!allUserList.isEmpty()) {
            Log.d(TAG, "onResume: 刷新数据");
            refreshData();
        }
    }

    private void initViews(View v) {
        v.findViewById(R.id.btn_back).setOnClickListener(view -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });
        spinnerProject = v.findViewById(R.id.spinner_project);
        spinnerTeam = v.findViewById(R.id.spinner_team);
        spinnerProject = v.findViewById(R.id.spinner_project);
        spinnerTeam = v.findViewById(R.id.spinner_team);
        btnFilter = v.findViewById(R.id.btn_filter);
        btnAddUser = v.findViewById(R.id.btn_add_user);
        rv = v.findViewById(R.id.rv_users);
        emptyView = v.findViewById(R.id.empty_view);
        tvResultCount = v.findViewById(R.id.tv_result_count);
        llFilterTags = v.findViewById(R.id.ll_filter_tags);
        chipGroupFilters = v.findViewById(R.id.chip_group_filters);
        progressLoading = v.findViewById(R.id.progress_loading);
        swipeRefresh = v.findViewById(R.id.swipe_refresh);

        // 分页控件 - 顶部
        llPaginationTop = v.findViewById(R.id.ll_pagination_top);
        tvPageInfoTop = v.findViewById(R.id.tv_page_info_top);
        btnPrevTop = v.findViewById(R.id.btn_prev_top);
        btnNextTop = v.findViewById(R.id.btn_next_top);

        // 分页控件 - 底部
        llPaginationBottom = v.findViewById(R.id.ll_pagination_bottom);
        tvPageInfoBottom = v.findViewById(R.id.tv_page_info_bottom);
        btnPrevBottom = v.findViewById(R.id.btn_prev_bottom);
        btnNextBottom = v.findViewById(R.id.btn_next_bottom);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorAccent,
                android.R.color.holo_blue_dark
        );

        swipeRefresh.setOnRefreshListener(() -> {
            Log.d(TAG, "下拉刷新触发");
            refreshData();
        });
    }

    private void setupPagination() {
        // 顶部分页按钮
        btnPrevTop.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                loadUsersByAdmin();
                rv.smoothScrollToPosition(0); // 滚动到顶部
            }
        });

        btnNextTop.setOnClickListener(v -> {
            if (currentPage < totalPages) {
                currentPage++;
                loadUsersByAdmin();
                rv.smoothScrollToPosition(0);
            }
        });

        // 底部分页按钮
        btnPrevBottom.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                loadUsersByAdmin();
                rv.smoothScrollToPosition(0);
            }
        });

        btnNextBottom.setOnClickListener(v -> {
            if (currentPage < totalPages) {
                currentPage++;
                loadUsersByAdmin();
                rv.smoothScrollToPosition(0);
            }
        });
    }

    private void updatePaginationUI() {
        totalPages = (int) Math.ceil((double) totalCount / pageSize);

        String pageInfo = "第 " + currentPage + " / " + totalPages + " 页";
        tvPageInfoTop.setText(pageInfo);
        tvPageInfoBottom.setText(pageInfo);

        // 更新按钮状态
        btnPrevTop.setEnabled(currentPage > 1);
        btnNextTop.setEnabled(currentPage < totalPages);
        btnPrevBottom.setEnabled(currentPage > 1);
        btnNextBottom.setEnabled(currentPage < totalPages);

        // 显示/隐藏分页控件
        boolean showPagination = totalCount > 0;
        llPaginationTop.setVisibility(showPagination ? View.VISIBLE : View.GONE);
        llPaginationBottom.setVisibility(showPagination ? View.VISIBLE : View.GONE);
    }

    private void refreshData() {
        Log.d(TAG, "refreshData: 重新加载数据");
        currentPage = 1; // 重置到第一页
        loadUsersByAdmin();

        if (swipeRefresh.isRefreshing()) {
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1000);
        }
    }

    private void setupRecyclerView() {
        adapter = new UserSearchAdapter();
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        adapter.setOnActionListener(new UserSearchAdapter.OnActionListener() {
            @Override
            public void onDeleteClick(UserInfoAccount item, int position) {
                if ("已激活".equals(item.getLoginStatus())) {
                    InfoPopup.showError(requireContext(), "只能删除未激活的用户");
                    return;
                }
                showDeleteConfirmDialog(item, position);
            }

            @Override
            public void onDetailClick(UserInfoAccount item, int position) {
                String userId = item.getUserId();
                Log.d(TAG, "查看用户详细, userId=" + userId);
                if (userId == null || userId.isEmpty()) {
                    InfoPopup.showError(requireContext(), "用户ID为空，无法查看详细");
                    return;
                }
                openUserDetailFragment(userId);
            }
        });
    }

    private void showDeleteConfirmDialog(UserInfoAccount user, int position) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除用户 " + user.getUserName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteUser(user, position))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteUser(UserInfoAccount user, int position) {
        showLoading(true);

        String url = ApiConfig.API_DELETE_USER_INFO + user.getUserId();
        Log.d(TAG, "删除用户, URL: " + url);

        client.newCall(new Request.Builder().url(url).delete().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runUiSafe(() -> {
                            showLoading(false);
                            InfoPopup.showError(requireContext(), "删除失败: " + e.getMessage());
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        boolean success = response.isSuccessful();
                        response.close();

                        runUiSafe(() -> {
                            showLoading(false);
                            if (success) {
                                InfoPopup.showSuccess(requireContext(), "删除成功");
                                // 删除后重新加载当前页
                                loadUsersByAdmin();
                            } else {
                                InfoPopup.showError(requireContext(), "删除失败");
                            }
                        });
                    }
                });
    }

    private void openUserDetailFragment(String userId) {
        Log.d(TAG, "准备跳转到用户详情, userId=" + userId);

        UserInfoAccount user = null;
        for (UserInfoAccount u : allUserList) {
            if (u.getUserId().equals(userId)) {
                user = u;
                break;
            }
        }

        UserInfoRegisterFragment detailFragment = new UserInfoRegisterFragment();

        Bundle args = new Bundle();
        args.putString("userId", userId);

        if (user != null) {
            if (!TextUtils.isEmpty(user.projectId)) {
                args.putString("projectId", user.projectId);
            }
            if (!TextUtils.isEmpty(user.teamId)) {
                args.putString("teamId", user.teamId);
            }
        }

        detailFragment.setArguments(args);

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                    )
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit();
        }
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

        if (teamList.isEmpty()) {
            teamList.add(new Team("", "请先选择课题"));
            teamAdapter.notifyDataSetChanged();
        }

        spinnerProject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;

                if (position > 0) {
                    Project selectedProject = projectList.get(position);
                    currentFilter.projectId = selectedProject.getProjectId();
                    currentFilter.projectName = selectedProject.getProjectName();
                    loadTeamsByProject(selectedProject.getProjectId());
                } else {
                    currentFilter.projectId = null;
                    currentFilter.projectName = null;
                    currentFilter.teamId = null;
                    currentFilter.teamName = null;
                    teamList.clear();
                    teamList.add(new Team("", "全部分组"));
                    teamAdapter.notifyDataSetChanged();
                    spinnerTeam.setSelection(0);
                }
                updateFilterTags();
                currentPage = 1; // 重置到第一页
                loadUsersByAdmin();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTeam.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;

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
                    currentPage = 1; // 重置到第一页
                    loadUsersByAdmin();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupClickListeners() {
        btnFilter.setOnClickListener(v -> openFilterFragment());
        btnAddUser.setOnClickListener(v -> openAddUserFragment());
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
                            Type listType = new TypeToken<List<Project>>() {}.getType();
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
                            Type listType = new TypeToken<List<Team>>() {}.getType();
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
        String url = ApiConfig.API_USERINFO_SEARCH_BY_PARAM + currentAdminId
                + "?pageNum=" + currentPage + "&pageSize=" + pageSize;

        Log.d(TAG, "加载用户数据, URL: " + url);

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
                            Log.d(TAG, "分页响应: " + resp);

                            Type pageType = new TypeToken<PageResult<UserInfoAccount>>() {}.getType();
                            PageResult<UserInfoAccount> pageResult = gson.fromJson(resp, pageType);

                            runUiSafe(() -> {
                                showLoading(false);
                                allUserList.clear();
                                if (pageResult != null && pageResult.getData() != null) {
                                    allUserList.addAll(pageResult.getData());
                                    totalCount = pageResult.getTotal();
                                } else {
                                    totalCount = 0;
                                }

                                updatePaginationUI();
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

        List<UserInfoAccount> filteredList = new ArrayList<>();

        for (UserInfoAccount user : allUserList) {
            boolean matchProject = true;
            boolean matchTeam = true;
            boolean matchUsername = true;
            boolean matchPhone = true;
            boolean matchGender = true;
            boolean matchLoginStatus = true;
            boolean matchDate = true;

            if (!TextUtils.isEmpty(currentFilter.projectId)) {
                matchProject = currentFilter.projectName != null &&
                        user.projectName != null &&
                        user.projectName.equals(currentFilter.projectName);
            }

            if (!TextUtils.isEmpty(currentFilter.teamId)) {
                if ("NOT_GROUPED".equals(currentFilter.teamId)) {
                    matchTeam = TextUtils.isEmpty(user.teamName);
                } else {
                    matchTeam = currentFilter.teamName != null &&
                            user.teamName != null &&
                            user.teamName.equals(currentFilter.teamName);
                }
            }

            if (!TextUtils.isEmpty(currentFilter.username)) {
                matchUsername = user.userName != null &&
                        user.userName.toLowerCase().contains(currentFilter.username.toLowerCase());
            }

            if (!TextUtils.isEmpty(currentFilter.phone)) {
                matchPhone = user.phone != null &&
                        user.phone.contains(currentFilter.phone);
            }

            if (!TextUtils.isEmpty(currentFilter.gender)) {
                matchGender = user.gender != null &&
                        user.gender.equals(currentFilter.gender);
            }

            if (!TextUtils.isEmpty(currentFilter.loginStatus)) {
                matchLoginStatus = user.loginStatus != null &&
                        user.loginStatus.equals(currentFilter.loginStatus);
            }

            if (!TextUtils.isEmpty(currentFilter.dateStart) || !TextUtils.isEmpty(currentFilter.dateEnd)) {
                matchDate = false;
                if (user.createdTime != null) {
                    try {
                        String dateTimeStr = user.createdTime.toString();
                        if (dateTimeStr.length() >= 10) {
                            String userDateStr = dateTimeStr.substring(0, 10);
                            boolean afterStart = true;
                            boolean beforeEnd = true;

                            if (!TextUtils.isEmpty(currentFilter.dateStart)) {
                                afterStart = userDateStr.compareTo(currentFilter.dateStart) >= 0;
                            }
                            if (!TextUtils.isEmpty(currentFilter.dateEnd)) {
                                beforeEnd = userDateStr.compareTo(currentFilter.dateEnd) <= 0;
                            }

                            matchDate = afterStart && beforeEnd;
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
            currentFilter = filter;
            syncFilterToSpinners();
            currentPage = 1; // 重置到第一页
            loadUsersByAdmin();
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

    private void openAddUserFragment() {
        UserInfoRegisterFragment registerFragment = new UserInfoRegisterFragment();

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                    )
                    .replace(R.id.fragment_container, registerFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void syncFilterToSpinners() {
        isUpdatingSpinners = true;

        if ("NOT_GROUPED".equals(currentFilter.teamId)) {
            isUpdatingSpinners = false;
            updateFilterTags();
            return;
        }

        if (!TextUtils.isEmpty(currentFilter.projectId)) {
            for (int i = 0; i < projectList.size(); i++) {
                if (projectList.get(i).getProjectId().equals(currentFilter.projectId)) {
                    spinnerProject.setSelection(i);

                    if (!TextUtils.isEmpty(currentFilter.teamId)) {
                        loadTeamsAndSync(currentFilter.projectId, currentFilter.teamId);
                    } else {
                        isUpdatingSpinners = false;
                    }
                    return;
                }
            }
        }

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

                                for (int i = 0; i < teamList.size(); i++) {
                                    if (teamList.get(i).getTeamId().equals(teamId)) {
                                        final int position = i;
                                        spinnerTeam.post(() -> {
                                            spinnerTeam.setSelection(position);
                                            spinnerTeam.postDelayed(() -> {
                                                isUpdatingSpinners = false;
                                                updateFilterTags();
                                            }, 100);
                                        });
                                        return;
                                    }
                                }

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
        currentPage = 1; // 重置到第一页
        loadUsersByAdmin();
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
            String countText = "用户数:" + totalCount + "  当前页:" + count;
            tvResultCount.setText(countText);
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