# 数据智能分析平台

## 项目简介

本项目是一个基于 Spring Boot 框架构建的智能数据分析平台，通过集成人工智能技术，实现对上传数据文件的自动化分析与图表生成。用户可以上传 Excel 或 CSV 格式的数据文件，系统将利用 AI 模型进行数据解读，并自动生成符合要求的可视化图表和分析报告。

平台采用现代化的微服务架构设计，集成了消息队列、对象存储、缓存等中间件，支持高并发场景下的稳定运行。

## 核心功能

### 1. 智能图表生成
用户只需上传数据文件并描述分析需求，系统即可自动调用 AI 能力，生成专业的可视化图表。支持的图表类型包括折线图、柱状图、饼图、雷达图、散点图、热力图等。

### 2. 异步分析处理
针对大型数据文件，平台提供异步处理模式。用户提交分析请求后，系统立即返回任务 ID，用户可通过任务 ID 查询处理进度或等待 WebSocket 推送的完成通知。

### 3. 用户认证管理
完整的用户注册、登录流程，采用 JWT 令牌机制实现无状态身份验证。用户登录后可查看个人历史分析记录，并管理自己的图表资源。

### 4. 文件智能采样
对于数据量较大的文件，系统内置智能采样算法，可在保留数据分布特征的前提下，将数据量压缩至适合 AI 处理的规模，提高分析效率。

### 5. 消息队列集成
采用 RabbitMQ 实现异步任务调度，确保高并发场景下系统的稳定性和响应速度。分析任务通过消息队列分发，由专门的消费者异步处理。

## 技术栈

| 类别 | 技术选型 |
|------|----------|
| 后端框架 | Spring Boot 2.7 / Spring Cloud |
| 数据库 | MySQL 8.0 |
| ORM 框架 | MyBatis-Plus |
| 消息队列 | RabbitMQ 3.1.2 |
| 对象存储 | MinIO |
| 缓存 | Redis + Redisson |
| AI 模型 | DeepSeek / Qwen (阿里云通义千问) |
| 权限控制 | JWT + 自定义注解 |
| 构建工具 | Maven |
| 数据处理 | Apache POI (Excel) |

## 环境要求

- JDK 1.8 或以上版本
- Maven 3.6+
- MySQL 5.7+
- RabbitMQ 3.1.2
- Redis 6.0+
- MinIO (对象存储服务)

## 快速开始

### 1. 环境准备

确保所有依赖服务已正确安装并启动。RabbitMQ 需要启用管理插件：

```bash
# 进入 RabbitMQ sbin 目录
cd rabbitmq_server-{version}/sbin

# 启用管理插件
rabbitmq-plugins.bat enable rabbitmq_management

# 重启 RabbitMQ 服务
rabbitmq-service.bat stop
rabbitmq-service.bat install
rabbitmq-service.bat start
```

RabbitMQ 管理界面访问地址：`http://localhost:15672`，默认账号：`guest`，默认密码：`guest`

### 2. 数据库初始化

创建数据库并执行初始化脚本：

```sql
CREATE DATABASE data_analysis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE data_analysis;

-- 执行 db/data_analysis.sql 脚本完成表结构创建
```

### 3. 配置修改

在 `src/main/resources` 目录下创建 `application.yml` 文件，配置数据库、Redis、MinIO 等信息：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/data_analysis?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
  
  data:
    redis:
      host: localhost
      port: 6379
      database: 0

  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: data-analysis

suke:
  jwt:
    secret-key: your-jwt-secret-key
    ttl: 86400000
    token-name: Authorization

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  ai:
    dashscope:
      api-key: your-ali-api-key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

### 4. 构建与运行

```bash
# 克隆项目
git clone https://gitee.com/su-kejin/data-analysis.git

# 进入项目目录
cd data-analysis

# 编译项目
mvn clean package -DskipTests

# 启动应用
java -jar target/data-analysis.jar
```

## API 接口文档

### 用户模块

