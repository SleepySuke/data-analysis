---
skillName: web_table_extract
description: 网页表格提取
agentName: web_scraper
allowedTools:
  - executeScript
scripts:
  - web_scraper/scripts/web_scraper.py
  - web_scraper/scripts/table_extractor.py
---

# 网页表格提取

## 目标

从用户指定的网页中提取 HTML 表格数据，转换为结构化 CSV 格式，供后续分析使用。

## 前置条件

- 用户必须提供有效的 URL 地址
- 目标网页必须包含可识别的 HTML 表格（`<table>` 标签）
- 网页必须可公开访问（无需登录验证）

## 执行步骤

### 步骤 1：抓取网页并提取表格

调用 `executeScript` 工具，执行 `web_scraper/scripts/web_scraper.py`：
- `scriptPath`: `"web_scraper/scripts/web_scraper.py"`
- `arguments`: `{"url": "用户提供的URL", "extract_type": "table"}`
- `stdinData`: `""`

脚本返回 JSON，包含：
- `tables`: 所有表格数据（headers + rows）
- `table_count`: 表格数量

检查 `exitCode` 是否为 0，如果非 0 则查看 `error` 字段。

### 步骤 2（可选）：精确提取指定表格

如果页面有多个表格，只需提取特定表格，调用 `executeScript` 执行 `web_scraper/scripts/table_extractor.py`：
- `scriptPath`: `"web_scraper/scripts/table_extractor.py"`
- `arguments`: `{"url": "用户提供的URL", "table_index": 0, "output": "csv"}`
- `stdinData`: `""`

`table_index` 从 0 开始，-1 表示提取全部表格。

### 步骤 3：数据校验

对提取的 CSV 数据进行基本校验：
- 列数一致性检查（每行列数应与表头一致）
- 空行过滤
- 字符编码确认（确保中文内容正确）

## 输出格式

按以下结构输出：

```
【网页表格提取结果】

来源：{URL}
提取时间：{时间戳}
表格数量：{n}

表格 1：{表格标题或描述}
- 行数：{n}，列数：{n}
- 列名：{列出所有列名}
{CSV 数据}

表格 2：{表格标题或描述}
...

【提取质量】
- 成功率：{成功提取的表格数/总表格数}
- 数据完整性：{缺失值统计}
```

## 约束

- 仅提取公开可访问的网页，不尝试绕过任何访问限制
- 提取的数据仅用于用户声明的分析目的
- 如果网页包含动态加载内容（JavaScript 渲染），告知用户当前工具可能无法完整提取
- 单次提取的数据量上限为 10000 行
