package com.exam.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 答案自检中「纯计算」部分的单元测试。
 *
 * <p>这里刻意不去 mock LlmRouter：真正值得测的是解析与比对逻辑——
 * 模型调用失败时的降级行为在实现里是 try/catch 兜底，无需靠测试证明。
 */
class AnswerSelfCheckServiceTest {

    private final AnswerSelfCheckService service = new AnswerSelfCheckService(null);

    private Map<String, Object> q(String answer) {
        Map<String, Object> m = new HashMap<>();
        m.put("content", "题干");
        m.put("answer", answer);
        return m;
    }

    @Test
    @DisplayName("答案归一化：忽略顺序、大小写与分隔符")
    void normalizeAnswer() {
        assertEquals("A", service.normalizeAnswer("a"));
        assertEquals("A,B", service.normalizeAnswer("b,a"));
        assertEquals("A,C", service.normalizeAnswer("A，C"));
        assertEquals("A,C", service.normalizeAnswer("CA"));
        assertEquals("", service.normalizeAnswer(null));
        assertEquals("", service.normalizeAnswer("  "));
    }

    @Test
    @DisplayName("重答与声明不一致 → 标记为可疑")
    void mismatchMarkedSuspect() {
        List<Map<String, Object>> questions = List.of(q("A"), q("B"), q("C"));
        List<String> reanswers = List.of("A", "D", "C");

        Set<Integer> suspects = service.compareAnswers(questions, reanswers);

        assertEquals(1, suspects.size());
        assertTrue(suspects.contains(1));
    }

    @Test
    @DisplayName("多选答案顺序不同但集合一致 → 不算可疑")
    void orderInsensitiveForMultiChoice() {
        List<Map<String, Object>> questions = List.of(q("B,A"), q("A,C"));
        List<String> reanswers = List.of("A,B", "C,A");

        assertTrue(service.compareAnswers(questions, reanswers).isEmpty());
    }

    @Test
    @DisplayName("任一侧答案为空时不判可疑，避免误伤")
    void emptyAnswerNotSuspect() {
        List<Map<String, Object>> questions = List.of(q(""), q("A"));
        List<String> reanswers = List.of("A", "");

        assertTrue(service.compareAnswers(questions, reanswers).isEmpty());
    }

    @Test
    @DisplayName("解析模型返回的 JSON 数组（含 markdown 包裹）")
    void parseAnswerArrayFromMarkdown() {
        String content = "```json\n[\"A\", \"B,C\", \"A\"]\n```";
        List<String> answers = service.parseAnswerArray(content, 3);

        assertNotNull(answers);
        assertEquals(3, answers.size());
        assertEquals("A", answers.get(0));
        assertEquals("B,C", answers.get(1));
    }

    @Test
    @DisplayName("兼容对象形式 [{answer:A}]")
    void parseAnswerArrayFromObjects() {
        String content = "[{\"answer\":\"A\"},{\"answer\":\"B\"}]";
        List<String> answers = service.parseAnswerArray(content, 2);

        assertNotNull(answers);
        assertEquals(List.of("A", "B"), answers);
    }

    @Test
    @DisplayName("数量对不上时返回 null，宁可放弃自检也不做错位比对")
    void parseAnswerArrayRejectsSizeMismatch() {
        assertNull(service.parseAnswerArray("[\"A\",\"B\"]", 3));
    }

    @Test
    @DisplayName("非 JSON 内容返回 null")
    void parseAnswerArrayRejectsGarbage() {
        assertNull(service.parseAnswerArray("抱歉，我无法完成", 2));
        assertNull(service.parseAnswerArray("", 2));
        assertNull(service.parseAnswerArray(null, 2));
    }

    @Test
    @DisplayName("模型未启用时自检直接跳过，不产生任何调用")
    void disabledWhenLlmUnavailable() {
        assertTrue(new AnswerSelfCheckService(null)
                .findSuspectIndexes(List.of(q("A")))
                .isEmpty());
    }
}
