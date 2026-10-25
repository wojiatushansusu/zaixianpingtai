package com.exam.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 题目结构校验的单元测试。
 *
 * <p>重点覆盖模型最常出错的地方：选项缺失/重复、答案字母非法、单选多答案、
 * 判断题答案不在 A/B 范围、题干占位符与题干重复。
 */
class AiQuestionValidatorTest {

    private final AiQuestionValidator validator = new AiQuestionValidator();

    // ──────────────────────────── 夹具 ────────────────────────────

    private Map<String, Object> single(String content, String answer,
                                       String a, String b, String c, String d) {
        Map<String, Object> q = new HashMap<>();
        q.put("type", 1);
        q.put("content", content);
        q.put("optionA", a);
        q.put("optionB", b);
        q.put("optionC", c);
        q.put("optionD", d);
        q.put("answer", answer);
        q.put("analysis", "这是解析");
        return q;
    }

    private Map<String, Object> multi(String content, String answer) {
        Map<String, Object> q = single(content, answer, "甲", "乙", "丙", "丁");
        q.put("type", 2);
        return q;
    }

    private Map<String, Object> judge(String content, String answer) {
        Map<String, Object> q = new HashMap<>();
        q.put("type", 3);
        q.put("content", content);
        q.put("optionA", "正确");
        q.put("optionB", "错误");
        q.put("answer", answer);
        q.put("analysis", "这是解析");
        return q;
    }

    // ──────────────────────────── 正常路径 ────────────────────────────

    @Test
    @DisplayName("结构完整的单选/多选/判断题全部通过")
    void validQuestionsPass() {
        List<Map<String, Object>> list = List.of(
                single("下列哪个是质数？", "B", "4", "7", "9", "15"),
                multi("以下属于面向对象特性的有哪些？", "A,B,C"),
                judge("太阳从东边升起。", "A")
        );

        AiQuestionValidator.Report report = validator.validate(list);

        assertEquals(3, report.accepted().size());
        assertEquals(0, report.rejected().size());
        assertEquals(0.0, report.rejectRatio());
    }

    @Test
    @DisplayName("多选题答案兼容 A,B / a，c / AB 等多种写法")
    void multiAnswerFormatsTolerated() {
        List<Map<String, Object>> list = List.of(
                multi("选出所有偶数。", "a，c"),
                multi("选出所有奇数。", "B、D"),
                multi("选出所有正整数。", "ABC")
        );
        AiQuestionValidator.Report report = validator.validate(list);
        assertEquals(3, report.accepted().size());
    }

    // ──────────────────────────── 结构错误必须剔除 ────────────────────────────

