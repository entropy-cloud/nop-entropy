# Marquez 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 Marquez 的架构设计，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

Marquez 是一个专注于数据血缘追踪的开源元数据服务，由 WeWork 开源，是 LF AI & Data Foundation 的毕业项目。本文档基于源码深度分析，详细拆解其四实体核心模型、递归 CTE 血缘查询、异步双路径事件处理和 OpenLineage Facet 扩展机制，为 nop-metadata 的设计提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│               Marquez Web UI                         │
│            (React Frontend)                          │
└──────────────────────┬──────────────────────────────┘
                       │ REST API (Dropwizard JAX-RS)
┌──────────────────────▼──────────────────────────────┐
│  ┌─────────────────┐  ┌──────────────────────────┐  │
│  │DatasetResource  │  │OpenLineageResource       │  │
│  │JobResource      │  │LineageResource           │  │
│  │RunResource      │  │SearchResource            │  │
│  └────────┬────────┘  └────────────┬─────────────┘  │
│           │                        │                 │
│  ┌────────▼────────────────────────▼─────────────┐  │
│  │           ServiceFactory (轻量 IoC)            │  │
│  │  DatasetService, JobService, RunService,      │  │
│  │  LineageService, OpenLineageService           │  │
│  └────────┬────────────────────────┬─────────────┘  │
│           │                        │                 │
│  ┌────────▼────────────────────────▼─────────────┐  │
│  │         DelegatingDaos (DAO 委托模式)          │  │
│  └────────┬────────────────────────┬─────────────┘  │
└───────────┼────────────────────────┼────────────────┘
            │                        │
┌───────────▼──────────┐  ┌─────────▼───────────────┐
│     PostgreSQL       │  │    Elasticsearch         │
│  (主存储 + 递归 CTE)  │  │      (搜索索引)          │
└──────────────────────┘  └─────────────────────────┘
```

### 2. 存储层

- **PostgreSQL**: 主存储，使用递归 CTE 实现血缘图遍历（无需图数据库）
- **Elasticsearch**: 搜索索引，支持全文搜索
- **Redis**: 缓存层（可选）

## 核心设计模式

### 模式 1：四实体核心模型

**关键文件:**
- `api/src/main/java/marquez/service/models/Namespace.java`
- `api/src/main/java/marquez/service/models/Dataset.java`
- `api/src/main/java/marquez/service/models/Job.java`
- `api/src/main/java/marquez/service/models/Run.java`

**模型结构:**

```java
// Namespace: 一等公民实体，实现多租户隔离
public class Namespace {
    NamespaceName name; Instant createdAt; Instant updatedAt;
    OwnerName ownerName; String description; Boolean isHidden;
}

// Dataset: 多态设计，Jackson @JsonSubTypes 支持
@JsonSubTypes({
    @JsonSubTypes.Type(value = DbTable.class, name = "DB_TABLE"),
    @JsonSubTypes.Type(value = Stream.class, name = "STREAM")
})
public abstract class Dataset {
    DatasetId id; DatasetType type; DatasetName name;
    DatasetName physicalName; SourceName sourceName;
    List<Field> fields; ImmutableSet<TagName> tags;
    List<ColumnLineage> columnLineage;
    ImmutableMap<String, Object> facets;  // 可扩展元数据
}

// Job: 版本化设计，包含输入/输出数据集
public final class Job {
    JobId id; JobType type; JobName name;
    Set<DatasetId> inputs; Set<DatasetId> outputs;
    Run latestRun; List<Run> latestRuns;
    ImmutableMap<String, Object> facets;
}

// Run: 有状态执行单元，Builder 模式
@JsonDeserialize(builder = Run.Builder.class)
public final class Run {
    RunId id; RunState state; Instant startedAt; Instant endedAt;
    Map<String, String> args;
    List<InputDatasetVersion> inputDatasetVersions;
    List<OutputDatasetVersion> outputDatasetVersions;
}
```

**设计优势:**
- **Namespace 一等公民**: 每个数据集和作业属于命名空间，支持多租户隔离
- **多态数据集**: `Dataset` 抽象类 + Jackson 多态，JSON 序列化干净
- **版本化模型**: Job 和 Dataset 有 `current_version_uuid`，每个版本捕获输入/输出快照
- **Facets 到处都是**: `ImmutableMap<String, Object>` 支持无 Schema 变更的扩展元数据
- **Run 作为执行单元**: 跟踪状态转换（NEW -> RUNNING -> COMPLETE/FAILED）

---

### 模式 2：关系型 SQL 实现图模型（无图数据库）

**关键文件:**
- `api/src/main/java/marquez/service/models/Node.java`
- `api/src/main/java/marquez/service/models/Edge.java`
- `api/src/main/java/marquez/service/models/Graph.java`
- `api/src/main/java/marquez/service/models/NodeId.java`

**图模型结构:**

```java
public final class Node implements Comparable<Node> {
    NodeId id; NodeType type; NodeData data;
    Set<Edge> inEdges; Set<Edge> outEdges;

