# Data-Analysis 智能数据分析平台 — 系统架构设计说明书

---

## 一、项目概述

| 项目 | 说明 |
|------|------|
| **项目名称** | data-analysis 智能数据分析平台 |
| **核心定位** | AI + BI 数据可视化分析平台 |
| **核心能力** | 用户上传 Excel → AI 自动分析 → 生成分析结论 + ECharts 图表配置 |
| **技术基座** | Spring Boot 3.4.5 + Java 17 |
| **作者** | 自然醒 |

### 一句话概括

> 用户上传 Excel 文件并指定分析目标和图表类型，系统通过大语言模型（Qwen-Plus）自动完成数据分析，输出专业分析结论和可直接渲染的 ECharts 图表 JSON 配置。

---

## 二、技术栈全景图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (未包含在项目中)                    │
│               ECharts 渲染 · WebSocket 接收状态               │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTP / WebSocket
┌──────────────────────────────▼──────────────────────────────┐
│                     Spring Boot 3.4.5                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐   │
│  │ Web (REST)│ │ WebSocket│ │  AOP     │ │  Scheduling   │   │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │               业务层 (Service)                        │    │
│  │  UserService  ·  ChartService (同步/线程池异步/MQ异步)  │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────────┐   │
│  │  AIDocking     │  │  RAG 知识库     │  │  文件处理层    │   │
│  │  (Qwen-Plus)   │  │  (Redis Vector) │  │  (EasyExcel)  │   │
│  │  (DeepSeek)    │  │  (Embedding)    │  │  (MinIO)      │   │
│  └───────┬────────┘  └───────┬────────┘  └───────┬───────┘   │
└──────────┼───────────────────┼───────────────────┼───────────┘
           │                   │                   │
     ┌─────▼─────┐      ┌─────▼─────┐      ┌──────▼──────┐
     │ DashScope  │      │  Redis    │      │   MinIO     │
     │ (阿里云AI) │      │ (向量库)  │      │ (对象存储)   │
     └───────────┘      └─────┬─────┘      └─────────────┘
                           │
     ┌───────────┐   ┌─────▼─────┐   ┌──────────┐
     │  RabbitMQ  │   │   MySQL   │   │ Redisson │
     │ (消息队列) │   │ (业务库)   │   │ (限流器) │
     └───────────┘   └───────────┘   └──────────┘
```

### 依赖组件详表

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.5 | 应用框架 |
| MyBatis-Plus | 3.5.7 | ORM 框架 |
| MySQL | 8.0 | 业务数据持久化 |
| Redis + Redisson | - | 缓存 / 分布式限流 / 向量存储 |
| RabbitMQ (Spring AMQP) | 3.4.5 | 异步消息队列 |
| MinIO | 8.5.17 | 大文件对象存储 |
| Spring AI Alibaba | 1.0.0.2 | AI 模型对接 (DashScope) |
| Spring AI Redis Store | 1.0.0 | 向量存储 |
| EasyExcel | 3.1.1 | Excel 文件解析 |
| guava-retrying | 2.0.0 | 重试机制 |
| Knife4j | 4.4.0 | API 文档 |
| jjwt | 0.9.1 | JWT 认证 |
| Hutool | 5.8.8 | 工具类库 |
| FastJSON2 | 2.0.51 | JSON 解析 |

---

## 三、系统架构分层

```
┌──────────────────────────────────────────────────┐
│                 Controller 层                      │
│   ChartController · UserController                │
│   (参数校验 · 限流 · 路由分发)                      │
├──────────────────────────────────────────────────┤
│                  Service 层                        │
│   ChartServiceImpl · UserServiceImpl              │
│   (业务编排 · 事务管理 · 状态机控制)                 │
├──────────────────────────────────────────────────┤
│              基础设施 / 工具层                      │
│   AIDocking · FileUtils · MinioUtil · RedisUtils  │
│   WebSocketServer · MessageProducer/Consumer      │
│   KnowledgeMap · KnowledgeSearchTool              │
│   PromptBuilder · ParseAIResponse · JWTUtil       │
├──────────────────────────────────────────────────┤
│                 数据访问层                          │
│   ChartMapper · UserMapper (MyBatis-Plus)         │
├──────────────────────────────────────────────────┤
│              外部存储 / 服务                        │
│   MySQL · Redis · MinIO · RabbitMQ · DashScope    │
└──────────────────────────────────────────────────┘
```

---

## 四、核心业务流程

### 4.1 主流程全链路

```
 ┌────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
 │  用户   │    │  文件     │    │  AI      │    │  响应     │    │  数据    │
 │  上传   │───▶│  预处理   │───▶│  分析    │───▶│  解析    │───▶│  持久化  │
 │ Excel  │    │  层       │    │  层      │    │  层      │    │  层      │
 └────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
