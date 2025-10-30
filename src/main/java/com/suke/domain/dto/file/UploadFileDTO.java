package com.suke.domain.dto.file;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
@Data
public class UploadFileDTO implements Serializable {
    private static final long serialVersionUID = 4561368974L;
    /**
     * 文件名
     */
    private String fileName;

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图表类型
     */
    private String chartType;
}
