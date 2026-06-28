package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.enums.DifficultyEnum;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.dto.AutoPaperDTO;
import com.exam.dto.PaperPreviewDTO;
import com.exam.mapper.*;
import com.exam.pojo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能组卷引擎
 * 
 * 支持两种策略：
 * 1. greedy - 贪心算法：按题型、难度分组后随机选取
 * 2. random - 纯随机：直接随机选题
 * 
 * 通过seed保证结果可重现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperGeneratorService {

    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;

    /**
     * 生成试卷预览（不保存）
     */
    public PaperPreviewDTO generatePreview(AutoPaperDTO config) {
        return doGenerate(config, false);
    }

    /**
     * 生成并保存试卷
     */
    public PaperPreviewDTO generateAndSave(AutoPaperDTO config, Long creatorId) {
        return doGenerate(config, true, creatorId);
    }

    private PaperPreviewDTO doGenerate(AutoPaperDTO config, boolean save, Long... creatorId) {
        Random random = config.getSeed() != null
                ? new Random(config.getSeed())
                : new Random();

        PaperPreviewDTO result = new PaperPreviewDTO();
        result.setTitle(config.getTitle());
        result.setDescription(config.getDescription());
        result.setTotalScore(config.getTotalScore());
        result.setPassScore(config.getPassScore() != null ? config.getPassScore() : (int)(config.getTotalScore() * 0.6));
        result.setQuestions(new ArrayList<>());

        int actualTotalScore = 0;
        int actualQuestionCount = 0;

        for (AutoPaperDTO.TypeConfig tc : config.getTypeConfigs()) {
            if (tc.getCount() == null || tc.getCount() <= 0) continue;

            // 1. 获取可选题目池
            List<Question> pool = getQuestionPool(tc.getType(), tc.getTagIds());
            if (pool.isEmpty()) {
                log.warn("题目类型{}在指定范围内没有可用题目", tc.getType());
                continue;
            }

            // 2. 按难度分组
            Map<Integer, List<Question>> byDifficulty = pool.stream()
                    .collect(Collectors.groupingBy(Question::getDifficulty));

            // 3. 计算各难度需要的数量
            int easyRatio = tc.getEasyRatio() != null ? tc.getEasyRatio() : 30;
            int mediumRatio = tc.getMediumRatio() != null ? tc.getMediumRatio() : 50;
            int hardRatio = tc.getHardRatio() != null ? tc.getHardRatio() : 20;
            int total = easyRatio + mediumRatio + hardRatio;
            if (total == 0) { easyRatio = 30; mediumRatio = 50; hardRatio = 20; total = 100; }

            int needCount = tc.getCount();
            int needEasy = (int) Math.round(needCount * (double) easyRatio / total);
            int needMedium = (int) Math.round(needCount * (double) mediumRatio / total);
            int needHard = needCount - needEasy - needMedium;

            // 4. 贪心选取
            List<Question> selected = new ArrayList<>();

            // 简单题
            selected.addAll(pickRandom(byDifficulty.getOrDefault(1, Collections.emptyList()), needEasy, random));
            // 中等题
            selected.addAll(pickRandom(byDifficulty.getOrDefault(2, Collections.emptyList()), needMedium, random));
            // 困难题
            selected.addAll(pickRandom(byDifficulty.getOrDefault(3, Collections.emptyList()), needHard, random));

            // 如果总数不够，用中等题补充
            if (selected.size() < needCount) {
                Set<Long> selectedIds = selected.stream().map(Question::getId).collect(Collectors.toSet());
                List<Question> remaining = pool.stream()
                        .filter(q -> !selectedIds.contains(q.getId()))
                        .collect(Collectors.toList());
                selected.addAll(pickRandom(remaining, needCount - selected.size(), random));
            }

            // 5. 构建预览项
            for (Question q : selected) {
                PaperPreviewDTO.PaperQuestionItem item = new PaperPreviewDTO.PaperQuestionItem();
                item.setQuestionId(q.getId());
                item.setContent(q.getContent());
                item.setType(q.getType());
                item.setTypeDesc(QuestionTypeEnum.fromCode(q.getType()).getDescription());
                item.setOptionA(q.getOptionA());
                item.setOptionB(q.getOptionB());
                item.setOptionC(q.getOptionC());
                item.setOptionD(q.getOptionD());
                item.setAnswer(q.getAnswer());
                item.setAnalysis(q.getAnalysis());
                item.setDifficulty(q.getDifficulty());
                item.setDifficultyDesc(DifficultyEnum.fromCode(q.getDifficulty()).getDescription());
                item.setScore(tc.getScore());
                item.setTagNames(getTagNames(q.getId()));

                result.getQuestions().add(item);
                actualTotalScore += tc.getScore();
                actualQuestionCount++;
            }
        }

        // 打乱题目顺序
        Collections.shuffle(result.getQuestions(), random);

        result.setQuestionCount(actualQuestionCount);
        // 如果实际分数和目标不一致，以实际为准
        if (actualTotalScore != config.getTotalScore()) {
            result.setTotalScore(actualTotalScore);
        }

        // 保存试卷
        if (save && creatorId != null && creatorId.length > 0) {
            Paper paper = new Paper();
            paper.setTitle(config.getTitle());
            paper.setDescription(config.getDescription());
            paper.setTotalScore(result.getTotalScore());
            paper.setPassScore(result.getPassScore());
            paper.setQuestionCount(result.getQuestionCount());
            paper.setCreatorId(creatorId[0]);
            paperMapper.insert(paper);
            result.setPaperId(paper.getId());

            // 保存关联
            for (int i = 0; i < result.getQuestions().size(); i++) {
                PaperPreviewDTO.PaperQuestionItem item = result.getQuestions().get(i);
                PaperQuestion pq = new PaperQuestion();
                pq.setPaperId(paper.getId());
                pq.setQuestionId(item.getQuestionId());
                pq.setScore(item.getScore());
                pq.setSortOrder(i);
                paperQuestionMapper.insert(pq);
            }
        }

        return result;
    }

    /**
     * 从题库中获取可选题目池
     */
    private List<Question> getQuestionPool(Integer type, List<Long> tagIds) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getType, type);

        // 如果指定了标签，则限定范围
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> questionIds = questionTagMapper.selectList(
                    new LambdaQueryWrapper<QuestionTag>().in(QuestionTag::getTagId, tagIds)
            ).stream().map(QuestionTag::getQuestionId)
             .distinct().collect(Collectors.toList());

            if (questionIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(Question::getId, questionIds);
        }

        return questionMapper.selectList(wrapper);
    }

    /**
     * 从列表中随机选取指定数量的题目（不重复）
     */
    private List<Question> pickRandom(List<Question> pool, int count, Random random) {
        if (pool == null || pool.isEmpty()) return Collections.emptyList();
        int pick = Math.min(count, pool.size());
        List<Question> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return copy.subList(0, pick);
    }

    /**
     * 获取题目标签名
     */
    private List<String> getTagNames(Long questionId) {
        List<QuestionTag> qts = questionTagMapper.selectList(
                new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getQuestionId, questionId));
        if (qts.isEmpty()) return Collections.emptyList();
        List<Long> tagIds = qts.stream().map(QuestionTag::getTagId).collect(Collectors.toList());
        return knowledgeTagMapper.selectBatchIds(tagIds)
                .stream().map(KnowledgeTag::getName).collect(Collectors.toList());
    }
}
