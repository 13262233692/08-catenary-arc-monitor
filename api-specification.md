# 弓网电弧在线监测中台 — 接口规约字典

> 版本: 1.0.0 | 更新日期: 2026-06-20

---

## 一、通用约定

### 1.1 基础信息

| 项目 | 说明 |
|------|------|
| 协议 | HTTP/1.1, WebSocket |
| 基础路径 | `/api` |
| 字符编码 | UTF-8 |
| 时间格式 | Unix 时间戳（毫秒） / ISO 8601 |
| 认证方式 | JWT Bearer Token |

### 1.2 统一响应结构 `ApiResponse<T>`

```json
{
  "code": 200,
  "message": "success",
  "data": T
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务状态码，200=成功 |
| message | String | 响应描述 |
| data | T | 业务数据，泛型 |

### 1.3 错误码定义

| 错误码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 过期 |
| 403 | 无访问权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 二、认证模块 `/api/auth`

### 2.1 用户登录

| 项目 | 说明 |
|------|------|
| **接口** | `POST /api/auth/login` |
| **认证** | 无需认证 |
| **描述** | 使用用户名密码登录，获取 JWT Token |

**请求体 `LoginRequest`：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | ✅ | 用户名 |
| password | String | ✅ | 密码 |

**响应体 `ApiResponse<LoginResponse>`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| data.token | String | JWT 访问令牌 |
| data.tokenType | String | 固定值 "Bearer" |
| data.expiresIn | long | 有效期（毫秒） |
| data.username | String | 用户名 |
| data.role | String | 角色标识：ADMIN / BUREAU / STATION / WORK_AREA |
| data.bureauId | String | 所属铁路局 ID |
| data.stationId | String | 所属站段 ID |
| data.workAreaId | String | 所属工区 ID |

**请求示例：**
```json
POST /api/auth/login
{
  "username": "admin",
  "password": "admin123"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "username": "admin",
    "role": "ADMIN",
    "bureauId": "",
    "stationId": "",
    "workAreaId": ""
  }
}
```

---

### 2.2 获取当前用户信息

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/auth/info` |
| **认证** | Bearer Token |
| **描述** | 获取当前登录用户的详细信息 |

**请求头：**

| Header | 值 |
|--------|-----|
| Authorization | Bearer {token} |

**响应体 `ApiResponse<Map>`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| data.username | String | 用户名 |
| data.realName | String | 真实姓名 |
| data.role | String | 角色 |
| data.bureauId | String | 所属局 ID |
| data.stationId | String | 所属站段 ID |
| data.workAreaId | String | 所属工区 ID |

---

## 三、电弧数据模块 `/api/arc-data`

### 3.1 单点数据摄入

| 项目 | 说明 |
|------|------|
| **接口** | `POST /api/arc-data/ingest` |
| **认证** | Bearer Token |
| **描述** | 接收单个电弧放电强度测点数据，写入 Kafka |

