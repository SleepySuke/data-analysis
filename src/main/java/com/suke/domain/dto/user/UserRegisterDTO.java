package com.suke.domain.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
//用户注册
@Data
public class UserRegisterDTO implements Serializable {
    private static final long serialVersionUID = 3191241716373120793L;

    private String userAccount;

    private String userPassword;

    private String checkPassword;
}
