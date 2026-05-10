package com.suke.utils;

import com.alibaba.excel.EasyExcel;
import com.suke.domain.dto.file.SmartFileResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FileUtilsTest {

    // ========== #28: validSuffix should only accept .xlsx ==========

    @Test
    @DisplayName("validSuffix应接受xlsx文件")
    void validSuffix_xlsx_shouldAccept() {
        MockMultipartFile file = new MockMultipartFile(
                "d.xlsx", "d.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);
        assertTrue(FileUtils.validSuffix(file));
    }

    @Test
    @DisplayName("validSuffix应拒绝xls文件")
    void validSuffix_xls_shouldReject() {
        MockMultipartFile file = new MockMultipartFile(
                "d.xls", "d.xls", "application/vnd.ms-excel", new byte[0]);
        assertFalse(FileUtils.validSuffix(file));
    }

    @Test
    @DisplayName("validSuffix应拒绝csv文件")
    void validSuffix_csv_shouldReject() {
        MockMultipartFile file = new MockMultipartFile(
                "d.csv", "d.csv", "text/csv", new byte[0]);
        assertFalse(FileUtils.validSuffix(file));
    }

    // ========== #26: performStratifiedSampling should not infinite loop ==========

    @Test
    @DisplayName("所有行相同时分层采样不应死循环")
    void stratifiedSampling_allDuplicateRows_shouldNotInfiniteLoop() {
        // Create Excel with 200 identical rows
        List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList("col1", "col2"));
        for (int i = 0; i < 200; i++) {
            data.add(Arrays.asList("same", "value"));
        }

        MockMultipartFile file = createRealExcel(data);
        MinioUtil minioUtil = mock(MinioUtil.class);

        // Should complete within 5 seconds (would hang forever without fix)
        assertDoesNotThrow(() -> {
            SmartFileResult result = FileUtils.smartProcessLargeFile(
                    file, minioUtil, 100, false);
            assertNotNull(result);
            assertTrue(result.getSampledData().contains("same"));
        }, "分层采样在重复数据场景下应在有限时间内返回");
    }

    // ========== #29: smartProcessLargeFile should not call getInputStream twice ==========

    @Test
    @DisplayName("smartProcessLargeFile不应重复调用getInputStream")
    void smartProcessLargeFile_shouldWorkWithOneShotStream() {
        List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList("month", "sales"));
        data.add(Arrays.asList("1月", 1000));
        data.add(Arrays.asList("2月", 1200));

        byte[] excelBytes = createExcelBytes(data);
        AtomicInteger getStreamCalls = new AtomicInteger(0);

        MockMultipartFile file = new MockMultipartFile(
                "d.xlsx", "d.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes) {
            @Override
            public InputStream getInputStream() throws IOException {
                if (getStreamCalls.incrementAndGet() > 1) {
                    throw new IOException("getInputStream() called more than once");
                }
                return super.getInputStream();
            }
        };

        MinioUtil minioUtil = mock(MinioUtil.class);

        assertDoesNotThrow(() -> FileUtils.smartProcessLargeFile(file, minioUtil, 100, false),
                "应使用getBytes()而非多次调用getInputStream()");
    }

    // ========== #25/#27: Dead code should be removed ==========

    @Test
    @DisplayName("validSuffix空文件名应拒绝")
    void validSuffix_nullFilename_shouldReject() {
        MockMultipartFile file = new MockMultipartFile(
                "file", (String) null, "application/octet-stream", new byte[0]);
        assertFalse(FileUtils.validSuffix(file));
    }

    // ========== Helpers ==========

    private MockMultipartFile createRealExcel(List<List<Object>> rows) {
        return new MockMultipartFile(
                "d.xlsx", "d.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createExcelBytes(rows));
    }

    private byte[] createExcelBytes(List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).sheet("data").doWrite(rows);
        return out.toByteArray();
    }
}
