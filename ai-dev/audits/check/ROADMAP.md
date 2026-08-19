# nop-entropy 全模块实现代码检查 Roadmap

> **定位**: 对仓库内所有模块的实现代码做系统性问题检查（check 系列）。只检查、记录问题，**不修改任何产品代码**。
> **启动日期**: 2026-08-19
> **执行方式**: 每个检查单元派发一个独立子代理，输出报告到本目录（`ai-dev/audits/check/{unit}.md`），roadmap 勾选进度。
> **方法论基础**: `ai-dev/skills/deep-audit-prompts.md`（本系列是其轻量化全量版：单轮多维度扫描 + 逐条验证，不做多轮深挖/独立复核；P0/P1 密集或覆盖率不足的单元在 Phase 7 安排复检）。

## 硬性约束

1. **不修改任何产品代码**。子代理只允许写自己的报告文件。
2. 检查以 **live code** 为准，禁止读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 的历史记录来"继承"结论。
3. 每条发现必须包含：文件路径 + 行号 + 证据代码片段（3-10 行）+ 严重程度 + 现状 + 风险 + 建议 + 误报排除说明。禁止无证据的推测性条目。
4. 报告必须声明**覆盖范围**（哪些文件深读、哪些仅模式扫描），供后续复检安排。

## 检查维度（每个单元统一执行）

| # | 维度 | 关注点 |
|---|------|--------|
| D1 | 正确性 | NPE 风险、边界条件、逻辑错误、错误的空/默认值处理、复制粘贴错误 |
| D2 | 资源管理 | 流/连接/通道泄漏、close 顺序、try-with-resources 缺失、finally 缺失 |
| D3 | 并发与线程安全 | 共享可变状态、非线程安全类误用（SimpleDateFormat 等）、锁粒度/死锁、volatile 缺失、并发集合误用 |
| D4 | 错误处理 | 异常吞噬（catch 后不处理不抛）、bare RuntimeException、错误码两档策略违背、异常信息丢失（只留 message 丢 cause） |
| D5 | 安全 | 注入（SQL/命令/路径）、路径遍历、敏感信息日志泄漏、不安全的反序列化、随机数误用 |
| D6 | 性能隐患 | 循环内重计算/IO、O(n²) 拼接、不必要的全量复制、热路径上重复编译/反射 |
| D7 | 平台规范 | Nop IoC `@Inject` private 字段、Spring `@Value`（应为 `@InjectValue`）、手改 `_` 前缀生成文件迹象、bean 未在 beans.xml 注册却预期注入 |
| D8 | API/契约一致性 | 公开接口与实现不匹配、返回值与声明不符、跨模块类型契约漂移 |

说明：测试代码本身的有效性不在本系列范围（另有 unit-test-antipatterns 专项），仅当 main 代码问题波及测试时顺带记录。

## 严重程度定义

- **P0**: 明确 bug，会造成数据错误、安全漏洞、崩溃或资源耗尽，且触发路径现实存在
- **P1**: 特定条件（并发、异常路径、边界输入、特定配置）下触发的正确性/资源/并发问题
- **P2**: 错误处理不当、规范违背、契约漂移、可维护性风险（当前无直接运行时危害）
- **P3**: 轻微问题、风格问题、低价值改进建议

## 检查单元与进度

状态: `pending` / `in-progress` / `done`。文件数为 `src/main/java` 下 `.java` 数（不含 target）。

### Phase 1 — 框架主干（其余一切模块的地基，优先级最高）

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-commons | `nop-kernel/nop-commons` | 431 | [nop-commons.md](nop-commons.md) | done |
| nop-core | `nop-kernel/nop-core` | 810 | [nop-core.md](nop-core.md) | done |
| nop-xlang | `nop-kernel/nop-xlang` | 919 | [nop-xlang.md](nop-xlang.md) | done |
| nop-api-core | `nop-kernel/nop-api-core` | 338 | [nop-api-core.md](nop-api-core.md) | done |
| kernel-small | codegen/javac/dataset/antlr4/markdown/record-mapping/kernel-cli | 220 | [kernel-small.md](kernel-small.md) | done |
| nop-core-framework | `nop-core-framework`（ioc/config/boot/plugin/security/log） | 315 | [nop-core-framework.md](nop-core-framework.md) | done |
| nop-orm | `nop-persistence/nop-orm` | 265 | [nop-orm.md](nop-orm.md) | done |
| nop-orm-eql | `nop-persistence/nop-orm-eql` | 226 | [nop-orm-eql.md](nop-orm-eql.md) | done |
| orm-periph | orm-model/orm-drivers/orm-pdm/orm-rpc/orm-data/orm-geo | 120 | [orm-periph.md](orm-periph.md) | done |
| nop-dao | `nop-persistence/nop-dao` | 128 | [nop-dao.md](nop-dao.md) | done |
| db-migration | `nop-persistence/nop-db-migration` + dbtool | 115 | [db-migration.md](db-migration.md) | done |
| nosql-cdc | `nop-persistence/nop-nosql` + nop-cdc | 40 | [nosql-cdc.md](nosql-cdc.md) | done |
| nop-biz | `nop-service-framework/nop-biz` | 109 | [nop-biz.md](nop-biz.md) | done |
| nop-graphql | `nop-service-framework/nop-graphql` | 266 | [nop-graphql.md](nop-graphql.md) | done |
| gateway-bizauth | nop-gateway + biz-auth-api/core + biz-file-core | 186 | [gateway-bizauth.md](gateway-bizauth.md) | done |

