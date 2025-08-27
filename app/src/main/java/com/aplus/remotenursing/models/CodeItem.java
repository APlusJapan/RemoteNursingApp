package com.aplus.remotenursing.models;

public class CodeItem {
    private String code;
    private String value;
    private String valueType;

    public CodeItem() {}

    public CodeItem(String code, String value, String valueType) {
        this.code = code;
        this.value = value;
        this.valueType = valueType;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public String getValueType() {
        return valueType;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    @Override
    public String toString() {
        return "CodeItem{" +
                "code='" + code + '\'' +
                ", value='" + value + '\'' +
                ", valueType='" + valueType + '\'' +
                '}';
    }
}
