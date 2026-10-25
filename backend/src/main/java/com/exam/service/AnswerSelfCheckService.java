package com.exam.service;

import com.exam.service.LlmRouter.LlmResult;
import com.exam.service.LlmRouter.TaskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 答案自检：让模型在不看答案的情况下把题目重做一遍，与它先前声明的答案比对。
 *
 * <p>结构校验只能发现"格式错"，发现不了"题目本身有歧义"或"答案就是错的"。
 * 自检利用的是模型的自洽性——如果它对同一道题前后给出不同的答案，
 * 这题至少值得人工看一眼。这是成本最低、能覆盖语义错误的手段，
 * 但它<b>不是证明正确</b>，只是把可疑题目筛出来。
 *
 * <p>任何异常都不向上抛：自检失败绝不能影响出卷本身。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerSelfCheckService {

    private final LlmRouter llmRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 默认开启；模型未启用时会自动跳过 */
    @Value("${app.ai.self-check:true}")
    private boolean enabled;

    /**
     * 找出答案可疑的题目下标（0 基）。
     *
     * @param questions 已通过结构校验的题目
     * @return 可疑题目下标；自检不可用时返回空集合
     */
    public Set<Integer> findSuspectIndexes(List<Map<String, Object>> questions) {
        if (!enabled || questions == null || questions.isEmpty()) {
            return Set.of();
        }
        if (!llmRouter.isEnabled()) {
            return Set.of();
        }
        try {
            String systemPrompt = """
                    你正在做一场闭卷测验。你只能看到题目和选项，看不到标准答案。
                    请独立作答，不要解释、不要复述题目。
                    只输出一个 JSON 数组，元素按题目顺序排列，每个元素是该题的答案：
                    - 单选题：一个字母，如 "A"
                    - 多选题：逗号分隔的字母，如 "A,C"
                    - 判断题：A 表示正确，B 表示错误
                    示例：["A","B,C","A"]
                    """;

            String userMessage = buildQuestionsMessage(questions);

            Map<String, String> msg = new HashMap<>();
            msg.put("role", "user");
            msg.put("content", userMessage);

            // 用 LIGHT 级别：自检只需要判断题意，不需要长文本生成，成本要低
            LlmResult result = llmRouter.call(TaskLevel.LIGHT, List.of(msg), systemPrompt);
            if (!result.isSuccess()) {
                log.warn("答案自检跳过（模型调用失败）：{}", result.getError());
                return Set.of();
            }

            List<String> reanswers = parseAnswerArray(result.getContent(), questions.size());
            if (reanswers == null) {
                log.warn("答案自检跳过（返回格式无法对齐）：{}", abbreviate(result.getContent()));
                return Set.of();
            }

            Set<Integer> suspects = compareAnswers(questions, reanswers);
            log.info("答案自检完成：{} 道题中 {} 道答案存疑", questions.size(), suspects.size());
            return suspects;

        } catch (Exception e) {
            log.warn("答案自检异常，已忽略：{}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 只把题干与选项发给模型——绝不能带上 answer 字段，否则自检毫无意义。
     */
    private String buildQuestionsMessage(List<Map<String, Object>> questions) {
        StringBuilder sb = new StringBuilder("题目如下：\n");
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            int type = toInt(q.get("type"), 1);
            sb.append("\n[").append(i + 1).append("] ").append(str(q.get("content"))).append("\n");
            if (type == 3) {
                sb.append("题型：判断题（A=正确，B=错误）\n");
            } else {
                sb.append("题型：").append(type == 2 ? "多选题" : "单选题").append("\n");
                for (String key : List.of("optionA", "optionB", "optionC", "optionD")) {
                    String text = str(q.get(key)).trim();
                    if (!text.isEmpty()) {
                        sb.append(key.substring("option".length())).append(". ").append(text).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 解析模型返回的答案数组。
     *
     * @return 与题目数量一一对应的答案列表；数量对不上或无法解析时返回 null
     */
    List<String> parseAnswerArray(String content, int expectedCount) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            String json = extractJsonArray(content);
            if (json == null) return null;

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.size() != expectedCount) {
                return null;
            }
            List<String> answers = new ArrayList<>(expectedCount);
            for (JsonNode node : root) {
                if (node.isObject()) {
                    // 容错：模型可能返回 {"answer":"A"} 这种结构
                    answers.add(node.path("answer").asText(""));
                } else {
                    answers.add(node.asText(""));
                }
            }
            return answers;
        } catch (Exception e) {
            log.debug("解析自检答案失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 比对声明答案与重答结果，返回不一致的题目下标。
     *
     * <p>比较前统一归一化为「去重并排序后的字母集合」，因此 "b,a" 与 "A,B" 视为一致。
     * 判断题的 A/B 与其他题型同样按字母比较。
     */
    Set<Integer> compareAnswers(List<Map<String, Object>> questions, List<String> reanswers) {
        Set<Integer> suspects = new LinkedHashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            String declared = normalizeAnswer(str(questions.get(i).get("answer")));
            String reanswer = normalizeAnswer(reanswers.get(i));
            // 任一侧为空说明信息不足，不判为可疑
            if (declared.isEmpty() || reanswer.isEmpty()) {
                continue;
            }
            if (!declared.equals(reanswer)) {
                suspects.add(i);
            }
        }
        return suspects;
    }

    /** 把 "A,C" / "c，a" / "AC" 统一成 "A,C" 形式 */
    String normalizeAnswer(String raw) {
        if (raw == null) return "";
        Set<String> letters = new LinkedHashSet<>();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                letters.add(String.valueOf(c));
            }
        }
        List<String> sorted = new ArrayList<>(letters);
        sorted.sort(String::compareTo);
        return String.join(",", sorted);
    }

    /** 从可能被 markdown 包裹的文本中抠出最外层的 JSON 数组 */
    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String abbreviate(String text) {
        if (text == null) return "";
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }
}
