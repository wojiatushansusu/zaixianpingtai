package com.exam.common.enums;

import lombok.Getter;

/**
 * 考试记录提交状态枚举
 */
@Getter
public enum SubmitStatusEnum {

    NOT_SUBMITTED(0, "未提交"),
    SUBMITTED(1, "已提交"),
    AUTO_SUBMITTED(2, "自动提交（超时）");

    private final int code;
    private final String description;

    SubmitStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
