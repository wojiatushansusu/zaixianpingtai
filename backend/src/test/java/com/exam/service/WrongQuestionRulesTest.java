package com.exam.service;

import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错题本收录规则测试。
 *
 * 背景：旧判定 `is_correct != 1` 让批了 7/10 的简答题永远躺在错题本里。
 */
class WrongQuestionRulesTest {

    private static final double T = WrongQuestionRules.DEFAULT_WEAK_THRESHOLD; // 0.6

    private static AnswerRecord answer(Integer isCorrect, Integer score,
                                       Integer teacherScore, Integer questionScore) {
        AnswerRecord a = new AnswerRecord();
        a.setIsCorrect(isCorrect);
        a.setScore(score);
        a.setTeacherScore(teacherScore);
        a.setQuestionScore(questionScore);
        return a;
    }

    private static Question q(int type, Integer score) {
        Question q = new Question();
        q.setType(type);
        q.setScore(score);
        return q;
    }

    // ==================== 客观题 ====================

    @Test
    @DisplayName("单选答对不进错题本")
    void correctSingleIsNotWrong() {
        assertFalse(WrongQuestionRules.countsAsWrong(answer(1, 5, null, 5),
                q(ScoringRules.TYPE_SINGLE, 5), T));
    }

    @Test
    @DisplayName("单选答错进错题本")
    void wrongSingleIsWrong() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(0, 0, null, 5),
                q(ScoringRules.TYPE_SINGLE, 5), T));
    }

    @Test
    @DisplayName("isCorrect 为空按未判对处理")
    void nullCorrectCountsAsWrong() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(null, 0, null, 5),
                q(ScoringRules.TYPE_JUDGE, 5), T));
    }

    // ==================== 主观题：按得分率 ====================

    @Test
    @DisplayName("简答批了 7/10（70%）不进错题本")
    void essayAboveThresholdIsNotWrong() {
        assertFalse(WrongQuestionRules.countsAsWrong(answer(0, 0, 7, 10),
                q(ScoringRules.TYPE_SHORT_ANSWER, 10), T));
    }

    @Test
    @DisplayName("简答批了 3/10（30%）进错题本")
    void essayBelowThresholdIsWrong() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(0, 0, 3, 10),
                q(ScoringRules.TYPE_SHORT_ANSWER, 10), T));
    }

    @Test
    @DisplayName("恰好等于阈值不进（70% 边界）")
    void exactlyAtThresholdIsNotWrong() {
        AnswerRecord a = answer(0, 0, 6, 10); // 0.6，不小于阈值
        assertFalse(WrongQuestionRules.countsAsWrong(a, q(ScoringRules.TYPE_SHORT_ANSWER, 10), T));
    }

    @Test
    @DisplayName("未批改的简答进错题本，等老师给分")
    void ungradedEssayIsWrong() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(0, 0, null, 10),
                q(ScoringRules.TYPE_SHORT_ANSWER, 10), T));
    }

    @Test
    @DisplayName("满分未知时保守保留")
    void unknownFullScoreIsWrong() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(0, 0, 5, null),
                q(ScoringRules.TYPE_SHORT_ANSWER, null), T));
    }

    @Test
    @DisplayName("题目已删除时保留")
    void missingQuestionIsWrong() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(0, 0, null, null), null, T));
    }

    // ==================== 阈值可调 ====================

    @Test
    @DisplayName("阈值设 1.0 等价于「不满分就算错」——旧行为的近似")
    void thresholdOneRestoresOldBehavior() {
        assertTrue(WrongQuestionRules.countsAsWrong(answer(0, 0, 9, 10),
                q(ScoringRules.TYPE_SHORT_ANSWER, 10), 1.0));
        assertFalse(WrongQuestionRules.countsAsWrong(answer(0, 0, 10, 10),
                q(ScoringRules.TYPE_SHORT_ANSWER, 10), 1.0));
    }

    @Test
    @DisplayName("阈值设 0 时已批改的主观题一律不进")
    void thresholdZeroExcludesGradedEssays() {
        assertFalse(WrongQuestionRules.countsAsWrong(answer(0, 0, 1, 10),
                q(ScoringRules.TYPE_SHORT_ANSWER, 10), 0));
    }

    @Test
    @DisplayName("满分优先取答题记录里的快照（试卷分值）")
    void prefersPaperScoreSnapshot() {
        // 题目自身 5 分，但试卷给了 20 分；7 分只占 35%
        AnswerRecord a = answer(0, 0, 7, 20);
        assertTrue(WrongQuestionRules.countsAsWrong(a, q(ScoringRules.TYPE_SHORT_ANSWER, 5), T));
        // 若误用题目自身 5 分，7/5 = 140% 会被判为掌握
    }
}
