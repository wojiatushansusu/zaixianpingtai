package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.mapper.KnowledgeTagMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.QuestionTagMapper;
import com.exam.pojo.KnowledgeTag;
import com.exam.pojo.Question;
import com.exam.pojo.QuestionTag;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 题目分类服务
 * 利用 LLM 为题目自动生成知识点标签和难度评级
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiClassifyService {

    private final LlmRouter llmRouter;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final QuestionTagMapper questionTagMapper;
    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 批量分类结果
     */
    @Data
    public static class ClassifyResult {
        private List<ClassifyItem> items;
        private int totalCount;
        private int successCount;
        private int failCount;

        public static ClassifyResult empty() {
            ClassifyResult r = new ClassifyResult();
            r.setItems(Collections.emptyList());
            return r;
        }
    }

    /**
     * 单个题目的分类结果
     */
    @Data
    public static class ClassifyItem {
        private Long questionId;
        private boolean success;
        private String error;
        /** AI 推荐的知识点标签名列表 */
        private List<String> suggestedTags;
        /** AI 推荐的难度 (1-简单 2-中等 3-困难) */
        private Integer suggestedDifficulty;
        /** 难度描述 */
        private String suggestedDifficultyDesc;
    }

    /**
     * 批量分析题目，返回 AI 推荐的标签和难度
     * 不直接修改数据库，由前端确认后再应用
     *
     * @param questionIds 要分析的题目ID列表
     * @param existingTags 系统已有标签列表（供 AI 参考）
     * @return 分类结果
     */
    @Transactional(readOnly = true)
    public ClassifyResult classifyQuestions(List<Long> questionIds, List<String> existingTags) {
        if (questionIds == null || questionIds.isEmpty()) {
            return ClassifyResult.empty();
        }

        // 查询题目详情
        List<Question> questions = questionMapper.selectBatchIds(questionIds);
        if (questions.isEmpty()) {
            return ClassifyResult.empty();
        }

        // 构建分析提示
        String systemPrompt = buildSystemPrompt(existingTags);

        // 每次最多分析 10 道题（避免 token 超限）
        int batchSize = 10;
        ClassifyResult result = new ClassifyResult();
        result.setItems(new ArrayList<>());
        result.setTotalCount(questions.size());

        for (int i = 0; i < questions.size(); i += batchSize) {
            List<Question> batch = questions.subList(i, Math.min(i + batchSize, questions.size()));
            List<ClassifyItem> batchResult = analyzeBatch(batch, systemPrompt);
            result.getItems().addAll(batchResult);
        }

        result.setSuccessCount((int) result.getItems().stream().filter(ClassifyItem::isSuccess).count());
        result.setFailCount(result.getTotalCount() - result.getSuccessCount());
        return result;
    }

    /**
     * 应用 AI 分类结果到数据库
     * 使用事务保证每个题目的标签清除+重建的原子性
     *
     * @param items 分类结果列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyClassifyResult(List<ClassifyItem> items) {
        if (items == null || items.isEmpty()) return;

        for (ClassifyItem item : items) {
            if (!item.isSuccess() || item.getQuestionId() == null) continue;

            // 更新难度
            if (item.getSuggestedDifficulty() != null) {
                Question question = questionMapper.selectById(item.getQuestionId());
                if (question != null) {
                    question.setDifficulty(item.getSuggestedDifficulty());
                    questionMapper.updateById(question);
                }
            }

            // 更新标签
            if (item.getSuggestedTags() != null && !item.getSuggestedTags().isEmpty()) {
                // 先清除旧标签
                questionTagMapper.delete(
                        new LambdaQueryWrapper<QuestionTag>()
                                .eq(QuestionTag::getQuestionId, item.getQuestionId()));

                // 创建或查找标签，建立关联
                for (String tagName : item.getSuggestedTags()) {
                    Long tagId = getOrCreateTagId(tagName);
                    QuestionTag qt = new QuestionTag();
                    qt.setQuestionId(item.getQuestionId());
                    qt.setTagId(tagId);
                    questionTagMapper.insert(qt);
                }
            }
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(List<String> existingTags) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的考试题目分类专家。你需要为每道题目分析出最合适的知识点标签和难度等级。\n\n");
        sb.append("规则：\n");
        sb.append("1. 知识点标签：1~3个，从已有标签中选择或创建新的（优先使用已有标签）\n");
        sb.append("2. 难度等级：1(简单)、2(中等)、3(困难)\n");
        sb.append("3. 根据题目内容、答案复杂度、考察深度综合判断\n\n");

        if (existingTags != null && !existingTags.isEmpty()) {
            sb.append("系统已有知识点标签：\n");
            for (String tag : existingTags) {
                sb.append("- ").append(tag).append("\n");
            }
            sb.append("\n");
        }

        sb.append("你必须严格按以下JSON格式返回，不要添加任何其他文字：\n");
        sb.append("[\n");
        sb.append("  {\"id\": 题目ID, \"tags\": [\"标签1\", \"标签2\"], \"difficulty\": 1或2或3},\n");
        sb.append("  ...\n");
        sb.append("]\n");

        return sb.toString();
    }

    /**
     * 分析一批题目
     */
    private List<ClassifyItem> analyzeBatch(List<Question> questions, String systemPrompt) {
        // 构建用户消息
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("请分析以下 ").append(questions.size()).append(" 道题目：\n\n");

        for (Question q : questions) {
            userMsg.append("题目ID: ").append(q.getId()).append("\n");
            userMsg.append("类型: ").append(q.getType()).append("\n");
            userMsg.append("题干: ").append(q.getContent()).append("\n");
            if (q.getOptionA() != null) userMsg.append("选项A: ").append(q.getOptionA()).append("\n");
            if (q.getOptionB() != null) userMsg.append("选项B: ").append(q.getOptionB()).append("\n");
            if (q.getOptionC() != null) userMsg.append("选项C: ").append(q.getOptionC()).append("\n");
            if (q.getOptionD() != null) userMsg.append("选项D: ").append(q.getOptionD()).append("\n");
            userMsg.append("答案: ").append(q.getAnswer()).append("\n");
            if (q.getAnalysis() != null && !q.getAnalysis().isEmpty()) {
                userMsg.append("解析: ").append(q.getAnalysis()).append("\n");
            }
            userMsg.append("---\n");
        }

        // 调用 LLM
        LlmRouter.LlmResult llmResult = llmRouter.call(
                LlmRouter.TaskLevel.HEAVY,
                systemPrompt,
                userMsg.toString()
        );

        if (!llmResult.isSuccess()) {
            // 整批失败
            return questions.stream().map(q -> {
                ClassifyItem item = new ClassifyItem();
                item.setQuestionId(q.getId());
                item.setSuccess(false);
                item.setError(llmResult.getError());
                return item;
            }).collect(Collectors.toList());
        }

        // 解析 JSON 响应
        return parseResponse(llmResult.getContent(), questions);
    }

    /**
     * 解析 AI 返回的 JSON
     */
    private List<ClassifyItem> parseResponse(String content, List<Question> questions) {
        List<ClassifyItem> results = new ArrayList<>();

        try {
            // 尝试提取 JSON 数组（AI 可能在 JSON 前后加说明文字）
            String json = extractJson(content);

            List<Map<String, Object>> items = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});

            // 按 ID 建立映射
            Map<Long, Map<String, Object>> itemMap = new HashMap<>();
            for (Map<String, Object> item : items) {
                Object idObj = item.get("id");
                if (idObj != null) {
                    Long id = convertToLong(idObj);
                    if (id != null) {
                        itemMap.put(id, item);
                    }
                }
            }

            for (Question q : questions) {
                ClassifyItem ci = new ClassifyItem();
                ci.setQuestionId(q.getId());

                Map<String, Object> aiItem = itemMap.get(q.getId());
                if (aiItem == null) {
                    ci.setSuccess(false);
                    ci.setError("AI未返回该题目的分类结果");
                    results.add(ci);
                    continue;
                }

                try {
                    // 解析标签
                    List<String> tags = new ArrayList<>();
                    Object tagsObj = aiItem.get("tags");
                    if (tagsObj instanceof List) {
                        for (Object t : (List<?>) tagsObj) {
                            String tag = String.valueOf(t).trim();
                            if (!tag.isEmpty()) tags.add(tag);
                        }
                    }
                    ci.setSuggestedTags(tags);

                    // 解析难度
                    Object diffObj = aiItem.get("difficulty");
                    int difficulty = 2; // 默认中等
                    if (diffObj instanceof Number) {
                        difficulty = ((Number) diffObj).intValue();
                    } else if (diffObj instanceof String) {
                        try {
                            difficulty = Integer.parseInt((String) diffObj);
                        } catch (NumberFormatException ignored) {
                            String d = ((String) diffObj).trim();
                            if (d.contains("简单") || d.contains("easy")) difficulty = 1;
                            else if (d.contains("困难") || d.contains("hard")) difficulty = 3;
                        }
                    }
                    // 限制在 1-3
                    difficulty = Math.max(1, Math.min(3, difficulty));
                    ci.setSuggestedDifficulty(difficulty);
                    ci.setSuggestedDifficultyDesc(
                            difficulty == 1 ? "简单" : difficulty == 2 ? "中等" : "困难");
                    ci.setSuccess(true);
                } catch (Exception e) {
                    ci.setSuccess(false);
                    ci.setError("解析AI结果失败: " + e.getMessage());
                }
                results.add(ci);
            }
        } catch (Exception e) {
            log.error("解析AI分类结果失败: {}", e.getMessage());
            // 全部失败
            for (Question q : questions) {
                ClassifyItem ci = new ClassifyItem();
                ci.setQuestionId(q.getId());
                ci.setSuccess(false);
                ci.setError("AI返回格式异常: " + e.getMessage());
                results.add(ci);
            }
        }

        return results;
    }

    /**
     * 从 AI 响应中提取 JSON 数组
     */
    private String extractJson(String content) {
        if (content == null) return "[]";

        String cleaned = content.trim();

        // 去除 markdown 代码块包裹（可能多行）
        // 正则 ^...$ 在非多行模式下不能处理多行匹配，改用 String 操作
        while (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline < 0) break;
            cleaned = cleaned.substring(firstNewline + 1).trim();
        }
        while (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        // 找到第一个 [ 和最后一个 ]
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    private Long convertToLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取或创建标签，返回标签ID
     * 并发安全：knowledge_tag 有 uk_name_deleted 唯一索引，
     * 并发 insert 时捕获 DuplicateKeyException 后回退查询
     */
    private Long getOrCreateTagId(String tagName) {
        String trimmed = tagName.trim();
        // 先查
        KnowledgeTag existing = knowledgeTagMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeTag>()
                        .eq(KnowledgeTag::getName, trimmed)
        );
        if (existing != null) return existing.getId();

        // 再插，并发时可能冲突
        try {
            KnowledgeTag tag = new KnowledgeTag();
            tag.setName(trimmed);
            tag.setSortOrder(99);
            knowledgeTagMapper.insert(tag);
            return tag.getId();
        } catch (DuplicateKeyException e) {
            // 并发创建同名标签，回退查询
            log.debug("标签 '{}' 并发创建冲突，回退查询", trimmed);
            KnowledgeTag tag = knowledgeTagMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeTag>()
                            .eq(KnowledgeTag::getName, trimmed)
            );
            return tag != null ? tag.getId() : null;
        }
    }
}
