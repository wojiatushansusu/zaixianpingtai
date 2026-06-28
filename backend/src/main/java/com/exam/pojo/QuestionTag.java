package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("question_tag")
public class QuestionTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long tagId;
}
