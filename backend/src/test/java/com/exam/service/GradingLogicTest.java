package com.exam.service;

import com.exam.pojo.Question;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 纯Java工具类测试（无需Spring容器）
 * 覆盖评分逻辑、答案比较等核心算法
 */
class GradingLogicTest {

    // ==================== 单选题评分测试 ====================

    @Nested
    class SingleChoiceGrading {
        @Test
        void correctAnswerShouldScore() {
            assertTrue(isSingleChoiceCorrect("A", "A"));
            assertTrue(isSingleChoiceCorrect("B", "B"));
            assertTrue(isSingleChoiceCorrect("C", "C"));
            assertTrue(isSingleChoiceCorrect("D", "D"));
        }

        @Test
        void wrongAnswerShouldNotScore() {
            assertFalse(isSingleChoiceCorrect("A", "B"));
            assertFalse(isSingleChoiceCorrect("B", "A"));
            assertFalse(isSingleChoiceCorrect("A", ""));
        }

        @Test
        void caseInsensitiveComparison() {
            assertTrue(isSingleChoiceCorrect("a", "A"));
            assertTrue(isSingleChoiceCorrect("b", "B"));
        }
    }

    // ==================== 多选题评分测试（排序后比较） ====================

    @Nested
    class MultiChoiceGrading {
        @Test
        void correctOrderShouldMatch() {
            String student = "A,B,C";
            String correct = "A,B,C";
            assertTrue(isMultiChoiceCorrect(student, correct));
        }

        @Test
        void differentOrderShouldMatchAfterSort() {
            // 学生选了 B,A，正确答案是 A,B → 排序后应一致
            String student = "B,A";
            String correct = "A,B";
            assertTrue(isMultiChoiceCorrect(student, correct));
        }

        @Test
        void extraOptionsShouldNotMatch() {
            assertFalse(isMultiChoiceCorrect("A,B,C", "A,B"));
        }

        @Test
        void missingOptionsShouldNotMatch() {
            assertFalse(isMultiChoiceCorrect("A,B", "A,B,C"));
        }

        @Test
        void emptyAnswerShouldNotMatch() {
            assertFalse(isMultiChoiceCorrect("", "A,B"));
        }
    }

    // ==================== 判断题评分测试 ====================

    @Nested
    class JudgeChoiceGrading {
        @Test
        void trueAnswerShouldMatch() {
            assertTrue(isJudgeCorrect("A", "true") || isJudgeCorrect("TRUE", "true"));
            assertTrue(isJudgeCorrect("B", "false") || isJudgeCorrect("FALSE", "false"));
        }

        @Test
        void wrongAnswerShouldNotMatch() {
            assertFalse(isJudgeCorrect("A", "false"));
            assertFalse(isJudgeCorrect("B", "true"));
        }
    }

    // ==================== 难度等级测试 ====================

    @Nested
    class DifficultyLevel {
        @Test
        void validDifficultyLevels() {
            assertEquals(1, getDifficultyLabel(1));  // 简单
            assertEquals(2, getDifficultyLabel(2));  // 中等
            assertEquals(3, getDifficultyLabel(3));  // 困难
        }

        @Test
        void invalidDifficultyDefaultsToMedium() {
            assertEquals(2, getDifficultyLabel(0));
            assertEquals(2, getDifficultyLabel(4));
            assertEquals(2, getDifficultyLabel(-1));
        }
    }

    // ==================== 剩余时间计算测试 ====================

    @Nested
    class RemainingTimeCalculation {

        @Test
        void positiveRemainingTime() {
            long remainingSecs = calculateRemainingMinutes(60, 30);
            // 应该返回剩余秒数（取min值）
            assertTrue(remainingSecs > 0);
            assertTrue(remainingSecs <= 1800); // 不超过60分钟
        }

        @Test
        void expiredExamReturnsZero() {
            // elapsed 超过 duration 才是真正超时；原先传 -10 表示"提前 10 分钟开始"，语义相反
            long remainingSecs = calculateRemainingMinutes(60, 70);
            // 已超时
            assertTrue(remainingSecs <= 0);
        }

        @Test
        void justStartedFullTime() {
            long remainingSecs = calculateRemainingMinutes(60, 1);
            // 刚开始，接近满时长
            assertTrue(remainingSecs > 3000); // 至少50分钟
        }
    }

    // ==================== 违规扣分计算测试 ====================

    @Nested
    class ViolationScoring {

        @Test
        void switchScreenPenaltyIncreasesWithSeverity() {
            int score1 = calcViolationScore("switch_screen", 1); // 5
            int score2 = calcViolationScore("switch_screen", 2); // 10
            int score3 = calcViolationScore("switch_screen", 3); // 15
            assertTrue(score1 < score2);
            assertTrue(score2 < score3);
        }

