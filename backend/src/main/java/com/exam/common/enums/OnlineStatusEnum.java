package com.exam.common.enums;

import lombok.Getter;

/**
 * 在线状态枚举
 */
@Getter
public enum OnlineStatusEnum {

    OFFLINE(0, "离线"),
    ONLINE(1, "在线"),
    DISCONNECTED(2, "断网中");

    private final int code;
    private final String description;

    OnlineStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