**请求体 `ArcDataPoint`：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| timestamp | long | ✅ | 数据时间戳（毫秒） |
| sectionId | String | ✅ | 接触网区段 ID |
| sensorId | String | ✅ | 传感器 ID |
| intensity | double | ✅ | 电弧放电强度（W） |
| voltage | double | ❌ | 电压（kV） |
| current | double | ❌ | 电流（A） |
| temperature | double | ❌ | 传感器温度（℃） |

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": "Data sent to Kafka"
}
```

---

### 3.2 批量数据摄入

| 项目 | 说明 |
|------|------|
| **接口** | `POST /api/arc-data/ingest-batch` |
| **认证** | Bearer Token |
| **描述** | 批量接收电弧测点数据，写入 Kafka |

**请求体：** `List<ArcDataPoint>`（JSON 数组）

**响应示例：**
```json
{
  "code": 200,
  "message": "success",
  "data": "Batch data sent to Kafka"
}
```

---

### 3.3 降采样查询

| 项目 | 说明 |
|------|------|
| **接口** | `POST /api/arc-data/query` |
| **认证** | Bearer Token |
| **描述** | 查询电弧强度时序数据，支持 InfluxDB aggregateWindow 降采样 |

**请求体 `ArcDataQueryRequest`：**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| sectionId | String | ✅ | - | 区段 ID |
| startMs | long | ✅ | - | 起始时间戳（毫秒） |
| endMs | long | ✅ | - | 结束时间戳（毫秒） |
| interval | String | ❌ | "1s" | 降采样聚合窗口，如 "500ms", "1s", "5s", "1m" |
| maxPoints | int | ❌ | 2000 | 返回最大数据点数 |

**响应体 `ApiResponse<List<ArcDataPoint>>`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| data[].timestamp | long | 时间戳（毫秒） |
| data[].sectionId | String | 区段 ID |
| data[].sensorId | String | 传感器 ID |
| data[].intensity | double | 聚合后放电强度均值（W） |
| data[].voltage | double | 电压 |
| data[].current | double | 电流 |
| data[].temperature | double | 温度 |

**请求示例：**
```json
POST /api/arc-data/query
{
  "sectionId": "SEC-001",
  "startMs": 1718800000000,
  "endMs": 1718803600000,
  "interval": "1s",
  "maxPoints": 2000
}
```

---

### 3.4 获取最新数据点

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/arc-data/latest/{sectionId}` |
| **认证** | Bearer Token |
| **描述** | 获取指定区段最新的电弧强度数据点 |

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| sectionId | String | 区段 ID |

**响应体 `ApiResponse<ArcDataPoint>`**

---

### 3.5 原始数据查询

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/arc-data/raw` |
| **认证** | Bearer Token |
| **描述** | 查询原始（未降采样）的电弧强度数据 |

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sectionId | String | ✅ | 区段 ID |
| startMs | long | ✅ | 起始时间戳（毫秒） |
| endMs | long | ✅ | 结束时间戳（毫秒） |

**响应体 `ApiResponse<List<ArcIntensity>>`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| data[].time | String(ISO 8601) | InfluxDB 时间戳 |
| data[].sectionId | String | 区段 ID |
| data[].sensorId | String | 传感器 ID |
| data[].intensity | double | 放电强度（W） |
| data[].voltage | double | 电压 |
| data[].current | double | 电流 |
| data[].temperature | double | 温度 |

---

## 四、区段监控模块 `/api/sections`

### 4.1 获取全部区段

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/sections/all` |
| **认证** | Bearer Token |
| **描述** | 获取所有接触网区段的监控概览数据 |

**响应体 `ApiResponse<List<SectionOverview>>`：**

| 字段 | 类型 | 说明 |
|------|------|------|
| data[].sectionId | String | 区段 ID |
| data[].sectionName | String | 区段名称 |
| data[].lineName | String | 线路名称 |
| data[].startKm | double | 起始里程（km） |
| data[].endKm | double | 终止里程（km） |
| data[].status | String | 状态：NORMAL / WARNING / ALARM / OFFLINE |
| data[].latestIntensity | double | 最新放电强度 |
| data[].avgIntensity | double | 平均放电强度 |
| data[].maxIntensity | double | 最大放电强度 |
| data[].alarmCount | int | 报警次数 |
| data[].sensorCount | int | 传感器数量 |

---

### 4.2 按铁路局查询区段

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/sections/bureau/{bureauId}` |
| **认证** | Bearer Token |
| **描述** | 按铁路局层级查询区段概览（层级访问控制） |

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| bureauId | String | 铁路局 ID，如 "B001" |

**响应体：** 同 4.1 `ApiResponse<List<SectionOverview>>`

---

### 4.3 按站段查询区段

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/sections/station/{stationId}` |
| **认证** | Bearer Token |
| **描述** | 按站段层级查询区段概览 |

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| stationId | String | 站段 ID，如 "S001" |

**响应体：** 同 4.1

---

