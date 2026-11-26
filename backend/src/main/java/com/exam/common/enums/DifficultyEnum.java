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

    /**
     * 宽松解析：合法值原样返回，非法值返回 null。
     *
     * <p>{@link #fromCode} 对未知值静默兜底成 MEDIUM，用于"少个显示总比报错强"的场景没问题；
     * 但展示层需要区分「本来就是中等」和「数据坏了只能当中等」，
     * 否则一道 difficulty=9 的脏数据会安静地显示成「中等」，看起来完全正常。
     */
    public static DifficultyEnum fromCodeOrNull(Integer code) {
        if (code == null) {
            return null;
        }
        for (DifficultyEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    public static DifficultyEnum fromCode(Integer code) {
        DifficultyEnum exact = fromCodeOrNull(code);
        return exact != null ? exact : MEDIUM;
    }
}
