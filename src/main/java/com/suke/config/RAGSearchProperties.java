package com.suke.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.search")
@Data
public class RAGSearchProperties {

    private boolean hybridEnabled = false;
    private boolean rerankEnabled = false;
    private double similarityThreshold = 0.5;
    private int topK = 5;
    private int vectorTopK = 10;
    private int bm25TopK = 10;
    private int rrfK = 60;
    private String rerankModel = "gte-rerank-v2";
    private int rerankTopN = 5;
}
