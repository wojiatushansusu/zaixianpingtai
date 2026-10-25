package com.exam.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 判分规则（纯函数，无任何依赖）。
 *
 * <p>这些规则原本以 private 方法的形式混在 {@code ExamService} 里，无法单独测试，
 * 于是仓库里出现了一个把逻辑抄一遍再断言自己的测试类——覆盖率为零。
 * 抽出来之后，规则可以被真正验证，{@code ExamService} 只负责取数据和落库。
 *
 * <p>与旧实现的差异（均为修正，非行为倒退）：
 * <ul>
 *   <li>多选题答案按「字母集合」比较，不再用 split(",") 后逐项字符串对比——
 *       旧逻辑下 "A, B"（带空格）与 "A,B" 会被判错</li>
 *   <li>答案比较前统一剥离空格、标点与大小写，容忍 "A、" "a" 这类脏数据</li>
 *   <li>简答题（type=4）永不参与自动判分，即使题干配了参考答案也一样</li>
 * </ul>
 */
public final class ScoringRules {

    /** 单选题 */
    public static final int TYPE_SINGLE = 1;
    /** 多选题 */
    public static final int TYPE_MULTI = 2;
    /** 判断题 */
    public static final int TYPE_JUDGE = 3;
    /** 简答题 */
    public static final int TYPE_SHORT_ANSWER = 4;

    private ScoringRules() {
    }

    /**
     * 自动判分结果。
     *
     * @param autoGraded 是否参与自动判分（简答题为 false）
     * @param correct    是否答对
     * @param score      得分
     */
    public record GradeResult(boolean autoGraded, boolean correct, int score) {

        static GradeResult notAutoGraded() {
            return new GradeResult(false, false, 0);
        }

        static GradeResult wrong() {
            return new GradeResult(true, false, 0);
        }

        static GradeResult right(int score) {
            return new GradeResult(true, true, score);
        }
    }

    /**
     * 对单道客观题判分。
     *
     * @param type          题型编码
     * @param correctAnswer 标准答案，可能为空（纯主观题）
     * @param studentAnswer 学生作答
     * @param fullScore     该题满分
     */
    public static GradeResult gradeObjective(int type,
                                             String correctAnswer,
                                             String studentAnswer,
                                             int fullScore) {
        // 简答题必须人工批改；即便配了参考答案也不能用字符串相等去判
        if (type == TYPE_SHORT_ANSWER) {
            return GradeResult.notAutoGraded();
        }
        if (isBlank(correctAnswer) || isBlank(studentAnswer)) {
            return GradeResult.wrong();
        }

        Set<String> expected = toLetterSet(correctAnswer);
        Set<String> actual = toLetterSet(studentAnswer);
        if (expected.isEmpty() || actual.isEmpty()) {
            return GradeResult.wrong();
        }

        boolean correct;
        if (type == TYPE_MULTI) {
            // 多选：集合相等即对，顺序无关；缺一个也算错（保持「全对才得分」的原规则）
            correct = expected.equals(actual);
        } else {
            // 单选/判断：双方都必须恰好一个选项，且一致
            correct = expected.size() == 1 && actual.size() == 1 && expected.equals(actual);
        }

        return correct ? GradeResult.right(fullScore) : GradeResult.wrong();
    }

    /**
     * 违规扣分。分值来源见 {@code ExamService} 中对各违规类型的约定。
     */
    public static int violationScore(String violationType, int severity) {
        int level = Math.max(severity, 1);
        if (violationType == null) {
            return level * 3;
        }
        return switch (violationType) {
            case "switch_screen" -> 5 * level;
            case "no_face" -> 10 * level;
            case "multi_face" -> 15 * level;
            case "look_away" -> 3 * level;
            case "forbidden_item" -> 20 * level;
            case "keyboard_violation" -> 5 * level;
            // 替考嫌疑：固定分值，不随严重度线性放大
            case "face_position_changed" -> 15;
            default -> level * 3;
        };
    }

    /**
     * 把答案文本转成选项字母集合。
     * 兼容 "A"、"a"、"A,B"、"A，B"、"A、B"、"AB"、"A, B" 等写法。
     */
    static Set<String> toLetterSet(String answer) {
        Set<String> letters = new LinkedHashSet<>();
        if (answer == null) {
            return letters;
        }
        for (char c : answer.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                letters.add(String.valueOf(c));
            }
        }
        return letters;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
