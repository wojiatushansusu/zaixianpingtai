package com.exam.common.enums;

import lombok.Getter;

@Getter
public enum RoleEnum {

    ADMIN(1, "admin", "管理员"),
    TEACHER(2, "teacher", "教师"),
    STUDENT(3, "student", "学生");

    private final int code;
    private final String roleKey;
    private final String description;

    RoleEnum(int code, String roleKey, String description) {
        this.code = code;
        this.roleKey = roleKey;
        this.description = description;
    }

    public static RoleEnum fromRoleKey(String roleKey) {
        for (RoleEnum role : values()) {
            if (role.roleKey.equals(roleKey)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色: " + roleKey);
    }
}
