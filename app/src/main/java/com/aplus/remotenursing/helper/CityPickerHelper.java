package com.aplus.remotenursing.helper;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.aplus.remotenursing.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CityPickerHelper {

    public interface OnCitySelectedListener {
        void onCitySelected(String province, String city, String district);
    }

    private static Map<String, Map<String, List<String>>> CITY_DATA = null;

    /**
     * 从 JSON 文件加载城市数据
     */
    private static void loadCityData(Context context) {
        if (CITY_DATA != null) return; // 已加载，不重复加载

        CITY_DATA = new LinkedHashMap<>();

        try {
            // 从 raw 资源读取 JSON 文件
            InputStream is = context.getResources().openRawResource(R.raw.china_cities);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            String json = new String(buffer, "UTF-8");
            JSONObject root = new JSONObject(json);

            // 解析 JSON
            Iterator<String> provinces = root.keys();
            while (provinces.hasNext()) {
                String province = provinces.next();
                JSONObject cities = root.getJSONObject(province);

                Map<String, List<String>> cityMap = new LinkedHashMap<>();
                Iterator<String> cityNames = cities.keys();

                while (cityNames.hasNext()) {
                    String city = cityNames.next();
                    JSONArray districts = cities.getJSONArray(city);

                    List<String> districtList = new ArrayList<>();
                    for (int i = 0; i < districts.length(); i++) {
                        districtList.add(districts.getString(i));
                    }

                    cityMap.put(city, districtList);
                }

                CITY_DATA.put(province, cityMap);
            }

        } catch (Exception e) {
            e.printStackTrace();
            // 如果加载失败，使用默认数据
            loadDefaultData();
        }
    }

    /**
     * 加载默认数据（备用方案）
     */
    private static void loadDefaultData() {
        CITY_DATA = new LinkedHashMap<>();

        Map<String, List<String>> beijing = new LinkedHashMap<>();
        beijing.put("北京市", List.of("东城区", "西城区", "朝阳区", "丰台区", "石景山区", "海淀区"));
        CITY_DATA.put("北京市", beijing);

        Map<String, List<String>> shanghai = new LinkedHashMap<>();
        shanghai.put("上海市", List.of("黄浦区", "徐汇区", "长宁区", "静安区", "普陀区", "虹口区"));
        CITY_DATA.put("上海市", shanghai);
    }

    public static void showCityPicker(Context context, OnCitySelectedListener listener) {
        // 首次使用时加载数据
        loadCityData(context);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_city_picker, null);

        Spinner spinnerProvince = dialogView.findViewById(R.id.spinner_province);
        Spinner spinnerCity = dialogView.findViewById(R.id.spinner_city);
        Spinner spinnerDistrict = dialogView.findViewById(R.id.spinner_district);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        // 省份列表
        List<String> provinces = new ArrayList<>(CITY_DATA.keySet());
        ArrayAdapter<String> provinceAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, provinces);
        provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvince.setAdapter(provinceAdapter);

        // 城市和区县列表
        List<String> cities = new ArrayList<>();
        List<String> districts = new ArrayList<>();
        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, cities);
        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(cityAdapter);

        ArrayAdapter<String> districtAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, districts);
        districtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDistrict.setAdapter(districtAdapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // 省份选择监听
        spinnerProvince.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String province = provinces.get(position);
                Map<String, List<String>> cityMap = CITY_DATA.get(province);

                cities.clear();
                if (cityMap != null) {
                    cities.addAll(cityMap.keySet());
                }
                cityAdapter.notifyDataSetChanged();

                if (!cities.isEmpty()) {
                    spinnerCity.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 城市选择监听
        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (cities.isEmpty()) return;

                String province = provinces.get(spinnerProvince.getSelectedItemPosition());
                String city = cities.get(position);
                Map<String, List<String>> cityMap = CITY_DATA.get(province);

                districts.clear();
                if (cityMap != null) {
                    List<String> districtList = cityMap.get(city);
                    if (districtList != null) {
                        districts.addAll(districtList);
                    }
                }
                districtAdapter.notifyDataSetChanged();

                if (!districts.isEmpty()) {
                    spinnerDistrict.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 确认按钮
        btnConfirm.setOnClickListener(v -> {
            if (!provinces.isEmpty() && !cities.isEmpty() && !districts.isEmpty()) {
                String province = provinces.get(spinnerProvince.getSelectedItemPosition());
                String city = cities.get(spinnerCity.getSelectedItemPosition());
                String district = districts.get(spinnerDistrict.getSelectedItemPosition());

                if (listener != null) {
                    listener.onCitySelected(province, city, district);
                }
                dialog.dismiss();
            }
        });

        // 取消按钮
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 初始化第一个省份的城市
        if (!provinces.isEmpty()) {
            spinnerProvince.setSelection(0);
        }

        dialog.show();
    }
}