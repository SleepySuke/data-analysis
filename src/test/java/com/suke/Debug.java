package com.suke;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author 自然醒
 * @version 1.0
 */
@Component
public class Debug {
    @Autowired
    ApplicationContext context;

    @PostConstruct
    public void check(){
        System.out.println(Arrays.toString(context.getBeanNamesForType(VectorStore.class)));
    }

}
