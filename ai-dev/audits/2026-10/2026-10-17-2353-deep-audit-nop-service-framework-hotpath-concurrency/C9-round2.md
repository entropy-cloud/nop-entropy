# C9 维度审计报告：请求热路径 —— 第 2 轮深挖（收口轮）

## 基本信息

- **审计单元**: `nop-service-framework-hotpath`（请求热路径五模块 / 8 源码根 / 406 文件 / 42,748 行）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`（HEAD 5994c18211）
- **审计日期**: 2026-10-18
- **维度**: C9——请求热路径（每请求常驻开销：锁竞争、锁内开销、缓存命中路径常数因子、每请求分配热点、每记录循环）
- **轮次**: 第 2 轮深挖（盲审，收口轮）

## 本轮输入

1. 第 1 轮报告 `C9-hot-path.md`（C9-001~011：P2×6 + P3×5；CL-013 维持+量化；CL-015 疑似 P1 转交裁定；9 项误报排除）。
2. 任务书转述的跨单元条目：WI 2.1 C9-010（`ResourceCacheEntry` 直接使用方绕过 `refreshMinInterval` 门，4 处——仅按转述对照，未读原文）。
3. 基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt` + live code 通读取证。
4. 本轮新增实测脚本：`_tmp/c9r2-cl015/Cl015Probe.java`（protobuf-java 3.25.8 本地 jar 实跑，两失效模式复现）。

## 盲审纪律声明

本轮为盲审收口。除第 1 轮本维度文件 `C9-hot-path.md` 外，**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何其他文件（含同目录 C1–C4/C8 维度文件与第 2 轮其他维度文件）。WI 2.1 C9-010 的内容仅以任务书转述（"4 处直接使用方绕过 refreshMinInterval 门"）为对照输入，未回读原文；③ 的关系裁定基于转述事实 + 本域 live code 全量枚举独立完成。本轮编号沿用：新发现自 C9-012 起；本轮未新增维度集外线索（CL-026 起编号未动用）。

## 本轮目标（收口五项）

| # | 目标 | 结论 |
|---|---|---|
| ① | CL-015 终裁（LocalDateTimeFieldMarshaller computeSize=19 vs toString 可变长） | **成立，终裁 P1 正确性**，实测双模式复现；归属裁定：**移交功能正确性维度**，C9 以 C9-012 立案归档终裁证据（见下） |
| ② | C9-001 失效面扩展（选择树缓存可行性 + key + GraphQL 路径对照面） | 闭合：无 selection 时 key=返回类型名（可缓存，先例=documentCache 共享只读 AST）；有 selection 时 key=FieldSelectionBean 结构哈希（可行性成立、命中率次之）。**支持按 C9-001 原建议修复，无需升级**（见扩展复核节） |
| ③ | C9-002 与 WI 2.1 C9-010 关系裁定 | 闭合：同设施（nop-core ResourceCacheEntry/ResourceLoadingCache）同缺陷面的**跨单元包含关系**，非重复立案（见裁定节） |
| ④ | 热路径常驻开销终表（补全每请求必经链遗漏站点） | 闭合：系统性扫描上下文创建/拦截器遍历/JSON 序列化配置查找/入口 bean 查找/auth 路径匹配，**新增 1 条 P3（C9-013）+ 4 个微站点（<0.1µs 级，不立案入表）**，无新 P2+ 常驻开销站点 |
| ⑤ | 9 项误报排除抽验 3 项 | 闭合：抽验 parse 缓存命中路径、asProxy 启动期、ResourceLoadingCache 100ms 节流——**3/3 通过**（1 项补充精确化，见抽验记录节） |

---

## ① CL-015 终裁 → 立案 C9-012

### 独立核实过程（不依赖第 1 轮结论重推）

