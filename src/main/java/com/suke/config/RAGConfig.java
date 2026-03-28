package com.suke.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;

/**
 * @author 自然醒
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "rag.redis")
@Slf4j
@Data
public class RAGConfig {
    private String host;
    private Integer port;


    private String indexName = "idx:data_analysis_knowledge_v1";
    private String vectorPrefix = "vec:knowledge:";
    private String knowledgeContent = "knowledge_content";
    private String metadataPrefix = "meta:knowledge:";
    private int embeddingDimensions = 1536;

    @Bean
    public JedisPooled jedisPooled() {
        log.info("Redis连接信息：{}", host + ":" + port);
        return new JedisPooled(host, port);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled,
                               @Qualifier("embeddingModel") EmbeddingModel embeddingModel){
        log.info("初始化 VectorStore，使用嵌入模型: {}，向量维度: {}",
                embeddingModel.getClass().getName(), embeddingDimensions);
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(vectorPrefix)
                .contentFieldName(knowledgeContent)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("knowledge_type"),
                        RedisVectorStore.MetadataField.tag("industry"),
                        RedisVectorStore.MetadataField.tag("chart_type"),
                        RedisVectorStore.MetadataField.tag("source")
                )
                .initializeSchema(true)
                .vectorAlgorithm(RedisVectorStore.Algorithm.HSNW)
                .build();
    }

}
