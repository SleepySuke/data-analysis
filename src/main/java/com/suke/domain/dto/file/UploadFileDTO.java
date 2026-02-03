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

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 是否启用采样（针对大文件）
     */
    private Boolean enableSampling = false;

    /**
     * 采样行数（默认1000行）
     */
    private Integer sampleRows = 1000;

    /**
     * MinIO文件路径（处理后）
     */
    private String minioPath;
}
