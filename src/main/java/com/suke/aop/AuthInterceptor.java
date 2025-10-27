package com.suke.aop;

import com.suke.annotation.AuthCheck;
import com.suke.common.ErrorCode;
import com.suke.context.UserContext;
import com.suke.domain.entity.User;
import com.suke.domain.enums.UserRoleEnum;
import com.suke.service.IUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 自然醒
 * @version 1.0
 */
//鉴权拦截器 aop
@Aspect
@Component
public class AuthInterceptor {
    @Autowired
    private IUserService userService;

    /**
     * 执行鉴权拦截
     * @param joinPoint
     * @param authcheck
     * @return
     */
    @Around("@annotation(authcheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authcheck) throws Throwable {
        //获取到当前被注解的方法的用户 即登录用户
        String mustRole = authcheck.mustRole();
//        //通过RequestAttributes获取当前登录用户
//        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
//        //通过所有的请求属性获取当前登录用户
//        HttpServletRequest request =((ServletRequestAttributes) requestAttributes).getRequest();

        //当前用户
        Long userId = UserContext.getCurrentId();
        User loginUser = null;
        if(userId != null){
            loginUser = userService.getById(userId);
        }
        //无需权限 放行
        if (mustRole == null) {
            return joinPoint.proceed();
        }
        //权限验证 必须有权限才可以通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null || UserRoleEnum.valueOf(mustRole) != userRoleEnum) {
            throw new RuntimeException(ErrorCode.NO_AUTH_ERROR.getMessage());
        }
        //被封号
        if (UserRoleEnum.BAN.equals(userRoleEnum)) {
            throw new RuntimeException(ErrorCode.NO_AUTH_ERROR.getMessage());
        }
        //必须有管理员权限
        if(UserRoleEnum.ADMIN.equals(mustRole)){
            //判断当前用户是否是管理员
            if(!UserRoleEnum.ADMIN.equals(userRoleEnum)){
                throw new RuntimeException(ErrorCode.NO_AUTH_ERROR.getMessage());
            }
        }
        //放行
        return joinPoint.proceed();
    }
}
