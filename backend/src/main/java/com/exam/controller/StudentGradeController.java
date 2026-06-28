package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.pojo.ExamRecord;
import com.exam.service.ExamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生成绩控制器
 */
@RestController
@RequestMapping("/student/grades")
@RequiredArgsConstructor
public class StudentGradeController {

    private final ExamService examService;

    /**
     * 获取我的成绩列表
     */
    @GetMapping
    public Result<List<ExamRecord>> myGrades(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<ExamRecord> grades = examService.getStudentGrades(studentId);
        return Result.success(grades);
    }
}