```

#### 步骤详解

| 步骤 | 模块 | 做什么 |
|------|------|--------|
| 1 文件上传 | `ChartController` | 接收 `MultipartFile` + `UploadFileDTO`（目标、图表类型、文件名） |
| 2 参数校验 | Controller 层 | 文件格式、大小、目标非空、图表类型合法性 |
| 3 限流检查 | `RedisUtils` | Redisson 分布式限流，每秒 2 次请求 |
| 4 文件预处理 | `FileUtils` | Excel → CSV 转换，大文件智能分层采样 |
| 5 大文件存储 | `MinioUtil` | 原始文件 + 采样数据上传 MinIO |
| 6 Token 估算 | `ChartServiceImpl` | 粗略估算 AI 调用 Token 数，超阈值告警 |
| 7 Prompt 构建 | `PromptBuilder` | 系统提示词 + 图表模板 + 用户目标 + CSV 数据 |
| 8 AI 调用 | `AIDocking` | Qwen-Plus 模型，生成分析结论 + ECharts JSON |
| 9 重试保障 | `RetryConfig` | 网络超时重试（最多 3 次），Token 超限不重试 |
| 10 响应解析 | `ParseAIResponse` | 正则提取分析结论 + 图表 JSON，格式修复 |
| 11 数据持久化 | `ChartMapper` | 保存到 MySQL `chart` 表 |
| 12 状态推送 | `WebSocketServer` | 异步模式下实时推送 `waiting -> running -> succeed/failed` |

### 4.2 三种分析模式对比

| 维度 | 同步分析 | 线程池异步 | MQ 异步 |
|------|---------|-----------|---------|
| **接口** | `POST /chart/gen` | `POST /chart/gen/async` | `POST /chart/gen/async/mq` |
| **实现类方法** | `analysisFile()` | `asyncAnalyzeFile()` | `asyncAnalyze()` |
| **AI 调用方式** | 同步阻塞 | `CompletableFuture` + 线程池 | RabbitMQ 消费者 |
| **用户感知** | 请求等待响应 | 立即返回 `chartId` | 立即返回 `chartId` |
| **状态推送** | 无 | WebSocket 实时推送 | 无（数据库状态查询） |
| **大文件支持** | 支持 采样 + MinIO | 仅小文件 | 仅小文件 |
| **重试机制** | `syncAnalyzeRetryer` | `aiAnalyzeRetryer` | 无 |
| **超时控制** | 无 | 5 分钟超时 | 无 |
| **分布式支持** | 单机 | 单机 | 多节点消费 |
| **任务可靠性** | 低 | 低（线程池满则丢失） | 高（MQ 持久化 + 手动 ACK） |
| **适用场景** | 小文件快速分析 | 中等文件实时反馈 | 高并发分布式部署 |

### 4.3 图表状态机

```
                    ┌──────────┐
                    │   wait   │  (初始保存，等待处理)
                    └────┬─────┘
                         │ 开始处理
                         ▼
                    ┌──────────┐
              ┌────▶│ running  │◀─────┐
              │     └──┬───┬───┘      │
              │        │   │          │
         处理成功      │   │     处理失败/超时
              │        │   │          │
              ▼        │   ▼          ▼
         ┌────────┐    │  ┌────────┐
         │succeed │    │  │ failed │
         └────────┘    │  └────────┘
                       │
                    重试
