package com.exam.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.enums.DifficultyEnum;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.common.result.Result;
import com.exam.dto.QuestionDTO;
import com.exam.dto.QuestionImportDTO;
import com.exam.dto.QuestionPreviewResultDTO;
import com.exam.listener.QuestionImportListener;
import com.exam.listener.QuestionPreviewListener;
import com.exam.mapper.*;
import com.exam.pojo.*;
import com.exam.service.AiClassifyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题库管理控制器（教师）
 */
@RestController
@RequestMapping("/teacher/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final QuestionTagMapper questionTagMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final AiClassifyService aiClassifyService;

    /**
     * 分页查询题目列表（支持标签和难度筛选）
     */
    @GetMapping
    public Result<Page<Question>> listQuestions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long tagId) {

        Page<Question> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(keyword), Question::getContent, keyword);
        wrapper.eq(type != null, Question::getType, type);
        wrapper.eq(difficulty != null, Question::getDifficulty, difficulty);
        wrapper.orderByDesc(Question::getCreateTime);

        // 如果有标签筛选，先查询该标签下的题目ID
        if (tagId != null) {
            List<Long> questionIds = questionTagMapper.selectList(
                    new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getTagId, tagId)
            ).stream().map(QuestionTag::getQuestionId).collect(Collectors.toList());

            if (questionIds.isEmpty()) {
                // 没有匹配的题目，返回空结果
                return Result.success(new Page<>(pageNum, pageSize, 0));
            }
            wrapper.in(Question::getId, questionIds);
        }

        Page<Question> result = questionMapper.selectPage(page, wrapper);

        if (!result.getRecords().isEmpty()) {
            batchFillQuestionDetails(result.getRecords());
        }

        return Result.success(result);
    }

    /**
     * 获取所有题目（不分页，用于组卷选题）
     */
    @GetMapping("/all")
    public Result<List<Question>> listAllQuestions(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null, Question::getType, type);
        wrapper.eq(difficulty != null, Question::getDifficulty, difficulty);
        wrapper.like(StringUtils.hasText(keyword), Question::getContent, keyword);
        wrapper.orderByDesc(Question::getCreateTime);

        if (tagId != null) {
            List<Long> questionIds = questionTagMapper.selectList(
                    new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getTagId, tagId)
            ).stream().map(QuestionTag::getQuestionId).collect(Collectors.toList());

            if (questionIds.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            wrapper.in(Question::getId, questionIds);
        }

        List<Question> list = questionMapper.selectList(wrapper);
        if (!list.isEmpty()) {
            batchFillQuestionDetails(list);
        }
        return Result.success(list);
    }

    /**
     * 根据ID获取题目
     */
    @GetMapping("/{id}")
    public Result<Question> getQuestion(@PathVariable Long id) {
        Question question = questionMapper.selectById(id);
        if (question != null) {
            question.setTypeDesc(QuestionTypeEnum.fromCode(question.getType()).getDescription());
            question.setDifficultyDesc(DifficultyEnum.fromCode(question.getDifficulty()).getDescription());
            fillQuestionTags(question);
        }
        return Result.success(question);
    }

    /**
     * 创建题目（支持标签）
     */
    @PostMapping
    public Result<Void> createQuestion(@Valid @RequestBody QuestionDTO dto, HttpServletRequest request) {
        Question question = new Question();
        BeanUtils.copyProperties(dto, question);
        question.setCreatorId((Long) request.getAttribute("userId"));
        if (question.getScore() == null) question.setScore(5);
        if (question.getDifficulty() == null) question.setDifficulty(2);

        questionMapper.insert(question);

        // 保存标签关联
        saveQuestionTags(question.getId(), dto.getTagIds());

        return Result.success();
    }

    /**
     * 更新题目
     */
    @PutMapping("/{id}")
    public Result<Void> updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionDTO dto) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            return Result.error(3001, "题目不存在");
        }

        if (dto.getType() != null) question.setType(dto.getType());
        if (StringUtils.hasText(dto.getContent())) question.setContent(dto.getContent());
        if (dto.getOptionA() != null) question.setOptionA(dto.getOptionA());
        if (dto.getOptionB() != null) question.setOptionB(dto.getOptionB());
        if (dto.getOptionC() != null) question.setOptionC(dto.getOptionC());
        if (dto.getOptionD() != null) question.setOptionD(dto.getOptionD());
        if (StringUtils.hasText(dto.getAnswer())) question.setAnswer(dto.getAnswer());
        if (dto.getAnalysis() != null) question.setAnalysis(dto.getAnalysis());
        if (dto.getScore() != null) question.setScore(dto.getScore());
        if (dto.getDifficulty() != null) question.setDifficulty(dto.getDifficulty());

        questionMapper.updateById(question);

        // 更新标签关联
        if (dto.getTagIds() != null) {
            questionTagMapper.delete(
                    new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getQuestionId, id));
            saveQuestionTags(id, dto.getTagIds());
        }

        return Result.success();
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        // 检查是否被试卷引用
        Long paperRefCount = paperQuestionMapper.selectCount(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getQuestionId, id));
        if (paperRefCount > 0) {
            return Result.error(3002, "该题目已被试卷引用，无法删除，请先从试卷中移除");
        }

        questionMapper.deleteById(id);
        // 同时删除标签关联
        questionTagMapper.delete(
                new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getQuestionId, id));
        return Result.success();
    }

    /**
     * 批量删除题目
     */
    @DeleteMapping("/batch")
    public Result<Map<String, Object>> batchDeleteQuestions(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的题目");
        }

        // 检查是否有题目被试卷引用
        List<Long> referencedIds = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>().in(PaperQuestion::getQuestionId, ids)
        ).stream().map(PaperQuestion::getQuestionId).distinct().collect(Collectors.toList());

        if (!referencedIds.isEmpty()) {
            // 返回哪些题目被引用了
            List<Question> referencedQuestions = questionMapper.selectBatchIds(referencedIds);
            String names = referencedQuestions.stream()
                    .map(q -> "ID:" + q.getId() + "「" + (q.getContent() != null && q.getContent().length() > 15
                            ? q.getContent().substring(0, 15) + "..." : q.getContent()) + "」")
                    .collect(Collectors.joining("、"));
            return Result.error(3002, "以下题目已被试卷引用，无法删除：" + names);
        }

        // 批量删除
        questionMapper.deleteBatchIds(ids);

        // 批量删除标签关联
        questionTagMapper.delete(
                new LambdaQueryWrapper<QuestionTag>().in(QuestionTag::getQuestionId, ids));

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", ids.size());
        return Result.success(result);
    }

    /**
     * Excel批量导入题目
     * 支持不同列名格式的Excel文件（.xls / .xlsx），自动识别表头列名
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importQuestions(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {

        if (file.isEmpty()) {
            return Result.error("请选择要导入的Excel文件");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return Result.error("请上传 .xlsx 或 .xls 格式的Excel文件");
        }

        Long creatorId = (Long) request.getAttribute("userId");
        QuestionImportListener listener = new QuestionImportListener(
                questionMapper, knowledgeTagMapper, questionTagMapper, creatorId);

        // 使用 Map<Integer, String> 读取，不绑定具体DTO类，由Listener内部动态映射列名
        EasyExcel.read(file.getInputStream(), listener).sheet().doRead();

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", listener.getSuccessCount());
        result.put("errorCount", listener.getErrors().size());
        result.put("errors", listener.getErrors());

        return Result.success(result);
    }

    /**
     * Excel导入预检 —— 只校验不写入数据库
     * 返回每行的校验状态(ok/warning/error)和具体问题
     */
    @PostMapping("/import/preview")
    public Result<QuestionPreviewResultDTO> previewImport(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return Result.error("请选择要导入的Excel文件");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return Result.error("请上传 .xlsx 或 .xls 格式的Excel文件");
        }

        QuestionPreviewListener listener = new QuestionPreviewListener();
        EasyExcel.read(file.getInputStream(), listener).sheet().doRead();

        return Result.success(listener.getResult());
    }

    /**
     * 确认导入 —— 带行级别过滤（只导入用户勾选的行）
     * 前端传回需要导入的行号列表，跳过被排除的行
     */
    @PostMapping("/import/confirm")
    public Result<Map<String, Object>> confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "includeRows", required = false) String includeRows,
            HttpServletRequest request) throws IOException {

        if (file.isEmpty()) {
            return Result.error("请选择要导入的Excel文件");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return Result.error("请上传 .xlsx 或 .xls 格式的Excel文件");
        }

        Long creatorId = (Long) request.getAttribute("userId");

        // 解析需要包含的行号集合（为空则导入全部）
        Set<Integer> includeSet = new HashSet<>();
        if (includeRows != null && !includeRows.trim().isEmpty()) {
            for (String s : includeRows.split(",")) {
                try {
                    includeSet.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        QuestionImportListener listener = new QuestionImportListener(
                questionMapper, knowledgeTagMapper, questionTagMapper, creatorId, includeSet);

        EasyExcel.read(file.getInputStream(), listener).sheet().doRead();

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", listener.getSuccessCount());
        result.put("skippedCount", listener.getSkippedCount());
        result.put("errorCount", listener.getErrors().size());
        result.put("errors", listener.getErrors());

        return Result.success(result);
    }

    /**
     * 下载导入模板
     */
    @GetMapping("/import-template")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("题目导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 写入示例数据和表头（3种题型各1条）
        List<QuestionImportDTO> data = new ArrayList<>();

        // 单选题示例
        QuestionImportDTO single = new QuestionImportDTO();
        single.setTypeStr("单选题");
        single.setContent("Java中String属于什么类型？");
        single.setOptionA("基本数据类型");
        single.setOptionB("引用数据类型");
        single.setOptionC("原生类型");
        single.setOptionD("枚举类型");
        single.setAnswer("B");
        single.setAnalysis("String是引用类型，不是基本数据类型");
        single.setScore(5);
        single.setDifficultyStr("简单");
        single.setTagNames("Java基础");
        data.add(single);

        // 多选题示例
        QuestionImportDTO multi = new QuestionImportDTO();
        multi.setTypeStr("多选题");
        multi.setContent("以下哪些是Java的基本数据类型？");
        multi.setOptionA("int");
        multi.setOptionB("String");
        multi.setOptionC("boolean");
        multi.setOptionD("double");
        multi.setAnswer("A,C,D");
        multi.setAnalysis("String是引用类型，其余为基本数据类型");
        multi.setScore(6);
        multi.setDifficultyStr("中等");
        multi.setTagNames("Java基础");
        data.add(multi);

        // 判断题示例
        QuestionImportDTO judge = new QuestionImportDTO();
        judge.setTypeStr("判断题");
        judge.setContent("Java中final修饰的变量值不可修改");
        judge.setOptionA("正确");
        judge.setOptionB("错误");
        judge.setAnswer("正确");
        judge.setAnalysis("final修饰基本类型变量值不可变，引用类型变量引用不可变");
        judge.setScore(4);
        judge.setDifficultyStr("简单");
        judge.setTagNames("Java基础");
        data.add(judge);

        // 简答题示例
        QuestionImportDTO shortAnswer = new QuestionImportDTO();
        shortAnswer.setTypeStr("简答题");
        shortAnswer.setContent("请简述Java中ArrayList和LinkedList的区别");
        shortAnswer.setAnswer("ArrayList基于动态数组实现，随机访问效率高O(1)，插入删除效率低O(n)；LinkedList基于双向链表实现，随机访问效率低O(n)，插入删除效率高O(1)。");
        shortAnswer.setAnalysis("考察对Java集合框架底层数据结构的理解");
        shortAnswer.setScore(10);
        shortAnswer.setDifficultyStr("中等");
        shortAnswer.setTagNames("Java基础");
        data.add(shortAnswer);

        EasyExcel.write(response.getOutputStream(), QuestionImportDTO.class)
                .sheet("题目导入模板")
                .doWrite(data);
    }

    /**
     * 获取所有标签列表
     */
    @GetMapping("/tags")
    public Result<List<KnowledgeTag>> listTags() {
        List<KnowledgeTag> tags = knowledgeTagMapper.selectList(
                new LambdaQueryWrapper<KnowledgeTag>().orderByAsc(KnowledgeTag::getSortOrder));
        return Result.success(tags);
    }

    /**
     * 创建标签
     */
    @PostMapping("/tags")
    public Result<KnowledgeTag> createTag(@RequestBody Map<String, String> params) {
        String name = params.get("name");
        if (!StringUtils.hasText(name)) {
            return Result.error("标签名称不能为空");
        }

        // 检查标签名是否已存在
        Long existCount = knowledgeTagMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getName, name.trim()));
        if (existCount > 0) {
            // 返回已有的标签，前端可以继续使用
            KnowledgeTag existing = knowledgeTagMapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getName, name.trim()));
            return Result.success(existing);
        }

        KnowledgeTag tag = new KnowledgeTag();
        tag.setName(name.trim());
        tag.setSortOrder(99);
        knowledgeTagMapper.insert(tag);
        return Result.success(tag);
    }

    /**
     * 批量更新题目标签（仅更新标签关联，不触碰题目本身）
     */
    @PutMapping("/batch-tags")
    public Result<Void> batchUpdateTags(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Integer> questionIds = (List<Integer>) params.get("questionIds");
        @SuppressWarnings("unchecked")
        List<Integer> tagIds = (List<Integer>) params.get("tagIds");

        if (questionIds == null || questionIds.isEmpty()) {
            return Result.error("请选择要更新标签的题目");
        }

        // 先清除这些题目的旧标签关联
        questionTagMapper.delete(
                new LambdaQueryWrapper<QuestionTag>()
                        .in(QuestionTag::getQuestionId,
                                questionIds.stream().map(Long::valueOf).collect(Collectors.toList())));

        // 建立新的标签关联
        if (tagIds != null && !tagIds.isEmpty()) {
            for (Integer qId : questionIds) {
                for (Integer tId : tagIds) {
                    QuestionTag qt = new QuestionTag();
                    qt.setQuestionId(qId.longValue());
                    qt.setTagId(tId.longValue());
                    questionTagMapper.insert(qt);
                }
            }
        }

        return Result.success();
    }

    /**
     * AI 智能分类 —— 分析题目并返回推荐的知识点标签和难度
     * 不直接修改数据库，返回分类结果供前端确认
     */
    @PostMapping("/ai-classify")
    public Result<AiClassifyService.ClassifyResult> aiClassifyQuestions(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Integer> questionIds = (List<Integer>) params.get("questionIds");

        if (questionIds == null || questionIds.isEmpty()) {
            return Result.error("请选择要分析的题目");
        }

        // 获取已有标签列表
        List<KnowledgeTag> tags = knowledgeTagMapper.selectList(
                new LambdaQueryWrapper<KnowledgeTag>().orderByAsc(KnowledgeTag::getSortOrder));
        List<String> existingTagNames = tags.stream().map(KnowledgeTag::getName).collect(Collectors.toList());

        List<Long> ids = questionIds.stream().map(Long::valueOf).collect(Collectors.toList());
        AiClassifyService.ClassifyResult result = aiClassifyService.classifyQuestions(ids, existingTagNames);
        return Result.success(result);
    }

    /**
     * 应用 AI 分类结果 —— 将推荐的标签和难度写入数据库
     */
    @PostMapping("/ai-classify/apply")
    public Result<Void> applyAiClassifyResult(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) params.get("items");

        if (items == null || items.isEmpty()) {
            return Result.error("没有要应用的分类结果");
        }

        List<AiClassifyService.ClassifyItem> classifyItems = new ArrayList<>();
        for (Map<String, Object> item : items) {
            AiClassifyService.ClassifyItem ci = new AiClassifyService.ClassifyItem();
            ci.setQuestionId(convertToLong(item.get("questionId")));
            ci.setSuccess((Boolean) item.getOrDefault("success", false));
            ci.setSuggestedTags((List<String>) item.get("suggestedTags"));
            Object diff = item.get("suggestedDifficulty");
            if (diff instanceof Number) {
                ci.setSuggestedDifficulty(((Number) diff).intValue());
            }
            classifyItems.add(ci);
        }

        aiClassifyService.applyClassifyResult(classifyItems);
        return Result.success();
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        // 检查是否有题目关联
        Long count = questionTagMapper.selectCount(
                new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getTagId, id));
        if (count > 0) {
            return Result.error("该标签下还有题目，不能删除");
        }
        knowledgeTagMapper.deleteById(id);
        return Result.success();
    }

    // ==================== 私有方法 ====================

    private void batchFillQuestionDetails(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return;

        Set<Long> creatorIds = questions.stream()
                .map(Question::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> creatorNameMap = new HashMap<>();
        if (!creatorIds.isEmpty()) {
            userMapper.selectBatchIds(creatorIds).forEach(u ->
                    creatorNameMap.put(u.getId(), u.getRealName()));
        }

        List<Long> questionIds = questions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        List<QuestionTag> allQuestionTags = questionTagMapper.selectList(
                new LambdaQueryWrapper<QuestionTag>().in(QuestionTag::getQuestionId, questionIds));

        Map<Long, List<Long>> questionTagMap = new HashMap<>();
        Set<Long> allTagIds = new HashSet<>();
        for (QuestionTag qt : allQuestionTags) {
            questionTagMap.computeIfAbsent(qt.getQuestionId(), k -> new ArrayList<>()).add(qt.getTagId());
            allTagIds.add(qt.getTagId());
        }

        Map<Long, String> tagNameMap = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            knowledgeTagMapper.selectBatchIds(allTagIds).forEach(t ->
                    tagNameMap.put(t.getId(), t.getName()));
        }

        for (Question q : questions) {
            q.setTypeDesc(QuestionTypeEnum.fromCode(q.getType()).getDescription());
            q.setDifficultyDesc(DifficultyEnum.fromCode(q.getDifficulty()).getDescription());

            if (q.getCreatorId() != null) {
                q.setCreatorName(creatorNameMap.get(q.getCreatorId()));
            }

            List<Long> tagIds = questionTagMap.get(q.getId());
            if (tagIds != null && !tagIds.isEmpty()) {
                q.setTagIds(tagIds);
                q.setTagNames(tagIds.stream()
                        .map(tagNameMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
            } else {
                q.setTagIds(Collections.emptyList());
                q.setTagNames(Collections.emptyList());
            }
        }
    }

    private void fillQuestionTags(Question q) {
        List<QuestionTag> qts = questionTagMapper.selectList(
                new LambdaQueryWrapper<QuestionTag>().eq(QuestionTag::getQuestionId, q.getId()));
        if (!qts.isEmpty()) {
            List<Long> tagIds = qts.stream().map(QuestionTag::getTagId).collect(Collectors.toList());
            List<KnowledgeTag> tags = knowledgeTagMapper.selectBatchIds(tagIds);
            q.setTagIds(tagIds);
            q.setTagNames(tags.stream().map(KnowledgeTag::getName).collect(Collectors.toList()));
        } else {
            q.setTagIds(Collections.emptyList());
            q.setTagNames(Collections.emptyList());
        }
    }

    private void saveQuestionTags(Long questionId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        for (Long tagId : tagIds) {
            QuestionTag qt = new QuestionTag();
            qt.setQuestionId(questionId);
            qt.setTagId(tagId);
            questionTagMapper.insert(qt);
        }
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
}
