package com.suke.utils;

import com.suke.common.AnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParseAIResponseTest {

    // ========== 标准场景 ==========

    @Test
    @DisplayName("标准响应：包含分析结论标记和可视化图表代码标记，应正确提取")
    void parseResponse_standardFormat_shouldExtractBoth() {
        String response = """
            【数据分析结论】
            根据数据分析，销售额呈现上升趋势，第一季度总销售额为150万元。
            【可视化图表代码】
            ```json
            {"title":{"text":"销售趋势"},"xAxis":{"type":"category","data":["1月","2月","3月"]},"series":[{"type":"line","data":[50,48,52]}]}
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertNotNull(result.getAnalysis());
        assertNotNull(result.getChartConfig());
        assertTrue(result.getAnalysis().contains("销售额"));
        assertTrue(result.getChartConfig().contains("xAxis"));
    }

    // ========== Bug #11：正则多余双引号导致永远无法匹配 ==========

    @Test
    @DisplayName("分析结论不应以引号开头或结尾（验证正则无多余引号）")
    void parseResponse_analysisShouldNotHaveWrappedQuotes() {
        String response = """
            【数据分析结论】
            销售额同比增长15%。
            【可视化图表代码】
            ```json
            {"title":{"text":"test"},"xAxis":{"type":"category","data":[]},"series":[{"type":"line","data":[]}]}
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertNotNull(result.getAnalysis());
        assertFalse(result.getAnalysis().startsWith("\""),
                "分析结论不应以引号开头，当前正则可能多余引号");
        assertFalse(result.getAnalysis().endsWith("\""),
                "分析结论不应以引号结尾");
    }

    // ========== 边界场景 ==========

    @Test
    @DisplayName("响应中只有json代码块没有标记，应提取图表配置")
    void parseResponse_jsonCodeBlockOnly_shouldExtractChart() {
        String response = """
            根据数据分析，销售额呈现上升趋势。
            ```json
            {"title":{"text":"销售趋势"},"xAxis":{"type":"category","data":["1月","2月"]},"series":[{"type":"bar","data":[50,60]}]}
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertNotNull(result.getChartConfig());
        assertTrue(result.getChartConfig().contains("xAxis"));
    }

    @Test
    @DisplayName("非法JSON时应返回降级默认配置，不抛异常")
    void parseResponse_invalidJson_shouldReturnDefaultConfig() {
        String response = """
            【数据分析结论】
            数据分析完成。
            【可视化图表代码】
            ```json
            {invalid json content
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertNotNull(result.getChartConfig());
        assertTrue(result.getChartConfig().contains("title"), "非法JSON应降级为默认配置");
    }

    @Test
    @DisplayName("响应为空字符串时不应抛异常")
    void parseResponse_emptyResponse_shouldNotThrow() {
        assertDoesNotThrow(() -> ParseAIResponse.parseResponse(""));
    }

    @Test
    @DisplayName("分析文本中包含花括号不应被过度清理导致内容丢失")
    void parseResponse_analysisWithBrackets_shouldPreserveContent() {
        String response = """
            【数据分析结论】
            本季度ROI达到了120%，其中品类A贡献最大，关键指标{增长率}显著。
            【可视化图表代码】
            ```json
            {"title":{"text":"ROI分析"},"xAxis":{"type":"category","data":["A","B"]},"series":[{"type":"bar","data":[120,80]}]}
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertTrue(result.getAnalysis().contains("ROI"), "分析文本应保留ROI内容");
    }

    @Test
    @DisplayName("响应中包含多个json代码块时应提取第一个")
    void parseResponse_multipleJsonBlocks_shouldExtractFirst() {
        String response = """
            【数据分析结论】
            以下是分析结果。
            【可视化图表代码】
            ```json
            {"title":{"text":"第一个图表"},"xAxis":{"type":"category","data":["1月"]},"series":[{"type":"line","data":[100]}]}
            ```
            补充说明：
            ```json
            {"extra":"数据"}
            ```
            """;

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertTrue(result.getChartConfig().contains("第一个图表"), "应提取第一个图表配置");
    }

    @Test
    @DisplayName("响应中无任何json时应返回默认图表配置")
    void parseResponse_noJsonAtAll_shouldReturnDefault() {
        String response = "这是一段纯文本分析结论，没有图表代码。";

        AnalysisResult result = ParseAIResponse.parseResponse(response);

        assertNotNull(result);
        assertNotNull(result.getChartConfig());
        assertTrue(result.getChartConfig().contains("title"), "无JSON时应返回默认配置");
    }
}