### Phase 2 — 业务骨架模块

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-auth | `nop-auth`（api/dao/service/sso/oauth） | 390 | [nop-auth.md](nop-auth.md) | done |
| nop-sys | `nop-sys` | 184 | [nop-sys.md](nop-sys.md) | done |
| nop-job | `nop-job` | 222 | [nop-job.md](nop-job.md) | done |
| nop-task | `nop-task`（core 为主） | 264 | [nop-task.md](nop-task.md) | done |
| nop-wf | `nop-wf`（core/api 为主） | 286 | [nop-wf.md](nop-wf.md) | in-progress |

### Phase 3 — 可复用业务模块

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-report | `nop-report` | 176 | [nop-report.md](nop-report.md) | pending |
| nop-rule | `nop-rule` | 98 | [nop-rule.md](nop-rule.md) | pending |
| nop-batch | `nop-batch`（core/dsl 为主） | 269 | [nop-batch.md](nop-batch.md) | pending |
| nop-dyn | `nop-dyn` | 131 | [nop-dyn.md](nop-dyn.md) | pending |
| file-retry-tcc | `nop-file` + `nop-retry` + `nop-tcc` | 123 | [file-retry-tcc.md](file-retry-tcc.md) | pending |
| nop-metadata | `nop-metadata`（service/dao 为主） | 441 | [nop-metadata.md](nop-metadata.md) | pending |
| nop-excel | `nop-format/nop-excel` | 338 | [nop-excel.md](nop-excel.md) | pending |
| format-record | nop-record + nop-record-netty + nop-tablesaw | 175 | [format-record.md](format-record.md) | pending |
| format-pdf-svg | nop-pdf + nop-svg + nop-chart-export | 153 | [format-pdf-svg.md](format-pdf-svg.md) | pending |
| format-office | nop-ooxml + nop-office-model + nop-office-doc-model | 206 | [format-office.md](format-office.md) | pending |
| format-misc | nop-converter + nop-mermaid + nop-markdown-ext | 105 | [format-misc.md](format-misc.md) | pending |

### Phase 4 — 大型子系统

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-ai-agent | `nop-ai/nop-ai-agent` | 963 | [nop-ai-agent.md](nop-ai-agent.md) | pending |
| ai-core-api | nop-ai-core + nop-ai-api | 326 | [ai-core-api.md](ai-core-api.md) | pending |
| ai-toolkit-skills | nop-ai-toolkit + nop-ai-skills + nop-ai-tools | 172 | [ai-toolkit-skills.md](ai-toolkit-skills.md) | pending |
| ai-rest | nop-ai-gateway/shell/coder/dao/service/maven/mcp 等 | 215 | [ai-rest.md](ai-rest.md) | pending |
| stream-core | `nop-stream/nop-stream-core`（含 Flink 兼容层） | 578 | [stream-core.md](stream-core.md) | pending |
| stream-runtime | `nop-stream/nop-stream-runtime` + rocksdb | 262 | [stream-runtime.md](stream-runtime.md) | pending |
| stream-cep | `nop-stream/nop-stream-cep` | 131 | [stream-cep.md](stream-cep.md) | pending |
| stream-flow-conn | nop-stream-flow + connector 系 + fraud-example | 138 | [stream-flow-conn.md](stream-flow-conn.md) | pending |
| nop-code | `nop-code`（core/service/api/lang 为主） | 281 | [nop-code.md](nop-code.md) | pending |
| nop-graph | `nop-graph` | 30 | [nop-graph.md](nop-graph.md) | pending |
| nop-datav | `nop-datav`（service/dao） | 231 | [nop-datav.md](nop-datav.md) | pending |
| nop-search | `nop-search` | 19 | [nop-search.md](nop-search.md) | pending |

### Phase 5 — 集成与运行时外围

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| net-http-rpc | nop-network: http + rpc | 154 | [net-http-rpc.md](net-http-rpc.md) | pending |
| net-misc | nop-network: netty + codec + socket + vertx | 65 | [net-misc.md](net-misc.md) | pending |
| nop-integration | `nop-integration` | 75 | [nop-integration.md](nop-integration.md) | pending |
| msg-cluster-cred | nop-message + nop-cluster + nop-credential | 199 | [msg-cluster-cred.md](msg-cluster-cred.md) | pending |
| runner-cli | `nop-runner` | 50 | [runner-cli.md](runner-cli.md) | pending |
| spring-quarkus | `nop-spring` + `nop-quarkus` | 55 | [spring-quarkus.md](spring-quarkus.md) | pending |