    @Test
    @DisplayName("缺少选项 → 剔除")
    void missingOptionRejected() {
        Map<String, Object> q = single("以下哪个是偶数？", "A", "2", "3", "", "5");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertEquals(1, report.rejected().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("缺少选项")));
    }

    @Test
    @DisplayName("选项内容重复 → 剔除")
    void duplicatedOptionRejected() {
        Map<String, Object> q = single("以下哪个是偶数？", "A", "2", "2", "3", "5");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("选项内容重复")));
    }

    @Test
    @DisplayName("单选题给了两个答案 → 剔除")
    void singleWithTwoAnswersRejected() {
        Map<String, Object> q = single("以下哪个是偶数？", "A,B", "2", "4", "3", "5");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("单选题只能有一个答案")));
    }

    @Test
    @DisplayName("多选题只给一个答案 → 剔除")
    void multiWithOneAnswerRejected() {
        Map<String, Object> q = multi("选出所有偶数。", "A");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("多选题至少需要两个答案")));
    }

    @Test
    @DisplayName("答案字母超出 A~D → 剔除")
    void answerOutOfRangeRejected() {
        Map<String, Object> q = single("以下哪个是偶数？", "E", "2", "3", "4", "5");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("不在 A~D 范围内")));
    }

    @Test
    @DisplayName("判断题答案不是 A/B → 剔除")
    void judgeWithIllegalAnswerRejected() {
        Map<String, Object> q = judge("地球是圆的。", "C");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("判断题答案必须是 A")));
    }

    @Test
    @DisplayName("答案为空的题目 → 剔除")
    void emptyAnswerRejected() {
        Map<String, Object> q = single("以下哪个是偶数？", "", "2", "3", "4", "5");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("答案为空")));
    }

    // ──────────────────────────── 题干质量 ────────────────────────────

    @Test
    @DisplayName("题干为占位符 → 剔除")
    void placeholderContentRejected() {
        Map<String, Object> q = single("题目内容", "A", "1", "2", "3", "4");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("占位符")));
    }

    @Test
    @DisplayName("题干过短 → 剔除")
    void tooShortContentRejected() {
        Map<String, Object> q = single("求值", "A", "1", "2", "3", "4");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(0, report.accepted().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("题干过短")));
    }

    @Test
    @DisplayName("卷内题干重复 → 只保留第一道")
    void duplicatedContentKeepsFirstOnly() {
        List<Map<String, Object>> list = List.of(
                single("下列哪个是质数？", "B", "4", "7", "9", "15"),
                single("下列哪个是质数？", "B", "4", "7", "9", "15")
        );
        AiQuestionValidator.Report report = validator.validate(list);

        assertEquals(1, report.accepted().size());
        assertEquals(1, report.rejected().size());
        assertTrue(report.rejected().get(0).reasons().stream()
                .anyMatch(r -> r.contains("题干与本卷中其他题目重复")));
    }

    @Test
    @DisplayName("题干仅空格/标点差异会被归一化，视为同一道题")
    void whitespaceDifferenceTreatedAsDuplicate() {
        List<Map<String, Object>> list = List.of(
                single("下列哪个是质数？", "B", "4", "7", "9", "15"),
                single("下列哪个 是质数？", "B", "4", "7", "9", "15")
        );
        AiQuestionValidator.Report report = validator.validate(list);
        // 去空白与标点后两者一致，属于同一道题被重复生成，应剔除后者
        assertEquals(1, report.accepted().size());
        assertEquals(1, report.rejected().size());
    }

    // ──────────────────────────── 警告不影响可用性 ────────────────────────────

    @Test
    @DisplayName("缺少解析只产生警告，题目仍然可用")
    void missingAnalysisOnlyWarns() {
        Map<String, Object> q = single("下列哪个是质数？", "B", "4", "7", "9", "15");
        q.put("analysis", "暂无解析");
        AiQuestionValidator.Report report = validator.validate(List.of(q));

        assertEquals(1, report.accepted().size());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("缺少解析")));
    }

    @Test
    @DisplayName("整卷答案全为 A → 给出分布异常警告")
    void allSameAnswerWarns() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(single("这是第" + i + "道关于质数的题目？", "A", "7", "4", "9", "15"));
        }
        AiQuestionValidator.Report report = validator.validate(list);

        assertEquals(5, report.accepted().size());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("疑似未实际解题")));
    }

    @Test
    @DisplayName("空题目列表不抛异常")
    void emptyInputHandled() {
        AiQuestionValidator.Report report = validator.validate(List.of());
        assertEquals(0, report.total());
        assertFalse(report.warnings().isEmpty());
    }

    // ──────────────────────────── 答案解析 ────────────────────────────

    @Test
    @DisplayName("答案解析兼容多种分隔符并去重")
    void parseAnswerLettersHandlesDelimiters() {
        assertEquals(List.of("A"), validator.parseAnswerLetters("a"));
        assertEquals(List.of("A", "B"), validator.parseAnswerLetters("a,b"));
        assertEquals(List.of("A", "C"), validator.parseAnswerLetters("A，C"));
        assertEquals(List.of("A", "B"), validator.parseAnswerLetters("A、B"));
        assertEquals(List.of("A", "B"), validator.parseAnswerLetters("AB"));
        assertEquals(List.of("A"), validator.parseAnswerLetters("A,A"));
        assertTrue(validator.parseAnswerLetters(null).isEmpty());
        assertTrue(validator.parseAnswerLetters("  ").isEmpty());
    }
}
