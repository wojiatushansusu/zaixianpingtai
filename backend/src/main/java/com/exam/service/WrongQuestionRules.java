package com.exam.service;

import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Question;

/**
 * 错题本收录规则（纯函数）。
 *
 * <p>原来的判定是 `is_correct != 1`，对客观题正确，对主观题有问题：
 * 人工批改不会把 `is_correct` 置 1，于是**只要不满分就永远算错题**。
 * 实测简答题批了 7/10、15/20，仍然出现在错题本里——学生明明答得不差。
 *
 * <p>现在的口径：**看得分率，不只看对错**。
 * 客观题本来就是二元的（0 或满分），照旧；
 * 主观题按 `teacherScore / 满分` 与阈值比较，低于阈值才进错题本。
 *
 * <p>这样 7/10（70%）算掌握，3/10（30%）进错题本——既保住复习价值，
 * 又不会让学生觉得"我答对了一半还被判错"。
 */
public final class WrongQuestionRules {

    /** 主观题默认阈值：得分率低于 60% 视为未掌握 */
    public static final double DEFAULT_WEAK_THRESHOLD = 0.6;

    private WrongQuestionRules() {
    }

    /**
     * 这条作答是否应计入错题本。
     *
     * @param answer         答题记录
     * @param question       对应题目，可能为 null（题目已删除）
     * @param weakThreshold  主观题阈值，0~1；传 1.0 等价于"不满分就算错"（旧行为）
     */
    public static boolean countsAsWrong(AnswerRecord answer, Question question, double weakThreshold) {
        if (answer == null) {
            return false;
        }
        // 题目被删了：信息不足以判断，保守保留（学生仍需要复习这道题）
        if (question == null) {
            return true;
        }

        Integer type = question.getType();
        if (type == null || type != ScoringRules.TYPE_SHORT_ANSWER) {
            // 客观题：正确性就是唯一标准
            Integer correct = answer.getIsCorrect();
            return correct == null || correct != 1;
        }

        // 主观题：未批改视为未得分，进错题本等老师批
        Integer teacherScore = answer.getTeacherScore();
        if (teacherScore == null) {
            return true;
        }

        int fullScore = resolveFullScore(answer, question);
        if (fullScore <= 0) {
            // 满分未知，无法算得分率，保守保留
            return true;
        }

        double rate = (double) teacherScore / fullScore;
        return rate < clamp(weakThreshold);
    }

    /**
     * 这道题的满分：优先用答题记录里的快照（试卷分值），再退回题目自身分值。
     */
    public static int resolveFullScore(AnswerRecord answer, Question question) {
        if (answer.getQuestionScore() != null && answer.getQuestionScore() > 0) {
            return answer.getQuestionScore();
        }
        if (question != null && question.getScore() != null && question.getScore() > 0) {
            return question.getScore();
        }
        return 0;
    }

    private static double clamp(double threshold) {
        if (threshold <= 0) {
            return 0;
        }
        return Math.min(threshold, 1.0);
    }
}
