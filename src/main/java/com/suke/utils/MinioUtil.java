package com.suke.utils;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author 自然醒
 * @version 1.0
 */
@Component
@Slf4j
public class MinioUtil {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;


    private void init(){
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建bucket成功->{}", bucketName);
            }
        }catch (Exception e){
            log.error("初始化MinIO的Bucket失败", e);
        }
    }

    /**
     * 上传文件
     * @param file
     * @param objectName
     * @return
     */
    public String uploadFile(MultipartFile file,String objectName) {
        try{
            init();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("上传成功->{}", objectName);
            return objectName;
        }catch (Exception e){
            log.error("上传文件失败", e);
            return null;
        }
    }

    /**
     * 上传csv文件
     * @param csvData
     * @param objectName
     * @return
     */
    public String uploadCsvFile(String csvData,String objectName){
        try {
            init();
            byte[] bytes = csvData.getBytes("UTF-8");
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, bytes.length, -1)
                            .contentType("text/csv")
                            .build()
            );
            log.info("上传成功->{}",objectName);
            return objectName;
        }catch (Exception e){
            log.error("上传文件失败",e);
            return null;
        }
    }

    /**
     * 获取文件url
     * @param objectName
     * @return
     */
    public String getFileUrl(String objectName){
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );
        }catch (Exception e){
            log.error("获取文件url失败",e);
            return null;
        }
    }

    /**
     * 获取文件输入流（用于处理大文件）
     */
    public InputStream getFileStream(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("获取文件流失败", e);
            throw new RuntimeException("获取文件流失败");
        }
    }

    /**
     * 删除文件
     */
    public boolean deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}", objectName);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return false;
        }
    }

}
