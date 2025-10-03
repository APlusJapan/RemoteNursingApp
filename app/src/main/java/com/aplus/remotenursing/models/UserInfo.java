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

    @SerializedName(value = "projectId", alternate = {"project_id"})
    private String projectId;
    // 新增：课题和分组字段
    @SerializedName("adminId")
    private String adminId;

    @SerializedName(value = "projectName", alternate = {"project_name"})
    private String projectName;

    @SerializedName(value = "teamId", alternate = {"team_id"})
    private String teamId;

    @SerializedName(value = "teamName", alternate = {"team_name"})
    private String teamName;
    @SerializedName("province")
    private String province;

    @SerializedName("city")
    private String city;

    @SerializedName("district")
    private String district;
    public UserInfo() {}

    // ---- Getter & Setter ----
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
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    // 新增：课题和分组的 Getter & Setter
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
}