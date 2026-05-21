package com.suke.domain.dto.file;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class UploadFileDTO implements Serializable {
    private static final long serialVersionUID = 4561368974L;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotBlank(message = "分析目标不能为空")
    private String goal;

    @NotBlank(message = "图表类型不能为空")
    private String chartType;

    private Long fileSize;
    private Boolean enableSampling = false;
    private Integer sampleRows = 1000;
    private String minioPath;
}
