# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] 委托类使用 *Service 命名违反 Nop 平台命名约定

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:102`
- **证据片段**:
  ```java
  // CodeIndexService.java:102
  public class CodeIndexService implements ICodeIndexService {

  // CodeGraphService.java:46
  class CodeGraphService {

  // CodeSearchService.java:31
  class CodeSearchService {

  // CodeQueryService.java:39
  class CodeQueryService {
  ```
- **严重程度**: P2
- **现状**: `CodeIndexService` 是注册的 NopIoC bean，被三个 BizModel 通过 `@Inject` 注入使用。`CodeGraphService`、`CodeSearchService`、`CodeQueryService` 是包级私有的内部委托类。四个类均以 `*Service` 结尾，与 `service-layer.md` 明确规定的"不要在 Nop 模块中创建 `*Service` / `*Controller` 类"冲突。
- **风险**: 可能导致新开发者误解其角色为 Spring 风格 Service 层而非 Processor/委托类。与仓库中其他模块的命名风格不一致。
- **建议**: `CodeIndexService` 重命名为 `CodeIndexProcessor`（或 `CodeIndexManager`），包级私有类重命名为 `CodeGraphHelper`/`CodeSearchHelper`/`CodeQueryHelper`，同步更新 beans.xml。
- **信心水平**: 高
- **误报排除**: `service-layer.md` 将 `*Service` 命名列在"默认不要这样写"清单中，属于明确的约定违反。`CodeIndexService` 是 NopIoC 注册的公共 bean，其命名对所有使用者可见。
- **复核状态**: 未复核

### [维度07-02] incrementalStatusMap 使用非持久化内存状态，集群部署和重启时状态丢失

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:44-50, 106-108`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:44-50
  private final Map<String, IncrementalStatus> incrementalStatusMap = Collections.synchronizedMap(
          new LinkedHashMap<String, IncrementalStatus>(16, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<String, IncrementalStatus> eldest) {
                  return size() > MAX_STATUS_ENTRIES;  // MAX_STATUS_ENTRIES = 20
              }
          });

  // NopCodeIndexBizModel.java:106-108
  @BizQuery
  @Auth(permissions = "NopCodeIndex:query")
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }
  ```
- **严重程度**: P2
- **现状**: `triggerFullIndex` 和 `triggerIncrementalIndex` 执行后将状态写入 `incrementalStatusMap`（内存 LRU Map，最多 20 条）。`getIncrementalStatus` 从同一 Map 读取。状态无持久化、无集群同步、无 TTL。
- **风险**: 重启后所有状态丢失；多节点部署中状态仅在执行节点可见；超过 20 个 indexId 后早期状态被淘汰。API 行为不确定。
- **建议**: 短期：`getIncrementalStatus` 返回 null 时回退查询 NopCodeIndex 实体 status 字段。中期：将 IncrementalStatus 持久化到 extData 或专用字段。
- **信心水平**: 高
- **误报排除**: `incrementalStatusMap` 是 BizModel 实例字段，承载用户可查询的 API 语义（`getIncrementalStatus`），丢失导致 API 行为不符合消费者预期。这不是一般缓存问题。
- **复核状态**: 未复核

### [维度07-03] BizModel 方法参数命名与底层接口参数命名不一致

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:112-117`
- **对照文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:22`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:112-117 — 使用 "directoryPath"
  @BizMutation
  public int indexDirectory(
          @Name("indexId") String indexId,
          @Name("directoryPath") String directoryPath,
          @Name("filePattern") @Optional String filePattern) {
      return codeIndexService.indexDirectory(indexId, directoryPath, filePattern);
  }

  // ICodeIndexService.java:22 — 使用 "vfsPath"
  int indexDirectory(String indexId, String vfsPath, String filePattern);
  ```
- **严重程度**: P3
- **现状**: BizModel 层参数名 `directoryPath`，ICodeIndexService 接口参数名 `vfsPath`，另外 `triggerFullIndex` 使用 `projectPath`。三个不同命名指代同一概念（VFS 路径）。
- **风险**: 跨层调试时产生实际困惑。开发者阅读 BizModel 时可能认为参数是本地文件系统路径，而实际经过 VFS 解析。
- **建议**: 统一参数命名，或在 BizModel 方法注释中明确说明参数会被转换为 VFS 路径。
- **信心水平**: 中
- **误报排除**: 三个不同命名（`directoryPath`、`projectPath`、`vfsPath`）指代同一概念，在跨层调试时会产生实际困惑。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度07-01] | P2 | nop-code-service/.../impl/CodeIndexService.java | 委托类使用 *Service 命名违反平台约定 |
| [维度07-02] | P2 | nop-code-service/.../entity/NopCodeIndexBizModel.java | incrementalStatusMap 内存状态无持久化/集群同步 |
| [维度07-03] | P3 | nop-code-service/.../entity/NopCodeIndexBizModel.java | 参数命名不一致 directoryPath/projectPath/vfsPath |
