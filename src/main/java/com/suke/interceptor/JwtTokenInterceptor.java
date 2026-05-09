package com.suke.interceptor;

import com.suke.context.UserContext;
import com.suke.domain.entity.User;
import com.suke.properties.JWTProperties;
import com.suke.service.IUserService;
import com.suke.utils.JWTUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;


/**
 * @author 自然醒
 * @version 1.0
 */
//JWT令牌拦截器
@Component
@Slf4j
public class JwtTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private JWTProperties jwtProperties;
    @Resource
    private IUserService userService;

    /**
     * 拦截器
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }
        //从请求头中读取JWT令牌中token
        String token = request.getHeader(jwtProperties.getTokenName());
//        log.info("JWT令牌：{}，请求头名称：{}", token, jwtProperties.getTokenName());
        //token为空，未登录，拒绝访问
        if(StringUtils.isAnyBlank(token)){
            response.setStatus(401);
            return false;
        }
        // 去除Bearer前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        //验证令牌
        try{
            Claims claims = JWTUtil.parseJWT(jwtProperties.getSecretKey(), token);
            Long userId = claims.get("id", Long.class);
            if(userId == null){
                return false;
            }
            User loginUser = userService.getById(userId);
            if(loginUser == null){
                response.setStatus(401);
                return false;
            }
            UserContext.setCurrentId(userId);
            return true;
        }catch (Exception e){
            log.error("令牌验证失败:{}",e.getMessage());
//            不允许访问
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 拦截器后置处理
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.removeCurrentId();
    }
}
