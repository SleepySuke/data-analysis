/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description HTML内容提取工具，从网页中提取正文、标题和表格数据
 */

package com.suke.agent.tool.scraping;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ContentExtractorTool {

    @Tool(description = "从HTML中提取正文、标题和表格数据")
    public String extractContent(
            @ToolParam(description = "HTML内容") String htmlContent,
            @ToolParam(description = "提取类型：article/table/all，默认all") String extractType) {

        if (htmlContent == null || htmlContent.isBlank()) {
            return CsvUtils.errorJson("HTML内容为空");
        }

        String type = (extractType == null || extractType.isBlank()) ? "all" : extractType.toLowerCase();
        Document doc = Jsoup.parse(htmlContent);

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("title", doc.title());

        if ("article".equals(type) || "all".equals(type)) {
            result.put("content", extractArticleContent(doc));
        }

        if ("table".equals(type) || "all".equals(type)) {
            result.put("tables", extractTables(doc));
        }

        return result.toJSONString();
    }

    private String extractArticleContent(Document doc) {
        Element article = doc.selectFirst("article");
        if (article != null) {
            return article.text();
        }

        Element body = doc.body();
        if (body != null) {
            Elements paragraphs = body.select("p");
            if (!paragraphs.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Element p : paragraphs) {
                    String text = p.text().trim();
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n");
                    }
                }
                return sb.toString().trim();
            }
            return body.text();
        }
        return "";
    }

    private JSONArray extractTables(Document doc) {
        JSONArray tablesArr = new JSONArray();
        Elements tables = doc.select("table");

        for (Element table : tables) {
            JSONObject tableObj = new JSONObject();

            Elements headerRows = table.select("tr:has(th)");
            JSONArray headers = new JSONArray();
            if (!headerRows.isEmpty()) {
                Elements ths = headerRows.first().select("th");
                for (Element th : ths) {
                    headers.add(th.text().trim());
                }
            }

            Elements rows = table.select("tr");
            JSONArray rowsArr = new JSONArray();
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.isEmpty()) continue;

                JSONArray rowArr = new JSONArray();
                for (Element cell : cells) {
                    rowArr.add(cell.text().trim());
                }
                rowsArr.add(rowArr);
            }

            if (headers.isEmpty() && !rowsArr.isEmpty()) {
                JSONArray firstRow = rowsArr.getJSONArray(0);
                for (int i = 0; i < firstRow.size(); i++) {
                    headers.add("Column_" + (i + 1));
                }
                rowsArr.remove(0);
            }

            tableObj.put("headers", headers);
            tableObj.put("rows", rowsArr);
            tablesArr.add(tableObj);
        }

        return tablesArr;
    }

}
