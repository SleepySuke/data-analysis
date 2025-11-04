package com.suke.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 自然醒
 * @version 1.0
 */
//图表类型的模版,用于返回不同的json文件配置渲染给数据
@Component
public class ChartTypeTemplateConfig {

    private final Map<String, String> chartTypeTemplateMap = new HashMap<>();
    @PostConstruct
    public void init() {
        // 折线图模板
        chartTypeTemplateMap.put("line", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"trigger": "axis"},
              "legend": {"data": [], "bottom": 0},
              "xAxis": {"type": "category", "data": []},
              "yAxis": {"type": "value"},
              "series": [{"name": "", "type": "line", "data": []}],
              "grid": {"left": "3%", "right": "4%", "bottom": "12%", "containLabel": true}
            }
            """);

        // 柱状图模板
        chartTypeTemplateMap.put("bar", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"trigger": "axis"},
              "legend": {"data": [], "bottom": 0},
              "xAxis": {"type": "category", "data": []},
              "yAxis": {"type": "value"},
              "series": [{"name": "", "type": "bar", "data": []}],
              "grid": {"left": "3%", "right": "4%", "bottom": "12%", "containLabel": true}
            }
            """);

        // 饼图模板
        chartTypeTemplateMap.put("pie", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"trigger": "item"},
              "legend": {"orient": "vertical", "left": "left", "bottom": 0},
              "series": [{
                "name": "数据项",
                "type": "pie",
                "radius": "50%",
                "data": [],
                "emphasis": {"itemStyle": {"shadowBlur": 10, "shadowOffsetX": 0, "shadowColor": "rgba(0, 0, 0, 0.5)"}}
              }]
            }
            """);

        // 雷达图模板
        chartTypeTemplateMap.put("radar", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"trigger": "item"},
              "legend": {"data": [], "bottom": 0},
              "radar": {
                "indicator": [],
                "shape": "circle",
                "splitNumber": 5,
                "axisName": {"color": "#000"},
                "splitLine": {"lineStyle": {"color": "rgba(0, 0, 0, 0.1)"}},
                "splitArea": {"show": true, "areaStyle": {"color": ["rgba(0, 0, 0, 0.02)", "rgba(0, 0, 0, 0.05)"]}},
                "axisLine": {"lineStyle": {"color": "rgba(0, 0, 0, 0.2)"}}
              },
              "series": [{
                "name": "数据",
                "type": "radar",
                "data": [],
                "symbolSize": 6,
                "lineStyle": {"width": 2},
                "areaStyle": {"opacity": 0.1}
              }]
            }
            """);

        // 散点图模板
        chartTypeTemplateMap.put("scatter", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"trigger": "item", "formatter": "{a} <br/>{b} : {c}"},
              "legend": {"data": [], "bottom": 0},
              "xAxis": {"type": "value", "scale": true},
              "yAxis": {"type": "value", "scale": true},
              "series": [{
                "name": "数据点",
                "type": "scatter",
                "data": [],
                "symbolSize": 10,
                "itemStyle": {"color": "#5470c6"}
              }],
              "grid": {"left": "3%", "right": "4%", "bottom": "12%", "containLabel": true}
            }
            """);

        // 面积图模板
        chartTypeTemplateMap.put("area", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"trigger": "axis"},
              "legend": {"data": [], "bottom": 0},
              "xAxis": {"type": "category", "data": [], "boundaryGap": false},
              "yAxis": {"type": "value"},
              "series": [{
                "name": "",
                "type": "line",
                "data": [],
                "areaStyle": {"color": {"type": "linear", "x": 0, "y": 0, "x2": 0, "y2": 1, "colorStops": [{"offset": 0, "color": "rgba(84, 112, 198, 0.5)"}, {"offset": 1, "color": "rgba(84, 112, 198, 0.1)"}]}},
                "smooth": true
              }],
              "grid": {"left": "3%", "right": "4%", "bottom": "12%", "containLabel": true}
            }
            """);

        // 仪表盘模板
        chartTypeTemplateMap.put("gauge", """
            {
              "title": {"text": "%s", "left": "center"},
              "tooltip": {"formatter": "{a} <br/>{b} : {c}%"},
              "series": [{
                "name": "指标",
                "type": "gauge",
                "radius": "90%",
                "progress": {"show": true, "width": 18},
                "axisLine": {"lineStyle": {"width": 18}},
                "axisTick": {"show": false},
                "splitLine": {"length": 15, "lineStyle": {"width": 3, "color": "#999"}},
                "axisLabel": {"distance": -40, "color": "#999", "fontSize": 12},
                "anchor": {"show": true, "showAbove": true, "size": 18, "itemStyle": {"borderWidth": 8}},
                "detail": {
                  "valueAnimation": true,
                  "formatter": "{value}%",
                  "backgroundColor": "rgba(0,0,0,0.8)",
                  "borderColor": "#999",
                  "borderWidth": 1,
                  "width": "60%",
                  "lineHeight": 40,
                  "height": 40,
                  "borderRadius": 4,
                  "offsetCenter": [0, "70%"],
                  "textStyle": {"fontSize": 20, "color": "#fff"}
                },
                "title": {"show": false},
                "data": [{"value": 0, "name": ""}]
              }]
            }
            """);
    }

    //获取模版
    public String getTemplate(String chartType) {
        return chartTypeTemplateMap.getOrDefault(chartType, chartTypeTemplateMap.get("bar")); // 默认柱状图
    }

    //判断是否支持该图表类型
    public boolean supportsChartType(String chartType) {
        return chartTypeTemplateMap.containsKey(chartType);
    }
}