1. **computeSize/write 调用契约**：`GenericObjSchema.toByteArray`（GenericObjSchema.java:94-114）先 `ProtobufMarshallerHelper.computeSize`（:97，对每个 DATETIME 字段取 `computeTagSize(propId) + SIZE_NO_TAG`=tag+19），按总量分配 `byte[]`，再 `CodedOutputStream.newInstance(data)` + `writeObject`（:101）后**原样返回整个数组**（:106）——声明长度与实写长度必须严格一致，无任何容差。
2. **写入侧长度**：`writeField`/`writeFieldNoTag` 用 `value.toString()`（LocalDateTimeFieldMarshaller.java:39-46）。值来源链核实：gRPC 响应 `GraphQLServerCallHandler.onNext → call.sendMessage(response.getData())`（GraphQLServerCallHandler.java:207）→ `GenericMessageMarshaller.stream → schema.toByteArray`（GenericMessageMarshaller.java:26-28）；响应 Map 中 DATETIME 字段值经 `GraphQLExecutor.normalizeValue → GraphQLFieldDefinition.getTypeConverter → StdDataType.DATETIME.getConverter()`（StdDataType.java:93，javaClass=LocalDateTime）归一为 **java.time.LocalDateTime**。`LocalDateTime.toString()` 为 ISO-8601 可变长。
3. **实测复现**（`_tmp/c9r2-cl015/Cl015Probe.java`，protobuf-java 3.25.8 实跑）：
   - 长度谱系：`(sec=0,ns=0)`→16、`(sec>0,ns=0)`→19、millis→23、micros→26、nanos→29、midnight→16（`2026-01-01T00:00`）。
   - **模式 A（超长，纳秒/毫秒/微秒>0）**：向 tag+19 容量的堆缓冲 writeString(23字符) → `CodedOutputStream$OutOfSpaceException`（IOException 子类）→ `toByteArray` catch→NopException→**整个 gRPC 响应序列化失败**。
   - **模式 B（短写，秒=0且纳秒=0）**：实写 16 字节、声明 19 → 数组尾部 3 个脏 0x00 → 接收端 `ProtobufMarshallerHelper.parseObject` 读第一个字段成功后再次 `readTag()` 抛 `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)`——**接收端解析硬失败**（本平台 generic parser 与标准 protobuf 行为一致）。
   - 仅 `(sec>0, ns=0)`（19 字符）长度恰好安全；线格式为 ISO 'T' 分隔（与平台惯例 ' ' 不同），但平台解析端 `ConvertHelper.stringToLocalDateTime` 的 `'T'` 分支（ConvertHelper.java:993）可回环解析。
4. **触发面宽度**：Nop ORM 实体 DATETIME 字段 javaType 混合：`LocalDateTime`（如 `_NopAuthUser` 的 expireAt/pwdUpdateTime）与 `java.sql.Timestamp`（createTime/updateTime）。**Timestamp 字段有默认豁免**：`CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP` 默认 true（GraphQLConfigs.java:76-77）→ 转换为 `formatTimestampNoMillis` 的 19 字符定长 String（DateHelper.java:263-268，`toString().substring(0,19)`）→ 长度安全。**LocalDateTime 类型字段无豁免**，直接暴露。亚秒精度来源（真实存在）：PostgreSQL timestamp(6)/Oracle TIMESTAMP 默认微秒、H2 TIMESTAMP 默认 6 位、MySQL DATETIME(3)/(6)，以及业务代码 `LocalDateTime.now()`（JDK9+ 时钟微秒精度）。

### 判级终裁

**P1（正确性），疑似转确认成立。** 依据：含亚秒精度 DATETIME 值的记录经 gRPC generic marshaller 写出**必然失败**（模式 A 抛异常）；整秒且秒为 0 的值写出后**接收端必然解析失败**（模式 B）——均非静默降级而是硬失效，且失败点在序列化/解析边界，排查成本高（模式 B 的脏字节在发送端不可见）。未升 P0：非数据损坏、非安全面；规避路径存在（Timestamp 类型字段默认豁免 + DATETIME(0) 且秒非零的数据）。

### 归属裁定

**移交功能正确性维度执行修复**。理由：本缺陷是线格式正确性问题（marshaller 序列化契约违反），不属于 C9 维度定义（并发/每请求常驻开销）的缺陷类；C9 侧以 C9-012 立案归档终裁证据与实测复现，供正确性维度直接取用，不进入 C9 修复优先级排序的性能通道。

---

