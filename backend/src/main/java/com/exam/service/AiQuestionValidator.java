package com.exam.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 生成题目的确定性校验器。
 *
 * <p>大模型返回的题目结构上"看起来对"，但经常出现：选项缺失或重复、答案字母指向不存在的选项、
 * 单选题给了多个答案、判断题答案不是 A/B、题干是一句占位符。这类问题一旦进入试卷，
 * 学生就会照着错误答案背——这比任何功能缺陷都严重。
 *
 * <p>本类只做<b>不需要调用模型</b>的结构校验，成本为零、结果确定、可单测。
 * 语义层面「答案是否正确」由 {@link AnswerSelfCheckService} 用重答比对来处理。
 *
 * <p>所有 fail 一律按 error/warn 两级输出：
 * <ul>
 *   <li>error —— 结构不合法，必须剔除，否则试卷不可用</li>
 *   <li>warn  —— 可疑但不致命，保留并记录，交给使用者判断</li>
 * </ul>
 */
@Slf4j
@Component
public class AiQuestionValidator {

    /** 单选题 */
    private static final int TYPE_SINGLE = 1;
    /** 多选题 */
    private static final int TYPE_MULTI = 2;
    /** 判断题 */
    private static final int TYPE_JUDGE = 3;

    /** 题干最短长度，低于此值基本可以认定是占位符 */
    private static final int MIN_CONTENT_LENGTH = 6;

    /** 选项字母表：当前只支持四选一 */
    private static final List<String> OPTION_KEYS = List.of("optionA", "optionB", "optionC", "optionD");

