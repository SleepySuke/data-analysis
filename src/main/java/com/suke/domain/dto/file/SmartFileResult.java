package com.suke.domain.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author 自然醒
 * @version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SmartFileResult implements Serializable {
    private static final long serialVersionUID = 4561368974L;

    private String sampledData;      // 采样数据
    private String originalMinioPath; // 原始文件MinIO路径
    private String sampledMinioPath; // 采样数据MinIO路径
    private List<String> headers;    // 表头
    private int totalRows;          // 总行数
}
