package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDTO {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度为3-20个字符")
    private String username;

    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    private String password;

    private String realName;

    private String email;

    private String phone;

    @NotNull(message = "角色不能为空")
    private Long roleId;

    private Integer status;
}
