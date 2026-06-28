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

    private final LlmRouter llmRouter;

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

        try {
            String systemPrompt = buildSystemPrompt();
            String userMessage = buildUserMessage(prompt);

            // 构建请求消息
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
            log.info("AI生成试卷完成: model={}, elapsed={}ms, contentLen={}",
                    result.getUsedModel(), result.getElapsedMs(), response.length());

            // 解析JSON响应
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

            log.info("成功解析AI生成试卷: title={}, questions={}", title, questions.size());
            return GenerateResult.success(title, questions);

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

        public static GenerateResult success(String title, List<Map<String, Object>> questions) {
            GenerateResult r = new GenerateResult();
            r.success = true;
            r.title = title;
            r.questions = questions;
            return r;
        }

        public static GenerateResult fail(String error) {
            GenerateResult r = new GenerateResult();
            r.success = false;
            r.error = error;
            return r;
        }

        public boolean isSuccess() { return success; }
        public String getTitle() { return title; }
        public List<Map<String, Object>> getQuestions() { return questions; }
        public String getError() { return error; }
    }
}
