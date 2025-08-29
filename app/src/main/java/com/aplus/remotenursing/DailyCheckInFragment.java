package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.models.UserAccount; // 使用你的import路径

import org.json.*;

import java.io.IOException;
import java.util.*;

import okhttp3.*;

public class DailyCheckInFragment extends Fragment {
    private LinearLayout formContainer;
    private Button btnNext, btnPrev, btnSubmit;
    private ImageButton btnBack;
    private TextView tvTitle, tvPageInfo;

    private List<Field> fieldList = new ArrayList<>();
    private Map<Integer, String> answerCache = new HashMap<>(); // 缓存用户输入
    private int currentPage = 0;
    private long formId = 1;
    private String userId;
    private String adminId;
    private SharedPreferences prefs;
    private boolean isReadOnlyMode = false; // 保留变量但不再使用

    private static final String CACHE_PREFIX = "daily_checkin_";
    private static final String CACHE_FORM_ID = CACHE_PREFIX + "form_id";
    private static final String CACHE_CURRENT_PAGE = CACHE_PREFIX + "current_page";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_checkin, container, false);

        initViews(view);
        userId = UserUtils.loadUserId(requireContext());
        prefs = requireContext().getSharedPreferences("daily_checkin_cache", Context.MODE_PRIVATE);

        tvTitle.setText("每日打卡");

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        btnNext.setOnClickListener(v -> nextPage());
        btnPrev.setOnClickListener(v -> prevPage());
        btnSubmit.setOnClickListener(v -> submitCheckin());

        loadCheckinForm();
        return view;
    }

    private void initViews(View view) {
        formContainer = view.findViewById(R.id.form_container);
        btnNext = view.findViewById(R.id.btn_next);
        btnPrev = view.findViewById(R.id.btn_prev);
        btnSubmit = view.findViewById(R.id.btn_submit);
        btnBack = view.findViewById(R.id.btn_back);
        tvTitle = view.findViewById(R.id.tv_title);
        tvPageInfo = view.findViewById(R.id.tv_page_info);
    }

    private void loadCheckinForm() {
        // 首先获取用户账户信息
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null) {
            InfoPopup.showError(getContext(), "用户信息获取失败，请重新登录");
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        // 获取adminId
        adminId = userAccount.getAdminId();
        // 获取formId
        getFormId(adminId, userAccount.getProjectId(), userAccount.getTeamId());
    }

    private void getFormId(String adminId, String projectId, String teamId) {
        OkHttpClient client = new OkHttpClient();

        // 构建GET请求的URL参数
        String url = ApiConfig.API_GET_CHECKIN_FORMID +
                "?projectId=" + projectId +
                "&teamId=" + teamId +
                "&adminId=" + adminId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        InfoPopup.showError(getContext(), "获取表单信息失败"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        requireActivity().runOnUiThread(() ->
                                InfoPopup.showError(getContext(), "获取表单信息失败"));
                        return;
                    }

                    String resp = response.body().string();
                    try {
                        // 后台直接返回long值，不是JSON对象
                        long retrievedFormId = Long.parseLong(resp.trim());

                        if (retrievedFormId > 0) {
                            formId = retrievedFormId;
                            // 检查是否已经提交过
                            checkSubmissionStatus();
                        } else {
                            requireActivity().runOnUiThread(() -> {
                                InfoPopup.showError(getContext(), "问卷调查还未创建，请联系管理员");
                                // 隐藏所有表单相关控件
                                hideFormControls();
                            });
                        }
                    } catch (NumberFormatException e) {
                        requireActivity().runOnUiThread(() ->
                                InfoPopup.showError(getContext(), "解析表单信息失败"));
                        e.printStackTrace();
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void checkSubmissionStatus() {
        OkHttpClient client = new OkHttpClient();

        // 构建GET请求的URL参数
        String url = ApiConfig.API_CHECKIN_RECORD_COUNT +
                "?userId=" + userId +
                "&formId=" + formId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        InfoPopup.showError(getContext(), "检查提交状态失败"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        requireActivity().runOnUiThread(() ->
                                InfoPopup.showError(getContext(), "检查提交状态失败"));
                        return;
                    }

                    String resp = response.body().string();
                    try {
                        // 后台直接返回long值，不是JSON对象
                        long count = Long.parseLong(resp.trim());

                        if (count > 0) {
                            // 已经提交过，显示完成信息并返回首页
                            requireActivity().runOnUiThread(() -> {
                                InfoPopup.showSuccess(getContext(), "今日打卡已完成！");
                                // 直接返回首页，不显示表单内容
                                requireActivity().getSupportFragmentManager().popBackStack();
                            });
                        } else {
                            // 未提交过，正常加载表单
                            requireActivity().runOnUiThread(() -> loadFormFields());
                        }
                    } catch (NumberFormatException e) {
                        requireActivity().runOnUiThread(() ->
                                InfoPopup.showError(getContext(), "解析提交状态失败"));
                        e.printStackTrace();
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void hideFormControls() {
        formContainer.removeAllViews();
        tvPageInfo.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE);
        btnSubmit.setVisibility(View.GONE);

        // 显示提示信息
        TextView hintText = new TextView(requireContext());
        hintText.setText("问卷调查还未创建，请联系管理员");
        hintText.setTextSize(18);
        hintText.setTextColor(0xFF666666);
        hintText.setGravity(Gravity.CENTER);
        hintText.setPadding(dp2px(24), dp2px(48), dp2px(24), dp2px(48));
        formContainer.addView(hintText);
    }

    private void loadFormFieldsInReadOnlyMode() {
        // 加载表单字段但设置为只读模式
        isReadOnlyMode = true;
        loadFormFields();
    }

    private void loadFormFields() {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_CHECKIN_FIELDS + formId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        InfoPopup.showError(getContext(), "加载失败"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        requireActivity().runOnUiThread(() ->
                                InfoPopup.showError(getContext(), "接口错误"));
                        return;
                    }
                    String resp = response.body().string();
                    try {
                        JSONArray arr = new JSONArray(resp);
                        fieldList.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            Field field = Field.fromJson(obj);
                            fieldList.add(field);
                        }
                        requireActivity().runOnUiThread(() -> {
                            if (isReadOnlyMode) {
                                loadSubmittedAnswers();
                            } else {
                                loadCachedData();
                                showCurrentPage();
                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void loadSubmittedAnswers() {
        // 加载已提交的答案并以只读模式显示
        // 这里需要调用API获取已提交的数据
        // 暂时先显示表单结构，隐藏提交按钮
        loadCachedData();
        showCurrentPage();
    }

    private void loadCachedData() {
        long cachedFormId = prefs.getLong(CACHE_FORM_ID, -1);
        if (cachedFormId == formId) {
            // 同一个表单，恢复缓存数据
            currentPage = prefs.getInt(CACHE_CURRENT_PAGE, 0);
            // 加载答案缓存
            for (int i = 0; i < fieldList.size(); i++) {
                String cachedAnswer = prefs.getString(CACHE_PREFIX + "answer_" + i, "");
                if (!cachedAnswer.isEmpty()) {
                    answerCache.put(i, cachedAnswer);
                }
            }
            // 如果有缓存数据，跳转到下一页继续填写
            if (currentPage < fieldList.size() && hasAnswerForPage(currentPage)) {
                currentPage = Math.min(currentPage + 1, fieldList.size() - 1);
            }
        } else {
            // 不同表单，清除旧缓存
            clearCache();
            currentPage = 0;
            // 保存新的formId
            prefs.edit().putLong(CACHE_FORM_ID, formId).apply();
        }
    }

    private boolean hasAnswerForPage(int page) {
        return answerCache.containsKey(page) && !answerCache.get(page).trim().isEmpty();
    }

    private void clearCache() {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith(CACHE_PREFIX)) {
                editor.remove(entry.getKey());
            }
        }
        editor.apply();
    }

    private void showCurrentPage() {
        if (fieldList.isEmpty()) return;

        formContainer.removeAllViews();

        // 更新页面信息
        tvPageInfo.setText(String.format("第 %d 页 / 共 %d 页", currentPage + 1, fieldList.size()));

        // 显示当前问题
        Field currentField = fieldList.get(currentPage);
        View questionView = createQuestionView(currentField);
        formContainer.addView(questionView);

        // 从缓存中恢复答案
        restoreAnswerForCurrentPage();

        // 更新按钮状态
        updateButtonState();

        // 保存当前页码到缓存
        prefs.edit().putInt(CACHE_CURRENT_PAGE, currentPage).apply();
    }

    private View createQuestionView(Field field) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);

        // 根据问题长度动态设置padding
        String questionText = field.fieldLabel + (field.isRequired ? " *" : "");
        int lines = (questionText.length() / 20) + 1; // 估算行数
        int topBottomPadding = Math.max(dp2px(20), dp2px(16 + lines * 4)); // 动态padding
        container.setPadding(dp2px(24), topBottomPadding, dp2px(24), topBottomPadding);

        // 问题标题 - 放大字体
        TextView questionTextView = new TextView(requireContext());
        questionTextView.setText(questionText);
        questionTextView.setTextSize(24); // 增大字体
        questionTextView.setTextColor(0xFF222222);
        questionTextView.setLineSpacing(dp2px(4), 1.2f); // 增加行间距
        questionTextView.setPadding(0, 0, 0, dp2px(24));
        container.addView(questionTextView);

        // 输入控件 - 也相应放大
        View inputView = createInputView(field);
        container.addView(inputView);

        return container;
    }

    private View createInputView(Field field) {
        if ("text".equals(field.fieldType) || "number".equals(field.fieldType)) {
            EditText input = new EditText(requireContext());
            input.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp2px(60)));
            input.setTextSize(20); // 增大字体
            input.setTextColor(0xFF222222);
            input.setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16));
            input.setBackground(getResources().getDrawable(android.R.drawable.edit_text));
            input.setHint("请输入" + field.fieldLabel);

            if ("number".equals(field.fieldType)) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            if (field.param.has("maxLength")) {
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(field.param.optInt("maxLength"))});
            }

            input.setTag("field_" + currentPage);
            return input;
        } else {
            Button selectBtn = new Button(requireContext());
            selectBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp2px(60)));
            selectBtn.setTextSize(18); // 增大字体
            selectBtn.setTextColor(0xFF666666);
            selectBtn.setText("请选择" + field.fieldLabel);
            selectBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            selectBtn.setPadding(dp2px(16), 0, dp2px(16), 0);

            selectBtn.setOnClickListener(v -> {
                if ("date".equals(field.fieldType)) {
                    showDate(selectBtn);
                } else if ("checkbox".equals(field.fieldType)) {
                    showMulti(selectBtn, field.options);
                } else {
                    showSingle(selectBtn, field.options);
                }
            });

            selectBtn.setTag("field_" + currentPage);
            return selectBtn;
        }
    }

    private void restoreAnswerForCurrentPage() {
        String cachedAnswer = answerCache.get(currentPage);
        if (cachedAnswer == null || cachedAnswer.trim().isEmpty()) return;

        View inputView = formContainer.findViewWithTag("field_" + currentPage);
        if (inputView instanceof EditText) {
            ((EditText) inputView).setText(cachedAnswer);
        } else if (inputView instanceof Button) {
            ((Button) inputView).setText(cachedAnswer);
            ((Button) inputView).setTextColor(0xFF222222);
        }
    }

    private void saveCurrentPageAnswer() {
        View inputView = formContainer.findViewWithTag("field_" + currentPage);
        String answer = "";

        if (inputView instanceof EditText) {
            answer = ((EditText) inputView).getText().toString();
        } else if (inputView instanceof Button) {
            CharSequence text = ((Button) inputView).getText();
            if (text != null && !text.toString().startsWith("请选择")) {
                answer = text.toString();
            }
        }

        if (!answer.trim().isEmpty()) {
            answerCache.put(currentPage, answer);
            // 保存到SharedPreferences
            prefs.edit().putString(CACHE_PREFIX + "answer_" + currentPage, answer).apply();
        }
    }

    private void nextPage() {
        Field currentField = fieldList.get(currentPage);

        // 验证必填项
        if (currentField.isRequired) {
            View inputView = formContainer.findViewWithTag("field_" + currentPage);
            String currentAnswer = "";

            if (inputView instanceof EditText) {
                currentAnswer = ((EditText) inputView).getText().toString();
            } else if (inputView instanceof Button) {
                CharSequence text = ((Button) inputView).getText();
                if (text != null && !text.toString().startsWith("请选择")) {
                    currentAnswer = text.toString();
                }
            }

            if (currentAnswer.trim().isEmpty()) {
                InfoPopup.showError(requireContext(), currentField.fieldLabel + "为必填项");
                return;
            }
        }

        // 保存当前页答案
        saveCurrentPageAnswer();

        // 翻页
        if (currentPage < fieldList.size() - 1) {
            currentPage++;
            showCurrentPage();
        }
    }

    private void prevPage() {
        if (currentPage > 0) {
            saveCurrentPageAnswer();
            currentPage--;
            showCurrentPage();
        }
    }

    private void updateButtonState() {
        btnPrev.setVisibility(currentPage > 0 ? View.VISIBLE : View.INVISIBLE);

        if (currentPage == fieldList.size() - 1) {
            btnNext.setVisibility(View.GONE);
            // 只读模式时隐藏提交按钮
            if (isReadOnlyMode) {
                btnSubmit.setVisibility(View.GONE);
            } else {
                btnSubmit.setVisibility(View.VISIBLE);
            }
        } else {
            btnNext.setVisibility(View.VISIBLE);
            btnSubmit.setVisibility(View.GONE);
        }
    }

    private void submitCheckin() {
        // 保存最后一页的答案
        saveCurrentPageAnswer();

        // 检查所有必填项
        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            if (field.isRequired) {
                String answer = answerCache.get(i);
                if (answer == null || answer.trim().isEmpty()) {
                    InfoPopup.showError(requireContext(), field.fieldLabel + "为必填项");
                    return;
                }
            }
        }

        // 提交所有答案
        OkHttpClient client = new OkHttpClient();
        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            String answer = answerCache.get(i);
            if (answer != null && !answer.trim().isEmpty()) {
                JSONObject record = new JSONObject();
                try {
                    record.put("userId", userId);
                    record.put("formId", formId);
                    record.put("adminId", adminId);
                    record.put("fieldId", field.fieldId);
                    record.put("inputValue", answer);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                RequestBody body = RequestBody.create(record.toString(),
                        MediaType.get("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(ApiConfig.API_CHECKIN_RECORD)
                        .post(body)
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) { }
                    @Override public void onResponse(Call call, Response response) throws IOException {
                        try {
                            // no-op
                        } finally {
                            response.close();
                        }
                    }
                });
            }
        }

        // 清除缓存
        clearCache();
        InfoPopup.showSuccess(requireContext(), "今日打卡已完成，感谢您的配合！");
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    // 自定义单选对话框
    private void showSingle(Button target, List<String> options) {
        // 创建自定义布局
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

        // 获取当前选中项
        String currentText = target.getText().toString();
        int selectedIndex = -1;
        if (!currentText.startsWith("请选择")) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equals(currentText)) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        // 创建单选按钮组
        RadioGroup radioGroup = new RadioGroup(requireContext());
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        for (int i = 0; i < options.size(); i++) {
            RadioButton radioButton = new RadioButton(requireContext());
            radioButton.setText(options.get(i));
            radioButton.setTextSize(22); // 放大字体
            radioButton.setTextColor(0xFF333333);
            radioButton.setPadding(dp2px(8), dp2px(12), dp2px(8), dp2px(12));
            radioButton.setId(i);
            if (i == selectedIndex) {
                radioButton.setChecked(true);
            }
            radioGroup.addView(radioButton);
        }

        container.addView(radioGroup);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(container)
                .setPositiveButton("确定", (d, w) -> {
                    int selectedId = radioGroup.getCheckedRadioButtonId();
                    if (selectedId >= 0) {
                        target.setText(options.get(selectedId));
                        target.setTextColor(0xFF222222);
                    }
                })
                .setNegativeButton("取消", null)
                .create();

        // 设置对话框按钮字体大小
        dialog.show();
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) {
            positiveButton.setTextSize(20);
        }
        if (negativeButton != null) {
            negativeButton.setTextSize(20);
        }
    }

    // 自定义多选对话框
    private void showMulti(Button target, List<String> options) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

        // 解析当前选中项
        String currentText = target.getText().toString();
        Set<String> selectedItems = new HashSet<>();
        if (!currentText.startsWith("请选择")) {
            String[] selected = currentText.split(";");
            for (String item : selected) {
                selectedItems.add(item.trim());
            }
        }

        // 创建复选框组
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String option : options) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(option);
            checkBox.setTextSize(22); // 放大字体
            checkBox.setTextColor(0xFF333333);
            checkBox.setPadding(dp2px(8), dp2px(12), dp2px(8), dp2px(12));
            checkBox.setChecked(selectedItems.contains(option));
            checkBoxes.add(checkBox);
            container.addView(checkBox);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(container)
                .setPositiveButton("确定", (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < checkBoxes.size(); i++) {
                        if (checkBoxes.get(i).isChecked()) {
                            if (sb.length() > 0) sb.append(";");
                            sb.append(options.get(i));
                        }
                    }
                    target.setText(sb.toString());
                    target.setTextColor(0xFF222222);
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.show();
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) {
            positiveButton.setTextSize(20);
        }
        if (negativeButton != null) {
            negativeButton.setTextSize(20);
        }
    }

    // 自定义日期选择对话框
    private void showDate(Button target) {
        Calendar cal = Calendar.getInstance();

        // 尝试解析已有日期
        String currentText = target.getText().toString();
        if (!currentText.startsWith("请选择")) {
            try {
                String[] parts = currentText.split("-");
                if (parts.length == 3) {
                    cal.set(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2]));
                }
            } catch (Exception e) {
                // 使用默认日期
            }
        }

        // 创建自定义日期选择布局
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

        // 年份选择
        LinearLayout yearLayout = new LinearLayout(requireContext());
        yearLayout.setOrientation(LinearLayout.HORIZONTAL);
        yearLayout.setGravity(Gravity.CENTER);

        TextView yearLabel = new TextView(requireContext());
        yearLabel.setText("年份：");
        yearLabel.setTextSize(22);
        yearLabel.setTextColor(0xFF333333);
        yearLayout.addView(yearLabel);

        Button btnYearMinus = new Button(requireContext());
        btnYearMinus.setText("◀");
        btnYearMinus.setTextSize(22);
        btnYearMinus.setLayoutParams(new LinearLayout.LayoutParams(dp2px(60), dp2px(60)));
        yearLayout.addView(btnYearMinus);

        TextView yearText = new TextView(requireContext());
        yearText.setText(String.valueOf(cal.get(Calendar.YEAR)));
        yearText.setTextSize(28);
        yearText.setTextColor(0xFF222222);
        yearText.setGravity(Gravity.CENTER);
        yearText.setLayoutParams(new LinearLayout.LayoutParams(dp2px(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        yearLayout.addView(yearText);

        Button btnYearPlus = new Button(requireContext());
        btnYearPlus.setText("▶");
        btnYearPlus.setTextSize(22);
        btnYearPlus.setLayoutParams(new LinearLayout.LayoutParams(dp2px(60), dp2px(60)));
        yearLayout.addView(btnYearPlus);

        container.addView(yearLayout);

        // 月份选择
        LinearLayout monthLayout = new LinearLayout(requireContext());
        monthLayout.setOrientation(LinearLayout.HORIZONTAL);
        monthLayout.setGravity(Gravity.CENTER);

        TextView monthLabel = new TextView(requireContext());
        monthLabel.setText("月份：");
        monthLabel.setTextSize(22);
        monthLabel.setTextColor(0xFF333333);
        monthLayout.addView(monthLabel);

        Button btnMonthMinus = new Button(requireContext());
        btnMonthMinus.setText("◀");
        btnMonthMinus.setTextSize(22);
        btnMonthMinus.setLayoutParams(new LinearLayout.LayoutParams(dp2px(60), dp2px(60)));
        monthLayout.addView(btnMonthMinus);

        TextView monthText = new TextView(requireContext());
        monthText.setText(String.valueOf(cal.get(Calendar.MONTH) + 1));
        monthText.setTextSize(28);
        monthText.setTextColor(0xFF222222);
        monthText.setGravity(Gravity.CENTER);
        monthText.setLayoutParams(new LinearLayout.LayoutParams(dp2px(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        monthLayout.addView(monthText);

        Button btnMonthPlus = new Button(requireContext());
        btnMonthPlus.setText("▶");
        btnMonthPlus.setTextSize(22);
        btnMonthPlus.setLayoutParams(new LinearLayout.LayoutParams(dp2px(60), dp2px(60)));
        monthLayout.addView(btnMonthPlus);

        container.addView(monthLayout);

        // 日期选择
        LinearLayout dayLayout = new LinearLayout(requireContext());
        dayLayout.setOrientation(LinearLayout.HORIZONTAL);
        dayLayout.setGravity(Gravity.CENTER);

        TextView dayLabel = new TextView(requireContext());
        dayLabel.setText("日期：");
        dayLabel.setTextSize(22);
        dayLabel.setTextColor(0xFF333333);
        dayLayout.addView(dayLabel);

        Button btnDayMinus = new Button(requireContext());
        btnDayMinus.setText("◀");
        btnDayMinus.setTextSize(22);
        btnDayMinus.setLayoutParams(new LinearLayout.LayoutParams(dp2px(60), dp2px(60)));
        dayLayout.addView(btnDayMinus);

        TextView dayText = new TextView(requireContext());
        dayText.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));
        dayText.setTextSize(28);
        dayText.setTextColor(0xFF222222);
        dayText.setGravity(Gravity.CENTER);
        dayText.setLayoutParams(new LinearLayout.LayoutParams(dp2px(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        dayLayout.addView(dayText);

        Button btnDayPlus = new Button(requireContext());
        btnDayPlus.setText("▶");
        btnDayPlus.setTextSize(22);
        btnDayPlus.setLayoutParams(new LinearLayout.LayoutParams(dp2px(60), dp2px(60)));
        dayLayout.addView(btnDayPlus);

        container.addView(dayLayout);

        // 当前选择的日期
        final Calendar selectedDate = (Calendar) cal.clone();

        // 按钮事件
        btnYearMinus.setOnClickListener(v -> {
            selectedDate.add(Calendar.YEAR, -1);
            yearText.setText(String.valueOf(selectedDate.get(Calendar.YEAR)));
            // 检查日期有效性
            validateAndUpdateDate(selectedDate, dayText);
        });

        btnYearPlus.setOnClickListener(v -> {
            selectedDate.add(Calendar.YEAR, 1);
            yearText.setText(String.valueOf(selectedDate.get(Calendar.YEAR)));
            validateAndUpdateDate(selectedDate, dayText);
        });

        btnMonthMinus.setOnClickListener(v -> {
            selectedDate.add(Calendar.MONTH, -1);
            monthText.setText(String.valueOf(selectedDate.get(Calendar.MONTH) + 1));
            validateAndUpdateDate(selectedDate, dayText);
        });

        btnMonthPlus.setOnClickListener(v -> {
            selectedDate.add(Calendar.MONTH, 1);
            monthText.setText(String.valueOf(selectedDate.get(Calendar.MONTH) + 1));
            validateAndUpdateDate(selectedDate, dayText);
        });

        btnDayMinus.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            yearText.setText(String.valueOf(selectedDate.get(Calendar.YEAR)));
            monthText.setText(String.valueOf(selectedDate.get(Calendar.MONTH) + 1));
            dayText.setText(String.valueOf(selectedDate.get(Calendar.DAY_OF_MONTH)));
        });

        btnDayPlus.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            yearText.setText(String.valueOf(selectedDate.get(Calendar.YEAR)));
            monthText.setText(String.valueOf(selectedDate.get(Calendar.MONTH) + 1));
            dayText.setText(String.valueOf(selectedDate.get(Calendar.DAY_OF_MONTH)));
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(container)
                .setPositiveButton("确定", (d, w) -> {
                    String date = String.format("%04d-%02d-%02d",
                            selectedDate.get(Calendar.YEAR),
                            selectedDate.get(Calendar.MONTH) + 1,
                            selectedDate.get(Calendar.DAY_OF_MONTH));
                    target.setText(date);
                    target.setTextColor(0xFF222222);
                })
                .setNegativeButton("取消", null)
                .create();

        dialog.show();
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) {
            positiveButton.setTextSize(18);
        }
        if (negativeButton != null) {
            negativeButton.setTextSize(18);
        }
    }

    // 验证并更新日期（处理月份天数变化）
    private void validateAndUpdateDate(Calendar calendar, TextView dayText) {
        int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
        if (currentDay > maxDay) {
            calendar.set(Calendar.DAY_OF_MONTH, maxDay);
        }
        dayText.setText(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)));
    }

    private int dp2px(int dp) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    // Field类保持不变
    public static class Field {
        public long fieldId;
        public String fieldLabel;
        public String fieldType;
        public boolean isRequired;
        public List<String> options = new ArrayList<>();
        public JSONObject param = new JSONObject();

        public static Field fromJson(JSONObject obj) {
            Field field = new Field();
            field.fieldId = obj.optLong("id");
            field.fieldLabel = obj.optString("fieldLabel");
            field.fieldType = obj.optString("fieldType");
            field.isRequired = obj.optBoolean("isRequired", false);
            if (obj.has("fieldOptions") && !obj.isNull("fieldOptions")) {
                String optionsStr = obj.optString("fieldOptions");
                try {
                    JSONArray arr = new JSONArray(optionsStr);
                    for (int i = 0; i < arr.length(); i++) field.options.add(arr.optString(i));
                } catch (Exception ignore) {}
            }
            if (obj.has("fieldParam") && !obj.isNull("fieldParam")) {
                String paramStr = obj.optString("fieldParam");
                try {
                    field.param = new JSONObject(paramStr);
                } catch (Exception ignore) {}
            }
            return field;
        }
    }
}