    // 工厂方法
    public static Builder dataset()   { return new Builder(NodeType.DATASET); }
    public static Builder job()       { return new Builder(NodeType.JOB); }
    public static Builder run()       { return new Builder(NodeType.RUN); }
}

public class Edge implements Comparable<Edge> {
    NodeId origin; NodeId destination;
}

public final class Graph {
    private final Set<Node> mutableNodes = Sets.newTreeSet();
    // add(), addAll(), nodes()
}
```

**NodeId 编码格式:**
```
dataset:<namespace>:<name>           -- e.g., "dataset:prod:orders"
dataset:<namespace>:<name>#<uuid>    -- 版本化
job:<namespace>:<name>               -- e.g., "job:prod:etl_job"
run:<uuid>                           -- e.g., "run:550e8400-..."
datasetField:<ns>:<name>:<column>    -- 列级
```

**设计优势:**
- **内存图从 SQL 构建**: Marquez **不使用图数据库**，而是通过递归 SQL CTE 构建 `Graph` 对象
- **自描述字符串 ID**: `dataset:ns:name` 格式可自行解析，无需额外查询确定实体类型
- **Edge 一等公民**: 携带 origin/destination NodeId，可序列化为 JSON 无循环引用
- **有序不可变输出**: `ImmutableSortedSet<Node>` 确保 API 响应确定性

---

### 模式 3：递归 CTE 血缘查询（PostgreSQL）

**关键文件:**
- `api/src/main/java/marquez/db/LineageDao.java`

**核心查询:**

```sql
WITH RECURSIVE
  job_io AS (
    -- 构建每个 Job 版本的输入/输出数组
    SELECT io.job_uuid, io.job_symlink_target_uuid,
           ARRAY_AGG(DISTINCT io.dataset_uuid) FILTER (WHERE io.io_type='INPUT') AS inputs,
           ARRAY_AGG(DISTINCT io.dataset_uuid) FILTER (WHERE io.io_type='OUTPUT') AS outputs
    FROM job_versions_io_mapping io
    WHERE io.is_current_job_version = TRUE
    GROUP BY io.job_symlink_target_uuid, io.job_uuid
  ),
  lineage(job_uuid, ..., depth) AS (
    -- 基础情况: 用输入 Job ID 种子化
    SELECT ... FROM job_io WHERE job_uuid IN (<jobIds>)
    UNION
    -- 递归情况: 查找共享数据集的 Job
    SELECT io.job_uuid, ..., l.depth + 1
    FROM job_io io, lineage l
    WHERE array_cat(io.inputs, io.outputs) && array_cat(l.inputs, l.outputs)
      AND depth < :depth
  )
