package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("paper_question")
public class PaperQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long questionId;

    /** 本题分值 */
    private Integer score;

    /** 排序 */
    private Integer sortOrder;
}
