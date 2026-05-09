package com.suke.controller;

import com.suke.common.Result;
import com.suke.context.UserContext;
import com.suke.domain.entity.Chart;
import com.suke.service.IChartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChartControllerTest {

    @InjectMocks
    private ChartController chartController;

    @Mock
    private IChartService chartService;

    @BeforeEach
    void setUserContext() {
        UserContext.setCurrentId(100L);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.removeCurrentId();
    }

    private Chart buildChart(Long id, Long userId) {
        Chart chart = new Chart();
        chart.setId(id);
        chart.setUserId(userId);
        chart.setName("测试图表");
        chart.setGoal("分析趋势");
        chart.setChartType("line");
        chart.setChartData("data");
        chart.setStatus("succeed");
        return chart;
    }

    // ========== Bug #5: getChart 缺少用户归属校验 ==========

    @Test
    @DisplayName("getChart-图表不存在应返回错误")
    void getChart_notFound_shouldReturnError() {
        when(chartService.getChartById(1L)).thenReturn(null);

        Result<Chart> result = chartController.getChart(1L);

        assertNotNull(result);
        assertNotEquals(200, result.getCode(), "图表不存在应返回非200");
    }

    @Test
    @DisplayName("getChart-参数错误应返回错误")
    void getChart_invalidId_shouldReturnError() {
        Result<Chart> result = chartController.getChart(-1L);
        assertNotNull(result);
        assertNotEquals(200, result.getCode());

        Result<Chart> result2 = chartController.getChart(null);
        assertNotNull(result2);
        assertNotEquals(200, result2.getCode());
    }

    @Test
    @DisplayName("getChart-访问他人图表应返回错误（归属校验）")
    void getChart_otherUserChart_shouldReturnError() {
        Chart otherUserChart = buildChart(1L, 999L);
        when(chartService.getChartById(1L)).thenReturn(otherUserChart);

        Result<Chart> result = chartController.getChart(1L);

        assertNotNull(result);
        assertNotEquals(200, result.getCode(), "访问他人图表应返回非200");
    }

    @Test
    @DisplayName("getChart-访问自己的图表应返回成功")
    void getChart_ownChart_shouldReturnSuccess() {
        Chart ownChart = buildChart(1L, 100L);
        when(chartService.getChartById(1L)).thenReturn(ownChart);

        Result<Chart> result = chartController.getChart(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode(), "访问自己的图表应返回200");
        assertNotNull(result.getData());
    }

    // ========== Bug #5: getChartEdit 缺少用户归属校验 ==========

    @Test
    @DisplayName("getChartEdit-图表不存在应返回错误")
    void getChartEdit_notFound_shouldReturnError() {
        when(chartService.getChartById(1L)).thenReturn(null);

        Result<Chart> result = chartController.editChart(1L);

        assertNotNull(result);
        assertNotEquals(200, result.getCode());
    }

    @Test
    @DisplayName("getChartEdit-参数错误应返回错误")
    void getChartEdit_invalidId_shouldReturnError() {
        Result<Chart> result = chartController.editChart(-1L);
        assertNotNull(result);
        assertNotEquals(200, result.getCode());
    }

    @Test
    @DisplayName("getChartEdit-访问他人图表应返回错误（归属校验）")
    void getChartEdit_otherUserChart_shouldReturnError() {
        Chart otherUserChart = buildChart(1L, 999L);
        when(chartService.getChartById(1L)).thenReturn(otherUserChart);

        Result<Chart> result = chartController.editChart(1L);

        assertNotNull(result);
        assertNotEquals(200, result.getCode(), "编辑他人图表应返回非200");
    }

    @Test
    @DisplayName("getChartEdit-访问自己的图表应返回成功")
    void getChartEdit_ownChart_shouldReturnSuccess() {
        Chart ownChart = buildChart(1L, 100L);
        when(chartService.getChartById(1L)).thenReturn(ownChart);

        Result<Chart> result = chartController.editChart(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }
}
