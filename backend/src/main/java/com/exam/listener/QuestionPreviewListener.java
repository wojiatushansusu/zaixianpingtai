package com.exam.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.exam.common.enums.DifficultyEnum;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.dto.QuestionImportDTO;
import com.exam.dto.QuestionPreviewResultDTO;
import com.exam.dto.QuestionPreviewRowDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Excel导入预检监听器 —— 只校验不写入数据库
 *
 * 对每一行数据进行完整性检查，返回详细的校验结果，供前端展示。
 */
@Slf4j
public class QuestionPreviewListener extends AnalysisEventListener<Map<Integer, String>> {

    /** 表头列索引 -> 字段名映射 */
    private Map<Integer, String> headerFieldMap;

    /** 未识别的表头列名 */
    private final List<String> unknownHeaders = new ArrayList<>();

    /** 预检结果 */
    private final QuestionPreviewResultDTO result = new QuestionPreviewResultDTO();
    private final List<QuestionPreviewRowDTO> rows = new ArrayList<>();

    public QuestionPreviewListener() {
        result.setRows(rows);
        result.setUnknownHeaders(unknownHeaders);
    }

    @Override
    public void invokeHead(Map<Integer, com.alibaba.excel.metadata.data.ReadCellData<?>> headMap, AnalysisContext context) {
        headerFieldMap = new HashMap<>();
        for (Map.Entry<Integer, com.alibaba.excel.metadata.data.ReadCellData<?>> entry : headMap.entrySet()) {
            String fieldName = QuestionImportDTO.resolveField(entry.getValue().getStringValue());
            if (fieldName != null) {
                headerFieldMap.put(entry.getKey(), fieldName);
            } else {
                String header = entry.getValue().getStringValue();
                if (header != null && !header.trim().isEmpty()) {
                    unknownHeaders.add(header.trim());
                }
            }
        }
        log.info("预检表头映射: {}", headerFieldMap);
    }

    @Override
    public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1;
        QuestionPreviewRowDTO rowDTO = mapRowToPreviewDTO(rowData, rowIndex);

        // 跳过空行
        if (rowDTO == null) return;

        // 执行校验
        validateRow(rowDTO);

