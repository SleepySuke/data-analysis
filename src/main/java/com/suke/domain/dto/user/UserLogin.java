package com.suke.domain.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
//登录参数
@Data
public class UserLogin implements Serializable {
    private static final long serialVersionUID = 3191241716373120793L;

    private String userAccount;

    private String userPassword;
}
