package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String realName;

    /** 学号/工号 */
    private String studentNo;

    private String email;

    private String phone;

    /** 性别(male-男 female-女 unknown-保密) */
    private String gender;

    /** 头像路径 */
    private String avatar;

    /** 头像二进制数据(JPEG/PNG) */
    @TableField("avatar_data")
    private byte[] avatarData;

    private Long roleId;

    /** 状态(1-正常 0-禁用) */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非数据库字段 - 角色名称 */
    @TableField(exist = false)
    private String roleName;

    /** 非数据库字段 - 角色标识 */
    @TableField(exist = false)
    private String roleKey;
}
