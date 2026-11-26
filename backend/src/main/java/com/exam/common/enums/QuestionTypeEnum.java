package com.exam.common.enums;

import lombok.Getter;

/**
 * 题目类型枚举
 */
@Getter
public enum QuestionTypeEnum {

    SINGLE(1, "单选题"),
    MULTI(2, "多选题"),
    JUDGE(3, "判断题"),
    SHORT_ANSWER(4, "简答题");

    private final int code;
    private final String description;

    QuestionTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static QuestionTypeEnum fromCode(int code) {
        for (QuestionTypeEnum type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("未知的题目类型: " + code);
    }

    /**
     * 用于「展示」的宽松解析：认不出来就返回 null，不抛异常。
     *
     * <p>场景：列表接口给每条题填充类型描述。库里只要有一条 type 非法的历史数据，
     * {@link #fromCode} 抛出的 IllegalArgumentException 会让**整页列表**变成 400，
     * 一条脏数据毁掉整个查询。展示层不该有这种杀伤力。
     */
    public static QuestionTypeEnum fromCodeOrNull(Integer code) {
        if (code == null) {
            return null;
        }
        for (QuestionTypeEnum type : values()) {
            if (type.code == code) return type;
        }
        return null;
    }

    /**
     * 是否为客观题（可自动阅卷）
     */
    public boolean isObjective() {
        return this == SINGLE || this == MULTI || this == JUDGE;
    }
}
