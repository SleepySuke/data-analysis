---
skillName: ad_hoc_query
description: 即席查询
agentName: sql_analyst
allowedTools:
  - schema_introspect
  - sql_execution
  - result_interpreter
---

# 即席查询

## 目标

根据用户的自然语言描述，自动生成并执行 SQL 查询，将查询结果以易理解的格式呈现给用户。

## 前置条件

- 数据库连接可用
- 用户意图可转化为 SQL 查询
- 当前用户拥有查询权限（仅 SELECT 操作）

## 执行步骤

### 步骤 1：理解用户意图

分析用户自然语言请求，识别：
- 查询目标（查什么数据）
- 过滤条件（筛选范围）
- 聚合方式（求和、计数、平均等）
- 排序和分页需求
- 时间范围

### 步骤 2：获取表结构

调用 `schema_introspect` 工具，参数：
- `operation`: "describe_table"
- `tableName`: "{根据用户意图判断的相关表名}"

确认目标表的字段名和数据类型，避免 SQL 中引用不存在的列。

### 步骤 3：生成并执行 SQL

调用 `sql_execution` 工具，参数：
- `sql`: 根据步骤 1-2 生成的 SQL 语句

SQL 生成规则：
- 只允许 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP
- 添加 LIMIT 子句（默认上限 1000 行）
- 对用户输入的条件做参数化处理，防止 SQL 注入
- 复杂查询添加注释说明

### 步骤 4：结果解读

调用 `result_interpreter` 工具，参数：
- `queryResult`: 步骤 3 的查询结果
- `userIntent`: 用户的原始自然语言请求

将查询结果翻译为用户可理解的分析结论。

## 输出格式

按以下结构输出：

```
【即席查询结果】

用户问题：{原始问题}
生成 SQL：
```sql
{生成的 SQL 语句}
```

查询结果：
{结果表格或摘要}

结果解读：
{用自然语言解释查询结果的含义}

注意事项：
{数据局限性、时效性等提示}
```

## 约束

- 生成的 SQL 必须是只读查询（SELECT），绝对不能包含任何修改操作
- 必须添加 LIMIT 限制，防止返回过多数据
- 用户输入的值必须做安全处理，防止 SQL 注入
- 查询超时（> 30 秒）时提示用户优化查询条件
- 不直接执行用户提供的原始 SQL，必须经过意图解析和重生成
