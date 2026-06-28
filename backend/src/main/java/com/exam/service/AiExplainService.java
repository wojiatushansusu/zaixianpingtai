package com.exam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI错题讲解服务
 * 通过 LlmRouter 调用LLM，错题讲解用轻量级模型，多轮对话用标准级模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExplainService {

    private final LlmRouter llmRouter;

    public boolean isEnabled() {
        return llmRouter.isEnabled();
    }

    public String getProviderName() {
        return "LLM Router (自动选择模型)";
    }

    /**
     * 生成错题讲解（轻量级模型，快速响应）
     */
    public String generateExplanation(Map<String, Object> questionData) {
        if (!isEnabled()) {
            return null;
        }

        try {
            String prompt = buildPrompt(questionData);
            String systemPrompt = "你是一位经验丰富的考试辅导老师，擅长用简洁明了的语言帮助学生理解知识。";

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            LlmRouter.LlmResult result = llmRouter.call(LlmRouter.TaskLevel.LIGHT,
                    List.of(userMsg), systemPrompt);

            return result.isSuccess() ? result.getContent() : null;
        } catch (Exception e) {
            log.error("AI讲解生成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * AI自由对话（标准级模型，支持多轮上下文）
     * @param messages 对话历史 [{"role":"user","content":"..."},{"role":"assistant","content":"..."},...]
     * @param systemPrompt 系统提示词（可选）
     */
    public String chat(List<Map<String, String>> messages, String systemPrompt) {
        if (!isEnabled()) {
            return null;
        }

        try {
            LlmRouter.LlmResult result = llmRouter.call(LlmRouter.TaskLevel.STANDARD,
                    messages, systemPrompt);

            return result.isSuccess() ? result.getContent() : null;
        } catch (Exception e) {
            log.error("AI对话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建讲解Prompt
     */
    private String buildPrompt(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位经验丰富的考试辅导老师，请针对学生的错题进行详细讲解。\n\n");
        sb.append("## 题目信息\n");
        sb.append("- 题型：").append(data.getOrDefault("questionTypeDesc", "未知")).append("\n");
        sb.append("- 题目：").append(data.getOrDefault("content", "")).append("\n");

        // 选项
        List<String> options = (List<String>) data.getOrDefault("options", List.of());
        if (!options.isEmpty()) {
            sb.append("- 选项：\n");
            for (String opt : options) {
                sb.append("  ").append(opt).append("\n");
            }
        }

        sb.append("- 正确答案：").append(data.getOrDefault("correctAnswer", "")).append("\n");
        sb.append("- 学生答案：").append(data.getOrDefault("studentAnswer", "未作答")).append("\n");

        // 已有解析
        String analysis = (String) data.get("analysis");
        if (analysis != null && !analysis.isEmpty()) {
            sb.append("- 参考解析：").append(analysis).append("\n");
        }

        sb.append("\n## 要求\n");
        sb.append("请按以下结构进行讲解（200-400字）：\n");
        sb.append("1. **错误分析**：简要分析学生可能出错的原因\n");
        sb.append("2. **知识点讲解**：讲解该题涉及的核心知识点\n");
        sb.append("3. **解题思路**：提供清晰的解题步骤和方法\n");
        sb.append("4. **易错提醒**：指出该类题目的常见陷阱\n");
        sb.append("\n请用简洁明了的语言，帮助学生理解这道题。");

        return sb.toString();
    }
}
