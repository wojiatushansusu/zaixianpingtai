package com.exam.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.enums.DifficultyEnum;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.dto.QuestionImportDTO;
import com.exam.mapper.KnowledgeTagMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.QuestionTagMapper;
import com.exam.pojo.KnowledgeTag;
import com.exam.pojo.Question;
import com.exam.pojo.QuestionTag;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
public class QuestionImportListener extends AnalysisEventListener<Map<Integer, String>> {

    private final QuestionMapper questionMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final QuestionTagMapper questionTagMapper;
    private final Long creatorId;
    private final Set<Integer> includeRows;

    private Map<Integer, String> headerFieldMap;

    @Getter
    private int successCount = 0;
    @Getter
    private int skippedCount = 0;
    @Getter
    private final List<String> errors = new ArrayList<>();

    public QuestionImportListener(QuestionMapper questionMapper,
                                   KnowledgeTagMapper knowledgeTagMapper,
                                   QuestionTagMapper questionTagMapper,
                                   Long creatorId) {
        this(questionMapper, knowledgeTagMapper, questionTagMapper, creatorId, null);
    }

    public QuestionImportListener(QuestionMapper questionMapper,
                                   KnowledgeTagMapper knowledgeTagMapper,
                                   QuestionTagMapper questionTagMapper,
                                   Long creatorId,
                                   Set<Integer> includeRows) {
        this.questionMapper = questionMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
        this.questionTagMapper = questionTagMapper;
        this.creatorId = creatorId;
        this.includeRows = includeRows;
    }

    @Override
    public void invokeHead(Map<Integer, com.alibaba.excel.metadata.data.ReadCellData<?>> headMap, AnalysisContext context) {
        headerFieldMap = new HashMap<>();
        for (Map.Entry<Integer, com.alibaba.excel.metadata.data.ReadCellData<?>> entry : headMap.entrySet()) {
            String fieldName = QuestionImportDTO.resolveField(entry.getValue().getStringValue());
            if (fieldName != null) {
                headerFieldMap.put(entry.getKey(), fieldName);
            }
        }
        log.info("导入表头映射: {}", headerFieldMap);
    }

    @Override
    public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1;

        if (includeRows != null && !includeRows.isEmpty() && !includeRows.contains(rowIndex)) {
            skippedCount++;
            return;
        }

        QuestionImportDTO dto = mapRowToDTO(rowData, rowIndex);
        if (dto == null) {
            skippedCount++;
            return;
        }

        try {
            Question question = convertToQuestion(dto);
            if (question == null) {
                errors.add("第" + rowIndex + "行：题目类型无法识别");
                return;
            }

            question.setCreatorId(creatorId);
            questionMapper.insert(question);

            if (StringUtils.hasText(dto.getTagNames())) {
                handleTags(question.getId(), dto.getTagNames());
            }

            successCount++;
        } catch (Exception e) {
            log.error("导入第{}行失败", rowIndex, e);
            errors.add("第" + rowIndex + "行：" + e.getMessage());
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("导入完成，成功: {}，跳过: {}，失败: {}", successCount, skippedCount, errors.size());
    }

    private QuestionImportDTO mapRowToDTO(Map<Integer, String> rowData, int rowIndex) {
        QuestionImportDTO dto = new QuestionImportDTO();
        dto.setRowIndex(rowIndex);

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
                    try { dto.setScore(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                    break;
                case "difficultyStr": dto.setDifficultyStr(value); break;
                case "tagNames": dto.setTagNames(value); break;
            }
        }

        boolean allEmpty = !StringUtils.hasText(dto.getTypeStr())
                && !StringUtils.hasText(dto.getContent())
                && !StringUtils.hasText(dto.getAnswer());
        if (allEmpty) return null;

        return dto;
    }

    private Question convertToQuestion(QuestionImportDTO dto) {
        Question question = new Question();
        question.setContent(dto.getContent());
        question.setOptionA(dto.getOptionA());
        question.setOptionB(dto.getOptionB());
        question.setOptionC(dto.getOptionC());
        question.setOptionD(dto.getOptionD());
        question.setAnalysis(dto.getAnalysis());
        question.setScore(dto.getScore() != null ? dto.getScore() : 5);

        QuestionTypeEnum typeEnum = parseType(dto.getTypeStr());
        if (typeEnum == null) {
            typeEnum = inferType(dto);
        }
        if (typeEnum == null) {
            return null;
        }
        question.setType(typeEnum.getCode());

        String answer = normalizeAnswer(dto.getAnswer(), typeEnum);
        question.setAnswer(answer);

        DifficultyEnum diffEnum = parseDifficulty(dto.getDifficultyStr());
        question.setDifficulty(diffEnum != null ? diffEnum.getCode() : 1);

        return question;
    }

