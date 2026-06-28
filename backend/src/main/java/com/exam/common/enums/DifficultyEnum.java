package com.exam.common.enums;

import lombok.Getter;

@Getter
public enum DifficultyEnum {

    EASY(1, "简单"),
    MEDIUM(2, "中等"),
    HARD(3, "困难");

    private final int code;
    private final String description;

    DifficultyEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static DifficultyEnum fromCode(Integer code) {
        if (code == null) return MEDIUM;
        for (DifficultyEnum e : values()) {
            if (e.code == code) return e;
        }
        return MEDIUM;
    }
}
