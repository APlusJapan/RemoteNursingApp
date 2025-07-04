package com.aplus.remotenursing.models;

public class UserInfo {
    private String user_name;
    private String gender;
    private String birth_date;
    private String marital_status;
    private String education_level;
    private String living_status;
    private String job_status;
    private String insurance_type;

    public UserInfo() {}

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

    public String getInsurance_type() { return insurance_type; }
    public void setInsurance_type(String insurance_type) { this.insurance_type = insurance_type; }
}