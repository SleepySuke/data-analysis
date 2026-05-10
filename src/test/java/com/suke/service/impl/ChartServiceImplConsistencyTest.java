package com.suke.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.suke.context.UserContext;
import com.suke.datamq.MessageProducer;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.vo.GenChartVO;
import com.suke.mapper.ChartMapper;
import com.suke.utils.AIDocking;
import com.suke.utils.FileUtils;
import com.suke.utils.MinioUtil;
import com.suke.utils.WebSocketServer;
import com.github.rholder.retry.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChartServiceImplConsistencyTest {

    @InjectMocks
    private ChartServiceImpl chartService;

    @Mock private ChartMapper chartMapper;
    @Mock private AIDocking aiDocking;
    @Mock private ThreadPoolExecutor threadPoolExecutor;
    @Mock private WebSocketServer webSocketServer;
    @Mock private Retryer<String> aiAnalyzeRetryer;
    @Mock private Retryer<String> syncAnalyzeRetryer;
    @Mock private MinioUtil minioUtil;
    @Mock private MessageProducer messageProducer;

    @BeforeEach
    void setUp() throws Exception {
        UserContext.setCurrentId(1L);
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(chartService, chartMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeCurrentId();
    }

    private MockMultipartFile createSmallExcelFile() {
        return new MockMultipartFile(
                "test.xlsx", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "small-content".getBytes());
    }

    private UploadFileDTO buildFileDTO() {
        UploadFileDTO dto = new UploadFileDTO();
        dto.setFileName("测试");
        dto.setGoal("分析趋势");
        dto.setChartType("line");
        return dto;
    }

    // ========== #22: Three paths should have consistent behavior ==========

    @Test
    @DisplayName("三条路径对空文件应一致返回null")
    void allPaths_nullFile_shouldReturnNull() {
        UploadFileDTO dto = buildFileDTO();

        assertNull(chartService.analysisFile(null, dto));
        assertNull(chartService.asyncAnalyzeFile(null, dto));
        assertNull(chartService.asyncAnalyze(null, dto));
    }

    @Test
    @DisplayName("三条路径对未登录用户应一致返回null")
    void allPaths_notLoggedIn_shouldReturnNull() {
        UserContext.removeCurrentId();
        MockMultipartFile file = createSmallExcelFile();
        UploadFileDTO dto = buildFileDTO();

        assertNull(chartService.analysisFile(file, dto));
        assertNull(chartService.asyncAnalyzeFile(file, dto));
        assertNull(chartService.asyncAnalyze(file, dto));
    }

    @Test
    @DisplayName("三条路径对空分析目标应一致抛异常")
    void allPaths_blankGoal_shouldThrow() {
        MockMultipartFile file = createSmallExcelFile();
        UploadFileDTO dto = buildFileDTO();
        dto.setGoal("");

        assertThrows(Exception.class, () -> chartService.analysisFile(file, dto));
        assertThrows(Exception.class, () -> chartService.asyncAnalyzeFile(file, dto));
        assertThrows(Exception.class, () -> chartService.asyncAnalyze(file, dto));
    }

    @Test
    @DisplayName("三条路径对空图表类型应一致抛异常")
    void allPaths_blankChartType_shouldThrow() {
        MockMultipartFile file = createSmallExcelFile();
        UploadFileDTO dto = buildFileDTO();
        dto.setChartType("");

        assertThrows(Exception.class, () -> chartService.analysisFile(file, dto));
        assertThrows(Exception.class, () -> chartService.asyncAnalyzeFile(file, dto));
        assertThrows(Exception.class, () -> chartService.asyncAnalyze(file, dto));
    }

    @Test
    @DisplayName("三条路径对空文件名称应一致抛异常")
    void allPaths_blankFileName_shouldThrow() {
        MockMultipartFile file = createSmallExcelFile();
        UploadFileDTO dto = buildFileDTO();
        dto.setFileName("");

        assertThrows(Exception.class, () -> chartService.analysisFile(file, dto));
        assertThrows(Exception.class, () -> chartService.asyncAnalyzeFile(file, dto));
        assertThrows(Exception.class, () -> chartService.asyncAnalyze(file, dto));
    }

    @Test
    @DisplayName("同步路径应校验文件格式")
    void syncPath_shouldCallValidSuffix() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any(MultipartFile.class))).thenReturn(false);

            MockMultipartFile file = createSmallExcelFile();
            UploadFileDTO dto = buildFileDTO();

            // validSuffix returns false → should return null
            GenChartVO result = chartService.analysisFile(file, dto);
            assertNull(result);

            fileUtilsMock.verify(() -> FileUtils.validSuffix(any(MultipartFile.class)));
        }
    }

    @Test
    @DisplayName("同步路径应校验文件大小")
    void syncPath_shouldCallValidFileSize() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any(MultipartFile.class))).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(MultipartFile.class), anyLong())).thenReturn(false);

            MockMultipartFile file = createSmallExcelFile();
            UploadFileDTO dto = buildFileDTO();

            // validFileSize returns false → should return null
            GenChartVO result = chartService.analysisFile(file, dto);
            assertNull(result);

            fileUtilsMock.verify(() -> FileUtils.validFileSize(any(MultipartFile.class), eq(100L)));
        }
    }

    @Test
    @DisplayName("异步路径应校验文件大小")
    void asyncPath_shouldCallValidFileSize() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any(MultipartFile.class))).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(MultipartFile.class), anyLong())).thenReturn(false);

            MockMultipartFile file = createSmallExcelFile();
            UploadFileDTO dto = buildFileDTO();

            assertNull(chartService.asyncAnalyzeFile(file, dto));
            fileUtilsMock.verify(() -> FileUtils.validFileSize(any(MultipartFile.class), eq(100L)));
        }
    }

    @Test
    @DisplayName("MQ路径应校验文件大小")
    void mqPath_shouldCallValidFileSize() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any(MultipartFile.class))).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(MultipartFile.class), anyLong())).thenReturn(false);

            MockMultipartFile file = createSmallExcelFile();
            UploadFileDTO dto = buildFileDTO();

            assertNull(chartService.asyncAnalyze(file, dto));
            fileUtilsMock.verify(() -> FileUtils.validFileSize(any(MultipartFile.class), eq(100L)));
        }
    }

    @Test
    @DisplayName("异步路径应使用统一文件格式校验")
    void asyncPath_shouldCallValidSuffix() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any(MultipartFile.class))).thenReturn(false);

            MockMultipartFile file = createSmallExcelFile();
            UploadFileDTO dto = buildFileDTO();

            assertNull(chartService.asyncAnalyzeFile(file, dto));
            fileUtilsMock.verify(() -> FileUtils.validSuffix(any(MultipartFile.class)));
        }
    }

    @Test
    @DisplayName("MQ路径应使用统一文件格式校验")
    void mqPath_shouldCallValidSuffix() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any(MultipartFile.class))).thenReturn(false);

            MockMultipartFile file = createSmallExcelFile();
            UploadFileDTO dto = buildFileDTO();

            assertNull(chartService.asyncAnalyze(file, dto));
            fileUtilsMock.verify(() -> FileUtils.validSuffix(any(MultipartFile.class)));
        }
    }

    // ========== processFileToCsv coverage ==========

    @Test
    @DisplayName("MQ路径小文件应通过processFileToCsv处理")
    void mqPath_smallFile_shouldProcessToCsv() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(), anyLong())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.excelToCsv(any())).thenReturn("header,data\n1,2");
            doReturn(1).when(chartMapper).insert(any(com.suke.domain.entity.Chart.class));

            chartService.asyncAnalyze(createSmallExcelFile(), buildFileDTO());

            fileUtilsMock.verify(() -> FileUtils.excelToCsv(any()));
        }
    }

    @Test
    @DisplayName("异步路径小文件应通过processFileToCsv处理")
    void asyncPath_smallFile_shouldProcessToCsv() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(), anyLong())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.excelToCsv(any())).thenReturn("header,data\n1,2");
            doReturn(1).when(chartMapper).insert(any(com.suke.domain.entity.Chart.class));

            chartService.asyncAnalyzeFile(createSmallExcelFile(), buildFileDTO());

            fileUtilsMock.verify(() -> FileUtils.excelToCsv(any()));
        }
    }

    @Test
    @DisplayName("processFileToCsv空CSV应抛BaseException")
    void processFileToCsv_emptyCsv_shouldThrow() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(), anyLong())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.excelToCsv(any())).thenReturn("");

            assertThrows(Exception.class, () -> chartService.asyncAnalyze(createSmallExcelFile(), buildFileDTO()));
        }
    }

    @Test
    @DisplayName("processFileToCsv大文件应走采样路径")
    void processFileToCsv_largeFile_shouldUseSampling() throws Exception {
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.validSuffix(any())).thenReturn(true);
            fileUtilsMock.when(() -> FileUtils.validFileSize(any(), anyLong())).thenReturn(true);

            com.suke.domain.dto.file.SmartFileResult smartResult = new com.suke.domain.dto.file.SmartFileResult();
            smartResult.setSampledData("sampled,data\n1,2");
            smartResult.setTotalRows(10000);
            fileUtilsMock.when(() -> FileUtils.smartProcessLargeFile(any(), any(), anyInt(), anyBoolean()))
                    .thenReturn(smartResult);
            doReturn(1).when(chartMapper).insert(any(com.suke.domain.entity.Chart.class));

            byte[] largeContent = new byte[10 * 1024 * 1024 + 1];
            MockMultipartFile largeFile = new MockMultipartFile(
                    "large.xlsx", "large.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    largeContent);

            chartService.asyncAnalyze(largeFile, buildFileDTO());

            fileUtilsMock.verify(() -> FileUtils.smartProcessLargeFile(any(), any(), anyInt(), anyBoolean()));
        }
    }

    // ========== validateParams null check ==========

    @Test
    @DisplayName("三条路径对空DTO应一致抛异常")
    void allPaths_nullDTO_shouldThrow() {
        MockMultipartFile file = createSmallExcelFile();

        assertThrows(Exception.class, () -> chartService.analysisFile(file, null));
        assertThrows(Exception.class, () -> chartService.asyncAnalyzeFile(file, null));
        assertThrows(Exception.class, () -> chartService.asyncAnalyze(file, null));
    }
}
