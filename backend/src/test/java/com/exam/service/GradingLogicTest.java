package com.exam.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判分规则测试。
 *
 * <p><b>这个文件的前身是一个「假测试」</b>：里面的 calculateRemainingMinutes、calcViolationScore
 * 等方法全部定义在测试类内部，断言的是测试自己抄的一份逻辑副本，对生产代码零覆盖。
 *
 * <p>现在测试对象换成了真实的生产类 {@link ScoringRules}——它由 {@code ExamService#autoGrade}
 * 与 {@code ExamService#calculateViolationScore} 直接调用，规则改坏了这里一定会红。
 *
 * <p>顺带覆盖了旧实现里三个真实缺陷，见各用例注释。
 */
class GradingLogicTest {

    private static final int FULL_SCORE = 10;

    private ScoringRules.GradeResult grade(int type, String correct, String student) {
        return ScoringRules.gradeObjective(type, correct, student, FULL_SCORE);
    }

    // ==================== 单选题 ====================

    @Nested
    class SingleChoiceGrading {

        @Test
        @DisplayName("答案一致得满分")
        void correctAnswerGetsFullScore() {
            ScoringRules.GradeResult r = grade(ScoringRules.TYPE_SINGLE, "B", "B");
            assertTrue(r.autoGraded());
            assertTrue(r.correct());
            assertEquals(FULL_SCORE, r.score());
        }

        @Test
        @DisplayName("答案不一致得零分")
        void wrongAnswerGetsZero() {
            ScoringRules.GradeResult r = grade(ScoringRules.TYPE_SINGLE, "B", "C");
            assertTrue(r.autoGraded());
            assertFalse(r.correct());
            assertEquals(0, r.score());
        }

        @Test
        @DisplayName("大小写与首尾空白不影响判分")
        void caseAndWhitespaceIgnored() {
            assertTrue(grade(ScoringRules.TYPE_SINGLE, "a", "  A ").correct());
        }

        @Test
        @DisplayName("脏数据容忍：答案带标点也能判对")
        void punctuationTolerated() {
            // 旧实现用字符串精准相等，"A、" 会被判错
            assertTrue(grade(ScoringRules.TYPE_SINGLE, "A", "A、").correct());
            assertTrue(grade(ScoringRules.TYPE_SINGLE, "A.", "A").correct());
        }

        @Test
        @DisplayName("单选作答多个选项判错")
        void multiLettersOnSingleQuestionIsWrong() {
            assertFalse(grade(ScoringRules.TYPE_SINGLE, "A", "A,B").correct());
            assertFalse(grade(ScoringRules.TYPE_SINGLE, "A,B", "A").correct());
        }

        @Test
        @DisplayName("空答案与 null 判错且不抛异常")
        void blankAnswerIsWrong() {
            assertFalse(grade(ScoringRules.TYPE_SINGLE, "A", "").correct());
            assertFalse(grade(ScoringRules.TYPE_SINGLE, "A", null).correct());
            assertFalse(grade(ScoringRules.TYPE_SINGLE, "   ", "A").correct());
            assertFalse(grade(ScoringRules.TYPE_SINGLE, null, "A").correct());
        }
    }

    // ==================== 多选题 ====================

    @Nested
    class MultiChoiceGrading {

        @Test
        @DisplayName("完全一致得满分")
        void exactMatchGetsFullScore() {
            ScoringRules.GradeResult r = grade(ScoringRules.TYPE_MULTI, "A,B", "A,B");
            assertTrue(r.correct());
            assertEquals(FULL_SCORE, r.score());
        }

        @Test
        @DisplayName("顺序不同不影响结果")
        void orderIndependent() {
            assertTrue(grade(ScoringRules.TYPE_MULTI, "A,B", "B,A").correct());
            assertTrue(grade(ScoringRules.TYPE_MULTI, "A,B,C", "C,A,B").correct());
        }

        @Test
        @DisplayName("带空格的答案也能判对（旧实现的缺陷）")
        void spacesBetweenLettersTolerated() {
            // 旧实现 split(",") 后逐项比较，"A, B" 得到 ["A"," B"]，" B" != "B" → 误判为错
            assertTrue(grade(ScoringRules.TYPE_MULTI, "A,B", "A, B").correct());
            assertTrue(grade(ScoringRules.TYPE_MULTI, "A, B", "B,A").correct());
            assertTrue(grade(ScoringRules.TYPE_MULTI, "A,B", "AB").correct());
        }

        @Test
        @DisplayName("漏选判错（保持全对才得分的原规则）")
        void missingOneOptionIsWrong() {
            ScoringRules.GradeResult r = grade(ScoringRules.TYPE_MULTI, "A,B", "A");
            assertFalse(r.correct());
            assertEquals(0, r.score());
        }

        @Test
        @DisplayName("多选判错")
        void extraOptionIsWrong() {
            assertFalse(grade(ScoringRules.TYPE_MULTI, "A,B", "A,B,C").correct());
        }

        @Test
        @DisplayName("重复字母按集合去重后比较")
        void duplicateLettersIgnored() {
            assertTrue(grade(ScoringRules.TYPE_MULTI, "A,B", "A,A,B").correct());
        }

        @Test
        @DisplayName("全选但与标准答案不符判错")
        void allOptionsSelectedIsWrongUnlessExpected() {
            assertFalse(grade(ScoringRules.TYPE_MULTI, "A,B", "A,B,C,D").correct());
        }
    }

    // ==================== 判断题 ====================

    @Nested
    class JudgeChoiceGrading {

        @Test
        @DisplayName("A 表示正确，答 A 得分")
        void answerATrue() {
            assertTrue(grade(ScoringRules.TYPE_JUDGE, "A", "A").correct());
        }

        @Test
        @DisplayName("答反了不得分")
        void oppositeIsWrong() {
            ScoringRules.GradeResult r = grade(ScoringRules.TYPE_JUDGE, "A", "B");
            assertFalse(r.correct());
            assertEquals(0, r.score());
        }

        @Test
        @DisplayName("判断题不允许多个答案")
        void multipleLettersIsWrong() {
            assertFalse(grade(ScoringRules.TYPE_JUDGE, "A", "A,B").correct());
        }
    }

    // ==================== 简答题 ====================

    @Nested
    class ShortAnswerGrading {

        @Test
        @DisplayName("简答题永不自动判分（即使配了参考答案）")
        void neverAutoGraded() {
            // 旧实现只在参考答案为 null 时跳过；一旦老师填了参考答案，
            // 就会拿学生答案做字符串精确比较，几乎必然判 0 分。
            ScoringRules.GradeResult r = grade(ScoringRules.TYPE_SHORT_ANSWER, "示例答案", "示例答案");
            assertFalse(r.autoGraded());
            assertFalse(r.correct());
            assertEquals(0, r.score());
        }

        @Test
        @DisplayName("简答题没有参考答案时同样不判分")
        void notAutoGradedWithoutReference() {
            assertFalse(grade(ScoringRules.TYPE_SHORT_ANSWER, null, "随便写点").autoGraded());
        }
    }

    // ==================== 违规扣分 ====================

    @Nested
    class ViolationScoring {

        @Test
        @DisplayName("各违规类型的基础分值符合约定")
        void baseScores() {
            assertEquals(5, ScoringRules.violationScore("switch_screen", 1));
            assertEquals(10, ScoringRules.violationScore("no_face", 1));
            assertEquals(15, ScoringRules.violationScore("multi_face", 1));
            assertEquals(3, ScoringRules.violationScore("look_away", 1));
            assertEquals(20, ScoringRules.violationScore("forbidden_item", 1));
            assertEquals(5, ScoringRules.violationScore("keyboard_violation", 1));
        }

        @Test
        @DisplayName("严重度线性放大")
        void severityScalesLinearly() {
            assertEquals(15, ScoringRules.violationScore("switch_screen", 3));
            assertEquals(30, ScoringRules.violationScore("no_face", 3));
        }

        @Test
        @DisplayName("替考嫌疑是固定分值，不随严重度放大")
        void impersonationIsFixed() {
            assertEquals(15, ScoringRules.violationScore("face_position_changed", 1));
            assertEquals(15, ScoringRules.violationScore("face_position_changed", 5));
        }

        @Test
        @DisplayName("未知类型与 null 走默认档位")
        void unknownTypeFallsBack() {
            assertEquals(6, ScoringRules.violationScore("unknown_type", 2));
            assertEquals(3, ScoringRules.violationScore(null, 1));
        }

        @Test
        @DisplayName("严重度为 0 或负数时按 1 处理，避免扣 0 分")
        void invalidSeverityClampedToOne() {
            assertEquals(5, ScoringRules.violationScore("switch_screen", 0));
            assertEquals(5, ScoringRules.violationScore("switch_screen", -3));
        }
    }

    // ==================== 答案解析 ====================

    @Nested
    class AnswerNormalization {

        @Test
        @DisplayName("兼容逗号、中文逗号、顿号、分号")
        void delimiterVariants() {
            assertEquals(2, ScoringRules.toLetterSet("A,B").size());
            assertEquals(2, ScoringRules.toLetterSet("A，B").size());
            assertEquals(2, ScoringRules.toLetterSet("A、B").size());
            assertEquals(2, ScoringRules.toLetterSet("A;B").size());
        }

        @Test
        @DisplayName("非字母字符被忽略")
        void nonLettersIgnored() {
            assertTrue(ScoringRules.toLetterSet("A").contains("A"));
            assertTrue(ScoringRules.toLetterSet("  A  ").contains("A"));
            assertTrue(ScoringRules.toLetterSet("1").isEmpty());
            assertTrue(ScoringRules.toLetterSet(null).isEmpty());
        }
    }
}