```

---

## 五、各模块详细设计

### 5.1 用户认证模块

```
┌──────────┐     ┌──────────────────┐     ┌──────────┐
│  请求    │────▶│ JwtTokenInterceptor│────▶│ Controller│
│ (Header  │     │                  │     │          │
│  Auth)   │     │ 1. 提取 Token     │     │          │
└──────────┘     │ 2. 解析 Claims    │     └──────────┘
                 │ 3. 查询 User      │
                 │ 4. 存入 UserContext│
                 │    (ThreadLocal)  │
                 └──────────────────┘
```

**关键组件：**

| 组件 | 职责 |
|------|------|
| `JWTUtil` | Token 生成与解析 |
| `JWTProperties` | 配置：密钥 `SukeBug`、TTL 30 分钟、Header 名 `Authorization` |
| `JwtTokenInterceptor` | 拦截器：解析 Token -> 查用户 -> 设 ThreadLocal -> 请求后清理 |
| `UserContext` | ThreadLocal 存储当前登录用户 ID |
| `AuthCheck` 注解 | 标记需要权限校验的接口 |
| `AuthInterceptor` | AOP 切面，根据注解校验用户角色 |

### 5.2 文件处理模块

```
              MultipartFile
                   │
                   ▼
            ┌──────────────┐
            │  文件校验     │
            │  后缀/大小    │
            └──────┬───────┘
                   │
            ┌──────▼───────┐
            │  大小判断     │
            │  < 10MB ?    │
            └──┬───────┬───┘
          Yes  │       │  No
               ▼       ▼
     ┌─────────────┐  ┌─────────────────────────┐
     │ 直接全量转换 │  │  智能分层采样             │
     │ Excel->CSV  │  │  ┌─────────────────────┐ │
     └──────┬──────┘  │  │ >50MB -> 采样1000行  │ │
            │         │  │ >20MB -> 采样2000行  │ │
            │         │  │ >10MB -> 采样3000行  │ │
            │         │  └─────────────────────┘ │
            │         │                          │
            │         │  分层采样策略：            │
            │         │  ┌─────────────────────┐ │
            │         │  │ 前25%  -> 取 sampleN │ │
            │         │  │ 中50%  -> 随机 sampleN│ │
            │         │  │ 后25%  -> 取 sampleN │ │
            │         │  │ 不够   -> 随机补充    │ │
            │         │  └─────────────────────┘ │
            │         └──────────┬───────────────┘
            │                    │
            │         ┌──────────▼───────────────┐
            │         │ 采样 CSV > 1MB ?          │
            │         │ Yes -> 上传 MinIO 存路径   │
            │         │ No  -> 直接内存处理        │
            │         └──────────┬───────────────┘
            │                    │
            └────────┬───────────┘
                     ▼
               CSV 字符串