## ② C9-001/R2 失效面扩展复核（选择树重建族缓存可行性）

**结论：缓存可行性成立，key 明确，平台内已有兼容性先例。C9-001 维持原判级 P2 与原建议，无需第 3 轮。**

1. **无客户端 selection 的情形（REST `/r/`、`/f/` 主流量）**：`RpcSelectionSetBuilder.buildForType(returnType, selectionBean)`（RpcSelectionSetBuilder.java:65-84）在 `selectionBean==null`（或空）时走 `FRAGMENT_DEFAULTS`/`addDefaultFieldsForObjType`——树是 `(返回类型名, maxObjLevel, FRAGMENT_DEFAULTS)` 的确定性函数；`maxObjLevel=CFG_GRAPHQL_QUERY_MAX_DEPTH.get()` 为全局配置，fragment 定义全局唯一 → **key = 返回类型名**，可用平台 `LocalCache`/`ResourceLoadingCache` 直接承载。请求参数（data/args）不挂在选择子树上——`doc.addOperation` 仅在顶层 `GraphQLFieldSelection.setOpRequest(request)`（GraphQLOperation.java:45-53），与 `selection.setSelectionSet(...)` 挂载的子树分离，模板可只读共享。
2. **有客户端 selection 的情形**：树额外依赖调用方 `FieldSelectionBean` 内容 → key = 该 bean 的结构哈希（平台已有 `deepHashCode` 先例，如本域 `buildLoaderKey`）；命中率低于查询文本缓存（HTTP 路径 documentCache 的同构先例），修复可分期：先缓存无 selection 主路径。
3. **GraphQL HTTP 路径对照面（同树是否已缓存）**：已部分缓存——`documentCache.get(query)` 以查询文本为 key 缓存 AST（GraphQLEngine.java:321），且 `doc.isResolved()` 使 `resolveSelections` 全树解析仅首请求执行（:414-417，resolved 状态持久在缓存条目上）；**HTTP 路径与并发执行器兼容共享只读选择树是既有事实**（同一缓存 AST 被同查询文本的并发请求共享游走），即 C9-001 修复所需的"冻结后共享只读"在该路径已被验证。残留差异：HTTP 路径每请求仍执行一次 `buildSelectionBean`（:436，依赖 per-request vars 的变量替换，天然不可按文本缓存）；RPC 路径则 AST、resolve、FieldSelectionBean 三者全部每请求重建（C9-001 主面）。
4. **失效面增量**：`resolveSelections` 每次 `new GraphQLSelectionResolver`（:293，小对象）随主案一并消除；`SYS_OPERATION_FETCH_RESULTS`（fetchResultWithSelection）同族适用同一 key 方案（typeName+selection 结构）。

## ③ C9-002/R2 与 WI 2.1 C9-010 关系裁定

**结论：同设施、同缺陷面的跨单元包含关系——非重复立案，建议合并修复单。**

1. **本域全量枚举**（8 源码根 grep `\.getObject(`）：仅 3 站点——`GatewayHttpFilter.java:127`（`getObject(true, loader)`，无节流，依赖含真实文件 → 每请求真实 stat）、`GraphQLEngine.java:288`（`getObject(false)`，不查变更，安全）、`GraphQLEngine.java:321`（`getObject(CFG.get())`，text-parse 条目 deps 内层 map 为 null → `isAnyDependsChange(null)` 直接 false，无 stat）。即**本域硬站点唯一：GatewayHttpFilter:127**。
2. **设施层机制核实**：节流门在 `ResourceLoadingCache.get`（ResourceLoadingCache.java:192：`checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get())`），而 `GatewayHttpFilter` 持有裸 `ResourceCacheEntry`（:95 `new ResourceCacheEntry<>(modelPath)`）直接调 `getObject(true, loader)`（ResourceCacheEntry.java:176-178：`value == null || checkChanged && isChanged()` → `isChanged()` → `ResourceDependsManager.isAnyDependsChange` 递归走查 + 每次入口分配一个 `HashSet`（ResourceDependsManager.java:215）+ 逐依赖真实 stat（ResourceTimestampCache 整文件注释停用））。`isRefreshEnabled` 本身是 public（ResourceCacheEntry.java:103），调用点修复无 API 障碍。
3. **与 WI 2.1 C9-010 的关系**：按任务书转述，WI 2.1 已在设施层登记"直接使用方绕过 refreshMinInterval 门（4 处）"。本域 C9-002 是该缺陷面在**请求热路径的具体实例**（gateway 全量入站、每请求 O(依赖数) 次 stat 的量化与入口语义）。裁定：**保留 C9-002 为热路径量化实例（本单元验收面），修复动作并入设施层条目统一下单**；若 WI 2.1 的 4 处清单不含 gateway 站点，则两单合并时补入（盲审纪律下无法核对其清单原文，交协调者对齐）。按"跨维度仅在 F3 互引"规则，两单互引不重复计数。