        @Test
        void forbiddenItemHasHighestPenalty() {
            int switchScore = calcViolationScore("switch_screen", 1);
            int forbidScore = calcViolationScore("forbidden_item", 1);
            assertTrue(forbidScore > switchScore); // 20 > 5
        }

        @Test
        void lookAwayLowestPenalty() {
            int lookAway = calcViolationScore("look_away", 1);
            int multiFace = calcViolationScore("multi_face", 1);
            assertTrue(multiFace > lookAway); // 15 > 3
        }
    }

    // ==================== 统计计算测试 ====================

    @Nested
    class StatisticsCalculation {

        @Test
        void passRateCalculation() {
            List<Integer> scores = Arrays.asList(80, 90, 45, 60, 75);
            double passRate = calculatePassRate(scores, 60);
            // 及格的: 80,90,60,75 = 4/5 = 80%
            assertEquals(0.8, passRate, 0.01);
        }

        @Test
        void averageScoreCalculation() {
            List<Integer> scores = Arrays.asList(80, 90, 100);
            double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            assertEquals(90.0, avg, 0.01);
        }

        @Test
        void emptyScoresZeroAverage() {
            List<Integer> scores = Collections.emptyList();
            double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            assertEquals(0.0, avg);
        }

        @Test
        void medianEvenCount() {
            List<Integer> scores = Arrays.asList(60, 70, 80, 90);
            double median = calculateMedian(scores);
            assertEquals(75.0, median, 0.01); // (70+80)/2
        }

        @Test
        void medianOddCount() {
            List<Integer> scores = Arrays.asList(60, 70, 90);
            double median = calculateMedian(scores);
            assertEquals(70.0, median, 0.01); // 中间值
        }
    }

    // ==================== 答案解析/清理测试 ====================

    @Nested
    class AnswerParsing {

        @Test
        void trimAndUppercaseStudentAnswer() {
            String raw = " a ";
            String cleaned = normalizeAnswer(raw);
            assertEquals("A", cleaned);
        }

        @Test
        void nullAnswerHandling() {
            String result = normalizeAnswer(null);
            assertNull(result);
        }

        @Test
        void splitMultiChoiceAnswer() {
            String answer = "A,B,C";
            String[] parts = answer.split(",");
            assertArrayEquals(new String[]{"A", "B", "C"}, parts);
        }
    }

    // ════════════════════════════════════════════════
    // 模拟业务逻辑的纯函数（从Service中提取的核心算法）
    // ════════════════════════════════════════════════

    private boolean isSingleChoiceCorrect(String student, String correct) {
        if (student == null || correct == null) return false;
        return student.trim().equalsIgnoreCase(correct.trim());
    }

    private boolean isMultiChoiceCorrect(String student, String correct) {
        if (student == null || correct == null) return false;
        String[] sArr = student.trim().toUpperCase().split(",");
        String[] cArr = correct.trim().toUpperCase().split(",");
        if (sArr.length != cArr.length) return false;
        java.util.Arrays.sort(sArr);
        java.util.Arrays.sort(cArr);
        return java.util.Arrays.equals(sArr, cArr);
    }

    private boolean isJudgeCorrect(String student, String correct) {
        if (student == null || correct == null) return false;
        String s = student.trim().toUpperCase();
        String c = correct.trim().toLowerCase();
        if ("A".equals(s)) return "true".equals(c);
        if ("B".equals(s)) return "false".equals(c);
        return s.equals(c.toUpperCase());
    }

    private int getDifficultyLabel(int difficulty) {
        return switch (difficulty) {
            case 1 -> 1; // 简单
            case 2 -> 2; // 中等
            case 3 -> 3; // 困难
            default -> 2; // 默认中等
        };
    }

    private long calculateRemainingMinutes(int durationMin, int elapsedMin) {
        long totalSeconds = (long) durationMin * 60;
        long elapsedSeconds = (long) elapsedMin * 60;
        return Math.max(0, totalSeconds - elapsedSeconds);
    }

    private int calcViolationScore(String type, int severity) {
        return switch (type) {
            case "switch_screen" -> 5 * severity;
            case "no_face" -> 10 * severity;
            case "multi_face" -> 15 * severity;
            case "look_away" -> 3 * severity;
            case "forbidden_item" -> 20 * severity;
            default -> severity * 3;
        };
    }

    private double calculatePassRate(List<Integer> scores, int passScore) {
        if (scores == null || scores.isEmpty()) return 0.0;
        long passed = scores.stream().filter(s -> s >= passScore).count();
        return (double) passed / scores.size();
    }

    private double calculateMedian(List<Integer> sortedList) {
        List<Integer> list = new ArrayList<>(sortedList);
        Collections.sort(list);
        int size = list.size();
        if (size % 2 == 0) {
            return (list.get(size / 2 - 1) + list.get(size / 2)) / 2.0;
        } else {
            return list.get(size / 2);
        }
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) return null;
        return answer.trim().toUpperCase();
    }
}
