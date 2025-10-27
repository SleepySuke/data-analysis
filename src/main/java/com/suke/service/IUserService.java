package com.suke.service;

import com.suke.domain.dto.user.UserLoginDTO;
import com.suke.domain.dto.user.UserRegisterDTO;
import com.suke.domain.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.suke.domain.vo.LoginUserVO;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 用户 服务类
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
public interface IUserService extends IService<User> {


    /**
     * 用户登录
     * @param userLoginDTO
     * @return
     */
    LoginUserVO userLogin(UserLoginDTO userLoginDTO, HttpServletRequest  request);

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    Long userRegister(UserRegisterDTO userRegisterDTO, HttpServletRequest request);

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
}
