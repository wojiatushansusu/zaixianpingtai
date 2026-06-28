package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_face")
public class UserFace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 注册照片路径 */
    private String photoUrl = "";

    /** 注册照片二进制数据 */
    @TableField("photo_data")
    private byte[] photoData;

    /** 人脸特征Token（预留商业SDK） */
    private String faceToken;

    /** 状态(1-有效 0-无效) */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
