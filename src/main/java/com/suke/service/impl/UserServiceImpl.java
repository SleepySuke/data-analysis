package com.suke.service.impl;

import com.suke.domain.entity.User;
import com.suke.mapper.UserMapper;
import com.suke.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 用户 服务实现类
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public User getLoginUser(HttpServletRequest request) {
        return null;
    }
}
