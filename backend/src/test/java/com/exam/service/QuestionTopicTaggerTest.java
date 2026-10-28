package com.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识点判定的解析与校验测试。
 *
 * 这里只测确定性部分（解析、词表校验），不测真实模型调用——
 * 模型说什么无法断言，但「说了不该写的名字时我们必须拒绝」是可以断言的。
 */
class QuestionTopicTaggerTest {

    private KnowledgeTopicCatalog catalog;
    private QuestionTopicTagger tagger;

    @BeforeEach
    void setUp() {
        catalog = new KnowledgeTopicCatalog(new ObjectMapper());
        catalog.load();
        // parseTopic 不依赖 LlmRouter，传 null 即可
        tagger = new QuestionTopicTagger(null, catalog);
    }

    @Test
    @DisplayName("词表加载出复习路径的全部模块，且没有空项")
    void catalogLoaded() {
        assertFalse(catalog.getTopics().isEmpty(), "词表不应为空");
        assertTrue(catalog.getTopics().stream().noneMatch(t -> t == null || t.isBlank()));
        assertTrue(catalog.contains("不定积分"));
        assertTrue(catalog.contains("行列式"));
        // 同一模块名不应重复出现
        assertEquals(catalog.getTopics().size(),
                catalog.getTopics().stream().distinct().count());
    }

    @Test
    @DisplayName("标准 JSON 能取到知识点")
    void parsePlainJson() {
        assertEquals("不定积分", tagger.parseTopic("{\"topic\":\"不定积分\"}"));
    }

    @Test
    @DisplayName("包在 markdown 代码块里也能取到")
    void parseFencedJson() {
        String content = """
                ```json
                {"topic": "定积分"}
                ```
                """;
        assertEquals("定积分", tagger.parseTopic(content));
    }

    @Test
    @DisplayName("模型说「归不了类」时返回 null，不往下猜")
    void parseEmptyTopicMeansUnclassified() {
        assertNull(tagger.parseTopic("{\"topic\":\"\"}"));
        // 即使正文里出现了某个知识点名，也不该拿它充数
        assertNull(tagger.parseTopic("{\"topic\":\"\", \"reason\":\"内容残缺，涉及不定积分\"}"));
    }

    @Test
    @DisplayName("自创名称一律拒绝——这是词表存在的意义")
    void rejectInventedTopic() {
        assertNull(tagger.parseTopic("{\"topic\":\"积分计算\"}"));
        assertNull(tagger.parseTopic("{\"topic\":\"高数\"}"));
    }

    @Test
    @DisplayName("裸文本正好是词表项时也能识别")
    void parseBareTopic() {
        assertEquals("行列式", tagger.parseTopic("行列式"));
        assertEquals("行列式", tagger.parseTopic("  行列式 \n"));
    }

    @Test
    @DisplayName("夹在句子里时取最长匹配，避免短词吃掉长词")
    void preferLongestMatch() {
        // 「不定积分」包含「定积分」，必须取更长的那个
        assertEquals("不定积分", tagger.parseTopic("这道题考的是不定积分。"));
        assertEquals("定积分", tagger.parseTopic("这道题考的是定积分。"));
    }

    @Test
    @DisplayName("脏输入不抛异常")
    void tolerateJunk() {
        assertNull(tagger.parseTopic(null));
        assertNull(tagger.parseTopic("   "));
        assertNull(tagger.parseTopic("我不知道"));
    }

    @Test
    @DisplayName("词表项带阶段信息，便于排查")
    void entriesCarryStage() {
        assertNotNull(catalog.getEntries());
        KnowledgeTopicCatalog.Entry entry = catalog.getEntries().stream()
                .filter(e -> e.name().equals("不定积分"))
                .findFirst()
                .orElseThrow();
        assertEquals("阶段 2", entry.stage());
    }
}
