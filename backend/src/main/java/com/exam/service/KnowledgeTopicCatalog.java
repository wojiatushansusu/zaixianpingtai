package com.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识点词表。
 *
 * <p>词表来自复习路径的阶段模块（`knowledge-topics.json` 由前端 reviewPath.js 导出），
 * 前后端共用同一份，避免两边各写一套再慢慢漂移。
 *
 * <p>为什么必须是闭集：AI 补标时若允许自创名称，同一考点会出现「不定积分」「积分计算」
 * 「求不定积分」三种写法，诊断的汇总就散了——每个都只出现一次，等于没有统计。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTopicCatalog {

    private final ObjectMapper objectMapper;

    /** 全部知识点名称（顺序即词表顺序，提示词里也用这个顺序） */
    @Getter
    private List<String> topics = List.of();

    private Set<String> topicSet = Set.of();

    /** 名称 → 所属阶段，用于展示与排查 */
    @Getter
    private List<Entry> entries = List.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("knowledge-topics.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<Entry> list = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();
            for (JsonNode node : root.path("topics")) {
                String name = node.path("name").asText("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (!names.add(name)) {
                    continue; // 词表里同名模块只保留第一次出现
                }
                list.add(new Entry(name, node.path("stage").asText(""), node.path("stageTitle").asText("")));
            }
            this.entries = List.copyOf(list);
            this.topics = list.stream().map(Entry::name).toList();
            this.topicSet = Set.copyOf(names);
            log.info("知识点词表加载完成: {} 个", topics.size());
        } catch (Exception e) {
            // 词表缺失属于部署问题，宁可启动失败也不要静默降级成「什么都不能标」
            throw new IllegalStateException("加载知识点词表失败", e);
        }
    }

    public boolean contains(String topic) {
        return topic != null && topicSet.contains(topic);
    }

    public record Entry(String name, String stage, String stageTitle) {
    }
}
