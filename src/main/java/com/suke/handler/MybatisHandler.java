package com.suke.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author 自然醒
 * @version 1.0
 */
//填充处理器
@Component
@Slf4j
public class MybatisHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("开始插入填充 - 设置 createTime 和 updateTime");
        this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        log.info("更新填充完成");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始更新填充 - 设置 updateTime");
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        log.info("更新填充完成");
    }
}
