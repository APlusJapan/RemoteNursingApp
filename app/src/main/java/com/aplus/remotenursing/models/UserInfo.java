package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;

public class UserInfo {
    @SerializedName(value = "userId", alternate = {"user_id"})
    private String userId;

    @SerializedName(value = "userName", alternate = {"user_name"})
    private String userName;

    @SerializedName("gender")
    private String gender;

    @SerializedName(value = "birthDate", alternate = {"birth_date"})
    private String birthDate;

    @SerializedName(value = "maritalStatus", alternate = {"marital_status"})
    private String maritalStatus;

    @SerializedName(value = "educationLevel", alternate = {"education_level"})
    private String educationLevel;

    @SerializedName(value = "livingStatus", alternate = {"living_status"})
    private String livingStatus;

    @SerializedName(value = "jobStatus", alternate = {"job_status"})
    private String jobStatus;

    @SerializedName("phone")
    private String phone;

    @SerializedName(value = "incomePerCapita", alternate = {"income_per_capita"})
    private String incomePerCapita;

    @SerializedName(value = "insuranceType", alternate = {"insurance_type"})
    private String insuranceType;

    public UserInfo() {}

    // ---- Getter & Setter (全部用驼峰风格，和字段保持一致) ----
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }

    public String getLivingStatus() { return livingStatus; }
    public void setLivingStatus(String livingStatus) { this.livingStatus = livingStatus; }

    public String getJobStatus() { return jobStatus; }
    public void setJobStatus(String jobStatus) { this.jobStatus = jobStatus; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getIncomePerCapita() { return incomePerCapita; }
    public void setIncomePerCapita(String incomePerCapita) { this.incomePerCapita = incomePerCapita; }

    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }
}
