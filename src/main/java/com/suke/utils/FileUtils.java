package com.suke.utils;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.suke.domain.dto.file.SmartFileResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author 自然醒
 * @version 1.0
 */

//文件工具类
@Slf4j
public class FileUtils {


    /**
     * 智能流式处理大文件
     *
     * @param multipartFile
     * @param minioUtil
     * @param targetSampleRows
     * @param enableStreaming
     * @return
     */
    public static SmartFileResult smartProcessLargeFile(MultipartFile multipartFile,
                                                        MinioUtil minioUtil,
                                                        int targetSampleRows,
                                                        boolean enableStreaming) {
        SmartFileResult result = new SmartFileResult();

        try {
            // 1. 先读取表头
            List<Map<Integer, String>> headerData = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();

            if (CollUtil.isEmpty(headerData)) {
                log.error("文件数据为空");
                return result;
            }

            // 获取表头
            Map<Integer, String> headerRow = headerData.get(0);
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.size(); i++) {
                headers.add(headerRow.get(i));
            }
            result.setHeaders(headers);

            // 2. 如果启用流式处理，先上传原始文件到MinIO
            if (enableStreaming) {
                String originFileName = "original/" + UUID.randomUUID() + "_" +
                        multipartFile.getOriginalFilename();
                String minioPath = minioUtil.uploadFile(multipartFile, originFileName);
                result.setOriginalMinioPath(minioPath);
                log.info("原始文件已上传到MinIO: {}", minioPath);
            }

            // 3. 执行智能分层采样
            List<List<String>> sampledRows = new ArrayList<>();
            AtomicInteger totalRows = new AtomicInteger(0);
            List<Map<Integer, String>> allRowsForSample = new ArrayList<>();

            // 使用流式读取进行智能采样
            EasyExcel.read(multipartFile.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                private final List<Map<Integer, String>> cachedDataList = new ArrayList<>();
                private int batchCount = 0;

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    totalRows.incrementAndGet();
                    cachedDataList.add(data);

                    // 每读取100行，处理一次批次
                    if (cachedDataList.size() >= 100) {
                        processBatchForSampling(cachedDataList, allRowsForSample, totalRows.get(), targetSampleRows);
                        cachedDataList.clear();
                        batchCount++;
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    if (!cachedDataList.isEmpty()) {
                        processBatchForSampling(cachedDataList, allRowsForSample, totalRows.get(), targetSampleRows);
                    }

                    log.info("文件总行数: {} (包含表头)", totalRows.get());
                    result.setTotalRows(totalRows.get() - 1); // 减去表头

                    // 执行最终的分层采样
                    performStratifiedSampling(allRowsForSample, sampledRows, targetSampleRows, totalRows.get() - 1);

                    // 4. 如果数据量大，将采样数据也上传到MinIO
                    String csvContent = convertToCsv(headers, sampledRows);
                    result.setSampledData(csvContent);

                    if (csvContent.getBytes().length > 1024 * 1024) { // 大于1MB
                        String csvFileName = "csv/sampled_" + UUID.randomUUID() + ".csv";
                        String minioPath = minioUtil.uploadCsvFile(csvContent, csvFileName);
                        result.setSampledMinioPath(minioPath);
                        log.info("采样数据已上传到MinIO: {}", minioPath);
                    }
                }
            }).sheet().headRowNumber(0).doRead();

        } catch (Exception e) {
            log.error("智能大文件处理失败", e);
            throw new RuntimeException("大文件处理失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 处理批次数据用于采样
     */
    private static void processBatchForSampling(List<Map<Integer, String>> batch,
                                                List<Map<Integer, String>> allRowsForSample,
                                                int currentTotalRows,
                                                int targetSampleRows) {
        // 如果总数据量不大，直接保存所有行
        if (currentTotalRows <= targetSampleRows * 3) {
            allRowsForSample.addAll(batch);
            return;
        }

        // 对于大数据集，从每个批次中均匀采样
        int batchSize = batch.size();
        int sampleFromBatch = Math.max(1, targetSampleRows / 10); // 从每个批次取一部分

        Random random = new Random();
        for (int i = 0; i < sampleFromBatch && i < batchSize; i++) {
            int randomIndex = random.nextInt(batchSize);
            allRowsForSample.add(batch.get(randomIndex));
        }
    }

    /**
     * 执行分层采样
     */
    private static void performStratifiedSampling(List<Map<Integer, String>> allRows,
                                                  List<List<String>> sampledRows,
                                                  int targetSampleRows,
                                                  int totalDataRows) {
        if (allRows.isEmpty()) {
            return;
        }

        // 如果数据量小于目标采样行数，返回所有数据
        if (allRows.size() <= targetSampleRows) {
            for (Map<Integer, String> row : allRows) {
                List<String> rowData = new ArrayList<>();
                for (int i = 0; i < row.size(); i++) {
                    rowData.add(row.get(i));
                }
                sampledRows.add(rowData);
            }
            return;
        }

        // 分层采样：从开头、中间、结尾各取一部分
        int sampleEachPart = targetSampleRows / 3;
        int totalRows = allRows.size();

        // 开头部分（前25%）
        int startEnd = Math.min(totalRows / 4, sampleEachPart);
        for (int i = 0; i < startEnd; i++) {
            sampledRows.add(convertRowToList(allRows.get(i)));
        }

        // 中间部分（25%-75%）
        int middleStart = totalRows / 4;
        int middleEnd = totalRows * 3 / 4;
        int middleCount = Math.min(middleEnd - middleStart, sampleEachPart);

        Random random = new Random();
        for (int i = 0; i < middleCount; i++) {
            int randomIndex = middleStart + random.nextInt(middleEnd - middleStart);
            sampledRows.add(convertRowToList(allRows.get(randomIndex)));
        }

        // 结尾部分（后25%）
        int endStart = totalRows * 3 / 4;
        int endCount = Math.min(totalRows - endStart, sampleEachPart);
        for (int i = 0; i < endCount; i++) {
            sampledRows.add(convertRowToList(allRows.get(endStart + i)));
        }

        // 如果还不够，随机补充
        while (sampledRows.size() < targetSampleRows && sampledRows.size() < totalRows) {
            int randomIndex = random.nextInt(totalRows);
            List<String> randomRow = convertRowToList(allRows.get(randomIndex));
            if (!sampledRows.contains(randomRow)) {
                sampledRows.add(randomRow);
            }
        }

        log.info("分层采样完成: 总行数={}, 采样行数={}", totalDataRows, sampledRows.size());
    }

    /**
     * 将Map行转换为List
     */
    private static List<String> convertRowToList(Map<Integer, String> row) {
        List<String> rowData = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            rowData.add(row.get(i));
        }
        return rowData;
    }


    /**
     * excel文件转换成csv文件
     *
     * @param multipartFile
     * @return
     */
    public static String excelToCsv(MultipartFile multipartFile) {
        log.info("上传的文件：{}", multipartFile);
        List<Map<Integer, String>> csvList = null;
        try {
            csvList = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        } catch (Exception e) {
            log.error("文件转换异常");
            throw new RuntimeException(e.getMessage());
        }
        if (CollUtil.isEmpty(csvList)) {
            log.error("数据为空");
            return "";
        }
        return convert2Csv(csvList);
    }

    /**
     * 将List数据转换为CSV
     */
    private static String convertToCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.join(headers, ",")).append("\n");

        for (List<String> row : rows) {
            sb.append(StringUtils.join(row, ",")).append("\n");
        }
        return sb.toString();
    }


    /**
     * 校验文件后缀
     *
     * @param multipartFile
     * @return
     */
    public static boolean validSuffix(MultipartFile multipartFile) {
        List<String> list = Arrays.asList("xlsx", "xls");
        //获取到文件名
        String originalFilename = multipartFile.getOriginalFilename();
        if (StringUtils.isAnyBlank(originalFilename)) {
            return false;
        }
        //获取文件后缀
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        if (list.contains(suffix)) {
            return true;
        }
        return false;
    }

    public static boolean validFileSize(MultipartFile multipartFile, long maxSize) {
        if (multipartFile == null) {
            return false;
        }
        return multipartFile.getSize() <= maxSize * 1024 * 1024;
    }

    /**
     * 读取CSV文件内容
     */
    private static String readCsvFile(InputStream inputStream) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("CSV文件读取失败", e);
            throw new RuntimeException("CSV文件读取失败");
        }
    }


    /**
     * 大excel文件转换成csv文件
     *
     * @param multipartFile
     * @return
     */
    public static String excelToCsvWithStream(MultipartFile multipartFile, MinioUtil minioUtil, int batchSize) {
        String csvFileName = multipartFile.getOriginalFilename().replaceAll("\\.(xlsx|xls)$", ".csv");
        csvFileName = "csv/" + UUID.randomUUID() + "_" + csvFileName;

        try {
            // 使用临时文件
            File tempFile = File.createTempFile("temp_csv_", ".csv");
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            // 写入表头
            List<Map<Integer, String>> headerData = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();

            if (!CollUtil.isEmpty(headerData)) {
                writer.write(convertRowToCsvLine(headerData.get(0)));
                writer.newLine();
            }

            // 流式读取数据
            EasyExcel.read(multipartFile.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                private final List<Map<Integer, String>> cachedDataList = new ArrayList<>();

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    cachedDataList.add(data);
                    if (cachedDataList.size() >= batchSize) {
                        writeBatchToCsv(cachedDataList, writer);
                        cachedDataList.clear();
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    if (!cachedDataList.isEmpty()) {
                        writeBatchToCsv(cachedDataList, writer);
                    }
                    try {
                        writer.close();
                    } catch (IOException e) {
                        log.error("关闭writer失败", e);
                    }
                }
            }).sheet().headRowNumber(0).doRead();

            // 上传到MinIO
            String csvContent = readFileContent(tempFile);
            minioUtil.uploadCsvFile(csvContent, csvFileName);

            // 清理临时文件
            tempFile.delete();

            return csvFileName;

        } catch (Exception e) {
            log.error("流式Excel转CSV失败", e);
            return null;
        }
    }

    public static String smartSampling(MultipartFile multipartFile, MinioUtil minioUtil, int maxRows) {
        try {
            // 使用新的智能处理逻辑
            SmartFileResult result = smartProcessLargeFile(multipartFile, minioUtil, maxRows, false);

            if (StringUtils.isNotBlank(result.getSampledData())) {
                log.info("智能采样完成，总行数: {}，采样行数: {}",
                        result.getTotalRows(),
                        countLines(result.getSampledData()));
                return result.getSampledData();
            }

            // 降级处理：使用原来的采样逻辑
            return fallbackSampling(multipartFile, maxRows);

        } catch (Exception e) {
            log.error("智能采样失败", e);
            return fallbackSampling(multipartFile, maxRows);
        }
    }

    /**
     * 降级采样
     */
    private static String fallbackSampling(MultipartFile multipartFile, int maxRows) {
        try {
            List<Map<Integer, String>> csvList = new ArrayList<>();
            AtomicInteger rowCount = new AtomicInteger(0);

            EasyExcel.read(multipartFile.getInputStream(), new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    if (rowCount.get() >= maxRows) {
                        return;
                    }
                    csvList.add(new LinkedHashMap<>(data));
                    rowCount.incrementAndGet();
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    log.info("降级采样完成，采样行数: {}", csvList.size());
                }
            }).sheet().headRowNumber(0).doRead();

            return convert2Csv(csvList);
        } catch (Exception e) {
            log.error("降级采样失败", e);
            return "";
        }
    }


    private static String convert2Csv(List<Map<Integer, String>> data) {
        //转换为csv
        //读取第一行
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<Integer, String> csvMap = (LinkedHashMap) data.get(0);
        List<String> headerList = csvMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        sb.append(StringUtils.join(headerList, ",")).append("\n");
        //读取数据
        for (int i = 1; i < data.size(); i++) {
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap) data.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            sb.append(StringUtils.join(dataList, ",")).append("\n");
        }
        return sb.toString();
    }

    /**
     * 采样excel数据
     *
     * @param in
     * @param fileName
     * @param maxRows
     * @return
     */
    private static List<Map<Integer, String>> sampleExcelData(InputStream in, String fileName, int maxRows) {
        List<Map<Integer, String>> csvList = null;
        AtomicInteger rowNum = new AtomicInteger(0);
        try {
            if (rowNum.get() < maxRows) {
                csvList = EasyExcel.read(in)
                        .excelType(ExcelTypeEnum.XLSX)
                        .sheet()
                        .headRowNumber(0)
                        .doReadSync();
                rowNum.incrementAndGet();
            }
            return csvList;
        } catch (Exception e) {
            log.error("数据采样失败");
            return null;
        }
    }

    /**
     * 读取文件内容
     */
    private static String readFileContent(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败");
        }
    }


    /**
     * 分批写入CSV
     */
    private static void writeBatchToCsv(List<Map<Integer, String>> batch, BufferedWriter writer) {
        try {
            for (Map<Integer, String> row : batch) {
                writer.write(convertRowToCsvLine(row));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("CSV写入失败");
        }
    }


    /**
     * 将单行数据转换为CSV行
     */
    private static String convertRowToCsvLine(Map<Integer, String> row) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            values.add(escapeCsvField(row.get(i)));
        }
        return StringUtils.join(values, ",");
    }

    /**
     * CSV字段转义
     */
    private static String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * 计算CSV行数
     */
    public static int countLines(String csv) {
        if (StringUtils.isBlank(csv)) return 0;
        return csv.split("\n").length;
    }
}
