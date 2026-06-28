package com.exam.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建错题练习DTO
 */
@Data
public class CreatePracticeDTO {

    /** 练习标题 */
    private String title;

    /** 选中的错题answer_record ID列表 */
    private List<Long> wrongAnswerIds;

    /** 按题目ID列表创建练习（可选，与wrongAnswerIds二选一） */
    private List<Long> questionIds;
}