### Phase 6 — 工具、测试基建与示例

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-autotest | `nop-autotest` | 45 | [nop-autotest.md](nop-autotest.md) | pending |
| nop-utils | `nop-utils` | 138 | [nop-utils.md](nop-utils.md) | pending |
| dev-tools | `nop-dev-tools`（idea-plugin 为主） | 181 | [dev-tools.md](dev-tools.md) | pending |
| frontend-benchmark | nop-frontend-support + nop-benchmark | 182 | [frontend-benchmark.md](frontend-benchmark.md) | pending |
| demo-migration | `nop-demo` + `nop-migration` | 163 | [demo-migration.md](demo-migration.md) | pending |

### Phase 7 — 汇总与复检

| 单元 | 内容 | 产出 | 状态 |
|------|------|------|------|
| SUMMARY | 汇总全部单元的 P0/P1 发现，按模块与主题聚类 | [SUMMARY.md](SUMMARY.md) | pending |
| 复检批次 | 对 P0 密集、覆盖率声明不足（<60% 深读）或发现异常少的单元二次检查 | 各单元报告追加章节 | pending |

## 执行规则

1. **顺序**: 严格按 Phase 1 → 6 推进；同 Phase 内按表序。原因：框架主干的问题会放大到所有下游模块，先查地基再查上层，避免下游模块重复报告由上游引起的假象问题。
2. **并行**: 每批同时派发 3-4 个子代理，每个子代理只负责一个单元、只写自己的报告文件。
3. **进度维护**: 单元开始时把状态改为 `in-progress`，报告落盘后改 `done`。禁止事后批量补状态。
4. **报告模板**: 统一使用下述结构（子代理 prompt 中内嵌）：

```markdown
# {unit} 实现代码检查报告

- 检查日期: YYYY-MM-DD
- 模块路径: ...
- 文件数: ...（src/main/java）
- 覆盖范围声明: 深读 X 个文件（列举关键类），模式扫描覆盖 Y%；未覆盖区域: ...

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | n |
| P1 | n |
| P2 | n |
| P3 | n |

## 发现列表

### [{severity}] {title}

- **文件**: `path/File.java:{line}`
- **维度**: D1~D8
- **证据**:
```java
// 3-10 行代码片段
```
- **现状**: ...
- **风险**: ...
- **建议**: ...
- **误报排除**: 为何确认这不是误报（读过了哪些上下文）
```

5. **子代理纪律**（写入每个子代理 prompt）:
   - 不修改任何文件，只写自己的报告文件
   - 不读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`
   - 每条发现先读上下文验证再写入；grep 命中不等于发现
   - 大模块（>400 文件）允许按风险优先级取舍，但必须在覆盖范围声明中如实说明
6. **发现修复衔接**: 本系列只记录不修复。P0/P1 修复需另建 plan（`ai-dev/plans/`），引用对应 check 报告作为 baseline。

## 与既有审计体系的关系

- `ai-dev/audits/` 下已有 nop-stream / nop-code / nop-job / nop-metadata / nop-ai-agent 等模块的多轮 deep-audit 记录。本 check 系列**独立审计 live code**，允许与历史发现重叠（历史未修复的问题应再次出现）。
- check 系列的单轮轻量扫描不替代 deep-audit 的多轮深挖 + 独立复核；对高风险模块（如 stream/ai-agent）本系列的结论只作为增量线索。

## 进度日志

- 2026-08-19: roadmap 创建，启动 Phase 1。
- 2026-08-19: **Phase 1 全部 15 个单元完成**。合计 270 条发现（P0=7 / P1=58 / P2=107 / P3=98）。P0 摘要:
  - nop-api-core: `FutureHelper.thenRun` 成功路径回调被跳过 → 异步 biz action 成功后缓存永不失效
  - nop-core: `BeanCopier._copyToArray` 数组拷贝长度恒为 0 → 同类型数组浅拷贝静默丢失全部元素
  - kernel-small: `BaseRecordInput.next()` off-by-one → 首条记录读不到、末次迭代越界（污染 nop-dao 查询缓存路径）
  - nop-orm: `findPageAndReturnCursor` 无条件删除末元素 → 游标分页每页丢一条数据
  - nop-dao: `JdbcBatcher.flush()` PreparedStatement 从不关闭 → 批量保存路径每批泄漏一个 Statement
  - db-migration: XML 迁移文件 type 恒为 null → 全部 DDL 静默不执行却记成功
  - gateway-bizauth: 网关默认装配硬编码公开测试 API Key（sk-test-key-1/2）→ 未覆盖配置的应用等于发布有效凭证
  - 启动 Phase 2（业务骨架模块）。
