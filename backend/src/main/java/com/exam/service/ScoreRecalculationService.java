package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.mapper.AnswerRecordMapper;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Exam;
import com.exam.pojo.ExamRecord;
import com.exam.pojo.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史答卷重算。
 *
 * <p>修了判分规则之后，**已经交卷的记录不会自动变对**——分数是判完就落库的。
 * 判断题那条规则修复前，所有判断题都判成 0 分，这些历史记录需要一次性纠正，
 * 否则学生看到的仍是「答对却判错」，错题本里也还挂着。
 *
 * <p>两个刻意设计：
 * <ul>
 *   <li>决策逻辑放在纯函数 {@link AnswerRecalculation} 里，可单测；本类只负责取数与落库</li>
 *   <li>默认干跑。重算会改写成绩，属于不可逆操作，要先看到「从几分变几分」再落库</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRecalculationService {

    /** 答卷状态：已提交 */
    private static final int RECORD_SUBMITTED = 1;

    private final ExamRecordMapper examRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final QuestionMapper questionMapper;
    private final ExamMapper examMapper;

    /**
     * @param dryRun true 时只统计不落库
     */
    public Map<String, Object> recalculate(boolean dryRun) {
        List<ExamRecord> submitted = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getStatus, RECORD_SUBMITTED));

        List<Map<String, Object>> samples = new ArrayList<>();
        int scannedRecords = 0;
        int changedRecordCount = 0;
        int changedAnswerCount = 0;

        for (ExamRecord record : submitted) {
            scannedRecords++;

            List<AnswerRecord> answers = answerRecordMapper.selectList(
                    new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, record.getId()));
            if (answers.isEmpty()) {
                continue;
            }

            AnswerRecalculation.Plan plan =
                    AnswerRecalculation.plan(answers, loadQuestions(answers));
            changedAnswerCount += plan.changes().size();

            int oldTotal = nz(record.getScore());
            if (!plan.totalChanged(oldTotal)) {
                continue;
            }
            changedRecordCount++;

            if (!dryRun) {
                for (AnswerRecalculation.Change change : plan.changes()) {
                    AnswerRecord update = new AnswerRecord();
                    update.setId(change.answerId());
                    update.setScore(change.newScore());
                    update.setIsCorrect(change.newCorrect());
                    answerRecordMapper.updateById(update);
                }

                Exam exam = examMapper.selectById(record.getExamId());
                int passScore = exam != null && exam.getPassScore() != null ? exam.getPassScore() : 60;
                record.setScore(plan.newTotal());
                record.setIsPassed(plan.newTotal() >= passScore ? 1 : 0);
                examRecordMapper.updateById(record);
            }

            if (samples.size() < 20) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("recordId", record.getId());
                item.put("examId", record.getExamId());
                item.put("studentId", record.getStudentId());
                item.put("before", oldTotal);
                item.put("after", plan.newTotal());
                item.put("changedAnswers", plan.changes().size());
                samples.add(item);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dryRun", dryRun);
        summary.put("scannedRecords", scannedRecords);
        summary.put("changedRecords", changedRecordCount);
        summary.put("changedAnswers", changedAnswerCount);
        summary.put("samples", samples);

        log.info("历史答卷重算: dryRun={}, 扫描 {} 份, 分数变化 {} 份, 判分变化 {} 条",
                dryRun, scannedRecords, changedRecordCount, changedAnswerCount);

        return summary;
    }

    /** 批量取题，避免逐条查 */
    private Map<Long, Question> loadQuestions(List<AnswerRecord> answers) {
        List<Long> ids = answers.stream()
                .map(AnswerRecord::getQuestionId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, Question> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Question question : questionMapper.selectBatchIds(ids)) {
            map.put(question.getId(), question);
        }
        return map;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
