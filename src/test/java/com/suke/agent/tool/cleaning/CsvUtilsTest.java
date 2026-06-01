/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description CsvUtils 单元测试
 */

package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvUtilsTest {

    // ==================== parseCsvLine ====================

    @Nested
    @DisplayName("parseCsvLine")
    class ParseCsvLineTests {

        @Test
        @DisplayName("简单CSV行：a,b,c")
        void parseCsvLineSimple() {
            String[] result = CsvUtils.parseCsvLine("a,b,c");
            assertArrayEquals(new String[]{"a", "b", "c"}, result);
        }

        @Test
        @DisplayName("引号内包含逗号：a,\"b,c\",d")
        void parseCsvLineQuotedComma() {
            String[] result = CsvUtils.parseCsvLine("a,\"b,c\",d");
            assertArrayEquals(new String[]{"a", "b,c", "d"}, result);
        }

        @Test
        @DisplayName("空行返回单元素数组")
        void parseCsvLineEmptyLine() {
            String[] result = CsvUtils.parseCsvLine("");
            assertEquals(1, result.length);
            assertEquals("", result[0]);
        }

        @Test
        @DisplayName("单字段行")
        void parseCsvLineSingleField() {
            String[] result = CsvUtils.parseCsvLine("hello");
            assertArrayEquals(new String[]{"hello"}, result);
        }

        @Test
        @DisplayName("尾部逗号产生空字段")
        void parseCsvLineTrailingComma() {
            String[] result = CsvUtils.parseCsvLine("a,b,");
            assertArrayEquals(new String[]{"a", "b", ""}, result);
        }

        @Test
        @DisplayName("引号内包含多个逗号")
        void parseCsvLineMultipleQuotedCommas() {
            String[] result = CsvUtils.parseCsvLine("\"a,b,c\",d");
            assertArrayEquals(new String[]{"a,b,c", "d"}, result);
        }

        @Test
        @DisplayName("空格不作为分隔符")
        void parseCsvLineSpacesPreserved() {
            String[] result = CsvUtils.parseCsvLine("hello world,foo bar");
            assertArrayEquals(new String[]{"hello world", "foo bar"}, result);
        }
    }

    // ==================== escapeField ====================

    @Nested
    @DisplayName("escapeField")
    class EscapeFieldTests {

        @Test
        @DisplayName("无特殊字符不转义")
        void escapeFieldNoSpecial() {
            assertEquals("hello", CsvUtils.escapeField("hello"));
        }

        @Test
        @DisplayName("包含逗号加引号转义")
        void escapeFieldWithComma() {
            assertEquals("\"a,b\"", CsvUtils.escapeField("a,b"));
        }

        @Test
        @DisplayName("包含双引号：双引号转义为两个双引号")
        void escapeFieldWithQuote() {
            assertEquals("\"say \"\"hi\"\"\"", CsvUtils.escapeField("say \"hi\""));
        }

        @Test
        @DisplayName("包含换行加引号转义")
        void escapeFieldWithNewline() {
            assertEquals("\"line1\nline2\"", CsvUtils.escapeField("line1\nline2"));
        }

        @Test
        @DisplayName("null返回空字符串")
        void escapeFieldNull() {
            assertEquals("", CsvUtils.escapeField(null));
        }

        @Test
        @DisplayName("空字符串不转义")
        void escapeFieldEmpty() {
            assertEquals("", CsvUtils.escapeField(""));
        }
    }

    // ==================== toCsv ====================

    @Nested
    @DisplayName("toCsv")
    class ToCsvTests {

        @Test
        @DisplayName("toCsv(headers, rows) - 含逗号数据正确转义")
        void toCsvWithHeadersEscapesData() {
            String[] headers = {"name", "desc"};
            List<String[]> rows = Arrays.<String[]>asList(new String[]{"alice", "hello,world"});
            String csv = CsvUtils.toCsv(headers, rows);
            assertTrue(csv.contains("\"hello,world\""));
            assertFalse(csv.contains("hello,world\"\"")); // no double-escaping
        }

        @Test
        @DisplayName("toCsv(headers, rows) - 基本输出格式正确")
        void toCsvWithHeadersBasicFormat() {
            String[] headers = {"a", "b"};
            List<String[]> rows = Arrays.asList(new String[]{"1", "2"}, new String[]{"3", "4"});
            String csv = CsvUtils.toCsv(headers, rows);
            String expected = "a,b\n1,2\n3,4";
            assertEquals(expected, csv);
        }

        @Test
        @DisplayName("toCsv(rows) - 空列表返回空字符串")
        void toCsvRowsEmpty() {
            assertEquals("", CsvUtils.toCsv(List.of()));
        }

        @Test
        @DisplayName("toCsv(rows) - 单行正确输出")
        void toCsvRowsSingleRow() {
            List<String[]> rows = Arrays.<String[]>asList(new String[]{"x", "y"});
            assertEquals("x,y", CsvUtils.toCsv(rows));
        }

        @Test
        @DisplayName("toCsv(rows) - 多行正确输出")
        void toCsvRowsMultipleRows() {
            List<String[]> rows = Arrays.asList(
                    new String[]{"h1", "h2"},
                    new String[]{"v1", "v2"}
            );
            assertEquals("h1,h2\nv1,v2", CsvUtils.toCsv(rows));
        }
    }

    // ==================== errorJson ====================

    @Nested
    @DisplayName("errorJson")
    class ErrorJsonTests {

        @Test
        @DisplayName("返回合法JSON，success=false")
        void errorJsonReturnsValidJson() {
            String json = CsvUtils.errorJson("something went wrong");
            JSONObject obj = JSON.parseObject(json);
            assertFalse(obj.getBoolean("success"));
            assertEquals("something went wrong", obj.getString("error"));
        }

        @Test
        @DisplayName("空消息也能正常序列化")
        void errorJsonEmptyMessage() {
            String json = CsvUtils.errorJson("");
            JSONObject obj = JSON.parseObject(json);
            assertFalse(obj.getBoolean("success"));
            assertEquals("", obj.getString("error"));
        }
    }

    // ==================== successJson ====================

    @Nested
    @DisplayName("successJson")
    class SuccessJsonTests {

        @Test
        @DisplayName("返回正确结构，包含自定义键值对")
        void successJsonReturnsCorrectStructure() {
            String json = CsvUtils.successJson("count", 42, "name", "test");
            JSONObject obj = JSON.parseObject(json);
            assertTrue(obj.getBoolean("success"));
            assertEquals(42, obj.getIntValue("count"));
            assertEquals("test", obj.getString("name"));
        }

        @Test
        @DisplayName("无额外参数只包含success=true")
        void successJsonNoExtraParams() {
            String json = CsvUtils.successJson();
            JSONObject obj = JSON.parseObject(json);
            assertEquals(1, obj.size());
            assertTrue(obj.getBoolean("success"));
        }

        @Test
        @DisplayName("保持插入顺序")
        void successJsonPreservesOrder() {
            String json = CsvUtils.successJson("z", 1, "a", 2);
            JSONObject obj = JSON.parseObject(json);
            // LinkedHashMap preserves order, fastjson2 JSONObject also preserves
            assertEquals(1, obj.getIntValue("z"));
            assertEquals(2, obj.getIntValue("a"));
        }
    }
}
