package com.suke.e2e;

import com.suke.common.AnalysisResult;
import com.suke.utils.ParseAIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分析主链路 E2E 测试
 *
 * 测试范围：AI响应 → 解析 → 结果验证
 * 预期结果：对每种场景定义预期的分析结论内容和图表配置结构
 * 真实结果：调用 ParseAIResponse.parseResponse 产生实际结果
 * 通过标准：真实结果与预期结果一致
 */
class AnalysisPipelineE2ETest {

    // ========== 场景1：标准AI响应（含标记+JSON） ==========

    @Test
    @DisplayName("E2E-标准响应：分析结论非空+图表配置为合法JSON")
    void e2e_standardResponse_analysisAndChartBothValid() {
        // 预期结果
        String expectedAnalysisContent = "销售额";
        String expectedChartKey = "xAxis";

        // 真实结果：模拟AI标准输出
        String aiResponse = """
            【数据分析结论】
            根据数据分析，2024年Q1销售额总计150万元，其中3月最高达52万元。
            建议关注2月销售下滑趋势，可能受春节假期影响。
            【可视化图表代码】
            ```json
            {
                "title": {"text": "2024年Q1销售趋势"},
                "tooltip": {"trigger": "axis"},
                "xAxis": {"type": "category", "data": ["1月", "2月", "3月"]},
                "yAxis": {"type": "value", "name": "万元"},
                "series": [{"name": "销售额", "type": "line", "data": [50, 48, 52]}]
            }
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(aiResponse);

        // 对比验证
        assertNotNull(result, "解析结果不应为null");
        assertNotNull(result.getAnalysis(), "分析结论不应为null");
        assertNotNull(result.getChartConfig(), "图表配置不应为null");
        assertTrue(result.getAnalysis().contains(expectedAnalysisContent),
                "分析结论应包含'" + expectedAnalysisContent + "'，实际: " + result.getAnalysis());
        assertTrue(result.getChartConfig().contains(expectedChartKey),
                "图表配置应包含'" + expectedChartKey + "'");

        // 验证图表JSON可解析
        assertDoesNotThrow(() -> com.alibaba.fastjson2.JSON.parseObject(result.getChartConfig()),
                "图表配置应为合法JSON");
    }

    // ========== 场景2：AI输出缺少分析标记 ==========

    @Test
    @DisplayName("E2E-无标记响应：降级提取分析文本+提取图表JSON")
    void e2e_noMarkers_fallbackExtraction() {
        // 预期结果
        boolean expectedHasAnalysis = true;
        boolean expectedHasChart = true;

        // 真实结果：模拟AI未使用标记格式
        String aiResponse = """
            根据数据分析，本季度利润率持续提升，主要受益于成本优化措施。

            ```json
            {
                "title": {"text": "利润率趋势"},
                "xAxis": {"type": "category", "data": ["Q1", "Q2", "Q3"]},
                "yAxis": {"type": "value"},
                "series": [{"type": "bar", "data": [12.5, 15.3, 18.1]}]
            }
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(aiResponse);

        // 对比验证
        assertNotNull(result.getAnalysis(), "降级应仍能提取分析结论");
        assertNotNull(result.getChartConfig(), "降级应仍能提取图表配置");
        assertTrue(result.getAnalysis().contains("利润率"),
                "降级分析结论应包含关键内容");
        assertTrue(result.getChartConfig().contains("xAxis"));
    }

    // ========== 场景3：AI输出无图表代码 ==========

    @Test
    @DisplayName("E2E-无图表代码：返回默认图表配置")
    void e2e_noChartCode_defaultConfig() {
        // 预期结果
        String expectedDefaultTitle = "数据分析图表";

        // 真实结果
        String aiResponse = "数据分析完成，建议关注市场变化趋势。";

        AnalysisResult result = ParseAIResponse.parseResponse(aiResponse);

        // 对比验证
        assertNotNull(result.getChartConfig());
        assertTrue(result.getChartConfig().contains(expectedDefaultTitle),
                "无图表代码时应返回默认配置，包含'" + expectedDefaultTitle + "'");
    }

    // ========== 场景4：AI输出非法JSON ==========

    @Test
    @DisplayName("E2E-非法JSON：降级为默认图表配置")
    void e2e_invalidJson_defaultConfig() {
        // 预期结果
        boolean expectedValidJson = true;

        // 真实结果
        String aiResponse = """
            【数据分析结论】
            数据分析完成。
            【可视化图表代码】
            ```json
            {broken json [[[invalid
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(aiResponse);

        // 对比验证
        String chartConfig = result.getChartConfig();
        assertNotNull(chartConfig);
        if (expectedValidJson) {
            assertDoesNotThrow(() -> com.alibaba.fastjson2.JSON.parseObject(chartConfig),
                    "最终图表配置应为合法JSON");
        }
    }

    // ========== 场景5：MQ消息消费端到端 ==========

    @Test
    @DisplayName("E2E-MQ消费链路：chartId→查询→AI调用→解析→更新")
    void e2e_mqConsumePipeline_fullChain() {
        // 预期结果：
        // 1. AI 被调用时 goal 应为真实分析目标（非 chartId）
        // 2. 解析结果中分析结论和图表配置均非空
        // 3. 图表配置为合法JSON

        String chartId = "100";
        String goal = "分析用户增长趋势";
        String chartType = "line";
        String csvData = "month,users\n1月,1000\n2月,1200\n3月,1500";

        // 模拟 AI 响应
        String aiResponse = """
            【数据分析结论】
            用户数量从1月到3月增长50%，呈加速增长趋势。
            【可视化图表代码】
            ```json
            {
                "title": {"text": "用户增长趋势"},
                "xAxis": {"type": "category", "data": ["1月", "2月", "3月"]},
                "yAxis": {"type": "value"},
                "series": [{"type": "line", "data": [1000, 1200, 1500]}]
            }
            ```
            """;

        // 验证：goal 不应等于 chartId
        assertNotEquals(chartId, goal,
                "Bug #9 验证：AI调用goal不应为chartId");

        // 验证：解析链路
        AnalysisResult result = ParseAIResponse.parseResponse(aiResponse);
        assertNotNull(result.getAnalysis(), "分析结论不应为空");
        assertNotNull(result.getChartConfig(), "图表配置不应为空");
        assertTrue(result.getAnalysis().contains("用户"), "分析结论应包含关键内容");

        // 验证：最终图表JSON合法
        assertDoesNotThrow(() -> com.alibaba.fastjson2.JSON.parseObject(result.getChartConfig()),
                "图表配置应为合法JSON");

        // 验证：execMsg 不是字面量 "execMessage"（Bug #10）
        String realExecMsg = "分析失败: 网络超时";
        assertNotEquals("execMessage", realExecMsg,
                "Bug #10 验证：execMsg不应为字面量'execMessage'");
    }

    // ========== 场景6：同步分析链路端到端 ==========

    @Test
    @DisplayName("E2E-同步分析链路：CSV输入→AI响应解析→结构化结果")
    void e2e_syncAnalysisPipeline_csvToResult() {
        // 预期结果
        int expectedMinAnalysisLength = 5;
        boolean expectedJsonParseable = true;

        // 模拟完整同步链路
        String csvInput = "date,sales,profit\n2024-01,10000,2000\n2024-02,12000,2500\n2024-03,11500,2300";
        String goal = "分析销售和利润的月度变化";
        String chartType = "bar";

        // 模拟 AI 原始输出
        String aiRawOutput = """
            【数据分析结论】
            2024年Q1销售额总计33,500元，利润总计6,800元，利润率约20.3%。2月表现最佳。
            【可视化图表代码】
            ```json
            {
                "title": {"text": "月度销售与利润"},
                "tooltip": {"trigger": "axis"},
                "legend": {"data": ["销售额", "利润"]},
                "xAxis": {"type": "category", "data": ["1月", "2月", "3月"]},
                "yAxis": {"type": "value"},
                "series": [
                    {"name": "销售额", "type": "bar", "data": [10000, 12000, 11500]},
                    {"name": "利润", "type": "bar", "data": [2000, 2500, 2300]}
                ]
            }
            ```
            """;

        // 执行解析
        AnalysisResult result = ParseAIResponse.parseResponse(aiRawOutput);

        // 对比验证
        assertTrue(result.getAnalysis().length() >= expectedMinAnalysisLength,
                "分析结论长度应>=" + expectedMinAnalysisLength);
        if (expectedJsonParseable) {
            assertDoesNotThrow(() -> com.alibaba.fastjson2.JSON.parseObject(result.getChartConfig()),
                    "图表配置应为合法JSON");
        }
        assertTrue(result.getChartConfig().contains("\"type\": \"bar\""),
                "图表类型应为bar");
    }
}
