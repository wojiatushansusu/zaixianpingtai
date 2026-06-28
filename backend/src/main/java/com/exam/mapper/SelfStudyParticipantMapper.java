package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.pojo.SelfStudyParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SelfStudyParticipantMapper extends BaseMapper<SelfStudyParticipant> {

    /**
     * 物理删除已逻辑删除的记录（避免唯一索引冲突）
     */
    @Update("DELETE FROM self_study_participant WHERE exam_id = #{examId} AND student_id = #{studentId} AND deleted = 1")
    int physicalDeleteDeleted(@Param("examId") Long examId, @Param("studentId") Long studentId);
}
