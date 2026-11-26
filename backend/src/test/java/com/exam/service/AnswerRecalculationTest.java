package com.exam.service;

import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史答卷重算的决策测试。
 *
 * 这段逻辑会改写**学生已出的成绩**，属于不可逆操作，必须测住。
 */
class AnswerRecalculationTest {

    private static AnswerRecord answer(long id, long questionId, String studentAnswer,
                                       Integer score, Integer isCorrect, Integer questionScore) {
        AnswerRecord a = new AnswerRecord();
        a.setId(id);
        a.setQuestionId(questionId);
        a.setStudentAnswer(studentAnswer);
        a.setScore(score);
        a.setIsCorrect(isCorrect);
        a.setQuestionScore(questionScore);
        return a;
    }

    private static Question question(long id, int type, String answer, Integer score) {
        Question q = new Question();
        q.setId(id);
        q.setType(type);
        q.setAnswer(answer);
        q.setScore(score);
        return q;
    }

    @Test
    @DisplayName("判断题从 0 分纠正为满分——这正是重算要解决的问题")
    void recoversJudgeScore() {
        AnswerRecord a = answer(1L, 6L, "true", 0, 0, 5);
        Question q = question(6L, ScoringRules.TYPE_JUDGE, "true", 5);

        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(List.of(a), Map.of(6L, q));

        assertEquals(5, plan.newTotal(), "判断题答对应该得 5 分");
        assertEquals(1, plan.changes().size());
        AnswerRecalculation.Change change = plan.changes().get(0);
        assertEquals(0, change.oldScore());
        assertEquals(5, change.newScore());
        assertEquals(0, change.oldCorrect());
        assertEquals(1, change.newCorrect());
    }

    @Test
    @DisplayName("本来判对的题不产生变更")
    void noChangeForCorrectAnswers() {
        AnswerRecord a = answer(1L, 11L, "B", 5, 1, 5);
        Question q = question(11L, ScoringRules.TYPE_SINGLE, "B", 5);

        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(List.of(a), Map.of(11L, q));

        assertEquals(5, plan.newTotal());
        assertTrue(plan.changes().isEmpty(), "分数和正确性都没变，不该写库");
        assertFalse(plan.totalChanged(5));
    }

    @Test
    @DisplayName("主观题保留教师给分，不参与重算")
    void keepsTeacherScoreForShortAnswer() {
        AnswerRecord a = answer(1L, 20L, "我的作答", 7, 1, 10);
        Question q = question(20L, ScoringRules.TYPE_SHORT_ANSWER, "参考答案", 10);

        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(List.of(a), Map.of(20L, q));

        assertEquals(7, plan.newTotal(), "人工给的 7 分不能被重算抹掉");
        assertTrue(plan.changes().isEmpty());
        assertEquals(1, plan.skippedAnswers());
    }

    @Test
    @DisplayName("题目被删除时保持原分，不归零")
    void keepsScoreWhenQuestionMissing() {
        AnswerRecord a = answer(1L, 999L, "A", 5, 1, 5);

        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(List.of(a), Map.of());

        assertEquals(5, plan.newTotal());
        assertTrue(plan.changes().isEmpty());
        assertEquals(1, plan.skippedAnswers());
    }

    @Test
    @DisplayName("整份答卷的合计：判断修复后总分从 15 变 20")
    void totalAcrossQuestions() {
        List<AnswerRecord> answers = List.of(
                answer(1L, 11L, "B", 5, 1, 5),
                answer(2L, 4L, "A,C", 0, 0, 10),
                answer(3L, 6L, "true", 0, 0, 5)
        );
        Map<Long, Question> questions = new HashMap<>();
        questions.put(11L, question(11L, ScoringRules.TYPE_SINGLE, "B", 5));
        questions.put(4L, question(4L, ScoringRules.TYPE_MULTI, "A,C,D", 10));
        questions.put(6L, question(6L, ScoringRules.TYPE_JUDGE, "true", 5));

        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(answers, questions);

        assertEquals(10, plan.newTotal(), "单选 5 + 多选 0（缺 D）+ 判断 5");
        assertEquals(1, plan.changes().size(), "只有判断题需要改");
        assertEquals(0, plan.changes().get(0).oldScore());
        assertEquals(5, plan.changes().get(0).newScore());
    }

    @Test
    @DisplayName("questionScore 缺失时退回题目自身分值")
    void fallsBackToQuestionScore() {
        AnswerRecord a = answer(1L, 6L, "true", 0, 0, null);
        Question q = question(6L, ScoringRules.TYPE_JUDGE, "true", 3);

        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(List.of(a), Map.of(6L, q));

        assertEquals(3, plan.newTotal(), "用题目自身 3 分");
    }

    @Test
    @DisplayName("空答卷不会崩，总分为 0")
    void emptyAnswers() {
        AnswerRecalculation.Plan plan = AnswerRecalculation.plan(List.of(), Map.of());

        assertEquals(0, plan.newTotal());
        assertTrue(plan.changes().isEmpty());
    }
}