## ④ 热路径常驻开销终表（修复优先级排序直接输入）

每请求必经链五段（GraphQL HTTP / RPC-REST / auth 过滤 / biz 拦截 / gateway）全量复核。**本轮新增站点以 ★ 标注；量化无法支撑 P2 的一律 P3 或表内不立案。**

| 优先级 | ID | 站点 | 每请求成本（量化） | 处置 |
|---|---|---|---|---|
| **P1**（正确性移交） | C9-012 | gRPC LocalDateTime marshaller 声明 19 vs 实写 16–29 | 触发即硬失效（整记录序列化/解析失败），非法开销而是正确性 | 移交正确性维度修复（`computeSize` 改按 `value.toString()` 实算） |
| P2 | C9-001 | RPC/REST 每请求三遍重建合成选择树 | O(F×D) 分配 ×3 遍 ≈ 数十–数百 µs；100–700 AST 对象/请求 | 按 key=typeName 缓存（可行性本轮已论证）；同族 4 面见第 1 轮 F3 |
| P2 | C9-002 | Gateway `handlerCache.getObject(true)` 无节流变更检查 | 每请求 ≥1、实为 O(N 依赖文件数) 次 stat + 1 HashSet；10k QPS → 10k+ stat/s | 并入 WI 2.1 C9-010 设施层修复（③ 裁定） |
| P2 | C9-003 | `JwtAuthTokenProvider.ensureKeys()` synchronized 全平台串行点 | 每认证请求 1 次 monitor acquire（临界区 O(1)），10k+ QPS 尾延迟抖动 | volatile 快检 + DCL 或急切初始化 |
| P2 | C9-004 | gRPC MAP/ANY 每记录双次 JSON 序列化 | 每记录 ×k 个 JSON 字段 = 2k 次 stringify（50% 纯浪费）；分页 100 行×3 字段 = 300 次浪费 | 序列化一次共用（byte[]/String payload） |
| P2 | C9-005 | 流式每元素 parse→拦截器→serialize 往返 + 锁内下游回调 | 每元素 4–10µs CPU 落事件循环线程；锁内含序列化与投递 | 透传快速路径 + subscriber.onNext 移出监视器 |
| P2 | C9-006 | GraphQL 入口 INFO 全量 query 日志 | 每请求 1 条（≤4KB 文本拷贝+格式化+I/O）；1k QPS×2KB ≈ 2MB/s | 对齐 runRest 口径（截断/降级） |
| P3 | ★C9-013 | auth 过滤链对同一 path 两次 `isPublicPath` 线性扫描 | 认证请求（service path）恒 2 次 O(#publicPaths+#authPaths)×matchSimplePattern（equals/regionMatches 快速路径），亚 µs 级；1 行修复（复用 ：137 的 publicPath 局部变量） | 本轮新立案，见发现清单 |
| P3 | C9-009 | GatewayContextImpl 8 个 synchronized 访问器 | 6–10 次 × ~40–60ns < 1µs | 仅流式回程 2 字段保留同步 |
| P3 | C9-010 | Gateway 双重 trie 匹配 + 拦截器 List 每请求 stream 重建 | 2×trie(~百ns) + stream 管道 + when 求值 ≈ 1–2µs | MatchResult 复用 / 预计算 |
| P3 | C9-011 | auth 每认证请求强制 executeBlocking 双跳 | 1 提交 + 2 线程切换 ≈ 2–10µs | 快慢路径分流（可选） |
| 量化记录 | C9-007/008 | dispatchAll / resultLock 监视器 | 单请求串行总量 <10µs / ~50µs，与 I/O 重叠 | 维持现状 + 契约注释 |
| 不立案（★本轮新量化） | — | `runGraphQL:75`/`runRest:237` 每请求 `BeanContainer.getBeanByType(IGraphQLEngine.class)` | `beansByType` 为 ConcurrentHashMap（BeanContainerImpl.java:73），命中路径 = 1 次 CHM computeIfAbsent + 1 次 `new BeanCreationContext` ≈ 0.1µs | 平台通用模式，非缺陷 |
| 不立案（★本轮新量化） | — | `getExecutionInvoker` 每请求 `new GraphQLFlowControlInvoker` + `cancelTokenManager.wrap` | 无 request-id 头时 wrap 零开销快返（CancelTokenManager.java:114-116）；FlowControlInvoker 1 个小对象 ≈ 20ns | 必要包装，非缺陷 |
| 不立案（★本轮新量化） | — | `JsonTool` 序列化路径配置查找 | serialize 侧无每调用配置读取；parse 侧 1 次 volatile 读（CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP，JsonTool.java:105）+ 1 个 ParseOptions 分配 | 非缺陷 |
| 不立案（★本轮新量化） | — | biz 调用链（BizActionInvocation.proceedAsync） | 4 字段赋值 + 1 次 attribute map get；decorator 链构建期组合（第 1 轮已核） | 非缺陷 |

## ⑤ 误报排除抽验复核（3/9，全部通过）

| 抽验项 | 复核证据 | 结论 |
|---|---|---|
| graphql parse 缓存命中路径 | `loadObject` 对**每次装载**无条件 `this.deps = new VirtualResourceDependencySet(path)`（ResourceCacheEntry.java:224-232）→ 条目 deps 字段非 null；text-parse 无文件依赖 → 内层 `dependsMap==null → getDepends() 返回 null`（ResourceDependencySet.java:113-117）→ `isAnyDependsChange(null)` 直返 false。**精确化**：`ResourceCacheEntry.isChanged()` 的 `deps==null → return true` 分支（:163-173）只覆盖"从未装载"状态，不触及命中路径；空 HashSet 分配点定位在 `ResourceDependsManager.isAnyDependsChange` 入口包装器（:214-215） | 排除成立 |
| asProxy 启动期（0 次/请求） | `BizObjectImpl.asProxy:283`（synchronized）全域唯一调用方 = `BizProxyFactoryBean.getObject:27`（IoC bean 构建期） | 排除成立 |
| ResourceLoadingCache 100ms 节流 | `ResourceLoadingCache.get:192`：`checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get())`，节流先行于 `getObject(b, loader)`；`isRefreshEnabled` 为 volatile lastLoadTime 比较（:103-106） | 排除成立 |

---

## 新发现清单（本轮）

### [nop-service-framework-hotpath-C9-012] CL-015 终裁立案：gRPC LocalDateTime 线格式声明长度(19)与实写长度(16–29)不一致——亚秒精度记录序列化必然失败、整秒零值记录接收端解析必然失败（P1 正确性，移交功能正确性维度）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/proto/marshaller/LocalDateTimeFieldMarshaller.java:21, 39-46, 49-56`；调用契约 `GenericObjSchema.java:94-114`、`ProtobufMarshallerHelper.java:110-140`、`DynamicMessage.java:36-43`；值来源 `StdDataType.java:93`、`GraphQLFieldDefinition.java:108-127`、`GraphQLServerCallHandler.java:207`、`GenericMessageMarshaller.java:26-28`
- **证据片段**:
  ```java
  static final int SIZE_NO_TAG = CodedOutputStream.computeStringSizeNoTag("2002-01-02 11:02:03"); // 恒 19
  public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
      out.writeString(propId, value.toString());        // LocalDateTime.toString()：ISO 'T'，16/19/23/26/29 可变长
  }
  public int computeSize(int propId, Object value) {
      return CodedOutputStream.computeTagSize(propId) + SIZE_NO_TAG;  // 与实写无关，固定 19
  }
  // GenericObjSchema.toByteArray：按 computeSize 总量分配 byte[]，writeObject 后原样返回整个数组
  ```
- **严重程度**: P1（正确性；非 C9 性能缺陷类，归属裁定：移交功能正确性维度，C9 归档终裁证据）
- **并发场景**: 与并发无关（确定性线格式缺陷）；影响面为 gRPC 服务端（ServiceSchemaManager 构建的 generic marshaller，`MethodDescriptor.Marshaller.stream/parse`）全部含 DATETIME 字段的请求/响应记录，多线程下每记录独立触发。
- **触发时序**（实测，`_tmp/c9r2-cl015/Cl015Probe.java`，protobuf-java 3.25.8）: 值经 `normalizeValue → StdDataType.DATETIME.getConverter()`（javaClass=LocalDateTime）保持为 LocalDateTime → `toString()` 长度谱系：sec=0&ns=0→16（含 midnight）、sec>0&ns=0→19、millis→23、micros→26、nanos→29。**模式 A**（亚秒>0）：实写 23–29 > 声明 19 → `CodedOutputStream$OutOfSpaceException`（IOException 子类，实测抛出）→ `toByteArray` catch 转 NopException（`nop.grpc.serialize-object-fail`）→ 该记录所属 gRPC 调用失败。**模式 B**（秒=0且纳秒=0，如整点时间戳）：实写 16 < 声明 19 → 数组尾部 3 个脏 0x00 随响应发出 → 接收端 `parseObject` 完成合法字段读取后 `readTag()` 读到 0 → `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)`（实测抛出）→ 接收端解析硬失败。
- **现状**: 触发面宽度受 two 类 javaType 分布调节——`java.sql.Timestamp` 字段有默认豁免（`CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP` 默认 true → `formatTimestampNoMillis` 恒 19 字符，DateHelper.java:263-268）；**`LocalDateTime` 类型字段（Nop ORM 对 DATETIME 列的标准映射之一，如 auth 域 expireAt/pwdUpdateTime）无任何豁免**。亚秒精度在 PostgreSQL/Oracle/H2 默认精度及 `LocalDateTime.now()`（JDK9+ 微秒时钟）下真实存在。
- **风险**: 含亚秒时间戳的记录 gRPC 出口 100% 失败（模式 A）；整点秒值记录 100% 接收端解析失败（模式 B，且发送端无异常、故障点在接收端，排障成本高）。DB 切换（如迁到 PostgreSQL/H2）或业务引入 now() 时间戳即触发，属潜伏正确性地雷。
- **建议**: ① 最小修复：`computeSize`/`computeSizeNoTag` 改为按 `value.toString()` 实算（与 StringFieldMarshaller 同构，常数级开销无热路径影响）；② 可选加固：写入统一为 `DateHelper` 定长格式（"yyyy-MM-dd HH:mm:ss"，19 字符，与 SIZE_NO_TAG 恢复一致且消除 'T'/' ' 口径漂移）；③ 同族回归：对全部 `IFieldMarshaller` 增加 compute/write 一致性单测（随机值断言 `computeSize==write 后 position`）。
- **失效面枚举（F3 同族清扫）**: ① `LocalDateTimeFieldMarshaller.computeSize:49`/`computeSizeNoTag:54`（本案主面，两处同源）；② `writeField:39`/`writeFieldNoTag:44` 的 `value.toString()`（长度漂移源头）；③ `DynamicMessage.getSerializedSize:36 → ProtobufMarshallerHelper.computeSize`——嵌套对象帧同链污染（`out.writeMessage` 以 getSerializedSize 声明帧长，嵌套内 DATETIME 同样超长/短写，帧内脏字节/帧内溢出）；④ `GraphQLFieldDefinition.java:113-118`——Timestamp 字段配置了 graphql date-pattern 属性时走 `formatTimestamp(ts, pattern)`，pattern 长度≠19 时同族失效（条件面）；⑤ `LocalDateFieldMarshaller`（SIZE_NO_TAG=10，:21）同形模式但 `LocalDate.toString()` 在业务年份域恒 10 字符——**判定实际安全，仅年份 >9999/负年份理论边角，不立案**；⑥ `StringFieldMarshaller`/`BytesFieldMarshaller`/`ByteStringFieldMarshaller`/`EnumFieldMarshaller` compute/write 一致性逐一核对通过；⑦ `JsonFieldMarshaller` 尺寸一致（双算性能问题归 C9-004，非本案）。
- **信心水平**: 确定（调用契约通读 + 双失效模式本地实测复现 + 触发面 javaType 抽样核实）
- **误报排除**: 不是"protobuf 两阶段模式的固有成本"——两阶段要求 compute 与 write 严格一致，本缺陷恰是违反该契约；不是"仅理论边角"——亚秒精度是 PostgreSQL/Oracle/H2 的默认行为；不是"发送端异常可感知"——模式 B 发送端无任何异常，脏字节静默发出。
- **复核状态**: 未复核
- **复核状态**: 已复核——**终裁确认：P1 成立，移交功能正确性维度裁定成立**（详见 `C9-hot-path.md`「维度复核结论」§一.1）。(a) computeSize 定值 vs `toString()` 可变长亲核（LocalDateTimeFieldMarshaller.java:21/:39-46 + GenericObjSchema.toByteArray :94-114 原样返回整个数组的契约）；(b) probe 经复核者本地独立复跑（protobuf-java 3.25.8）：长度谱系 16/19/23/26/29/16、模式 A `OutOfSpaceException`、模式 B 尾部 3 脏 0x00 + `InvalidProtocolBufferException: invalid tag (zero)` 逐项复现；(c) 豁免面亲核：`CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP` 默认 true（GraphQLConfigs.java:76-77）+ `formatTimestampNoMillis` 恒 19 字符（DateHelper.java:262-268）；LocalDateTime 无豁免（StdDataType.java:93 + SysConverterRegistry.java:76 + nop-auth.orm.xml:83-86 expireAt/pwdUpdateTime=`stdDataType="datetime"`，createTime/updateTime 族走 timestamp 域豁免）；(d) 嵌套帧 F3 ③ `DynamicMessage.getSerializedSize:36` 亲核成立。精度订正：`SIZE_NO_TAG` 实为 20（1 字节 varint 长度前缀 + 19 载荷），报告叙述"恒 19"差 1 字节——模式 B 脏字节数 3 与模式 A 越界结论均不受影响。现状：新发现（本 campaign），已知未修复；移交接收轨待单元收口登记 cra-index 时落名。

---

### [nop-service-framework-hotpath-C9-013] Auth 过滤链对同一请求路径重复执行两次 isPublicPath 线性扫描（P3 优化候选）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:137, 146`；`AuthFilterConfig.java:160-189`
- **证据片段**:
  ```java
  boolean publicPath = config.isPublicPath(path);      // :137 第 1 次全量扫描
  boolean logoutPath = checkLogoutUrl(routeContext);
  boolean servicePath = config.isServicePath(path);
  boolean requireContext = !publicPath || servicePath;
  if (!requireContext) return handlePublicPath(routeContext, next);
  if (config.isPublicPath(path)) {                     // :146 第 2 次同一全量扫描（结果必等于 publicPath）
      LOG.debug("nop.route.request-public-url:path={}", path);
      return handlePublicPath(routeContext, next);
  }
  ```
- **严重程度**: P3（优化候选；量化收益亚 µs 级，按"写不出量化收益的一律 P3"归优化候选）
- **并发场景**: 所有请求（认证请求为主要暴露面）；单线程语义内纯重复计算，无并发问题。
- **触发时序**（量化链）: 分支分析——public且非service：1 次扫描后早退；public且service：2 次；**非public且service（即全部认证 API 请求的主形态）：2 次**；非public且非service：1 次。每次 `isPublicPath` = O(#publicPaths + #authPaths) 次 `StringHelper.matchSimplePattern`（StringHelper.java:2150-2168：精确路径走 `String.equals`、通配走 `regionMatches/contains`，无编译开销）+ `isServicePath` 的 O(#servicePaths) 同构扫描。典型配置（public/auth/service 合计 10–50 条目、路径 ~30 字符）单次扫描 ≈ 0.2–1µs，双扫增量为亚 µs 级——无量化显著的吞吐影响，立案理由为修复零风险（复用 ：137 已算出的 `publicPath` 局部变量，1 行）。
- **现状**: :146 的判断在语义上恒等于 `publicPath`（同一 path、同一配置、两次调用间配置不可变），属第 1 轮遗留的直线重复。
- **风险**: 无正确性风险；纯常数项 + 语义噪音。
- **建议**: :146 改为 `if (publicPath) {...}`；顺带可将 `logoutPath` 的计算移入 `!requireContext` 早退之后的分支（public 快路径当前也白算一次 `checkLogoutUrl`）。
- **失效面枚举（F3 同族清扫）**: ① `_filterAsync:137` 与 ：146（本案面）；② `isServicePath:162-167`（线性扫描，每请求 1 次，量级同上，随本案一并核过——单独修复收益不足，保持现状）；③ `checkLogoutUrl`（路径比对，快路径白算一次，随本案建议顺带）；④ 同文件 `handlePublicPath`/`handleUserContext` 无同族重复站点（逐一核对）。
- **信心水平**: 确定（分支语义可静态判定）；收益量化属估算（故 P3）
- **误报排除**: 不是"配置可能被热更导致两次结果不同"的误判——单请求内两次调用间隔纳秒级，配置刷新存在同一性边界；也不是"matchSimplePattern 有编译开销"——已核实为形状分派+equals（复核第 1 轮该项误报排除成立）。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。亲核：:137 首扫与 :146 二扫（同一 path、同一 config、结果恒等）成立；`isPublicPath`/`isServicePath` 线性扫描（AuthFilterConfig.java:160-189，`matchSimplePattern` 形状分派 + equals/regionMatches，无编译开销）成立；分支分析正确（非 public 且 service 的认证 API 主形态恒 2 次扫描，双扫增量亚 µs 级）；1 行修复（:146 复用 `publicPath` 局部变量）与 `checkLogoutUrl` 快路径白算顺带建议成立。P3 归优化候选合规。现状：新发现（本 campaign），已知未修复。

---

## 终止判定

**写终止。** 收口五项全部闭合：

1. ① CL-015 终裁完成——P1 正确性成立并以 C9-012 立案（实测双模式复现），归属移交功能正确性维度，C9 维度无后续动作。
2. ② C9-001 缓存可行性闭合（key 明确 + documentCache 共享 AST 先例），维持 P2 原建议。
3. ③ C9-002 与 WI 2.1 C9-010 裁定为跨单元包含关系，合并修复单，本域硬站点唯一。
4. ④ 终表产出（P1×1 移交 + P2×6 + P3×4 + 量化记录×2 + 本轮新量化微站点×4 不立案）——系统性扫描未发现新的 P2+ 级每请求常驻开销站点。
5. ⑤ 误报排除抽验 3/3 通过（1 项精确化）。

**"无新 P0/P1"判定说明**：本轮唯一 P1（C9-012）是任务书输入中既有线索 CL-015 的裁定确认，非本轮新发现的缺陷面，且其缺陷类（线格式正确性）不属于 C9 维度，移交正确性维度即闭环——不构成第 3 轮 C9 方向的理由。第 1 轮盲区自评 4 项（executeBlocking 池配置、XPL when JMH、IUserContextHook 实现、subscription 常驻连接面）均维持"域外/低频待平台层审计"定位，无升级证据。

**修复优先级排序（直接输入）**: C9-012（P1，正确性通道，移交）→ C9-001 → C9-002（并入 WI 2.1 C9-010）→ C9-003 → C9-004 → C9-005 → C9-006（P2 按 QPS 暴露面排序）→ C9-009/010/011/013（P3 优化候选）→ C9-007/008（维持现状 + 契约注释）。

**本轮计数**: 新立案 C9-012（P1，移交）、C9-013（P3）；CL-026 起未动用（无新维度集外线索）；误报排除抽验 3 项通过。
