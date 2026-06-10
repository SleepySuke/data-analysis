---
skillName: data_quality_report
description: 数据质量报告
agentName: data_cleaner
allowedTools:
  - executeScript
scripts:
  - data_cleaner/scripts/data_profiler.py
---

# 数据质量报告

## 目标

生成完整的数据质量画像，包括缺失率、异常值比例、重复率、数据类型一致性、格式规范性等多维度指标，帮助用户全面评估数据可用性。

## 前置条件

- 数据为结构化格式（CSV 或表格）
- 至少包含表头和 1 行数据
- 无特定列要求，适用于任意数据集

## 执行步骤

### 步骤 1：数据概貌分析

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/data_profiler.py"
- `arguments`: {"scope": "full"}
- `stdinData`: 原始 CSV 数据

获取全局信息：
- 总行数、总列数
- 各列数据类型
- 存储大小估算

### 步骤 2：缺失值分析

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/data_profiler.py"
- `arguments`: {"scope": "missing"}
- `stdinData`: 原始 CSV 数据

分析各列缺失情况：
- 各列缺失值数量和占比
- 缺失值模式（随机缺失 / 连续缺失 / 特定条件缺失）
- 行级缺失统计（完全完整的行占比）

### 步骤 3：重复性分析

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/data_profiler.py"
- `arguments`: {"scope": "duplicate"}
- `stdinData`: 原始 CSV 数据

分析重复情况：
- 完全重复行数量
- 基于主键列的重复（如可识别主键）
- 近似重复率（大小写/空格差异）

### 步骤 4：一致性与规范性分析

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/data_profiler.py"
- `arguments`: {"scope": "consistency"}
- `stdinData`: 原始 CSV 数据

检查：
- 数据类型一致性（数值列中的非数值内容）
- 格式一致性（日期格式、电话号码格式等）
- 值域一致性（超出预期范围的值）
- 编码一致性（全角/半角、繁简体混用）

## 输出格式

按以下结构输出：

```
【数据质量报告】

一、数据概貌
- 数据规模：{行数} 行 × {列数} 列
- 评估时间：{时间戳}

二、质量评分（满分100）
| 维度 | 得分 | 等级 |
|------|------|------|
| 完整性 | {score} | {A/B/C/D} |
| 唯一性 | {score} | {A/B/C/D} |
| 一致性 | {score} | {A/B/C/D} |
| 规范性 | {score} | {A/B/C/D} |
| 综合评分 | {score} | {等级} |

等级标准：A(90-100) B(70-89) C(50-69) D(<50)

三、详细分析
{各维度的详细指标列表}

四、主要问题 Top 5
1. {最严重的质量问题}
2. ...
5. ...

五、修复建议
1. {优先修复的问题及方法}
2. ...
```

## 约束

- 评分标准客观，不因数据量大小而偏移评分
- 大数据集（> 10万行）采用采样分析时需注明
- 不修改原始数据，仅生成报告
- 质量评分算法需可解释
