package com.aplus.remotenursing.models;

import com.google.gson.annotations.SerializedName;

public class UserInfo {
    @SerializedName(value = "userId", alternate = {"user_id"})
    private String user_id;
    @SerializedName(value = "userName", alternate = {"user_name"})
    private String user_name;
    @SerializedName("gender")
    private String gender;
    @SerializedName(value = "birthDate", alternate = {"birth_date"})
    private String birth_date;
    @SerializedName(value = "maritalStatus", alternate = {"marital_status"})
    private String marital_status;
    @SerializedName(value = "educationLevel", alternate = {"education_level"})
    private String education_level;
    @SerializedName(value = "livingStatus", alternate = {"living_status"})
    private String living_status;
    @SerializedName(value = "jobStatus", alternate = {"job_status"})
    private String job_status;
    @SerializedName("phone")
    private String phone;
    @SerializedName(value = "incomePerCapita", alternate = {"income_per_capita"})
    private String income_per_capita;
    @SerializedName(value = "insuranceType", alternate = {"insurance_type"})
    private String insurance_type;

    public UserInfo() {}

    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }

    public String getUser_name() { return user_name; }
    public void setUser_name(String user_name) { this.user_name = user_name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBirth_date() { return birth_date; }
    public void setBirth_date(String birth_date) { this.birth_date = birth_date; }

    public String getMarital_status() { return marital_status; }
    public void setMarital_status(String marital_status) { this.marital_status = marital_status; }

    public String getEducation_level() { return education_level; }
    public void setEducation_level(String education_level) { this.education_level = education_level; }

    public String getLiving_status() { return living_status; }
    public void setLiving_status(String living_status) { this.living_status = living_status; }

    public String getJob_status() { return job_status; }
    public void setJob_status(String job_status) { this.job_status = job_status; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getIncome_per_capita() { return income_per_capita; }
    public void setIncome_per_capita(String income_per_capita) { this.income_per_capita = income_per_capita; }

    public String getInsurance_type() { return insurance_type; }
    public void setInsurance_type(String insurance_type) { this.insurance_type = insurance_type; }
}