package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.pojo.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /**
     * 物理删除考试记录（绕过逻辑删除，避免唯一索引冲突）
     */
    @Update("DELETE FROM exam_record WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 物理删除该学生在指定考试下所有已逻辑删除的记录（清理唯一索引残留）
     */
    @Update("DELETE FROM exam_record WHERE exam_id = #{examId} AND student_id = #{studentId} AND deleted = 1")
    int physicalDeleteDeleted(@Param("examId") Long examId, @Param("studentId") Long studentId);

    /**
     * 物理删除指定考试下所有记录（包括已逻辑删除的，用于删除考试时清理）
     */
    @Update("DELETE FROM exam_record WHERE exam_id = #{examId}")
    int physicalDeleteByExamId(@Param("examId") Long examId);

    /**
     * 原子增加切屏次数（避免并发竞态条件）
     */
    @Update("UPDATE exam_record SET switch_count = switch_count + 1 WHERE id = #{id} AND status = 0")
    int incrementSwitchCount(@Param("id") Long id);
}