```

### 5.3 AI 对接模块

```
┌───────────────────────────────────────────────────┐
│                  SaaLLMConfig                      │
│                                                    │
│   ┌─────────────┐    ┌──────────────┐             │
│   │  "qwen"     │    │  "deepseek"  │             │
│   │  ChatModel  │    │  ChatModel   │             │
│   │  qwen-plus  │    │  deepseek-v3 │             │
│   └──────┬──────┘    └──────────────┘             │
│          │                                         │
│   ┌──────▼──────┐    ┌──────────────┐             │
│   │qwenChatClient│   │deepseekClient│             │
│   │  ChatClient  │   │  ChatClient  │             │
│   └──────┬──────┘    └──────────────┘             │
│          │                                         │
│   ┌──────▼──────┐                                 │
│   │embeddingModel│  text-embedding-v2             │
│   │EmbeddingModel│  (用于 RAG 向量化)              │
│   └─────────────┘                                 │
└───────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────┐
│                  AIDocking                         │
│                                                    │
│   doDataAnalysis(goal, chartType, csvData)         │
│          │                                         │
│          ▼                                         │
│   PromptBuilder.buildPrompt(chartType)             │
│          │                                         │
│          ▼                                         │
│   System Prompt (数据分析师角色)                     │
│   + ECharts 图表模板 (ChartTypeTemplateConfig)      │
│   + 用户分析目标                                    │
│   + 原始 CSV 数据                                   │
│          │                                         │
│          ▼                                         │
│   qwenClient.prompt().system(prompt).call()        │
│          │                                         │
│          ▼                                         │
│   AI 原始响应文本                                   │
└───────────────────────────────────────────────────┘
```

**支持的图表类型 (7 种)：**

| 图表类型 | Key | 模板特点 |
|---------|-----|---------|
| 折线图 | `line` | xAxis(category) + yAxis(value) + series(line) |
| 柱状图 | `bar` | xAxis(category) + yAxis(value) + series(bar) |
| 饼图 | `pie` | series(pie) + radius(50%) + emphasis 阴影 |
| 雷达图 | `radar` | radar.indicator + series(radar) + areaStyle |
| 散点图 | `scatter` | xAxis(value) + yAxis(value) + symbolSize(10) |
| 面积图 | `area` | series(line) + areaStyle(渐变色) + smooth |
| 仪表盘 | `gauge` | series(gauge) + progress + detail(动画) |

### 5.4 AI 响应解析模块

```
AI 原始响应文本
       │
       ▼
