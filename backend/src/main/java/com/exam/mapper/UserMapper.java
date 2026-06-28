package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT u.id, u.username, u.password, u.real_name, u.student_no, u.email, u.phone, u.avatar, u.role_id, u.status, u.create_time, u.update_time, u.deleted, r.role_name, r.role_key FROM user u LEFT JOIN role r ON u.role_id = r.id AND r.deleted = 0 WHERE u.id = #{id} AND u.deleted = 0")
    User selectUserWithRole(@Param("id") Long id);

    @Select("SELECT u.id, u.username, u.password, u.real_name, u.student_no, u.email, u.phone, u.avatar, u.role_id, u.status, u.create_time, u.update_time, u.deleted, r.role_name, r.role_key FROM user u LEFT JOIN role r ON u.role_id = r.id AND r.deleted = 0 WHERE u.username = #{username} AND u.deleted = 0")
    User selectByUsernameWithRole(@Param("username") String username);
}
