package com.aplus.remotenursing;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.aplus.remotenusing.common.ApiConfig;
import com.aplus.remotenusing.common.UserUtil;

import org.json.*;

import java.io.IOException;
import java.util.*;

import okhttp3.*;

public class DailyCheckInFragment extends Fragment {
    private LinearLayout formParent;
    private Button btnSubmit;
    private ImageButton btnBack;
    private TextView tvTitle;
    private List<Field> fieldList = new ArrayList<>();
    private Map<Long, View> fieldViewMap = new HashMap<>();
    private long formId = 1;
    private String userId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_checkin, container, false);
        formParent = view.findViewById(R.id.form_parent);
        btnSubmit = view.findViewById(R.id.btn_submit);
        btnBack = view.findViewById(R.id.btn_back);
        tvTitle = view.findViewById(R.id.tv_title);

        userId = UserUtil.loadUserId(requireContext());
        tvTitle.setText("每日打卡");

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        btnSubmit.setOnClickListener(v -> submitCheckin());

        loadCheckinForm();
        return view;
    }

    private void loadCheckinForm() {
        OkHttpClient client = new OkHttpClient();
        String url = ApiConfig.API_CHECKIN_FIELDS + formId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "接口错误", Toast.LENGTH_SHORT).show());
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
                    requireActivity().runOnUiThread(() -> renderFormFields());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /** 渲染所有表单项 */
    private void renderFormFields() {
        formParent.removeAllViews();
        fieldViewMap.clear();
        int N = fieldList.size();
        for (int i = 0; i < N; i++) {
            Field field = fieldList.get(i);
            boolean showDivider = i != N - 1;
            View row = createFieldRow(field, showDivider);
            formParent.addView(row);
        }
    }
    // 新增 createFieldRow（支持分割线参数）
    private View createFieldRow(Field field, boolean showDivider) {
        // 1. 新建一行LinearLayout
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp2px(56));
        row.setLayoutParams(rowParams);

        // 2. 左侧标题
        TextView label = new TextView(requireContext());
        label.setText(field.fieldLabel);
        label.setTextColor(0xFF444444);
        label.setTextSize(16);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp2px(16);
        row.addView(label, labelParams);

        // 3. 右侧输入/选择控件
        if ("text".equals(field.fieldType) || "number".equals(field.fieldType)) {
            EditText input = new EditText(requireContext());
            input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            input.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            input.setBackground(null);
            input.setTextSize(15);
            input.setTextColor(0xFF222222);
            input.setHint("请输入" + field.fieldLabel);
            if ("number".equals(field.fieldType)) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            if (field.param.has("maxLength")) {
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(field.param.optInt("maxLength"))});
            }
            row.addView(input);
            fieldViewMap.put(field.fieldId, input);
        } else {
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            tv.setTextSize(15);
            tv.setTextColor(0xFF888888);
            tv.setHint("请选择");
            tv.setBackground(null);
            tv.setPadding(0, 0, dp2px(32), 0);
            tv.setFocusable(true);
            tv.setClickable(true);
            tv.setOnClickListener(v -> {
                if ("date".equals(field.fieldType)) {
                    showDate(tv);
                } else if ("checkbox".equals(field.fieldType)) {
                    showMulti(tv, field.options);
                } else {
                    showSingle(tv, field.options);
                }
            });
            row.addView(tv);
            fieldViewMap.put(field.fieldId, tv);
        }

        // 4. 包装一层竖直LinearLayout，加分割线
        if (showDivider) {
            LinearLayout wrapper = new LinearLayout(requireContext());
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            wrapper.addView(row);
            // 分割线
            View divider = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp2px(1));
            divider.setLayoutParams(lp);
            divider.setBackgroundColor(0xFFF1F1F1);
            wrapper.addView(divider);
            return wrapper;
        } else {
            return row;
        }
    }
    /** 每行和录入页面布局一致 */
    private View createFieldRow(Field field) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        LinearLayout row = (LinearLayout) inflater.inflate(R.layout.row_daily_checkin_field, null);

        TextView label = row.findViewById(R.id.tv_label);
        label.setText(field.fieldLabel);

        // 动态添加右侧控件
        if ("text".equals(field.fieldType) || "number".equals(field.fieldType)) {
            EditText input = new EditText(requireContext());
            input.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            input.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            input.setBackground(null);
            input.setTextSize(15);
            input.setTextColor(0xFF222222);
            input.setHint("请输入" + field.fieldLabel);
            if ("number".equals(field.fieldType)) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            }
            if (field.param.has("maxLength")) {
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(field.param.optInt("maxLength"))});
            }
            input.setId(View.generateViewId());
            row.addView(input);
            fieldViewMap.put(field.fieldId, input);
        } else {
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            tv.setTextSize(15);
            tv.setTextColor(0xFF888888);
            tv.setHint("请选择");
            tv.setBackground(null);
            tv.setPadding(0, 0, dp2px(32), 0);
            tv.setFocusable(true);
            tv.setClickable(true);
            tv.setId(View.generateViewId());

            tv.setOnClickListener(v -> {
                if ("date".equals(field.fieldType)) {
                    showDate(tv);
                } else if ("checkbox".equals(field.fieldType)) {
                    showMulti(tv, field.options);
                } else {
                    showSingle(tv, field.options);
                }
            });
            row.addView(tv);
            fieldViewMap.put(field.fieldId, tv);
        }
        return row;
    }

    private void showSingle(TextView target, List<String> options) {
        String[] arr = options.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setItems(arr, (d, which) -> target.setText(arr[which]))
                .show();
    }

    private void showMulti(TextView target, List<String> options) {
        String[] arr = options.toArray(new String[0]);
        boolean[] checks = new boolean[arr.length];
        new AlertDialog.Builder(requireContext())
                .setMultiChoiceItems(arr, checks, (d, which, isChecked) -> checks[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.length; i++) {
                        if (checks[i]) {
                            if (sb.length() > 0) sb.append(';');
                            sb.append(arr[i]);
                        }
                    }
                    target.setText(sb.toString());
                })
                .show();
    }

    private void showDate(TextView target) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String date = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    target.setText(date);
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    /** 提交逻辑，多选项以分号拼接 */
    private void submitCheckin() {
        OkHttpClient client = new OkHttpClient();
        boolean hasEmpty = false;
        for (Field field : fieldList) {
            View inputView = fieldViewMap.get(field.fieldId);
            Object value = getFieldValue(field, inputView);
            if (field.isRequired && (value == null || value.toString().trim().isEmpty())) {
                Toast.makeText(requireContext(), field.fieldLabel + "为必填项", Toast.LENGTH_SHORT).show();
                hasEmpty = true;
                break;
            }
            JSONObject record = new JSONObject();
            try {
                record.put("userId", userId);
                record.put("formId", formId);
                record.put("fieldId", field.fieldId);
                record.put("inputValue", value);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            RequestBody body = RequestBody.create(record.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(ApiConfig.API_CHECKIN_RECORD)
                    .post(body)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { }
                @Override public void onResponse(Call call, Response response) throws IOException { }
            });
        }
        if (!hasEmpty) {
            Toast.makeText(requireContext(), "已提交", Toast.LENGTH_SHORT).show();
        }
    }

    private Object getFieldValue(Field field, View inputView) {
        if (inputView instanceof EditText) {
            return ((EditText) inputView).getText().toString();
        } else if (inputView instanceof TextView) {
            return ((TextView) inputView).getText().toString();
        }
        return "";
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
    @Override
    public void onResume() {
        super.onResume();
        // 只要重新回到本Fragment，就重新拉一次数据
        //loadCheckinForm();
    }
}