        rows.add(rowDTO);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        int ok = 0, warn = 0, err = 0;
        for (QuestionPreviewRowDTO row : rows) {
            switch (row.getStatus()) {
                case "ok": ok++; break;
                case "warning": warn++; break;
                default: err++; break;
            }
        }
        result.setTotalCount(rows.size());
        result.setOkCount(ok);
        result.setWarningCount(warn);
        result.setErrorCount(err);
        log.info("预检完成，总计: {}，通过: {}，警告: {}，错误: {}", rows.size(), ok, warn, err);
    }

    /**
     * 将一行Excel数据映射为预检DTO
     */
    private QuestionPreviewRowDTO mapRowToPreviewDTO(Map<Integer, String> rowData, int rowIndex) {
        QuestionPreviewRowDTO dto = new QuestionPreviewRowDTO();
        dto.setRowIndex(rowIndex);
        dto.setMessages(new ArrayList<>());
        dto.setStatus("ok");

        if (headerFieldMap == null) return null;

        for (Map.Entry<Integer, String> entry : rowData.entrySet()) {
            String fieldName = headerFieldMap.get(entry.getKey());
            if (fieldName == null) continue;

            String value = entry.getValue();
            if (value == null) value = "";
            value = value.trim();

            switch (fieldName) {
                case "typeStr": dto.setTypeStr(value); break;
                case "content": dto.setContent(value); break;
                case "optionA": dto.setOptionA(value); break;
                case "optionB": dto.setOptionB(value); break;
                case "optionC": dto.setOptionC(value); break;
                case "optionD": dto.setOptionD(value); break;
                case "answer": dto.setAnswer(value); break;
                case "analysis": dto.setAnalysis(value); break;
                case "score":
                    try { dto.setScore(Integer.parseInt(value)); } catch (NumberFormatException e) { /* 保留null */ }
                    break;
                case "difficultyStr": dto.setDifficultyStr(value); break;
                case "tagNames": dto.setTagNames(value); break;
            }
        }

        // 跳过全空行
        boolean allEmpty = !StringUtils.hasText(dto.getTypeStr())
                && !StringUtils.hasText(dto.getContent())
                && !StringUtils.hasText(dto.getAnswer());
        if (allEmpty) return null;

        return dto;
    }

    /**
     * 对单行数据进行校验，填充 status 和 messages
     */
    private void validateRow(QuestionPreviewRowDTO row) {
        List<String> msgs = row.getMessages();
        String currentStatus = "ok";

        // ---- 错误级别检查 ----

        // 1. 题目类型（可为空，系统会自动推断）
        QuestionTypeEnum typeEnum = null;
        if (StringUtils.hasText(row.getTypeStr())) {
            typeEnum = parseTypeQuiet(row.getTypeStr());
            if (typeEnum == null) {
                msgs.add("不支持的题目类型: " + row.getTypeStr() + "（支持: 单选题/多选题/判断题/简答题/填空题等）");
                currentStatus = "error";
            }
        }

        // 2. 题干
        if (!StringUtils.hasText(row.getContent())) {
            msgs.add("题干为空");
            currentStatus = "error";
        }

        // 3. 答案（简答题跳过格式校验，允许自由文本）
        if (!StringUtils.hasText(row.getAnswer())) {
            msgs.add("答案为空");
            currentStatus = "error";
        } else if (typeEnum != null && typeEnum != QuestionTypeEnum.SHORT_ANSWER) {
            String answerFormatIssue = checkAnswerFormat(row.getAnswer(), typeEnum);
            if (answerFormatIssue != null) {
                msgs.add(answerFormatIssue);
                // 答案格式问题只提示，不阻止导入（降为警告）
                if (!"error".equals(currentStatus)) currentStatus = "warning";
            }
        }

        // ---- 尝试自动推断题型 ----
        if (typeEnum == null && !"error".equals(currentStatus)) {
            typeEnum = inferTypeFromOptions(row);
            if (typeEnum != null) {
                row.setTypeStr(typeEnum.getDescription()); // 用推断出的类型名替换
                msgs.add("题目类型为空，已自动推断为「" + typeEnum.getDescription() + "」");
                if (!"error".equals(currentStatus)) currentStatus = "warning";
            }
        }

        // ---- 警告级别检查 ----
        if (!"error".equals(currentStatus)) {
            // 4. 选择题缺少选项
            if (typeEnum != null && typeEnum != QuestionTypeEnum.JUDGE && typeEnum != QuestionTypeEnum.SHORT_ANSWER) {
                boolean noOptions = !StringUtils.hasText(row.getOptionA())
                        && !StringUtils.hasText(row.getOptionB())
                        && !StringUtils.hasText(row.getOptionC())
                        && !StringUtils.hasText(row.getOptionD());
                if (noOptions) {
                    msgs.add("选择题缺少所有选项内容");
                    currentStatus = "warning";
                } else {
                    // 单选题只填了部分选项
                    if (typeEnum == QuestionTypeEnum.SINGLE && !StringUtils.hasText(row.getOptionB())) {
                        msgs.add("单选题至少需要两个选项");
                        currentStatus = "warning";
                    }
                }
            }

            // 5. 分值为空
            if (row.getScore() == null) {
                msgs.add("分值未设置，将默认为5分");
                if (!"error".equals(currentStatus)) currentStatus = "warning";
            } else if (row.getScore() <= 0 || row.getScore() > 100) {
                msgs.add("分值异常: " + row.getScore() + "（建议1-100）");
                if (!"error".equals(currentStatus)) currentStatus = "warning";
            }

            // 6. 难度无法识别
            if (StringUtils.hasText(row.getDifficultyStr())) {
                DifficultyEnum diffEnum = parseDifficultyQuiet(row.getDifficultyStr());
                if (diffEnum == null) {
                    msgs.add("无法识别难度: " + row.getDifficultyStr() + "（支持: 简单/中等/困难，或 1/2/3）");
                    if (!"error".equals(currentStatus)) currentStatus = "warning";
                }
            }

            // 7. 标签内容提示
            if (StringUtils.hasText(row.getTagNames())) {
                String[] names = row.getTagNames().split("[,，、/]");
                List<String> validNames = new ArrayList<>();
                for (String name : names) {
                    String n = name.trim();
                    if (n.startsWith("/")) n = n.substring(1);
                    if (!n.isEmpty()) validNames.add(n);
                }
                if (validNames.isEmpty()) {
                    msgs.add("标签内容无效");
                    if (!"error".equals(currentStatus)) currentStatus = "warning";
                }
            }

            // 8. 没有解析
            if (!StringUtils.hasText(row.getAnalysis())) {
                msgs.add("未填写答案解析");
                // 不升级status，这只是建议
            }
        }

        row.setStatus(currentStatus);
    }

    /**
     * 检查答案格式是否正确（宽松模式）
     * @return null表示正确，否则返回警告描述
     */
    private String checkAnswerFormat(String answer, QuestionTypeEnum type) {
        // 简答题/填空题：答案为自由文本，不做格式校验
        if (type == QuestionTypeEnum.SHORT_ANSWER) {
            return null;
        }

        String upper = answer.toUpperCase().trim();

        if (type == QuestionTypeEnum.JUDGE) {
            // 判断题答案：正确/错误、true/false、对/错、A/B、1/0、T/F
            if (upper.matches("^(正确|错误|TRUE|FALSE|对|错|T|F|[AB]|1|0|YES|NO|是|否)$")) {
                return null;
            }
            // 答案不为空但格式奇怪，给个提示但不阻止
            return "判断题答案建议为: 正确/错误/对/错/A/B";
        }

        if (type == QuestionTypeEnum.SINGLE) {
            // 单选答案：单个字母A-D
            if (upper.matches("^[A-D]$")) {
                return null;
            }
            // 答案为数字1-4也接受（有些题库用数字表示选项）
            if (upper.matches("^[1-4]$")) {
                return "单选题答案使用了数字 " + upper + "，系统将自动识别（" + upper + "对应选项" + (char)('A' + Integer.parseInt(upper) - 1) + "）";
            }
            return "单选题答案建议为单个字母 A/B/C/D，当前: " + answer;
        }

        if (type == QuestionTypeEnum.MULTI) {
            // 多选答案：支持 A,B / A，B / AB / A B / A、B 等多种分隔格式
            // 先去除所有分隔符，得到纯字母序列
            String pure = upper.replaceAll("[,，;；\\s、/|]+", "");
            // 检查纯字母序列是否都是A-D
            if (!pure.isEmpty() && pure.matches("[A-D]+")) {
                // 至少两个不同字母
                if (pure.length() >= 2) {
                    return null;
                }
                // 只有一个字母，提醒但不阻止
                return "多选题当前只有1个选项(" + pure + ")，建议至少选择2个";
            }
            // 答案为数字格式也尝试识别（如 12 表示AB）
            if (pure.matches("[1-4]+")) {
                return null;
            }
            return "多选题答案建议为大写字母A-D（可用逗号分隔或直接连写如AB），当前: " + answer;
        }

        return null;
    }

    private QuestionTypeEnum parseTypeQuiet(String typeStr) {
        try {
            if (!StringUtils.hasText(typeStr)) return null;
            String type = typeStr.trim().toLowerCase();
            switch (type) {
                case "单选": case "单选题": case "single": case "single choice": return QuestionTypeEnum.SINGLE;
                case "多选": case "多选题": case "multiple": case "multiple choice": return QuestionTypeEnum.MULTI;
                case "判断": case "判断题": case "judge": case "是非题": case "对错题": return QuestionTypeEnum.JUDGE;
                case "简答": case "简答题": case "short answer": case "主观": case "主观题":
                case "问答": case "问答题": case "论述": case "论述题":
                case "填空": case "填空题": case "fill": case "作文":
                case "计算题": case "编程": case "编程题": case "设计题":
                case "分析题": case "综合题":
                    return QuestionTypeEnum.SHORT_ANSWER;
                default:
                    // 模糊匹配
                    if (type.contains("单选") || type.contains("single")) return QuestionTypeEnum.SINGLE;
                    if (type.contains("多选") || type.contains("multiple")) return QuestionTypeEnum.MULTI;
                    if (type.contains("判断") || type.contains("judge") || type.contains("是非") || type.contains("对错")) return QuestionTypeEnum.JUDGE;
                    if (type.contains("简答") || type.contains("主观") || type.contains("问答") || type.contains("填空")
                            || type.contains("论述") || type.contains("作文") || type.contains("计算") || type.contains("编程")
                            || type.contains("设计") || type.contains("分析") || type.contains("综合")) return QuestionTypeEnum.SHORT_ANSWER;
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private QuestionTypeEnum inferTypeFromOptions(QuestionPreviewRowDTO row) {
        boolean hasA = StringUtils.hasText(row.getOptionA());
        boolean hasB = StringUtils.hasText(row.getOptionB());
        boolean hasC = StringUtils.hasText(row.getOptionC());
        boolean hasD = StringUtils.hasText(row.getOptionD());
        boolean hasAnyOption = hasA || hasB || hasC || hasD;
        String answer = row.getAnswer() != null ? row.getAnswer().trim().toUpperCase() : "";

        if (!hasAnyOption) {
            // 没有任何选项，推断为简答题
            return QuestionTypeEnum.SHORT_ANSWER;
        }

        if (hasA && hasB) {
            if (answer.matches("^[A-D]$")) {
                return QuestionTypeEnum.SINGLE;
            }
            if (answer.matches("^[A-D][,，\\s]+[A-D].*")) {
                return QuestionTypeEnum.MULTI;
            }
        }

        return null;
    }

    private DifficultyEnum parseDifficultyQuiet(String difficultyStr) {
        try {
            if (!StringUtils.hasText(difficultyStr)) return DifficultyEnum.MEDIUM;
            String diff = difficultyStr.trim().toLowerCase();
            switch (diff) {
                case "简单": case "easy": case "1": return DifficultyEnum.EASY;
                case "中等": case "medium": case "2": return DifficultyEnum.MEDIUM;
                case "困难": case "hard": case "3": return DifficultyEnum.HARD;
                default: return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public QuestionPreviewResultDTO getResult() {
        return result;
    }
}
