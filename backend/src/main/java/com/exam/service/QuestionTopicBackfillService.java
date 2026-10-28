package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.pojo.Question;
import com.exam.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量题目的知识点补标。
 *
 * <p>为什么做成「一次一小批」而不是一次性全量：每道题都要调一次模型，
 * 数量一大就会超时、限流，中途失败还说不清补到哪了。分批可以由人控制节奏，
 * 每一次的返回都能核对。
 *
 * <p>为什么不做定时任务：补标是一次性的数据治理，跑完就结束。
 * 挂个定时器反而会在没人盯着的时候反复烧模型调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionTopicBackfillService {

    private static final int MAX_SAMPLES = 10;

    private final QuestionMapper questionMapper;
    private final QuestionTopicTagger tagger;

    /**
     * @param limit  本批最多处理多少题（调用方负责夹到 1~50）
     * @param dryRun true 时只判定不落库，用于先看效果
     */
    public Map<String, Object> backfill(int limit, boolean dryRun) {
        List<Question> batch = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .and(w -> w.isNull(Question::getKnowledgePoint)
                                .or().eq(Question::getKnowledgePoint, ""))
                        .orderByAsc(Question::getId)
                        .last("LIMIT " + limit));

        List<Map<String, Object>> samples = new ArrayList<>();
        int tagged = 0;
        int failed = 0;

        for (Question question : batch) {
            String topic = tagger.suggest(question);

            if (topic == null) {
                failed++;
                continue;
            }

            if (!dryRun) {
                Question update = new Question();
                update.setId(question.getId());
                update.setKnowledgePoint(topic);
                questionMapper.updateById(update);
            }

            tagged++;
            if (samples.size() < MAX_SAMPLES) {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("id", question.getId());
                sample.put("topic", topic);
                sample.put("content", abbreviate(question.getContent(), 40));
                samples.add(sample);
            }
        }

        long remaining = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>()
                        .and(w -> w.isNull(Question::getKnowledgePoint)
                                .or().eq(Question::getKnowledgePoint, "")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("scanned", batch.size());
        result.put("tagged", tagged);
        result.put("failed", failed);
        result.put("remaining", remaining);
        result.put("samples", samples);

        log.info("知识点补标: dryRun={}, scanned={}, tagged={}, failed={}, remaining={}",
                dryRun, batch.size(), tagged, failed, remaining);

        return result;
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }
}