SELECT DISTINCT ON (j.uuid) j.*, inputs, outputs
FROM lineage l2 INNER JOIN jobs_view j ON (...)
```

**上游运行血缘查询:**
```sql
WITH RECURSIVE upstream_runs(...) AS (
    -- 基础: 查找初始运行的输入
    SELECT r.uuid, dv.dataset_uuid, dv.run_uuid, 0 AS depth
    FROM runs_input_mapping rim LEFT JOIN dataset_versions dv ...
    UNION
    -- 递归: 通过数据集版本向后追踪
    SELECT ur.u_r_uuid, dv2.dataset_uuid, dv2.run_uuid, ur.depth + 1
    FROM upstream_runs ur LEFT JOIN runs_input_mapping rim2 ...
    WHERE ur.u_r_uuid IS NOT NULL AND ur.u_r_uuid <> ur.r_uuid AND depth < :depth
)
```

**设计优势:**
- **递归 CTE 替代图遍历**: 用 PostgreSQL 的递归 CTE 在 Job-Dataset 二部图上执行 BFS
- **数组交集运算**: `array_cat(inputs, outputs) && array_cat(inputs, outputs)` 的 `&&` 运算符检查数组重叠
- **深度限制**: 防止有向无环图中的无限循环
- **Symlink 支持**: `job_symlink_target_uuid` 处理数据集/作业别名
- **环路避免**: `ur.u_r_uuid <> ur.r_uuid` 防止追踪回到自身

---

### 模式 4：异步双路径事件处理

**关键文件:**
- `api/src/main/java/marquez/service/OpenLineageService.java`
- `api/src/main/java/marquez/api/OpenLineageResource.java`

**处理流程:**

```java
public class OpenLineageService extends DelegatingOpenLineageDao {
    public CompletableFuture<Void> createAsync(LineageEvent event) {
        // 路径 1: 存储原始事件（审计/探索）
        CompletableFuture<Void> openLineage = CompletableFuture.runAsync(
            () -> createLineageEvent(eventType, eventTime, runUuid, ...));

        // 路径 2: 更新 Marquez 内部模型
        CompletableFuture<Void> marquez = CompletableFuture.supplyAsync(
            () -> updateMarquezModel(event, mapper))
            .thenAccept((update) -> {
                // 模型更新后，通知下游监听器
                buildJobOutputUpdate(update).ifPresent(runService::notify);
                buildJobInputUpdate(update).ifPresent(runService::notify);
                buildRunTransition(update).ifPresent(runService::notify);
            });

        return CompletableFuture.allOf(marquez, openLineage);
    }
}
```

REST 层使用异步 JAX-RS:
```java
@POST @Path("/lineage")
public void create(@Valid BaseEvent event, @Suspended final AsyncResponse asyncResponse) {
    if (event instanceof LineageEvent) {
        openLineageService.createAsync((LineageEvent) event)
            .whenComplete((result, err) -> onComplete(result, err, asyncResponse));
    }
}
```

**设计优势:**
- **双写**: 每个 OpenLineage 事件同时存储原始事件（审计/探索）和规范化模型（高效查询）
- **事件多态**: `BaseEvent` 可以是 `LineageEvent`、`DatasetEvent` 或 `JobEvent`
- **非阻塞 I/O**: 两条路径通过 `CompletableFuture` 异步执行，HTTP 响应立即返回 201
- **监听器通知**: 模型更新后通过 `RunService.notify()` 传播状态变更

---

### 模式 5：OpenLineage Facet 扩展模型

**关键文件:**
- `api/src/main/java/marquez/service/models/LineageEvent.java`

**Facet 层次结构:**

```java
public class LineageEvent extends BaseEvent {
    Run run;                          // 包含 RunFacet
    Job job;                          // 包含 JobFacet (documentation, sql, jobType)
    List<Dataset> inputs/outputs;     // 每个包含 DatasetFacets

    // 基础 Facet 模式:
    abstract static class BaseFacet {
        URI _producer;        // 生产者 URI
        URI _schemaURL;       // Schema URL（版本化）
        Map<String, Object> additional;  // @JsonAnySetter/@JsonAnyGetter

        public void setFacet(String key, Object value) { additional.put(key, value); }
        public Map<String, Object> getAdditionalFacets() { return additional; }
    }
}
```

**具体 Facet 类型:**
- `NominalTimeRunFacet` -- 标称开始/结束时间
- `ParentRunFacet` -- 父运行/作业链接
- `DocumentationJobFacet` -- 作业描述
- `SQLJobFacet` -- SQL 查询文本
- `JobTypeJobFacet` -- 流/批分类
- `SchemaDatasetFacet` -- 字段定义
- `DatasourceDatasetFacet` -- 源系统信息
- `ColumnLineageDatasetFacet` -- 列级血缘

**设计优势:**
- **OpenLineage 规范合规**: 每个 Facet 有 `_producer` 和 `_schemaURL` 用于溯源和版本化
- **开放扩展**: `@JsonAnySetter`/`@JsonAnyGetter` 允许任意自定义 Facet 无需 Schema 变更
- **已知 Facet 强类型**: 标准 Facet 有专用 Java 类带验证
- **方向特定 Facet**: `InputDatasetFacets` 和 `OutputDatasetFacets` 允许同一数据集的方向特定元数据

---

### 模式 6：NodeId 通用 URI 标识符

**关键文件:**
- `api/src/main/java/marquez/service/models/NodeId.java`

**编码格式:**
```
格式: "type:part1:part2[:part3][#version]"
示例:
  "dataset:prod:orders"              -- 数据集
  "dataset:prod:orders#<uuid>"       -- 数据集版本
  "job:prod:etl_job"                 -- 作业
  "run:<uuid>"                       -- 运行
  "datasetField:prod:orders:amount"  -- 列级
