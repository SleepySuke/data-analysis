---
skillName: missing_value_handle
description: 缺失值处理
agentName: data_cleaner
allowedTools:
  - executeScript
scripts:
  - data_cleaner/scripts/data_profiler.py
  - data_cleaner/scripts/missing_value_handler.py
---

# 缺失值处理

## 目标

根据数据的缺失模式和特征，选择合适的填充或删除策略，提升数据完整性，确保后续分析的可靠性。

## 前置条件

- 数据包含缺失值（空值、NULL、空白、NaN 等）
- 用户已了解缺失情况（如未了解，建议先运行 `data_quality_report` skill）
- 至少 1 列存在缺失值

## 执行步骤

### 步骤 1：缺失模式分析

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/data_profiler.py"
- `arguments`: {"scope": "missing"}
- `stdinData`: 原始 CSV 数据

识别缺失模式：
- MCAR（完全随机缺失）：缺失与任何变量无关
- MAR（随机缺失）：缺失与其他变量相关
- MNAR（非随机缺失）：缺失与自身值相关

统计信息：
- 各列缺失数量和占比
- 行级缺失分布
- 缺失值的相关性（某列缺失时其他列是否也缺失）

### 步骤 2：策略选择

根据步骤 1 的分析结果，为每列选择处理策略：

| 缺失率 | 推荐策略 |
|--------|----------|
| < 5% | 均值/中位数填充（数值列）或众数填充（类别列） |
| 5%-20% | 前向/后向填充（时序数据）或插值法 |
| 20%-50% | 考虑删除该列或使用模型预测填充 |
| > 50% | 建议删除该列 |

策略选择需考虑：
- 列的业务含义（关键指标不可删除）
- 数据是否为时序（时序数据优先前向填充）
- 缺失是否为有意义的缺失（如"未填写"本身就是信息）

### 步骤 3：执行处理

调用 `executeScript` 工具，参数：
- `scriptPath`: "data_cleaner/scripts/missing_value_handler.py"
- `arguments`: {"strategy": "mean|median|mode|forward|backward|interpolation|drop_row|drop_column", "columns": "col1,col2"}
- `stdinData`: 原始 CSV 数据

## 输出格式

按以下结构输出：

```
【缺失值处理报告】

一、缺失概况
总缺失率：{百分比}
存在缺失的列：{n}/{总列数}
完全完整的行：{百分比}

二、处理策略
| 列名 | 缺失率 | 策略 | 填充值/操作 |
|------|--------|------|-------------|

三、处理结果
- 处理前行数：{n}
- 处理后行数：{n}
- 剩余缺失：{n} 个（{百分比}）
- 变更说明：{描述每列的处理结果}

四、影响评估
- 数据分布变化：{描述填充对分布的影响}
- 建议：{后续分析需要注意的事项}
```

## 约束

- 不自动删除行或列，需经用户确认后执行
- 填充操作需记录原始值和填充值，支持回滚
- 关键业务字段（如主键、金额）的缺失不做自动填充
- 处理后的数据需保留处理标记列（如 `_is_filled_{列名}`）
