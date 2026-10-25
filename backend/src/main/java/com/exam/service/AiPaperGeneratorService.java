package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI试卷生成服务
 * 通过 LlmRouter 调用高能力模型（HEAVY级别）生成结构化试题数据（JSON格式）。
 * 生成试卷是重度任务，自动使用更大的max_tokens和更长的timeout。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPaperGeneratorService {

    /** 最多生成次数：一次不达标时带着约束提示重试一次 */
    private static final int MAX_ATTEMPTS = 2;

    /** 剔除比例低于此值时不再重试，避免为极少数瑕疵多花一次调用 */
    private static final double RETRY_THRESHOLD = 0.25;

    private final LlmRouter llmRouter;
    private final AiQuestionValidator questionValidator;
    private final AnswerSelfCheckService answerSelfCheckService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据用户要求生成试卷题目（HEAVY级别）
     * @param prompt 用户要求，如"生成10道Java基础单选题"
     * @param studentId 创建者ID（用于标记题目创建者）
     * @return 生成结果，包含 title 和 questions 列表
     */
    public GenerateResult generatePaper(String prompt, Long studentId) {
        log.info("开始AI生成试卷: prompt={}, studentId={}", prompt, studentId);

        if (!llmRouter.isEnabled()) {
            return GenerateResult.fail("AI功能未启用，请在application.yml中配置 app.ai.enabled=true");
        }

        GenerateResult best = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            GenerateResult current = generateOnce(prompt, studentId, attempt);

            if (!current.isSuccess()) {
                // 生成失败也值得重试一次，但要把最后一次的错误暴露出去
                best = current;
                continue;
            }
            if (best == null || !best.isSuccess() || current.getRejectedCount() < best.getRejectedCount()) {
                best = current;
            }
            // 一次就干净，或者瑕疵在可接受范围内 —— 不必多花一次调用
            if (current.getRejectedCount() == 0 || current.getRejectRatio() <= RETRY_THRESHOLD) {
                break;
            }
            log.warn("第 {} 次生成剔除比例 {}%，进入重试",
                    attempt, Math.round(current.getRejectRatio() * 100));
        }

        if (best == null || !best.isSuccess()) {
            return best != null ? best : GenerateResult.fail("生成失败");
        }

        // 结构合格只代表格式对；再用重答比对筛一遍语义可疑的题目
        Set<Integer> suspects = answerSelfCheckService.findSuspectIndexes(best.getQuestions());
        best.markSuspects(suspects);
        return best;
    }

    /**
     * 执行一次生成：调模型 → 解析 → 结构校验。
     *
     * @param attempt 第几次尝试，重试时会追加约束提示
     */
    private GenerateResult generateOnce(String prompt, Long studentId, int attempt) {
        try {
            String systemPrompt = buildSystemPrompt();
            String userMessage = buildUserMessage(prompt);
            if (attempt > 1) {
                userMessage = userMessage + """

                        上一次生成中有题目结构不合格，本次请务必保证：
                        1. 每个选项都有实际内容，且四个选项互不相同
                        2. 单选题的 answer 只有一个字母
                        3. 多选题的 answer 至少包含两个字母
                        4. 判断题的 answer 只能是 A（正确）或 B（错误）
                        """;
            }

            Map<String, String> usrMsg = new HashMap<>();
            usrMsg.put("role", "user");
            usrMsg.put("content", userMessage);

            // 使用HEAVY级别（高能力模型，更大max_tokens，更长timeout）
            LlmRouter.LlmResult result = llmRouter.call(
                    LlmRouter.TaskLevel.HEAVY,
                    List.of(usrMsg),
                    systemPrompt);

            if (!result.isSuccess()) {
                return GenerateResult.fail(result.getError());
            }

            String response = result.getContent();
            log.info("AI生成试卷完成(第{}次): model={}, elapsed={}ms, contentLen={}",
                    attempt, result.getUsedModel(), result.getElapsedMs(), response.length());

            return parseGeneratedPaper(response, studentId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI生成试卷失败: prompt={}", prompt, e);
            return GenerateResult.fail("生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
                你是一位专业的考试出题专家。你的任务是根据用户要求生成考试题目。
                
                你必须严格按照要求输出JSON格式，不要添加任何其他文字说明。
                JSON格式如下：
                ```json
                {
                  "title": "试卷标题",
                  "questions": [
                    {
                      "type": 1,
                      "content": "题目内容",
                      "optionA": "选项A（单选/多选题必填）",
                      "optionB": "选项B",
                      "optionC": "选项C",
                      "optionD": "选项D",
                      "answer": "A",
                      "analysis": "解析说明"
                    }
                  ]
                }
                ```
                
                题型规则：
                - type=1: 单选题，answer为单个选项字母（A/B/C/D）
                - type=2: 多选题，answer为逗号分隔的选项字母（如"A,B,C"）
                - type=3: 判断题，answer为"A"（正确）或"B"（错误），optionA为"正确"，optionB为"错误"
                
                注意事项：
                1. 题目内容要有实际意义，不能是占位符
                2. 所有题目必须有明确唯一的正确答案
                3. 解析要简洁但有助于理解
                4. 只输出JSON，不要包含markdown代码块标记或其他文字
                """;
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(String prompt) {
        return "请根据以下要求生成考试题目：\n" + prompt +
                "\n\n请直接输出JSON，不要包含任何其他文字。";
    }

    /**
     * 解析AI生成的JSON试卷
     */
    private GenerateResult parseGeneratedPaper(String response, Long studentId) {
        try {
            // 尝试提取JSON（可能被markdown代码块包裹）
            String jsonStr = extractJson(response);

            JsonNode root = objectMapper.readTree(jsonStr);

            String title = root.path("title").asText("AI生成试卷");
            JsonNode questionsNode = root.path("questions");

            if (!questionsNode.isArray() || questionsNode.size() == 0) {
                return GenerateResult.fail("AI未能生成有效的题目，请重新尝试或调整要求");
            }

            List<Map<String, Object>> questions = new ArrayList<>();
            for (JsonNode qNode : questionsNode) {
                Map<String, Object> question = new HashMap<>();
                int type = qNode.path("type").asInt(1);
                question.put("type", type);
                question.put("content", qNode.path("content").asText(""));
                question.put("optionA", getTextOrDefault(qNode, "optionA", ""));
                question.put("optionB", getTextOrDefault(qNode, "optionB", ""));
                question.put("optionC", getTextOrDefault(qNode, "optionC", ""));
                question.put("optionD", getTextOrDefault(qNode, "optionD", ""));
                question.put("answer", qNode.path("answer").asText(""));
                question.put("analysis", getTextOrDefault(qNode, "analysis", "暂无解析"));
                question.put("score", 10);
                question.put("difficulty", 2);
                question.put("creatorId", studentId);

                // 基本校验
                if (question.get("content").toString().isEmpty()) {
                    continue; // 跳过空题目
                }

                questions.add(question);
            }

            if (questions.isEmpty()) {
                return GenerateResult.fail("解析题目失败，未获得有效题目");
            }

            // 结构校验：不合格的题必须剔除，不能带着错答案进试卷
            AiQuestionValidator.Report report = questionValidator.validate(questions);
            if (report.accepted().isEmpty()) {
                return GenerateResult.fail("AI 生成的题目均未通过校验（选项缺失/答案格式错误），请调整要求后重试");
            }

            log.info("成功解析AI生成试卷: title={}, 合格={}/{}, 警告={}",
                    title, report.accepted().size(), report.total(), report.warnings().size());
            return GenerateResult.success(title, report.accepted(), report);

        } catch (Exception e) {
            log.error("解析AI生成试卷JSON失败: {}", response.substring(0, Math.min(200, response.length())), e);
            return GenerateResult.fail("解析AI生成内容失败，请重新尝试");
        }
    }

    /**
     * 从可能的markdown代码块中提取JSON
     */
    private String extractJson(String text) {
        // 去掉 ```json 和 ``` 标记
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        // 去掉可能的 ```json 开头
        cleaned = cleaned.replaceFirst("^```json\\s*", "");
        cleaned = cleaned.replaceFirst("^```\\s*", "");
        cleaned = cleaned.replaceAll("\\s*```\\s*$", "");
        return cleaned.trim();
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultVal) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? defaultVal : f.asText(defaultVal);
    }

    /**
     * 生成结果封装
     */
    public static class GenerateResult {
        private boolean success;
        private String title;
        private List<Map<String, Object>> questions;
        private String error;

        /** 本次生成一共返回了多少题（含被剔除的） */
        private int generatedCount;
        /** 被结构校验剔除的题数 */
        private int rejectedCount;
        /** 剔除比例 */
        private double rejectRatio;
        /** 校验阶段的非致命问题 */
        private List<String> warnings = List.of();
        /** 重答比对后答案存疑的题目下标（0 基） */
        private List<Integer> suspectIndexes = List.of();

        public static GenerateResult success(String title,
                                             List<Map<String, Object>> questions,
                                             AiQuestionValidator.Report report) {
            GenerateResult r = new GenerateResult();
            r.success = true;
            r.title = title;
            r.questions = questions;
            r.generatedCount = report.total();
            r.rejectedCount = report.rejected().size();
            r.rejectRatio = report.rejectRatio();
            r.warnings = report.warnings();
            return r;
        }

        public static GenerateResult fail(String error) {
            GenerateResult r = new GenerateResult();
            r.success = false;
            r.error = error;
            return r;
        }

        /** 记录语义存疑的题目，并把可读提示追加到警告里 */
        public void markSuspects(Set<Integer> indexes) {
            if (indexes == null || indexes.isEmpty()) {
                return;
            }
            this.suspectIndexes = indexes.stream().sorted().toList();
            List<String> merged = new ArrayList<>(warnings);
            String positions = indexes.stream()
                    .sorted()
                    .map(i -> String.valueOf(i + 1))
                    .reduce((a, b) -> a + "、" + b)
                    .orElse("");
            merged.add("第 " + positions + " 题的答案在自检中未能复现，建议人工核验");
            this.warnings = List.copyOf(merged);
            log.warn("答案自检发现 {} 道题存疑：{}", indexes.size(), positions);
        }

        public boolean isSuccess() { return success; }
        public String getTitle() { return title; }
        public List<Map<String, Object>> getQuestions() { return questions; }
        public String getError() { return error; }
        public int getGeneratedCount() { return generatedCount; }
        public int getRejectedCount() { return rejectedCount; }
        public double getRejectRatio() { return rejectRatio; }
        public List<String> getWarnings() { return warnings; }
        public List<Integer> getSuspectIndexes() { return suspectIndexes; }
    }
}
