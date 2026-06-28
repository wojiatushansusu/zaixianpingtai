package com.exam.common.enums;

import lombok.Getter;

@Getter
public enum ResultCodeEnum {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),

    // 认证相关 1001-1999
    UNAUTHORIZED(1001, "未认证，请先登录"),
    TOKEN_EXPIRED(1002, "Token已过期"),
    TOKEN_INVALID(1003, "Token无效"),
    FORBIDDEN(1004, "无权限访问"),
    LOGIN_FAILED(1005, "用户名或密码错误"),
    USER_DISABLED(1006, "账号已被禁用"),
    USER_NOT_FOUND(1007, "用户不存在"),

    // 用户相关 2001-2999
    USERNAME_EXISTS(2001, "用户名已存在"),
    PASSWORD_ERROR(2002, "原密码错误"),
    USER_EXISTS(2003, "用户已存在"),

    // 题目相关 3001-3999
    QUESTION_NOT_FOUND(3001, "题目不存在"),

    // 试卷相关 4001-4999
    PAPER_NOT_FOUND(4001, "试卷不存在"),
    PAPER_PUBLISHED(4002, "试卷已发布，不可修改"),
    PAPER_NO_QUESTIONS(4003, "试卷中没有题目"),

    // 考试相关 5001-5999
    EXAM_NOT_FOUND(5001, "考试不存在"),
    EXAM_NOT_STARTED(5002, "考试未开始"),
    EXAM_ENDED(5003, "考试已结束"),
    EXAM_ALREADY_SUBMITTED(5004, "已提交答卷"),
    EXAM_NOT_IN_WHITELIST(5005, "不在考试白名单中"),
    EXAM_ALREADY_STARTED(5006, "考试已在进行中"),

    // 成绩相关 6001-6999
    GRADE_NOT_FOUND(6001, "成绩不存在"),

    // 系统相关 7001-7999
    FILE_UPLOAD_ERROR(7001, "文件上传失败"),
    FILE_TYPE_ERROR(7002, "文件类型不支持"),
    FILE_SIZE_ERROR(7003, "文件大小超限"),
    PARAM_ERROR(7004, "参数错误");

    private final int code;
    private final String message;

    ResultCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
