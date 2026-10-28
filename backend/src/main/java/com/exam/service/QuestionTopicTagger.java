package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.pojo.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 给单道题判定知识点。
 *
 * <p>只做一件事：把题目交给模型，让它在**给定词表**里挑一个。
 * 结果必须能对上词表，否则当作没判出来——宁可留空，也不写一个自创的名字，
 * 否则诊断汇总会被拆成一堆一次性的分类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionTopicTagger {

    private static final String SYSTEM_PROMPT = """
            你是题库分类助手。给定一道题目和一份固定的知识点词表，
            你需要判断这道题考查的是词表中的哪一项。

            输出要求：
            - 只输出 JSON：{"topic": "词表里的一项"}
            - topic 必须与词表中的某一项**完全一致**，不得改写、合并或自创
            - 如果这道题无法归到任何一项（例如只有占位符、内容残缺），输出 {"topic": ""}
            - 不要输出任何解释文字
            """;

    private final LlmRouter llmRouter;
    private final KnowledgeTopicCatalog catalog;

    /**
     * @return 词表里的知识点名称；无法判定时返回 null
     */
    public String suggest(Question question) {
        if (question == null || !StringUtils.hasText(question.getContent())) {
            return null;
        }
        if (!llmRouter.isEnabled()) {
            log.warn("AI 未启用，跳过知识点判定: questionId={}", question.getId());
            return null;
        }

        try {
            LlmRouter.LlmResult result = llmRouter.call(
                    LlmRouter.TaskLevel.LIGHT, SYSTEM_PROMPT, buildUserMessage(question));
            if (!result.isSuccess()) {
                log.warn("知识点判定失败: questionId={}, error={}", question.getId(), result.getError());
                return null;
            }
            return parseTopic(result.getContent());
        } catch (BusinessException e) {
            log.warn("知识点判定异常: questionId={}, error={}", question.getId(), e.getMessage());
            return null;
        }
    }

    private String buildUserMessage(Question question) {
        StringBuilder sb = new StringBuilder();
        sb.append("知识点词表：\n");
        List<String> topics = catalog.getTopics();
        for (int i = 0; i < topics.size(); i++) {
            sb.append(i + 1).append(". ").append(topics.get(i)).append('\n');
        }
        sb.append("\n题目：\n").append(question.getContent()).append('\n');
        appendOption(sb, "A", question.getOptionA());
        appendOption(sb, "B", question.getOptionB());
        appendOption(sb, "C", question.getOptionC());
        appendOption(sb, "D", question.getOptionD());
        if (StringUtils.hasText(question.getAnswer())) {
            sb.append("正确答案：").append(question.getAnswer()).append('\n');
        }
        if (StringUtils.hasText(question.getAnalysis())) {
            sb.append("解析：").append(question.getAnalysis()).append('\n');
        }
        sb.append("\n请输出 JSON。");
        return sb.toString();
    }

    private void appendOption(StringBuilder sb, String label, String text) {
        if (StringUtils.hasText(text)) {
            sb.append(label).append(". ").append(text).append('\n');
        }
    }

    /**
     * 从模型输出里提取知识点。
     *
     * 模型偶尔不守格式（包一层 markdown、直接吐一句话），所以依次尝试：
     * JSON → 完全匹配 → 词表中最长的包含项。最后一步仍要过词表校验，
     * 因此不会写出词表之外的名字。
     *
     * @return 词表里的名称；对不上返回 null
     */
    String parseTopic(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        String text = stripCodeFence(content).trim();

        // 1) 期望的 JSON 形式
        if (text.startsWith("{")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (end > start) {
                String json = text.substring(start, end + 1);
                if (json.contains("\"topic\"")) {
                    // 模型已经按约定回答了，就不再往下猜：它说「归不了类」就是归不了
                    String topic = extractJsonTopic(json);
                    return catalog.contains(topic) ? topic : null;
                }
            }
        }

        // 2) 整个回答就是词表里的一项
        if (catalog.contains(text)) {
            return text;
        }

        // 3) 回答里夹带了一项 —— 取最长匹配，避免「积分」命中「不定积分」
        String longest = null;
        for (String topic : catalog.getTopics()) {
            if (text.contains(topic) && (longest == null || topic.length() > longest.length())) {
                longest = topic;
            }
        }
        return longest;
    }

    private String stripCodeFence(String content) {
        String text = content.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String body = text.substring(firstNewline + 1);
        int fence = body.lastIndexOf("```");
        return fence >= 0 ? body.substring(0, fence) : body;
    }

    /** 用最朴素的方式取 topic 字段，避免为一个小场景引入 JSON 依赖上的分支 */
    private String extractJsonTopic(String json) {
        int keyIndex = json.indexOf("\"topic\"");
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        String value = json.substring(firstQuote + 1, secondQuote).trim();
        return value.isEmpty() ? null : value;
    }
}
