package com.suke.context;

/**
 * @author 自然醒
 * @version 1.0
 */
//用户上下文
public class UserContext {
    private static final ThreadLocal<Long> currentId = new ThreadLocal<>();

    public static void setCurrentId(Long id){
        currentId.set(id);
    }

    public static Long getCurrentId(){
        return currentId.get();
    }

    public static void removeCurrentId(){
        currentId.remove();
    }
}
