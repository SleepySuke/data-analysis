package com.suke.domain.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {
    private static final long serialVersionUID = 3191241716373120793L;

    @NotBlank(message = "用户账号不能为空")
    @Size(min = 4, message = "用户账号长度不能小于4")
    private String userAccount;

    @NotBlank(message = "用户密码不能为空")
    @Size(min = 8, message = "用户密码长度不能小于8")
    private String userPassword;
}