```

**解析能力:**
```java
boolean isDatasetType()      // 以 "dataset:" 开头
boolean isJobType()          // 以 "job:" 开头
boolean hasVersion()         // 包含 "#"

DatasetId asDatasetId()      // 拆分 "dataset:ns:name"
JobId asJobId()              // 拆分 "job:ns:name"
RunId asRunId()              // 拆分 "run:uuid"
```

**设计优势:**
- **自描述标识符**: 类型前缀消除 API 响应中单独的类型字段
- **版本化后缀**: `#uuid` 后缀编码版本信息，无需单独端点
- **双重用途**: 既是 API 参数又是图节点标识符，桥接 REST 和图层
- **智能冒号解析**: 处理 URI 值中的冒号等边缘情况

---

### 模式 7：ServiceFactory + DelegatingDaos 组合

**关键文件:**
- `api/src/main/java/marquez/api/BaseResource.java`
- `api/src/main/java/marquez/service/DelegatingDaos.java`

**设计结构:**

```java
// BaseResource 为所有 REST 端点提供服务:
public class BaseResource {
    protected ServiceFactory serviceFactory;
    protected DatasetService datasetService;
    protected JobService jobService;
    protected OpenLineageService openLineageService;
    protected LineageService lineageService;

    public BaseResource(ServiceFactory serviceFactory) {
        this.datasetService = serviceFactory.getDatasetService();
        this.jobService = serviceFactory.getJobService();
        // ... 所有服务从工厂初始化
    }
}

// Service 继承 DAO 委托类:
public class OpenLineageService extends DelegatingOpenLineageDao {
    private final RunService runService;

    public OpenLineageService(BaseDao baseDao, RunService runService) {
        super(baseDao.createOpenLineageDao());  // 通过继承委托
        this.runService = runService;
    }
}
```

**设计优势:**
- **ServiceFactory**: 集中服务创建和装配，充当轻量 IoC 容器
- **DelegatingDaos**: Service 继承 DAO 委托类，继承所有 DAO 方法同时添加业务逻辑
- **前置验证**: `BaseResource.throwIfNotExists()` 封装常见验证模式

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **递归 CTE 血缘查询**: 无需图数据库，在关系型数据库上实现高效血缘遍历
2. **自描述 NodeId**: `type:ns:name` 格式的字符串 ID，可自行解析，桥接 REST 和图层
3. **异步双路径事件处理**: 原始事件存储 + 规范化模型更新，审计与查询分离
4. **Facet 扩展模型**: 标准 Facet 强类型 + 自定义 Facet 通过 JSON Map 扩展
5. **ServiceFactory 轻量 IoC**: 集中服务创建，避免重量级框架
6. **Namespace 一等公民**: 原生多租户支持
7. **四实体精简模型**: Namespace/Dataset/Job/Run 覆盖血缘核心需求

### 需要改进的地方

1. **功能单一**: 主要专注于血缘追踪，缺乏数据治理能力
2. **无版本化**: 实体版本化较弱（仅 UUID 后缀）
3. **缺乏分类系统**: 无标签/分类/词汇表管理

## Open Questions

- [ ] nop-metadata 是否需要支持 OpenLineage 标准？
- [ ] 递归 CTE 在 MySQL 上的性能如何？
- [ ] Facet 扩展模型是否适合 nop 的 XDef 扩展机制？

## References

- [Marquez GitHub](https://github.com/MarquezProject/marquez)
- [Marquez Documentation](https://marquezproject.github.io/marquez/)
- [OpenLineage](https://openlineage.io/)
- 源码: `api/src/main/java/marquez/service/models/`
- 源码: `api/src/main/java/marquez/db/LineageDao.java`
- 源码: `api/src/main/java/marquez/service/OpenLineageService.java`