    /** 模型偷懒时常见的占位符痕迹 */
    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "题目内容", "选项A（", "选项B（", "待补充", "TODO", "xxx", "XXX", "略", "占位"
    );

    private static final String DEFAULT_ANALYSIS = "暂无解析";

    /**
     * 执行校验。
     *
     * @param questions 待校验题目，结构见 AiPaperGeneratorService 中构建的 Map
     * @return 校验报告，包含可用题目、被剔除题目及原因、以及警告
     */
    public Report validate(List<Map<String, Object>> questions) {
        List<Map<String, Object>> accepted = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (questions == null || questions.isEmpty()) {
            return new Report(List.of(), List.of(), List.of("未提供任何题目"), 0);
        }

        Set<String> seenContents = new HashSet<>();
        Set<String> answers = new LinkedHashSet<>();

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            List<String> errors = new ArrayList<>();

            String content = asText(q.get("content"));
            int type = asInt(q.get("type"), TYPE_SINGLE);

            // ── 题干 ──
            if (content.isBlank()) {
                errors.add("题干为空");
            } else if (content.length() < MIN_CONTENT_LENGTH) {
                errors.add("题干过短（" + content.length() + " 字），疑似占位符");
            } else if (containsPlaceholder(content)) {
                errors.add("题干包含占位符痕迹");
            }

            String contentKey = normalizeForCompare(content);
            if (!contentKey.isEmpty() && !seenContents.add(contentKey)) {
                errors.add("题干与本卷中其他题目重复");
            }

            // ── 题型专属 ──
            if (type == TYPE_SINGLE || type == TYPE_MULTI) {
                validateChoice(q, type, errors, warnings, i);
            } else if (type == TYPE_JUDGE) {
                validateJudge(q, errors, warnings, i);
            } else if (type != 4) {
                errors.add("未知题型：" + type);
            }

            // 解析缺失只算警告，不影响作答
            String analysis = asText(q.get("analysis")).trim();
            if (analysis.isEmpty() || analysis.equals(DEFAULT_ANALYSIS)) {
                warnings.add("第 " + (i + 1) + " 题缺少解析");
            }

            if (errors.isEmpty()) {
                accepted.add(q);
                String ans = asText(q.get("answer")).trim().toUpperCase(Locale.ROOT);
                if (!ans.isEmpty()) {
                    answers.add(ans);
                }
            } else {
                rejected.add(new Rejected(i + 1, abbreviate(content), errors));
            }
        }

        // 答案分布异常：整卷答案全一样，通常意味着模型没在真的解题
        if (accepted.size() >= 5 && answers.size() == 1) {
            warnings.add("整卷答案全部为 " + answers.iterator().next() + "，疑似未实际解题，建议人工核验");
        }

        if (!rejected.isEmpty()) {
            log.warn("AI 题目校验：共 {} 题，剔除 {} 题", questions.size(), rejected.size());
        }
        return new Report(accepted, rejected, warnings, questions.size());
    }

    /**
     * 选择题校验：选项必须齐全、互不重复，答案字母必须指向存在的选项，且数量与题型匹配。
     */
    private void validateChoice(Map<String, Object> q, int type,
                                List<String> errors, List<String> warnings, int index) {
        List<String> optionTexts = new ArrayList<>();
        for (String key : OPTION_KEYS) {
            String text = asText(q.get(key)).trim();
            if (text.isEmpty()) {
                errors.add("缺少选项 " + key.substring("option".length()));
            }
            optionTexts.add(normalizeForCompare(text));
        }

        // 选项内容重复（去空白后比较）
        Set<String> distinct = new HashSet<>();
        for (int i = 0; i < optionTexts.size(); i++) {
            String t = optionTexts.get(i);
            if (t.isEmpty()) continue;
            if (!distinct.add(t)) {
                errors.add("选项内容重复（第 " + (i + 1) + " 项）");
                break;
            }
        }

        List<String> answerLetters = parseAnswerLetters(q.get("answer"));
        if (answerLetters.isEmpty()) {
            errors.add("答案为空或格式非法");
            return;
        }
        for (String letter : answerLetters) {
            if (!OPTION_KEYS.contains("option" + letter)) {
                errors.add("答案 " + letter + " 不在 A~D 范围内");
            }
        }
        if (type == TYPE_SINGLE && answerLetters.size() != 1) {
            errors.add("单选题只能有一个答案，实际为 " + answerLetters.size() + " 个");
        }
        if (type == TYPE_MULTI && answerLetters.size() < 2) {
            errors.add("多选题至少需要两个答案，实际为 " + answerLetters.size() + " 个");
        }
        if (type == TYPE_MULTI && answerLetters.size() == 4) {
            warnings.add("第 " + (index + 1) + " 题四个选项全选，建议确认是否为设计如此");
        }
    }

    /**
     * 判断题校验：答案只能是对应"正确/错误"的 A 或 B。
     */
    private void validateJudge(Map<String, Object> q,
                               List<String> errors, List<String> warnings, int index) {
        List<String> letters = parseAnswerLetters(q.get("answer"));
        if (letters.isEmpty()) {
            errors.add("判断题答案为空");
            return;
        }
        if (letters.size() != 1) {
            errors.add("判断题只能有一个答案，实际为 " + letters.size() + " 个");
            return;
        }
        String letter = letters.get(0);
        if (!"A".equals(letter) && !"B".equals(letter)) {
            errors.add("判断题答案必须是 A（正确）或 B（错误），实际为 " + letter);
            return;
        }
        String optA = normalizeForCompare(asText(q.get("optionA")));
        String optB = normalizeForCompare(asText(q.get("optionB")));
        if (!optA.isEmpty() && !optA.equals("正确")) {
            warnings.add("第 " + (index + 1) + " 题判断题的 A 选项不是「正确」，答案语义可能错位");
        }
        if (!optB.isEmpty() && !optB.equals("错误")) {
            warnings.add("第 " + (index + 1) + " 题判断题的 B 选项不是「错误」，答案语义可能错位");
        }
    }

    /**
     * 把答案文本解析成选项字母集合。
     * 兼容 "A"、"a"、"A,B"、"A，B"、"AB"、"A、B" 等模型常见的多种写法。
     */
    List<String> parseAnswerLetters(Object raw) {
        String text = asText(raw).trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> letters = new ArrayList<>();
        for (char c : text.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                String s = String.valueOf(c);
                if (!letters.contains(s)) {
                    letters.add(s);
                }
            }
        }
        return letters;
    }

    private boolean containsPlaceholder(String content) {
        for (String marker : PLACEHOLDER_MARKERS) {
            if (content.contains(marker)) return true;
        }
        return false;
    }

    /** 去掉空白与常见标点后用于比较，避免"换行不同"被当成两道题 */
    private String normalizeForCompare(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\s\\p{Punct}，。、；：？！（）【】「」《》]", "");
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String abbreviate(String text) {
        return text.length() <= 30 ? text : text.substring(0, 30) + "…";
    }

    // ──────────────────────────── 结果类型 ────────────────────────────

    /** 被剔除的题目及原因 */
    public record Rejected(int index, String content, List<String> reasons) {
    }

    /** 校验报告 */
    public record Report(List<Map<String, Object>> accepted,
                         List<Rejected> rejected,
                         List<String> warnings,
                         int total) {

        /** 剔除比例，用于决定是否需要重试 */
        public double rejectRatio() {
            return total == 0 ? 0 : (double) rejected.size() / total;
        }

        /** 转成可直接放进接口响应的结构，便于前端展示 */
        public Map<String, Object> toSummary() {
            Map<String, Object> summary = new HashMap<>();
            summary.put("total", total);
            summary.put("accepted", accepted.size());
            summary.put("rejected", rejected.size());
            summary.put("warnings", warnings);
            List<Map<String, Object>> details = new ArrayList<>();
            for (Rejected r : rejected) {
                Map<String, Object> item = new HashMap<>();
                item.put("index", r.index());
                item.put("content", r.content());
                item.put("reasons", r.reasons());
                details.add(item);
            }
            summary.put("rejectedDetails", details);
            return summary;
        }
    }
}