┌─────────────────────────────────────────┐
│           ParseAIResponse               │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ 1. 提取分析结论                     │  │
│  │   优先：正则匹配【数据分析结论】     │  │
│  │   降级：找图表代码之前的内容         │  │
│  │   清理：移除标记、代码块、JSON片段   │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ 2. 提取图表配置                     │  │
│  │   优先：正则匹配 ```json ... ```    │  │
│  │   降级：匹配含 "xAxis" 的 JSON     │  │
│  │   兜底：生成默认空图表配置          │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ 3. JSON 格式保障                   │  │
│  │   FastJSON2 校验合法性             │  │
│  │   不合法则自动补全 { }             │  │
│  └───────────────────────────────────┘  │
│                                         │
│  输出 -> AnalysisResult(analysis, config)│
└─────────────────────────────────────────┘
```

### 5.5 RAG 知识库模块

```
┌─────────────────────────────────────────────────────────────┐
│                     RAG 架构                                 │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────────────────┐   │
│  │  知识源 (CSV)     │    │  向量存储 (Redis VectorStore) │   │
│  │                  │    │                              │   │
│  │ financial.csv    │───▶│  索引: idx:data_analysis_    │   │
│  │ (金融 8 条知识)   │    │        knowledge_v1          │   │
│  │                  │    │                              │   │
│  │ medical.csv      │───▶│  前缀: vec:knowledge:        │   │
│  │ (医疗 8 条知识)   │    │                              │   │
│  │                  │    │  算法: HSNW                   │   │
│  └──────────────────┘    │                              │   │
│                          │  元数据字段:                   │   │
│  ┌──────────────────┐    │  - knowledge_type (知识类型)  │   │
│  │  嵌入模型         │    │  - industry (行业)           │   │
│  │  DashScope       │    │  - chart_type (推荐图表)     │   │
│  │  text-embedding  │    │  - source (来源)             │   │
│  │  -v2             │    └──────────────────────────────┘   │
│  └──────────────────┘                                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  知识库初始化流程 (KnowledgeMap.@PostConstruct)       │   │
│  │                                                       │   │
│  │  1. 加载 classpath:knowledge/*.csv                    │   │
│  │  2. 解析 CSV 行 -> Document 对象                       │   │
│  │     - content/text 列 -> 文档正文                      │   │
│  │     - 其他列 -> metadata                              │   │
│  │     - 文件名 -> 自动推断 knowledge_type/industry       │   │
│  │  3. 计算内容 MD5 作为文档 ID                          │   │
│  │  4. 根据策略更新：                                    │   │
│  │     - full: 清空重建                                 │   │
│  │     - incremental: MD5 对比差异更新                   │   │
│  │  5. 分批写入 VectorStore (100条/批, 间隔100ms)        │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  知识检索流程 (KnowledgeSearchTool)                   │   │
│  │                                                       │   │
│  │  用户查询 "分析销售额增长趋势"                          │   │
│  │       |                                               │   │
│  │       v                                               │   │
│  │  查询改写 (多路召回)                                   │   │
│  │  ├── 原始查询: "分析销售额增长趋势"                    │   │
│  │  ├── 关键词提取: ["趋势", "增长"]                     │   │
│  │  ├── 简化查询: "分析销售额增长"                        │   │
│  │  └── 领域扩展: "分析收入增长趋势" "分析业绩增长趋势"   │   │
│  │       |                                               │   │
│  │       v                                               │   │
│  │  多查询并行检索 VectorStore (每条 Top5)                │   │
│  │       |                                               │   │
│  │       v                                               │   │
│  │  结果去重 (按 Document ID)                            │   │
│  │       |                                               │   │
│  │       v                                               │   │
│  │  格式化返回 (最多5条知识)                              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 5.6 重试机制

```
┌────────────────────────────────────────────────────────┐
│               两种重试器配置                             │
│                                                        │
│  syncAnalyzeRetryer (同步分析用)                        │
│  ├── 触发条件: 网络异常 / 超时 / 繁忙 / 结果为空        │
│  ├── 不重试: Token 超限 / 参数错误                      │
│  ├── 等待策略: 固定 2s                                 │
│  └── 停止策略: 最多 3 次尝试                            │
│                                                        │
│  aiAnalyzeRetryer (异步分析用)                          │
│  ├── 触发条件: AIDockingException / 网络异常            │
│  ├── 不重试: 参数错误                                   │
│  ├── 等待策略: 固定 2s                                 │
│  └── 停止策略: 最多 3 次尝试                            │
│                                                        │
│  重试监听: 每次重试记录日志 (异常信息 / 成功结果)        │
└────────────────────────────────────────────────────────┘
```

### 5.7 WebSocket 实时通信

```
┌──────────┐  connect   ┌──────────────────────────┐
│  前端    │───────────▶│  WebSocketServer         │
│          │◀───────────│  /websocket/{userId}     │
│          │  connected │                          │
│          │            │  ConcurrentHashMap       │
│          │  ping      │  <userId, WebSocketServer>│
│          │───────────▶│                          │
│          │◀───────────│  pong                    │
│          │            │                          │
│          │◀───────────│  analysis_status:        │
│          │            │  {status: "waiting"}     │
│          │◀───────────│  {status: "running"}     │
│          │◀───────────│  {status: "succeed",     │
│          │            │   data: {genChart,       │
│          │            │         genResult}}      │
│          │◀───────────│  {status: "failed"}      │
└──────────┘            └──────────────────────────┘
```

**消息格式 (WebSocketMessage)：**

```json
{
    "type": "analysis_status",
    "message": "正在处理数据",
    "status": "running",
    "data": {
        "chartId": 1234567890
    }
}
```

---

## 六、数据模型设计

### 6.1 核心表结构

```
┌─────────────────────────────────────────────────────────┐
│                       chart 表                          │
├─────────────┬──────────────┬───────────────────────────┤
│    字段      │     类型      │         说明              │
├─────────────┼──────────────┼───────────────────────────┤
│ id          │ Long         │ 主键 (雪花算法)            │
│ name        │ String       │ 图表名称                   │
│ goal        │ String       │ 分析目标                   │
│ chartData   │ String(TEXT) │ 原始 CSV 数据              │
│ chartType   │ String       │ 图表类型                   │
│ genChart    │ String(TEXT) │ 生成的 ECharts JSON 配置   │
│ genResult   │ String(TEXT) │ 生成的分析结论             │
│ status      │ String       │ 状态: wait/running/        │
│             │              │       succeed/failed       │
│ execMsg     │ String       │ 执行信息/错误信息          │
│ userId      │ Long         │ 创建用户 ID                │
│ minioPath   │ String       │ MinIO 文件路径(大文件)     │
│ createTime  │ DateTime     │ 创建时间 (自动填充)        │
│ updateTime  │ DateTime     │ 更新时间 (自动填充)        │
│ isDelete    │ Integer      │ 逻辑删除 0/1               │
└─────────────┴──────────────┴───────────────────────────┘
```

### 6.2 实体关系

```
┌──────────┐ 1    N ┌──────────┐
│   User   │────────│  Chart   │
│          │        │          │
│ id       │        │ userId   │
│ userName │        │ ...      │
│ userAccount│       └──────────┘
│ userPassword│
│ userRole │
└──────────┘
```

---

## 七、API 接口清单

### 7.1 图表接口 `/api/chart`

| 方法 | 路径 | 功能 | 认证 | 限流 |
|------|------|------|------|------|
| POST | `/chart/add` | 添加图表 | 需要 | 无 |
| GET | `/chart/getChart` | 获取图表详情 | 需要 | 无 |
| GET | `/chart/getChartEdit` | 获取编辑图表 | 需要 | 无 |
| POST | `/chart/gen` | **同步分析生成** | 需要 | 需要 |
| POST | `/chart/gen/async` | **线程池异步生成** | 需要 | 需要 |
| POST | `/chart/gen/async/mq` | **MQ 异步生成** | 需要 | 需要 |
| POST | `/chart/my/list/page` | 我的图表分页 | 需要 | 无 |
| POST | `/chart/editChart` | 编辑图表 | 需要 | 无 |

### 7.2 用户接口 `/api/user`

| 方法 | 路径 | 功能 | 认证 |
|------|------|------|------|
| POST | `/user/login` | 用户登录 | 无 |
| POST | `/user/register` | 用户注册 | 无 |
| GET | `/user/getLoginUser` | 获取当前用户 | 需要 |

### 7.3 WebSocket 接口

| 协议 | 路径 | 功能 |
|------|------|------|
| WS | `/websocket/{userId}` | 实时分析状态推送 |

---

## 八、架构演进路线

```
V1 ---- 同步模式
|      - 简单直接，阻塞等待 AI 响应
|      - 适合小文件、低并发
|
|---- V2 ---- 线程池异步
|      - CompletableFuture 提交到线程池
|      - WebSocket 实时推送进度
|      - 超时控制 (5 分钟)
|      - 线程池满时优雅拒绝
|
|---- V3 ---- MQ 异步
|      - RabbitMQ 解耦生产者/消费者
|      - 手动 ACK 保证消息不丢失
|      - 支持多节点分布式部署
|
|---- V4 ---- 大文件支持
|      - 智能分层采样
|      - MinIO 对象存储
|      - 流式处理避免 OOM
|      - Token 估算 + 告警
|
`---- V5 ---- RAG 知识库增强 (进行中)
       - Redis VectorStore 向量存储
       - CSV 知识文档加载 + 向量化
       - 多查询改写 + 领域扩展检索
       - PromptBuilder 已有 buildEnhancedPrompt
       - 但尚未接入主分析流程
```

---

## 九、项目包结构

```
com.suke
├── AnalysisApplication          # 启动类
├── annotation/                  # 自定义注解
│   └── AuthCheck                # 权限校验注解
├── aop/                         # AOP 切面
│   └── AuthInterceptor          # 权限拦截器
├── common/                      # 公共类
│   ├── Result                   # 统一响应
│   ├── ErrorCode                # 错误码
│   ├── AnalysisResult           # AI 分析结果
│   └── WebSocketMessage         # WS 消息体
├── config/                      # 配置类
│   ├── SaaLLMConfig             # AI 多模型配置
│   ├── RAGConfig                # 向量库配置
│   ├── AIDockingConfig          # AI 对接 Bean
│   ├── ChartTypeTemplateConfig  # 图表模板
│   ├── MinioConfig              # MinIO 客户端
│   ├── RedisConfig              # Redis 配置
│   ├── RetryConfig              # 重试策略
│   ├── ThreadPoolExecutorConfig # 线程池
│   ├── WebSocketConfig          # WebSocket 配置
│   ├── WebMvcConfiguration      # MVC 拦截器注册
│   └── MybatisPlusConfiguration # ORM 配置
├── constant/                    # 常量
│   ├── BIConstant               # MQ 常量
│   └── UserConstant             # 用户常量
├── context/                     # 上下文
│   └── UserContext              # ThreadLocal 用户ID
├── controller/                  # 控制器
│   ├── ChartController          # 图表接口
│   └── UserController           # 用户接口
├── datamq/                      # MQ 消息
│   ├── Init                     # MQ 初始化
│   ├── MessageProducer          # 生产者
│   └── MessageConsumer          # 消费者
├── domain/                      # 领域模型
│   ├── dto/                     # 数据传输对象
│   │   ├── chart/               # ChartAddDTO, ChartEditDTO...
│   │   ├── file/                # UploadFileDTO, SmartFileResult...
│   │   └── user/                # UserLoginDTO, UserRegisterDTO
│   ├── entity/                  # 实体
│   │   ├── User                 # 用户实体
│   │   └── Chart                # 图表实体
│   ├── enums/                   # 枚举
│   │   ├── UserRoleEnum         # 用户角色
│   │   └── ChartType            # 图表类型
│   └── vo/                      # 视图对象
│       ├── LoginUserVO          # 登录用户
│       └── GenChartVO           # 生成图表
├── exception/                   # 异常
│   ├── BaseException            # 基础异常
│   ├── AIDockingException       # AI 对接异常
│   ├── FailLoginException       # 登录异常
│   ├── FailRegisterException    # 注册异常
│   └── FailSaveException        # 保存异常
├── handler/                     # 处理器
│   ├── GlobalExceptionHandler   # 全局异常处理
│   └── MybatisHandler           # MyBatis 字段处理
├── interceptor/                 # 拦截器
│   └── JwtTokenInterceptor      # JWT 拦截器
├── mapper/                      # 数据访问
│   ├── ChartMapper              # 图表 Mapper
│   └── UserMapper               # 用户 Mapper
├── properties/                  # 配置属性
│   └── JWTProperties            # JWT 配置
├── rag/                         # RAG 知识库
│   ├── KnowledgeMap             # 知识库加载/更新
│   └── Init                     # 初始化入口
├── service/                     # 服务接口
│   ├── IChartService            # 图表服务接口
│   ├── IUserService             # 用户服务接口
│   └── impl/                    # 服务实现
│       ├── ChartServiceImpl     # 图表服务实现
│       └── UserServiceImpl      # 用户服务实现
├── tool/                        # Agent 工具
│   └── KnowledgeSearchTool      # 知识库检索工具
└── utils/                       # 工具类
    ├── AIDocking                # AI 对接
    ├── PromptBuilder            # Prompt 构建
    ├── ParseAIResponse          # 响应解析
    ├── FileUtils                # 文件处理
    ├── MinioUtil                # MinIO 操作
    ├── RedisUtils               # Redis 限流
    ├── WebSocketServer          # WebSocket 服务
    └── JWTUtil                  # JWT 工具
```
