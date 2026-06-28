package com.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.result.Result;
import com.exam.dto.AutoPaperDTO;
import com.exam.dto.PaperDTO;
import com.exam.dto.PaperPreviewDTO;
import com.exam.mapper.*;
import com.exam.pojo.*;
import com.exam.service.PaperGeneratorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 试卷管理控制器（教师）
 */
@RestController
@RequestMapping("/teacher/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final ExamMapper examMapper;
    private final PaperGeneratorService paperGeneratorService;

    /**
     * 分页查询试卷列表
     */
    @GetMapping
    public Result<Page<Paper>> listPapers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title) {

        Page<Paper> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(title), Paper::getTitle, title);
        wrapper.orderByDesc(Paper::getCreateTime);

        Page<Paper> result = paperMapper.selectPage(page, wrapper);
        result.getRecords().forEach(p -> {
            User creator = userMapper.selectById(p.getCreatorId());
            if (creator != null) p.setCreatorName(creator.getRealName());
        });

        return Result.success(result);
    }

    /**
     * 获取所有试卷（不分页，用于创建考试时选择）
     */
    @GetMapping("/all")
    public Result<List<Paper>> listAllPapers() {
        List<Paper> list = paperMapper.selectList(
                new LambdaQueryWrapper<Paper>().orderByDesc(Paper::getCreateTime));
        return Result.success(list);
    }

    /**
     * 根据ID获取试卷（含题目列表）
     */
    @GetMapping("/{id}")
    public Result<Paper> getPaper(@PathVariable Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            return Result.error(4001, "试卷不存在");
        }
        User creator = userMapper.selectById(paper.getCreatorId());
        if (creator != null) paper.setCreatorName(creator.getRealName());

        // 查询试卷中的题目
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, id)
                        .orderByAsc(PaperQuestion::getSortOrder));

        List<Question> questions = new ArrayList<>();
        for (PaperQuestion pq : paperQuestions) {
            Question question = questionMapper.selectById(pq.getQuestionId());
            if (question != null) {
                question.setPaperScore(pq.getScore());
                question.setSortOrder(pq.getSortOrder());
                question.setTypeDesc(com.exam.common.enums.QuestionTypeEnum.fromCode(question.getType()).getDescription());
                questions.add(question);
            }
        }
        // BUG-08修复：将题目列表设置到试卷对象再返回
        paper.setQuestions(questions);
        return Result.success(paper);
    }

    /**
     * 获取试卷的题目列表
     */
    @GetMapping("/{id}/questions")
    public Result<List<Question>> getPaperQuestions(@PathVariable Long id) {
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, id)
                        .orderByAsc(PaperQuestion::getSortOrder));

        List<Question> questions = new ArrayList<>();
        for (PaperQuestion pq : paperQuestions) {
            Question question = questionMapper.selectById(pq.getQuestionId());
            if (question != null) {
                question.setPaperScore(pq.getScore());
                question.setSortOrder(pq.getSortOrder());
                question.setTypeDesc(com.exam.common.enums.QuestionTypeEnum.fromCode(question.getType()).getDescription());
                questions.add(question);
            }
        }
        return Result.success(questions);
    }

    /**
     * 创建试卷（手工组卷）
     */
    @PostMapping
    public Result<Void> createPaper(@Valid @RequestBody PaperDTO dto, HttpServletRequest request) {
        Paper paper = new Paper();
        paper.setTitle(dto.getTitle());
        paper.setDescription(dto.getDescription());
        paper.setPassScore(dto.getPassScore() != null ? dto.getPassScore() : 60);
        paper.setCreatorId((Long) request.getAttribute("userId"));

        int totalScore = 0;
        for (PaperDTO.PaperQuestionDTO q : dto.getQuestions()) {
            totalScore += q.getScore();
        }
        paper.setTotalScore(totalScore);
        paper.setQuestionCount(dto.getQuestions().size());

        paperMapper.insert(paper);

        for (int i = 0; i < dto.getQuestions().size(); i++) {
            PaperDTO.PaperQuestionDTO q = dto.getQuestions().get(i);
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(q.getQuestionId());
            pq.setScore(q.getScore());
            pq.setSortOrder(i);
            paperQuestionMapper.insert(pq);
        }

        return Result.success();
    }

    // ==================== 智能组卷 ====================

    /**
     * 智能组卷 - 预览（不保存）
     */
    @PostMapping("/auto-generate/preview")
    public Result<PaperPreviewDTO> autoGeneratePreview(@Valid @RequestBody AutoPaperDTO config) {
        PaperPreviewDTO preview = paperGeneratorService.generatePreview(config);
        return Result.success(preview);
    }

    /**
     * 智能组卷 - 确认保存
     */
    @PostMapping("/auto-generate/confirm")
    public Result<PaperPreviewDTO> autoGenerateConfirm(@Valid @RequestBody AutoPaperDTO config,
                                                        HttpServletRequest request) {
        Long creatorId = (Long) request.getAttribute("userId");
        PaperPreviewDTO result = paperGeneratorService.generateAndSave(config, creatorId);
        return Result.success(result);
    }

    /**
     * 更新试卷
     */
    @PutMapping("/{id}")
    public Result<Void> updatePaper(@PathVariable Long id, @Valid @RequestBody PaperDTO dto) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            return Result.error(4001, "试卷不存在");
        }

        Long examCount = examMapper.selectCount(
                new LambdaQueryWrapper<Exam>().eq(Exam::getPaperId, id));
        if (examCount > 0) {
            return Result.error(4002, "试卷已关联考试，不可修改");
        }

        paper.setTitle(dto.getTitle());
        paper.setDescription(dto.getDescription());
        if (dto.getPassScore() != null) paper.setPassScore(dto.getPassScore());

        int totalScore = 0;
        for (PaperDTO.PaperQuestionDTO q : dto.getQuestions()) {
            totalScore += q.getScore();
        }
        paper.setTotalScore(totalScore);
        paper.setQuestionCount(dto.getQuestions().size());

        paperMapper.updateById(paper);

        paperQuestionMapper.delete(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));

        for (int i = 0; i < dto.getQuestions().size(); i++) {
            PaperDTO.PaperQuestionDTO q = dto.getQuestions().get(i);
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(id);
            pq.setQuestionId(q.getQuestionId());
            pq.setScore(q.getScore());
            pq.setSortOrder(i);
            paperQuestionMapper.insert(pq);
        }

        return Result.success();
    }

    /**
     * 删除试卷
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePaper(@PathVariable Long id) {
        Long examCount = examMapper.selectCount(
                new LambdaQueryWrapper<Exam>().eq(Exam::getPaperId, id));
        if (examCount > 0) {
            return Result.error(4002, "试卷已关联考试，不可删除");
        }

        paperQuestionMapper.delete(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        paperMapper.deleteById(id);
        return Result.success();
    }
}