#### 用户注册
- **接口地址**：`POST /user/register`
- **请求参数**：
```json
{
  "userAccount": "testuser",
  "userPassword": "password123",
  "checkPassword": "password123"
}
```
- **响应结果**：
```json
{
  "code": 0,
  "message": "success",
  "data": 1234567890
}
```

#### 用户登录
- **接口地址**：`POST /user/login`
- **请求参数**：
```json
{
  "userAccount": "testuser",
  "userPassword": "password123"
}
```
- **响应结果**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1234567890,
    "userName": "测试用户",
    "userAvatar": "https://xxx.com/avatar.png",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "userRole": "user"
  }
}
```

### 图表模块

#### 同步生成图表
- **接口地址**：`POST /chart/gen`
- **请求方式**：multipart/form-data
- **请求参数**：
    - `file`：数据文件（Excel/CSV）
    - `fileName`：文件名
    - `goal`：分析目标描述
    - `chartType`：图表类型（bar、line、pie 等）
    - `enableSampling`：是否启用采样（可选）
    - `sampleRows`：采样行数（可选）

- **响应结果**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "chartId": 1234567890,
    "genResult": "分析结论...",
    "genChart": "{\"title\":\"...\",\"xAxis\":{...}}"
  }
}
```

#### 异步生成图表（消息队列）
- **接口地址**：`POST /chart/gen/async/mq`
- **请求参数**：同同步接口

#### 分页查询我的图表
- **接口地址**：`POST /chart/my/list/page`
- **请求参数**：
```json
{
  "page": 1,
  "pageSize": 10,
  "goal": "销售数据分析",
  "chartType": "bar",
  "sortField": "createTime",
  "sortOrder": "desc"
}
```

## 项目结构

```
data-analysis/
├── src/main/java/com/suke/
│   ├── annotation/          # 自定义注解
│   ├── common/              # 公共类（返回结果、错误码）
│   ├── config/              # 配置类（AI、MinIO、线程池等）
│   ├── constant/            # 常量定义
│   ├── controller/          # 控制器层
│   ├── context/             # 上下文（用户信息）
│   ├── datamq/              # RabbitMQ 消息处理
│   ├── domain/              # 实体类、DTO、枚举、VO
│   ├── exception/           # 异常处理
│   ├── handler/             # 处理器（全局异常、MyBatis填充）
│   ├── interceptor/         # 拦截器（JWT认证）
│   ├── mapper/              # MyBatis Mapper 接口
│   ├── properties/          # 配置属性类
│   ├── service/             # 服务层
│   └── utils/               # 工具类
├── src/main/resources/
│   ├── mapper/              # MyBatis XML 映射文件
│   └── application.yml      # 配置文件
├── db/                      # 数据库脚本
└── pom.xml                  # Maven 配置
```

## 主要特性实现

### AI 数据分析流程
1. 用户上传文件后，系统首先进行文件格式校验
2. 对 Excel/CSV 文件进行解析，转换为标准格式
3. 根据是否启用采样，对数据进行智能采样处理
4. 将处理后的数据连同分析目标发送至 AI 模型
5. AI 模型返回分析结论和图表配置
6. 解析 AI 响应，提取结构化数据和图表配置
7. 返回分析结果给用户

### 采样算法
系统采用分层采样策略，在保证数据分布特征的前提下，尽可能减少数据量。采样算法会：
- 保留数据的整体分布趋势
- 保持异常值和大值数据点
- 确保采样后的数据能够准确反映原始特征

### 限流机制
基于 Redis 实现的滑动窗口限流器，防止恶意请求对系统造成压力。

## License

本项目遵循开源协议，具体许可证信息请查看项目根目录下的 LICENSE 文件。

## 贡献指南

欢迎对本项目进行贡献。如有任何问题或建议，请通过 Issues 或 Pull Requests 提出。


## 改善中
目前正在改善其他的一些功能,欢迎一起加入讨论,目前正在添加向量搜索解决针对各行业不同的分析结果以及后续考虑添加不同的工具,最后通过MCP解决的方案


![输入图片说明](%E6%B5%81%E7%A8%8B%E5%9B%BE.png)

