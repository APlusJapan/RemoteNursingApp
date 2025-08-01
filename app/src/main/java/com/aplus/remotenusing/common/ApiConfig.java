package com.aplus.remotenusing.common;

/**
 * Centralized API configuration.
 */
public class ApiConfig {
    /** Base server address (must end with a slash) */
    public static final String BASE_URL = "http://192.168.2.9:8080/api/";

    public static final String API_SERIES = BASE_URL + "series";
    public static final String API_VIDEOS = BASE_URL + "videos";
    public static final String API_USER_TASK = BASE_URL + "usertask";
    public static final String API_USER_INFO = BASE_URL + "userinfo/";       // requires userId after slash
    public static final String API_UPDATE_USER_INFO = BASE_URL + "updateUserinfo/"; // requires userId after slash
    public static final String API_CREATE_USER_INFO = BASE_URL + "createUserinfo";
    public static final String API_ACCOUNT_REGISTER = BASE_URL + "account/register";
    public static final String API_ACCOUNT_LOGIN = BASE_URL + "account/login";
    public static final String API_USER_ACCOUNT = BASE_URL + "useraccount/"; // requires userId after slash
    public static final String API_CHECKIN_FIELDS = BASE_URL + "checkin/field/form/"; // 需要formId拼接
    public static final String API_CHECKIN_RECORD = BASE_URL + "checkin/record";
    public static final String API_QUESTIONNAIRE_FIELDS = BASE_URL + "questionnaire/field/form/";
    public static final String API_QUESTIONNAIRE_RECORD = BASE_URL + "questionnaire/record";

}