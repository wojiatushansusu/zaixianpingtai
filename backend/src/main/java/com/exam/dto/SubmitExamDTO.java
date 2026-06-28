package com.exam.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SubmitExamDTO {

    /** 考试ID */
    private Long examId;

    /** 答案Map: questionId -> answer */
    private Map<Long, String> answers;
}
