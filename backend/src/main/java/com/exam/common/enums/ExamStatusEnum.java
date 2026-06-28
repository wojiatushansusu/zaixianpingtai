package com.exam.common.enums;

import lombok.Getter;

/**
 * 考试状态枚举
 */
@Getter
public enum ExamStatusEnum {

    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    ENDED(2, "已结束");

    private final int code;
    private final String description;

    ExamStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ExamStatusEnum fromCode(Integer code) {
        for (ExamStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的考试状态: " + code);
    }
}
