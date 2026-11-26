package com.exam.service;

import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 历史答卷的重算决策（纯函数，无数据库依赖）。
 *
 * <p>从 {@code ScoreRecalculationService} 里抽出来的原因和当初抽 {@code ScoringRules} 一样：
 * 混着取数、落库、改分之后，这段逻辑没法单独验证。而它改的是**学生成绩**，
 * 恰恰是最该被测住的部分。
 *
 * <p>这里只回答一个问题：给定一批答题记录和对应的题目，按当前规则重算，
 * 每道题的分该变成多少、总分该是多少。落不落库由调用方决定。
 */
public final class AnswerRecalculation {

    private AnswerRecalculation() {
    }

    /**
     * 单条答题记录的变更。
     *
     * @param answerId   答题记录 id
     * @param oldScore   原分
     * @param newScore   重算后应得分
     * @param oldCorrect 原先是否正确（1/0）
     * @param newCorrect 重算后是否正确（1/0）
     */
    public record Change(Long answerId, int oldScore, int newScore,
                         int oldCorrect, int newCorrect) {
        public boolean different() {
            return oldScore != newScore || oldCorrect != newCorrect;
        }
    }

    /**
     * 一份答卷的重算结果。
     *
     * @param newTotal       重算后的总分
     * @param changes        需要改写的记录（已过滤掉没有变化的）
     * @param skippedAnswers 因题目缺失或主观题而跳过的条数（跳过的是"判分"，分数仍计入总分）
     */
    public record Plan(int newTotal, List<Change> changes, int skippedAnswers) {
        public boolean totalChanged(int oldTotal) {
            return newTotal != oldTotal;
        }
    }

    public static Plan plan(List<AnswerRecord> answers, Map<Long, Question> questions) {
        List<Change> changes = new ArrayList<>();
        int total = 0;
        int skipped = 0;

        for (AnswerRecord answer : answers) {
            int oldScore = nz(answer.getScore());
            int oldCorrect = nz(answer.getIsCorrect());
            Question question = questions.get(answer.getQuestionId());

            if (question == null) {
                // 题目被删了：保持原分，不去猜
                total += oldScore;
                skipped++;
                continue;
            }

            int type = question.getType() != null ? question.getType() : ScoringRules.TYPE_SINGLE;
            if (type == ScoringRules.TYPE_SHORT_ANSWER) {
                // 主观题不重算，但要**用教师给的分**参与合计。
                // 人工批改只写 teacherScore，不写 score（score 仍是自动判分的 0），
                // 这里若按 oldScore 累加，会把老师给的 7 分算成 0，
                // 落库时就会把 17 分改成 10 分——把批改结果抹掉。
                total += answer.getTeacherScore() != null ? answer.getTeacherScore() : oldScore;
                skipped++;
                continue;
            }

            int fullScore = answer.getQuestionScore() != null
                    ? answer.getQuestionScore()
                    : (question.getScore() != null ? question.getScore() : 0);

            ScoringRules.GradeResult result = ScoringRules.gradeObjective(
                    type, question.getAnswer(), answer.getStudentAnswer(), fullScore);

            int newScore = result.score();
            int newCorrect = result.correct() ? 1 : 0;
            total += newScore;

            Change change = new Change(answer.getId(), oldScore, newScore, oldCorrect, newCorrect);
            if (change.different()) {
                changes.add(change);
            }
        }

        return new Plan(total, changes, skipped);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
