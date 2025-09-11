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

import com.aplus.remotenursing.common.Contants;
import com.aplus.remotenursing.common.InfoPopup;
import com.aplus.remotenursing.common.ApiConfig;
import com.aplus.remotenursing.common.UserUtils;
import com.aplus.remotenursing.manager.FragmentSafetyManager;  // 注意包路径
import com.aplus.remotenursing.models.UserAccount;

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
    private Map<Integer, String> answerCache = new HashMap<>();
    private int currentPage = 0;
    private long formId = 1;
    private String userId;
    private String adminId;
    private SharedPreferences prefs;
    private boolean isReadOnlyMode = false;

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

        btnBack.setOnClickListener(v -> FragmentSafetyManager.safePopBackStack(this));
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
        UserAccount userAccount = UserUtils.getUserAccount(requireContext());
        if (userAccount == null) {
            FragmentSafetyManager.showError(this, "用户信息获取失败，请重新登录");
            FragmentSafetyManager.safePopBackStack(this);
            return;
        }
        adminId = userAccount.getAdminId();
        getFormId(adminId, userAccount.getProjectId(), userAccount.getTeamId());
    }

    private void getFormId(String adminId, String projectId, String teamId) {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_GET_CHECKIN_FORMID +
                "?projectId=" + projectId +
                "&teamId=" + teamId +
                "&adminId=" + adminId;

        Request request = new Request.Builder().url(url).get().build();

        // 使用带自动重试的关键数据加载
        client.newCall(request).enqueue(
                FragmentSafetyManager.createAutoRetryCallback(
                        this,
                        request,
                        client,
                        // 成功回调
                        (responseBody) -> {
                            try {
                                long retrievedFormId = Long.parseLong(responseBody.trim());
                                if (retrievedFormId > 0) {
                                    formId = retrievedFormId;
                                    checkSubmissionStatus();
                                } else {
                                    // 这种情况不是网络问题，是业务逻辑问题
                                    FragmentSafetyManager.showError(this, "问卷调查还未创建，请联系管理员");
                                    hideFormControls();
                                }
                            } catch (NumberFormatException e) {
                                throw new RuntimeException("返回的表单ID格式不正确", e);
                            }
                        },
                        "获取表单信息"
                )
        );
    }

    private void checkSubmissionStatus() {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_CHECKIN_RECORD_COUNT +
                "?userId=" + userId +
                "&formId=" + formId;

        Request request = new Request.Builder().url(url).get().build();

        // 使用带重试的计数回调
        client.newCall(request).enqueue(
                FragmentSafetyManager.createCountCallbackWithRetry(
                        this,
                        request,
                        client,
                        // 成功回调
                        (count) -> {
                            if (count > 0) {
                                // 已经提交过
                                FragmentSafetyManager.showSuccess(this, "今日打卡已完成！");
                                FragmentSafetyManager.safePopBackStack(this);
                            } else {
                                // 未提交过，正常加载表单
                                loadFormFields();
                            }
                        },
                        "检查提交状态"
                )
        );
    }

    private void hideFormControls() {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            formContainer.removeAllViews();
            tvPageInfo.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            btnPrev.setVisibility(View.GONE);
            btnSubmit.setVisibility(View.GONE);

            TextView hintText = new TextView(requireContext());
            hintText.setText("问卷调查还未创建，请联系管理员");
            hintText.setTextSize(18);
            hintText.setTextColor(0xFF666666);
            hintText.setGravity(Gravity.CENTER);
            hintText.setPadding(dp2px(24), dp2px(48), dp2px(24), dp2px(48));
            formContainer.addView(hintText);
        });
    }

    private void loadFormFields() {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_CHECKIN_FIELDS + formId;
        Request request = new Request.Builder().url(url).build();

        // 使用带重试的关键数据加载
        client.newCall(request).enqueue(
                FragmentSafetyManager.createAutoRetryCallback(
                        this,
                        request,
                        client,
                        // 成功回调
                        (responseBody) -> {
                            try {
                                JSONArray arr = new JSONArray(responseBody);
                                fieldList.clear();
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject obj = arr.getJSONObject(i);
                                    Field field = Field.fromJson(obj);
                                    fieldList.add(field);
                                }

                                if (fieldList.isEmpty()) {
                                    throw new RuntimeException("表单没有任何字段");
                                }

                                if (isReadOnlyMode) {
                                    loadSubmittedAnswers();
                                } else {
                                    loadCachedData();
                                    showCurrentPage();
                                }
                            } catch (JSONException e) {
                                throw new RuntimeException("表单数据格式错误", e);
                            }
                        },
                        "加载表单内容"
                )
        );
    }

    private void loadSubmittedAnswers() {
        loadCachedData();
        showCurrentPage();
    }

    private void loadCachedData() {
        long cachedFormId = prefs.getLong(CACHE_FORM_ID, -1);
        if (cachedFormId == formId) {
            currentPage = prefs.getInt(CACHE_CURRENT_PAGE, 0);
            for (int i = 0; i < fieldList.size(); i++) {
                String cachedAnswer = prefs.getString(CACHE_PREFIX + "answer_" + i, "");
                if (!cachedAnswer.isEmpty()) {
                    answerCache.put(i, cachedAnswer);
                }
            }
            if (currentPage < fieldList.size() && hasAnswerForPage(currentPage)) {
                currentPage = Math.min(currentPage + 1, fieldList.size() - 1);
            }
        } else {
            clearCache();
            currentPage = 0;
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
        tvPageInfo.setText(String.format("第 %d 页 / 共 %d 页", currentPage + 1, fieldList.size()));

        Field currentField = fieldList.get(currentPage);
        View questionView = createQuestionView(currentField);
        formContainer.addView(questionView);

        restoreAnswerForCurrentPage();
        updateButtonState();
        prefs.edit().putInt(CACHE_CURRENT_PAGE, currentPage).apply();
    }

    private View createQuestionView(Field field) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);

        String questionText = field.fieldLabel + (field.isRequired ? " *" : "");
        int lines = (questionText.length() / 20) + 1;
        int topBottomPadding = Math.max(dp2px(20), dp2px(16 + lines * 4));
        container.setPadding(dp2px(24), topBottomPadding, dp2px(24), topBottomPadding);

        TextView questionTextView = new TextView(requireContext());
        questionTextView.setText(questionText);
        questionTextView.setTextSize(24);
        questionTextView.setTextColor(0xFF222222);
        questionTextView.setLineSpacing(dp2px(4), 1.2f);
        questionTextView.setPadding(0, 0, 0, dp2px(24));
        container.addView(questionTextView);

        View inputView = createInputView(field);
        container.addView(inputView);

        return container;
    }

    private View createInputView(Field field) {
        if ("text".equals(field.fieldType) || "number".equals(field.fieldType)) {
            EditText input = new EditText(requireContext());
            input.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp2px(60)));
            input.setTextSize(20);
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
            selectBtn.setTextSize(18);
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
            prefs.edit().putString(CACHE_PREFIX + "answer_" + currentPage, answer).apply();
        }
    }

    private void nextPage() {
        Field currentField = fieldList.get(currentPage);

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
                FragmentSafetyManager.showError(this, currentField.fieldLabel + "为必填项");
                return;
            }
        }

        saveCurrentPageAnswer();

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
        saveCurrentPageAnswer();

        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            if (field.isRequired) {
                String answer = answerCache.get(i);
                if (answer == null || answer.trim().isEmpty()) {
                    FragmentSafetyManager.showError(this, field.fieldLabel + "为必填项");
                    return;
                }
            }
        }

        showSubmittingDialog();

        final int totalRecords = answerCache.size();
        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);

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
                    @Override
                    public void onFailure(Call call, IOException e) {
                        handleSubmissionComplete(completedCount.incrementAndGet(), successCount.get(), totalRecords);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            if (response.isSuccessful()) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            response.close();
                            handleSubmissionComplete(completedCount.incrementAndGet(), successCount.get(), totalRecords);
                        }
                    }
                });
            }
        }
    }

    private AlertDialog submittingDialog;

    private void showSubmittingDialog() {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            if (submittingDialog == null) {
                submittingDialog = new AlertDialog.Builder(requireContext())
                        .setMessage("正在提交打卡数据...")
                        .setCancelable(false)
                        .create();
            }
            submittingDialog.show();
        });
    }

    private void hideSubmittingDialog() {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            if (submittingDialog != null && submittingDialog.isShowing()) {
                submittingDialog.dismiss();
            }
        });
    }

    private void handleSubmissionComplete(int completed, int successful, int total) {
        if (completed < total) {
            return;
        }

        // 使用安全执行方法
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            hideSubmittingDialog();

            if (successful == total) {
                addPointsForDailyCheckin();
            } else {
                FragmentSafetyManager.showError(this, "部分数据提交失败，请重试");
            }
        });
    }

    private void addPointsForDailyCheckin() {
        int points = UserUtils.getPointForTaskType(requireContext(), Contants.USER_TASK_TYPE_DAILYCHECKIN_03);

        if (points <= 0) {
            finishCheckinSuccess(0);
            return;
        }

        // 积分API使用普通的安全回调，失败不影响打卡成功
        UserUtils.addPointsToUserApi(userId, points,
                FragmentSafetyManager.createSafeCallback(
                        this,
                        // 成功回调
                        (responseBody) -> {
                            try {
                                int newTotal = Integer.parseInt(responseBody.trim());
                                if (newTotal == -1) {
                                    finishCheckinSuccess(0);
                                } else {
                                    finishCheckinSuccess(points);
                                }
                            } catch (NumberFormatException e) {
                                finishCheckinSuccess(0);
                            }
                        },
                        // 失败回调 - 积分失败不影响打卡成功
                        (errorMessage) -> finishCheckinSuccess(0)
                )
        );
    }

    private void finishCheckinSuccess(int earnedPoints) {
        FragmentSafetyManager.safeExecuteOnUI(this, () -> {
            clearCache();

            String message;
            if (earnedPoints > 0) {
                message = "今日打卡已完成，获得积分 +" + earnedPoints + "！感谢您的配合！";
            } else {
                message = "今日打卡已完成，感谢您的配合！";
            }

            FragmentSafetyManager.showSuccess(this, message);
            FragmentSafetyManager.safePopBackStack(this);
        });
    }

    // 以下对话框方法保持不变，只是代码太长省略...
    private void showSingle(Button target, List<String> options) {
        // 单选对话框代码保持不变
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

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

        RadioGroup radioGroup = new RadioGroup(requireContext());
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        for (int i = 0; i < options.size(); i++) {
            RadioButton radioButton = new RadioButton(requireContext());
            radioButton.setText(options.get(i));
            radioButton.setTextSize(22);
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

        dialog.show();
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positiveButton != null) positiveButton.setTextSize(20);
        if (negativeButton != null) negativeButton.setTextSize(20);
    }

    private void showMulti(Button target, List<String> options) {
        // 多选对话框代码与原来相同...
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

        String currentText = target.getText().toString();
        Set<String> selectedItems = new HashSet<>();
        if (!currentText.startsWith("请选择")) {
            String[] selected = currentText.split(";");
            for (String item : selected) {
                selectedItems.add(item.trim());
            }
        }

        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String option : options) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(option);
            checkBox.setTextSize(22);
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
        if (positiveButton != null) positiveButton.setTextSize(20);
        if (negativeButton != null) negativeButton.setTextSize(20);
    }

    private void showDate(Button target) {
        // 日期选择对话框代码较长，与原来基本相同，只是包装在FragmentSafetyManager中...
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