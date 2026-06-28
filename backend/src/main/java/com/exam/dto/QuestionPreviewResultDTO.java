package com.exam.dto;

import lombok.Data;

import java.util.List;

/**
 * Excel预检 - 整体结果
 */
@Data
public class QuestionPreviewResultDTO {

    /** 总行数（有效数据行） */
    private int totalCount;

    /** 校验通过 */
    private int okCount;

    /** 有警告（可导入，但建议修正） */
    private int warningCount;

    /** 有错误（不可导入） */
    private int errorCount;

    /** 未识别的表头列名 */
    private List<String> unknownHeaders;

    /** 每行详情 */
    private List<QuestionPreviewRowDTO> rows;
}
