package com.suke.domain.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 自然醒
 * @version 1.0
 */
//用户枚举
public enum UserRoleEnum {
    USER("用户", "user"),
    ADMIN("管理员", "admin"),
    BAN("被封禁账号", "ban");

    private final String text;
    private final String name;

    UserRoleEnum(String text, String name) {
        this.text = text;
        this.name = name;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<String> getnames() {
        return Arrays.stream(values()).map(item -> item.name).collect(Collectors.toList());
    }

    /**
     * 根据 name 获取枚举
     *
     * @param name
     * @return
     */
    public static UserRoleEnum getEnumByname(String name) {
        if (ObjectUtils.isEmpty(name)) {
            return null;
        }
        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
            if (anEnum.name.equals(name)) {
                return anEnum;
            }
        }
        return null;
    }

    public String getname() {
        return name;
    }

    public String getText() {
        return text;
    }
}