### 4.4 按工区查询区段

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/sections/work-area/{workAreaId}` |
| **认证** | Bearer Token |
| **描述** | 按工区层级查询区段概览 |

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| workAreaId | String | 工区 ID，如 "W001" |

**响应体：** 同 4.1

---

### 4.5 获取区段详情

| 项目 | 说明 |
|------|------|
| **接口** | `GET /api/sections/{sectionId}` |
| **认证** | Bearer Token |
| **描述** | 获取指定区段的详细监控数据 |

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| sectionId | String | 区段 ID |

**响应体 `ApiResponse<SectionOverview>`**

---

## 五、WebSocket 实时推送 `/ws/arc`

### 5.1 连接建立

| 项目 | 说明 |
|------|------|
| **端点** | `ws://{host}/ws/arc?token={jwt_token}` |
| **协议** | WebSocket |
| **认证** | URL 查询参数传递 JWT Token |

### 5.2 客户端发送指令

**订阅区段：**
```json
{
  "action": "subscribe",
  "sectionId": "SEC-001"
}
```

**取消订阅：**
```json
{
  "action": "unsubscribe",
  "sectionId": "SEC-001"
}
```

### 5.3 服务端推送消息

**电弧数据推送（每 100ms）：**
```json
{
  "timestamp": 1718800000123,
  "sectionId": "SEC-001",
  "sensorId": "UV-SENSOR-001",
  "intensity": 45.678,
  "voltage": 27.5,
  "current": 320.0,
  "temperature": 42.3,
  "status": "NORMAL"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| timestamp | long | 时间戳（毫秒） |
| sectionId | String | 区段 ID |
| sensorId | String | 传感器 ID |
| intensity | double | 放电强度（W） |
| voltage | double | 电压（kV） |
| current | double | 电流（A） |
| temperature | double | 温度（℃） |
| status | String | 区段状态 |

---

## 六、数据模型定义

### 6.1 InfluxDB Measurement: `arc_intensity`

| 字段 | 类型 | Tag/Field | 说明 |
|------|------|-----------|------|
| time | timestamp | - | 时间戳（纳秒精度） |
| sectionId | string | Tag | 区段 ID |
| sensorId | string | Tag | 传感器 ID |
| intensity | float | Field | 放电强度（W） |
| voltage | float | Field | 电压（kV） |
| current | float | Field | 电流（A） |
| temperature | float | Field | 温度（℃） |

**RetentionPolicy:** 30天自动过期

### 6.2 Kafka Topic: `arc-intensity-data`

| 配置 | 值 |
|------|-----|
| Partitions | 3 |
| Replication Factor | 1 |
| Key Serializer | String (sectionId) |
| Value Serializer | String (JSON) |
| Compression | LZ4 |
| Batch Size | 32768 |
| Linger Ms | 5 |

### 6.3 用户角色层级

| 角色 | 标识 | 数据范围 |
|------|------|----------|
| 系统管理员 | ADMIN | 全部数据 |
| 铁路局 | BUREAU | 本局所辖区段 |
| 站段 | STATION | 本站段所辖区段 |
| 工区 | WORK_AREA | 本工区所辖区段 |

### 6.4 区段状态枚举

| 状态 | 标识 | 颜色 | 说明 |
|------|------|------|------|
| 正常 | NORMAL | 绿色 | 放电强度 < 80W |
| 预警 | WARNING | 黄色 | 80W ≤ 放电强度 < 120W |
| 报警 | ALARM | 红色 | 放电强度 ≥ 120W |
| 离线 | OFFLINE | 灰色 | 传感器无数据上报 |

---

## 七、JWT 令牌规范

| 项目 | 说明 |
|------|------|
| 算法 | HS256 (HMAC-SHA256) |
| 有效期 | 86400000ms (24小时) |
| Header | Authorization: Bearer {token} |
| Claims | sub=用户名, role=角色, bureauId, stationId, workAreaId |

---

## 八、降采样算法说明

| 算法 | 标识 | 说明 |
|------|------|------|
| LTTB | lttb | Largest Triangle Three Buckets，保留视觉特征的最佳降采样算法 |
| MinMax | minmax | 最小最大值聚合，保留峰值特征 |

**LTTB 算法流程：**
1. 首尾两点必选
2. 将中间数据分为 N-2 个桶（N为目标点数）
3. 每个桶选取与相邻已选点构成三角形面积最大的点
4. 前端阈值：数据量 > 2000 点时自动触发降采样至 800 点显示
