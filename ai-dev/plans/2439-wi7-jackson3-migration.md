# 2439 WI7 Jackson 3 migration (8 databind sites + SB4/Q4 config surface)

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI7；事实基座 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`（第 1.4 节）
> Related: plan 2437（WI6 SB4 BOM 升线，已收口）
> Draft Review: 2026-10-11 独立子代理对抗审查（agent_7bf469d0）——1 Major（ObjectMapper 构造器断言为假，javap 实证修正为风格标准化）+ 8 Minor，全部修入后执行
> Closure Audit: 2026-10-11 独立子代理审计（agent_dfc20c69）ALLOW，3 项 Minor 文本勘误已落实
> 用户裁定（2026-10-11 会话指令）：deprecated 用法必须迁移，一律采用最标准做法——Jackson 3 标准形态 = `tools.jackson.databind.json.JsonMapper.builder()` 构建器。

## Purpose

真实 Jackson databind 面（8 文件）迁移到 Jackson 3（`tools.jackson.*`），annotations 保持 Jackson-2 线（`com.fasterxml.jackson.annotation` 不改名）；SB4/Q4 的 Jackson 配置面发现如实记录（5 项零命中/默认满足裁定）。版本经 BOM 解析：quarkus-bom 先于 spring-boot-dependencies，`tools.jackson.core:jackson-databind` 生效 3.1.4（Q4 列胜出，WI0 2.2 节）。

## Current Baseline

- 8 个真实 databind 站点（WI0 1.4 核实，本轮 grep + 审查轮逐文件复核一致）：
  - `nop-benchmark/nop-benchmark-json` 4 文件（eishay：EishayParseString/EishayParseStringPretty/EishayParseTreeString/EishayParseTreeStringPretty）：`import com.fasterxml.jackson.databind.ObjectMapper` + `static ObjectMapper mapper = new ObjectMapper()` + `readValue(String, Class)`；pom 依赖 `com.fasterxml.jackson.core:jackson-databind`（compile，保持）
  - `nop-ai/nop-ai-api` 3 测试：TestChatMessageSerialization/TestChatStreamChunkSerialization 为 `new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)`，**TestChatStreamAccumulator 为无 configure 的 `new ObjectMapper()`**；TestChatMessageSerialization 另含 `mapper.getTypeFactory().constructCollectionType(...)` 链（3.1.4 javap 实证零改动可编译，记录在案）；pom 依赖 jackson-databind（test，带本地版本 2.15.2 需删）
  - `nop-kernel/nop-api-core` 1 测试（TestCrudInputBase）：`new ObjectMapper()` + `@JsonAnyGetter/@JsonAnySetter`（annotations 线不动）；pom jackson-databind（test，本地版本 2.15.2 需删）+ jackson-annotations（**compile，其 `${jackson.version}` 属性必须保留**）
  - 第 9 处 com.fasterxml 痕迹（非 Java，记录在案）：`nop-runner/nop-cli-core/native-image.properties:14` 的 `--initialize-at-build-time=...,com.fasterxml.jackson`——迁移后仅剩 annotations 宿主，GraalVM 面归 WI8
- annotations 基线（复核锚）：全仓 `com.fasterxml.jackson.annotation` 命中 **529 文件 / 629 行**（排除 target/_tmp），迁移前后应零变化
- annotations 面：`com.fasterxml.jackson.annotation` 全仓保持 Jackson-2 线（roadmap Decisions，禁止改名）
- Jackson 3 事实（2026-10-11 核实，审查轮 javap 实证修正）：`tools.jackson.databind.ObjectMapper` 在 3.x **仍是 public 具体类、无参构造器仍可用**（仅 `ObjectMapper(MapperBuilder)` 为 protected）——本迁移是**风格标准化**（官方标准形态 = `tools.jackson.databind.json.JsonMapper.builder()[.disable(...)].build()` + 用户裁定"最标准做法"），非 API 强制；`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` 在 Jackson 3 存在（且 3.0 起默认 false——保持显式 disable 以语义自明）；`readValue`/`writeValueAsString` 签名兼容（异常未检查化，`throws Exception`/`catch (Exception)` 继续合法）；annotations 面 Jackson 3 databind 官方继续消费 `com.fasterxml.jackson.core:jackson-annotations`（3.1.4 pom 实证）
- SB4 配置面 5 项发现（2026-10-11 实测）：①全仓零 `spring.jackson.*` 配置；②`spring.jackson.use-jackson2-defaults` 官方默认即 `false`（SB4 默认 = Jackson-3 defaults，spring.io 官方迁移指南），全仓零配置置 true——"Jackson-3 defaults end to end" 意图由默认满足；③SB4 all-classpath Jackson-module 自动探测：全仓零手工模块注册（`SimpleModule`/`registerModule`/`JavaTimeModule` 零命中）——默认即采纳；④`Jackson2ObjectMapperBuilderCustomizer`/`@JacksonComponent`/`@JacksonMixin`/`spring.jackson.parser.*` 全仓零命中（WI0 预判成立）；⑤nop-spring 唯一 application.yaml 属 nop-spring-proxy（WebClient 代理 demo，无 Jackson 序列化面）
- 版本解析：Q4 直管 `tools.jackson.core:jackson-databind` 3.1.4；SB4 经 jackson-bom 3.1.5；导入序 Q4 先 → 3.1.4 生效（WI0 2.2/1.4）

## Goals

- 8 databind 站点 100% 迁移到 `tools.jackson.*`，标准 `JsonMapper` builder 形态；annotations 零改动。
- 3 个 pom 的 jackson-databind 坐标换线（`com.fasterxml.jackson.core` → `tools.jackson.core`），版本全部 BOM 管理（零本地版本）。
- SB4 配置面 5 项发现记录在案（默认满足裁定，不发明迁移）。
- 受影响模块测试绿 + 全量门保持基线态。

## Non-Goals

- BOM 版本变更（WI5/WI6 已供给；Jackson 3 生效版本 3.1.4 由导入序决定，不新增钉）。
- annotations 线迁移（永不做，Decisions）。
- nop-benchmark-json 的 eishay 基准数值对比（性能数字非本 plan 收口面）。
- spring-boot-jackson2 stop-gap 采纳（Decisions：Jackson 3 default）。

## Scope

### In Scope

- 3 pom 坐标换线 + 8 java 文件迁移。
- 配置面发现记录（plan + 日志）。
- 受影响模块聚焦测试 + 全量 install 门。

### Out Of Scope

- `com.fasterxml.jackson.core:jackson-annotations`（坐标保持，BOM 管理 2.x 线）。
- 任何 Jackson 版本本地钉。
- nop-orm/nop-core 自有 JSON 层（JsonTool 等非 Jackson databind 实现，不在 8 站点内）。

## Execution Plan

### Phase 1 - databind 迁移（3 pom + 8 文件）

Status: completed
Targets: `nop-benchmark/nop-benchmark-json`, `nop-ai/nop-ai-api`（test）, `nop-kernel/nop-api-core`（test）

- Item Types: `Fix`（Jackson 2 databind 是 roadmap 认定的待迁移漂移；Jackson 3 为双框架共同目标线）

- [x] 3 pom：`com.fasterxml.jackson.core:jackson-databind` → `tools.jackson.core:jackson-databind`（无版本号，BOM 管理；nop-ai-api 孤儿属性 `jackson.version` 删除）
- [x] 4 eishay 文件：`new ObjectMapper()` → `JsonMapper.builder().build()`（`tools.jackson.databind.json.JsonMapper`），import 同步
- [x] 3 nop-ai-api 测试：configure 形态 → `JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()`；TestChatStreamAccumulator → `JsonMapper.builder().build()`；`getTypeFactory().constructCollectionType` 链零改动保留
- [x] 1 nop-api-core 测试：同 eishay 标准形态
- [x] annotations import（`com.fasterxml.jackson.annotation.*`）逐文件确认零改动
- [x] 执行期新发现①（运行时实证）：nop-api-core 本地钉 `jackson-annotations` 2.15.2 与 Jackson 3 databind 不兼容——Jackson 3 introspector 类初始化需要 annotations 2.17+ 的 `JsonSerializeAs`，`NoClassDefFoundError` 5 用例全红。修复 = 撤销本地钉回落 BOM 管理（解析 2.21——quarkus-bom 4.0.0.Beta1 直管 jackson-annotations=2.21，审计 dependency:tree 实证；满足 ≥2.17 要求，与全仓其余模块对齐，原注释"2.10.2 与 idea plugin 不兼容"为 ancient history）。此为 WI10 BOM-first 政策的又一个先例。
- [x] 执行期新发现②（运行时实证）：Jackson 3 **不再自动探测下划线前缀访问器**（`get_chgType` 在 Jackson 2 隐式映射为 `_chgType`，Jackson 3 下完全被排除——探针实证 JSON 仅含 name）。修复 = `CrudInputBase` 的 `get_chgType`/`set_chgType` 加显式 `@JsonProperty("_chgType")`（annotations 线标准做法），JSON 契约名保持不变

Exit Criteria:

- [x] `grep -rnE "com\.fasterxml\.jackson\.(core|databind)" --include="*.java" --exclude-dir=target nop-benchmark/nop-benchmark-json nop-ai/nop-ai-api nop-kernel/nop-api-core` 零命中（实测 0）
- [x] `grep -rn "com.fasterxml.jackson.annotation" --include="*.java" --exclude-dir=target . | wc -l` = 629 基线 → 终态 630（+1 = 本 plan 修复② 的 JsonProperty import，文件集 529 不变——审计逐文件对账）
- [x] 3 pom 无 jackson-databind 本地版本号
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SB4/Q4 配置面裁定记录

Status: completed
Targets: 本 plan + `ai-dev/logs/2026/10-11.md`

- Item Types: `Decision`（配置面 5 项发现的事实裁定）

- [x] 5 项发现写入 Current Baseline 与日志（零命中实证 + use-jackson2-defaults 默认满足裁定 + module 自动探测默认采纳）
- [x] 不为 `spring.jackson.use-jackson2-defaults=false` 发明配置落点（全仓零 spring.jackson 配置、无共享 application.yaml；SB4 官方默认即 false = Jackson-3 defaults，意图由默认满足——spring.io 官方迁移指南佐证）

Exit Criteria:

- [x] plan 与日志记录 5 项发现及裁定理由
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 测试门

Status: completed
Targets: `nop-benchmark/nop-benchmark-json`, `nop-ai/nop-ai-api`, `nop-kernel/nop-api-core`

- Item Types: `Proof`（迁移后行为等价验证）

- [x] `./mvnw clean test -pl nop-ai/nop-ai-api` 61/61 绿、`-pl nop-kernel/nop-api-core` 165/165 绿（序列化往返测试验证 Jackson 3 行为，含 `_chgType` 契约与 AnyGetter/AnySetter 吸收语义）
- [x] `./mvnw clean install -DskipTests -pl nop-benchmark/nop-benchmark-json,nop-ai/nop-ai-api,nop-kernel/nop-api-core` 绿
- [x] 全量 `clean install -DskipTests -T 1C -fae` 与 build5 基线同态（build6：399 绿，较 build5 +4——stream-runtime 随其会话 plan 2435 C2 落库转绿；非绿 4 = tcc-dao/sys-dao/job-dao/retry-engine 全为慢性 gen-aop 族，sys-dao 为该族已文档化的成员漂移）

Exit Criteria:

- [x] 受影响模块测试输出 "Tests run: N, Failures: 0, Errors: 0"（61/61、165/165）
- [x] 全量门非绿集按 gen-aop 族签名归因（族成员逐轮漂移，sys-dao 已文档化）：实测非绿 = tcc-dao/sys-dao/job-dao/retry-engine 全同签名，无新增；stream-runtime 已随其会话 C2 落库转绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（8 站点 Jackson 2 databind 迁移清零 + annotations 撤钉 + `_chgType` 显式契约）
- [x] 所有 in-scope confirmed contract drifts 已收敛（databind 坐标统一 tools.jackson 线，3.1.4 实证解析）
- [x] 行为/契约结果已达成（序列化往返测试绿；`_chgType` JSON 契约保持）
- [x] 必要 focused verification 已完成（nop-ai-api 61/61、nop-api-core 165/165，审计独立复跑确认）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步——审计 grep 实证 docs-for-ai 零 stale jackson-databind 表述：**No owner-doc update required**
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_dfc20c69，ALLOW）
- [x] Anti-Hollow Check：审计实读 8/8 文件确认 tools.jackson import 与 JsonMapper 形态、零 `tools.jackson.databind.ObjectMapper` 直用；测试复跑 165/165+61/61 非 skip
- [x] `./mvnw clean install -DskipTests -T 1C -fae`（JDK 25）通过（build6 399 绿，非绿 4 全 gen-aop 族同签名）
- [x] 受影响模块 `./mvnw test` 通过
- [x] 代码风格符合本仓约定（imports 分组 io.nop→第三方→java.*、最小 diff；nop-cli lint 门在提交时自动执行）

## Deferred But Adjudicated

（无——本 plan 无 deferred 项；若执行期出现新缺陷按模板补录）

## Non-Blocking Follow-ups

- eishay 基准在 Jackson 3 下的性能数字重测（benchmark 语义，非升级正确性面）。

## Closure

Status Note: WI7 三 deliverable 全部落地并经独立审计 ALLOW：8 databind 站点 100% 迁移到 tools.jackson 标准 JsonMapper.builder 形态（审计实读 8/8 + 全仓零 com.fasterxml databind 残留 + 零 ObjectMapper 直用）；annotations 线 529 文件未动（基线 629 行 → 终态 630 行，+1 为本 plan 修复②的 import，审计逐文件对账）；3 pom 换线零本地版本；执行期两个运行时缺陷（annotations 2.15.2 钉不兼容 Jackson 3、Jackson 3 不探测下划线访问器）以标准方式修复。配置面 5 项发现按 roadmap "record rather than invent" 风格裁定。全量门 build6 399 绿、非绿恰为慢性 gen-aop 族。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 closure auditor（fresh session）
- Audit Session: agent_dfc20c69-48ab-47f6-a709-03aa5007d485
- Evidence:
  - Phase 1/2/3 Exit Criteria live 复核全 PASS（databind grep 零命中、配置面 5 项在档、3 模块 build6 SUCCESS）
  - 8/8 文件实读迁移真实性 PASS；`getTypeFactory().constructCollectionType` 链原样保留实证
  - annotations 计数对账：HEAD 629 → 工作树 630（+1 = 修复② JsonProperty import，文件集 529 不变）
  - pom 检查 PASS（3 pom 零本地版本；撤钉实证；孤儿属性已删；全仓无存活 `${jackson.version}` 引用）
  - CrudInputBase @JsonProperty("_chgType") getter+setter 双侧实证（:29/:33）
  - 测试复跑：nop-api-core 165/165、nop-ai-api 61/61（含 TestCrudInputBase 5/5）
  - build6 非绿 4 项逐个验证 gen-aop 同签名（`_tmp/wi7-build6.log`），无新增
  - 版本解析实检：tools.jackson.core:jackson-databind 3.1.4（dependency:tree）；jackson-annotations 实为 2.21（quarkus-bom 直管，勘误入档）
  - `check-doc-links.mjs --strict` exit 0；`check-plan-checklist.mjs --strict` 于本 Closure 段写入后复跑 exit 0
  - Deferred 分类：无 deferred 项

Follow-up:

- eishay 基准 Jackson 3 性能数字重测（Non-Blocking Follow-ups 保持）
