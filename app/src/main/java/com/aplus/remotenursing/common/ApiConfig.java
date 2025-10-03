package com.aplus.remotenursing.common;

/**
 * Centralized API configuration.
 */
public class ApiConfig {
    /** Base server address (must end with a slash) */
    public static final String BASE_URL = "http://192.168.2.24:8080/api/";
    public static final String ALIYUN_OSS_PRIVACY_URL =  "https://bucket-copd-pr-vedios.oss-cn-hangzhou.aliyuncs.com/privacy/privacy.htm";

    public static final String API_VIDEO_TASK_BY_USER = BASE_URL + "video-task/user/";
    public static final String API_VIDEO_DETAIL_BY_SERIES_ID = BASE_URL + "video-detail/series/";
    public static final String API_USER_TASK = BASE_URL + "usertask";
    public static final String API_USER_INFO = BASE_URL + "userinfo/";       // requires userId after slash
    public static final String API_USERINFO_SEARCH_BY_PARAM = BASE_URL + "userinfo/searchByParam/";
    public static final String API_UPDATE_USER_INFO = BASE_URL + "updateUserinfo/"; // requires userId after slash
    public static final String API_CREATE_USER_INFO = BASE_URL + "createUserinfo";
    public static final String API_DELETE_USER_INFO = BASE_URL + "deleteUserinfo/"; // requires userId after slash
    public static final String API_ACCOUNT_REGISTER = BASE_URL + "account/register";
    public static final String API_ACCOUNT_LOGIN = BASE_URL + "account/login";
    public static final String API_SEARCH_ACCOUNT = BASE_URL + "account/searchAccount"; // requires userId after slash
    public static final String API_CHECKIN_FIELDS = BASE_URL + "dailycheckin/field/form/"; // 需要formId拼接
    public static final String API_CHECKIN_RECORD = BASE_URL + "dailycheckin/record";
    public static final String API_CHECKIN_RECORD_COUNT = BASE_URL + "dailycheckin/record/count";
    public static final String API_GET_CHECKIN_FORMID = BASE_URL + "dailycheckin/form/valid-id";
    public static final String API_QUESTIONNAIRE_FIELDS = BASE_URL + "questionnaire/field/form/";
    public static final String API_QUESTIONNAIRE_RECORD = BASE_URL + "questionnaire/record";
    public static final String API_QUESTIONNAIRE_RECORD_COUNT = BASE_URL + "questionnaire/record/count";
    public static final String API_GET_QUESTIONNAIRE_FORMID = BASE_URL + "questionnaire/form/valid-id";
    public static final String API_POINT_RULES = BASE_URL + "point/rule";
    public static final String API_USER_POINT_ACCOUNT = BASE_URL + "point/countByUserId";
    public static final String API_ADD_USERPOINT = BASE_URL + "point/addPoint";
    public static final String API_CHECKUP_STANDARD = BASE_URL + "checkup-standard/by-user/";
    public static final String API_CHECKUP_RECORD_SAVE = BASE_URL + "user-checkup-record";
    public static final String API_VIDEO_HISTORY_RECORD_SAVE = BASE_URL + "video-play-history/batch-update";
    public static final String API_VIDEO_UPDATE_NOTICE = BASE_URL + "videos/getUpdateNotice";
    public static final String API_VIDEO_UPDATE_RECEIPT = BASE_URL + "videos/updates/";
    public static final String API_GET_NOTICE = BASE_URL + "usernotice/search";
    public static final String API_CODE_MASTER = BASE_URL + "code-master/list";

    public static final String API_GET_BANNERS = BASE_URL + "banner/list";
    public static final String API_BANNER_CLICK = BASE_URL + "banner/click";
    public static final String API_BANNER_VIEW = BASE_URL + "banner/view";
    // 快捷登录API（已激活设备无需激活码）
    public static final String API_ACCOUNT_QUICK_LOGIN = BASE_URL + "account/quick-login";

    public static final String API_AUTH_OTP_REQUEST = BASE_URL + "account/auth/otp/request";
    public static final String API_AUTH_OTP_VERIFY  = BASE_URL + "account/auth/otp/verify";
    public static final String API_PROJECT  = BASE_URL + "project-team-master/project/admin/";

    public static final String API_PROJECT_TEAM  = BASE_URL + "project-team-master/project/team/";

}