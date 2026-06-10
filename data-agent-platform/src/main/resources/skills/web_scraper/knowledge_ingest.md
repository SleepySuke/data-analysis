---
skillName: knowledge_ingest
description: 知识入库
agentName: web_scraper
allowedTools:
  - executeScript
scripts:
  - web_scraper/scripts/web_scraper.py
  - data_cleaner/scripts/dedup_standardizer.py
---

# 知识入库

## 目标

将提取的网页内容进行向量化处理后写入知识库，支持后续通过语义检索进行知识查询和辅助分析。

## 前置条件

- 用户必须提供有效的 URL 地址
- 知识库（向量存储）必须可用且已配置
- 提取的内容应具有知识价值（非临时性信息）

## Pipeline 流程

本技能采用 3 步 Pipeline 架构：

1. **Scrape（抓取）**：调用 `web_scraper.py` 获取网页内容并提取正文
2. **Clean（清洗）**：调用 `dedup_standardizer.py` 对提取内容进行去重与标准化
3. **Ingest（入库）**：将清洗后的内容分块向量化并写入知识库

## 执行步骤

### 步骤 1：抓取网页内容

调用 `executeScript` 工具，参数：
- `scriptPath`: "web_scraper/scripts/web_scraper.py"
- `arguments`: {"url": "<用户提供的网页地址>", "extract_type": "article"}

验证获取结果：
- HTTP 状态码为 200
- 内容长度 > 100 字符（过短的内容可能无知识价值）

### 步骤 2：内容清洗与标准化

将步骤 1 的输出通过标准输入传递给清洗脚本：

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/dedup_standardizer.py"
- `arguments`: {"standardize": "all"}
- `stdinData`: 步骤 1 返回的提取内容

处理清洗结果：
- 去除导航栏、广告、页脚等无关内容
- 保留正文文本和小标题结构
- 过滤过短的段落（< 20 字符）
- 识别内容主题和领域分类

### 步骤 3：向量化入库

使用 `knowledge_ingest` 工具（预留接口，后续集成），参数：
- `content`: 步骤 2 清洗后的内容
- `source`: URL 来源地址
- `category`: 根据内容自动判断的领域分类

入库处理：
- 内容按段落分块（每块 200-500 字）
- 每块生成向量嵌入
- 存储到向量数据库，附加上下文元数据

## 输出格式

按以下结构输出：

```
【知识入库结果】

来源：{URL}
内容主题：{识别的主题}
领域分类：{分类}

入库统计：
- 原始内容长度：{字符数}
- 分块数量：{n}
- 成功入库：{n} 块
- 失败：{n} 块（{原因}）

知识摘要：
{对入库内容的简要概述，3-5 句话}
```

## 约束

- 不入库版权受限的内容
- 入库前必须进行内容质量检查（去除垃圾信息）
- 相同 URL 不重复入库（基于 source 去重）
- 入库失败时提供明确错误信息，不静默跳过
