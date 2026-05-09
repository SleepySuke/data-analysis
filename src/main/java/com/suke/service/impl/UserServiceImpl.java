package com.suke.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.suke.common.ErrorCode;
import com.suke.context.UserContext;
import com.suke.domain.dto.user.UserLoginDTO;
import com.suke.domain.dto.user.UserRegisterDTO;
import com.suke.domain.entity.User;
import com.suke.domain.vo.LoginUserVO;
import com.suke.exception.FailLoginException;
import com.suke.exception.FailRegisterException;
import com.suke.mapper.UserMapper;
import com.suke.properties.JWTProperties;
import com.suke.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suke.utils.JWTUtil;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

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
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    //加密的盐值
    private static final String SALT = "suke";

    @Autowired
    private UserMapper userMapper;
    @Resource
    private JWTProperties jwtProperties;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginUserVO userLogin(UserLoginDTO userLoginDTO, HttpServletRequest request) {
        String userAccount = userLoginDTO.getUserAccount();
        String userPassword = userLoginDTO.getUserPassword();
        log.info("用户登录：{}", userAccount);
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            log.error("登录参数为空");
            throw new FailLoginException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        if (userAccount.length() < 4) {
            log.error("用户账号不符合格式");
            throw new FailLoginException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        if (userPassword.length() < 8) {
            log.error("用户密码不符合格式");
            throw new FailLoginException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null || user.equals("")) {
            log.error("用户不存在");
            throw new FailLoginException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        String encryptPasswd = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        String correctPasswd = user.getUserPassword();
        if (!encryptPasswd.equals(correctPasswd)) {
            log.error("用户密码错误");
            throw new FailLoginException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("userName", user.getUserName());
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        String token = JWTUtil.createJWT(
                jwtProperties.getSecretKey(),
                jwtProperties.getTtl(),
                claims
        );
        loginUserVO.setToken(token);
        return loginUserVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long userRegister(UserRegisterDTO userRegisterDTO, HttpServletRequest request) {
        String userAccount = userRegisterDTO.getUserAccount();
        String userPassword = userRegisterDTO.getUserPassword();
        String checkPassword = userRegisterDTO.getCheckPassword();
        log.info("用户注册：{}", userAccount);
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            log.error("注册参数为空");
            throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        if (userAccount.length() < 4) {
            log.error("用户账号不符合格式");
            throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            log.error("用户密码不符合格式");
            throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        if (!userPassword.equals(checkPassword)) {
            log.error("两次输入的密码不一致");
            throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        //查询账户是否存在，使用synchronized进行同步搜索，防止重复注册账号
        synchronized (userAccount.intern()) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_account", userAccount);
            long count = userMapper.selectCount(queryWrapper);
            if (count > 0) {
                log.error("用户已存在");
                throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
            }
            //使用md5加密密码
            String encryptPasswd = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPasswd);
            if (userMapper.insert(user) < 0) {
                log.error("用户注册失败");
                throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
            }
            log.info("用户注册成功：{}", user);
            return user.getId();
        }
    }


    @Override
    public User getLoginUser(HttpServletRequest request) {
        Long userId = UserContext.getCurrentId();
        log.info("当前用户id：{}", userId);
        if(userId == null){
            log.error("用户未登录");
            return null;
        }
       return this.getById(userId);
    }
}
