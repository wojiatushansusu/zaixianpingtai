package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("face_verify_record")
public class FaceVerifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long examId;

    /** 核验照片路径 */
    private String verifyPhotoUrl;

    /** 核验照片二进制数据(JPEG) */
    @TableField("verify_photo_data")
    private byte[] verifyPhotoData;

    /** 相似度得分(0-1) */
    private Double similarity;

    /** 核验结果(1-通过 0-未通过) */
    private Integer verifyResult;

    /** 失败原因 */
    private String failReason;

    /** 核验时间 */
    private LocalDateTime verifyTime;

    /** 非数据库字段 - 用户姓名 */
    @TableField(exist = false)
    private String userName;

    /** 非数据库字段 - 考试名称 */
    @TableField(exist = false)
    private String examTitle;
}
