package com.suke.controller;


import cn.hutool.core.bean.BeanUtil;
import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.domain.dto.user.UserLoginDTO;
import com.suke.domain.dto.user.UserRegisterDTO;
import com.suke.domain.entity.User;
import com.suke.domain.vo.LoginUserVO;
import com.suke.exception.FailLoginException;
import com.suke.exception.FailRegisterException;
import com.suke.service.IUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;




/**
 * <p>
 * 用户 前端控制器
 * </p>
 *
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private IUserService userService;

    /**
     * 用户登录
     * @param userLoginDTO
     * @param request
     * @return
     */
    @PostMapping("/login")
    public Result<LoginUserVO> login(@RequestBody UserLoginDTO userLoginDTO, HttpServletRequest request){
        log.info("用户登录：{}", userLoginDTO);
        if(userLoginDTO == null){
            log.error("用户登录参数错误");
            throw new FailLoginException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        LoginUserVO loginUserVO = userService.userLogin(userLoginDTO, request);
        log.info("用户登录成功：{}",loginUserVO);
        return Result.success(loginUserVO);
    }

    /**
     * 用户注册
     * @param userRegisterDTO
     * @param request
     * @return
     */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody UserRegisterDTO userRegisterDTO, HttpServletRequest request){
        log.info("用户注册：{}", userRegisterDTO);
        if(userRegisterDTO == null){
            log.error("用户注册参数错误");
            throw new FailRegisterException(ErrorCode.PARAMS_ERROR.getMessage());
        }
        Long res = userService.userRegister(userRegisterDTO, request);
        log.info("用户注册成功：{}",res);
        return Result.success(res);
    }

    @GetMapping("/getLoginUser")
    public Result<LoginUserVO> getLoginUser(HttpServletRequest request){
        User user = userService.getLoginUser(request);
        if(user == null){
            return Result.error(ErrorCode.NOT_LOGIN_ERROR.getMessage());
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user,loginUserVO);
        return Result.success(loginUserVO);
    }
}
