package com.suke.utils;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 自然醒
 * @version 1.0
 */

//文件工具类
@Slf4j
public class FileUtils {

    /**
     * excel文件转换成csv文件
     * @param multipartFile
     * @return
     */
    public static String excelToCsv(MultipartFile multipartFile){
        log.info("上传的文件：{}",multipartFile);
//        File file = null;
//        try{
//            file = ResourceUtils.getFile("classpath:test_excel.xlsx");
//        }catch (Exception e){
//            log.error("文件转换异常");
//            throw new RuntimeException(e.getMessage());
//        }
//        log.info("文件转换成功：{}",file);
        //读取excel文件，返回的是一个以列索引为键，列数据为值的Map
        List<Map<Integer,String>> csvList = null;
        try{
           csvList =  EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        }catch (Exception e){
            log.error("文件转换异常");
            throw new RuntimeException(e.getMessage());
        }
        if(CollUtil.isEmpty(csvList)){
            log.error("数据为空");
            return "";
        }
        //转换为csv
        //读取第一行
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<Integer,String> csvMap = (LinkedHashMap) csvList.get(0);
        List<String> headerList = csvMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        sb.append(StringUtils.join(headerList, ",")).append("\n");
        //读取数据
        for(int i = 1; i < csvList.size(); i++){
            LinkedHashMap<Integer,String> dataMap = (LinkedHashMap) csvList.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            sb.append(StringUtils.join(dataList, ",")).append("\n");
        }
        return sb.toString();
    }

    /**
     * 校验文件后缀
     * @param multipartFile
     * @return
     */
    public static boolean validSuffix(MultipartFile multipartFile){
        List<String> list = Arrays.asList("xlsx", "xls");
        //获取到文件名
        String originalFilename = multipartFile.getOriginalFilename();
        if(StringUtils.isAnyBlank(originalFilename)){
            return false;
        }
        //获取文件后缀
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        if(list.contains(suffix)){
            return true;
        }
        return false;
    }
}
