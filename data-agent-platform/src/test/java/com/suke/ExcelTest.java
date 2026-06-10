package com.suke;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author 自然醒
 * @version 1.0
 */

public class ExcelTest {

     public static MultipartFile createSmallExcelFile() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Test Data");

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"日期", "销售额", "利润"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 创建10行数据
            String[][] data = {
                    {"2024-01", "10000", "2000"},
                    {"2024-02", "12000", "2500"},
                    {"2024-03", "15000", "3000"},
                    {"2024-04", "13000", "2700"},
                    {"2024-05", "18000", "3500"},
                    {"2024-06", "20000", "4000"},
                    {"2024-07", "19000", "3800"},
                    {"2024-08", "21000", "4200"},
                    {"2024-09", "22000", "4400"},
                    {"2024-10", "24000", "4800"}
            };

            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < data[i].length; j++) {
                    row.createCell(j).setCellValue(data[i][j]);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            return new MockMultipartFile(
                    "test_small.xlsx",
                    "test_small.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建大型Excel测试文件（模拟大数据量）
     */
    public static MultipartFile createLargeExcelFile(int rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Large Data");

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "姓名", "年龄", "城市", "工资", "部门"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 创建大量数据
            String[] cities = {"北京", "上海", "广州", "深圳", "杭州"};
            String[] departments = {"技术部", "市场部", "销售部", "人事部", "财务部"};

            for (int i = 1; i <= rows; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(i); // ID
                row.createCell(1).setCellValue("员工" + i); // 姓名
                row.createCell(2).setCellValue(20 + (i % 40)); // 年龄 20-60
                row.createCell(3).setCellValue(cities[i % cities.length]); // 城市
                row.createCell(4).setCellValue(5000 + (i % 20) * 1000); // 工资 5000-25000
                row.createCell(5).setCellValue(departments[i % departments.length]); // 部门
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            return new MockMultipartFile(
                    "test_large.xlsx",
                    "test_large.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建CSV测试文件
     */
    public static MultipartFile createCsvFile() {
        String csvContent = "日期,销售额,利润,增长率\n" +
                "2024-01,10000,2000,20%\n" +
                "2024-02,12000,2500,25%\n" +
                "2024-03,15000,3000,20%\n" +
                "2024-04,13000,2700,15%\n" +
                "2024-05,18000,3500,30%";

        return new MockMultipartFile(
                "test.csv",
                "test.csv",
                "text/csv",
                csvContent.getBytes()
        );
    }

}