    private QuestionTypeEnum parseType(String typeStr) {
        if (!StringUtils.hasText(typeStr)) return null;
        String lower = typeStr.trim().toLowerCase();
        switch (lower) {
            case "单选题":
            case "单选":
            case "single":
            case "single_choice":
            case "1":
                return QuestionTypeEnum.SINGLE;
            case "多选题":
            case "多选":
            case "multi":
            case "multiple":
            case "2":
                return QuestionTypeEnum.MULTI;
            case "判断题":
            case "判断":
            case "judge":
            case "true_false":
            case "3":
                return QuestionTypeEnum.JUDGE;
            case "简答题":
            case "简答":
            case "主观题":
            case "short_answer":
            case "essay":
            case "4":
                return QuestionTypeEnum.SHORT_ANSWER;
            default:
                return null;
        }
    }

    private QuestionTypeEnum inferType(QuestionImportDTO dto) {
        boolean hasOptions = StringUtils.hasText(dto.getOptionA()) || StringUtils.hasText(dto.getOptionB());
        boolean hasC = StringUtils.hasText(dto.getOptionC());
        boolean hasD = StringUtils.hasText(dto.getOptionD());

        if (hasOptions) {
            String answer = dto.getAnswer() != null ? dto.getAnswer().trim().toUpperCase() : "";
            if (answer.matches("[A-D]{2,}") || answer.contains(",") || answer.contains("，")) {
                return QuestionTypeEnum.MULTI;
            }
            if (answer.matches("[A-D]")) {
                return QuestionTypeEnum.SINGLE;
            }
            if (hasC || hasD) {
                return QuestionTypeEnum.SINGLE;
            }
            return QuestionTypeEnum.JUDGE;
        }

        String answer = dto.getAnswer() != null ? dto.getAnswer().trim() : "";
        if (answer.matches("^(正确|错误|对|错|true|false|yes|no|是|否|[AB]|1|0|T|F)$")) {
            return QuestionTypeEnum.JUDGE;
        }

        return QuestionTypeEnum.SHORT_ANSWER;
    }

    private String normalizeAnswer(String answer, QuestionTypeEnum type) {
        if (answer == null) return "";
        String trimmed = answer.trim();

        if (type == QuestionTypeEnum.JUDGE) {
            String upper = trimmed.toUpperCase();
            if (upper.matches("^(正确|对|TRUE|YES|是|A|1|T)$")) {
                return "true";
            }
            if (upper.matches("^(错误|错|FALSE|NO|否|B|0|F)$")) {
                return "false";
            }
            return trimmed;
        }

        if (type == QuestionTypeEnum.SINGLE) {
            String upper = trimmed.toUpperCase();
            if (upper.matches("^[1-4]$")) {
                return String.valueOf((char)('A' + Integer.parseInt(upper) - 1));
            }
            if (upper.matches("^[A-D]$")) {
                return upper;
            }
            return trimmed;
        }

        if (type == QuestionTypeEnum.MULTI) {
            String upper = trimmed.toUpperCase();
            if (upper.matches("[1-4]+")) {
                StringBuilder sb = new StringBuilder();
                for (char c : upper.toCharArray()) {
                    sb.append((char)('A' + (c - '1')));
                }
                return sb.toString();
            }
            String pure = upper.replaceAll("[,，;；\\s、/|]+", "");
            if (!pure.isEmpty() && pure.matches("[A-D]+")) {
                char[] chars = pure.toCharArray();
                Arrays.sort(chars);
                return String.join(",", chars[0] + "", chars.length > 1 ? chars[1] + "" : "",
                        chars.length > 2 ? chars[2] + "" : "", chars.length > 3 ? chars[3] + "" : "")
                        .replaceAll(",$", "");
            }
            return trimmed;
        }

        return trimmed;
    }

    private DifficultyEnum parseDifficulty(String difficultyStr) {
        if (!StringUtils.hasText(difficultyStr)) return null;
        return DifficultyEnum.fromCode(null);
    }

    private void handleTags(Long questionId, String tagNamesStr) {
        String[] names = tagNamesStr.split("[,，、/]");
        Set<String> processedNames = new LinkedHashSet<>();
        for (String name : names) {
            String n = name.trim();
            if (n.startsWith("/")) n = n.substring(1);
            if (!n.isEmpty()) processedNames.add(n);
        }

        for (String tagName : processedNames) {
            KnowledgeTag tag = knowledgeTagMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getName, tagName)
            );
            if (tag == null) {
                tag = new KnowledgeTag();
                tag.setName(tagName);
                knowledgeTagMapper.insert(tag);
            }

            QuestionTag questionTag = new QuestionTag();
            questionTag.setQuestionId(questionId);
            questionTag.setTagId(tag.getId());
            questionTagMapper.insert(questionTag);
        }
    }
}
