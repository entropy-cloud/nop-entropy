# nop-entropy 全模块实现代码检查 Roadmap（check2 系列）

> **定位**: 对仓库内所有模块的实现代码做第二轮系统性全量检查（check2 系列）。只检查、记录问题，**不修改任何产品代码**。
> **启动日期**: 2026-08-23
> **基线**: worktree `nop-entropy-fix-ai-check` @ branch `fix-ai-check`（commit `47cf66b865`，与 master 同步）
> **执行方式**: 每个检查单元派发一个独立子代理，输出报告到本目录（`ai-dev/audits/check2/{unit}.md`），roadmap 实时勾选进度。
> **方法论基础**: `ai-dev/skills/deep-audit-prompts.md` 的轻量化全量版（单轮多维度扫描 + 逐条验证，不做多轮深挖/独立复核）；与 check 系列同构，独立于其结论。
> **与第一轮（`../check/`）的关系**: check2 是**独立复审**，不是增量 diff 审计。子代理禁止读取 check 系列报告（含其 P0 修复标注），必须以 fresh eyes 审 live code。两个用途：
> 1. 复验修复后的代码基线（第一轮 36 条 P0 已在 fix-ai-check 分支处置，修复本身可能引入新问题）
> 2. 提升第一轮覆盖薄弱单元的检查深度（复检建议见 Phase 7）

## 硬性约束

1. **不修改任何产品代码**。子代理只允许写自己的报告文件（`ai-dev/audits/check2/{unit}.md`）。
2. 检查以 **live code** 为准，禁止读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下的历史记录来"继承"结论（含第一轮 check 系列报告）。
3. 每条发现必须包含：文件路径 + 行号 + 证据代码片段（3-10 行）+ 严重程度 + 现状 + 风险 + 建议 + 误报排除说明。禁止无证据的推测性条目。
4. 报告必须声明**覆盖范围**（哪些文件深读、哪些仅模式扫描），供复检安排。
5. 修复痕迹（注释/测试中的 fix 标注）不构成"此处已正确"的证据——一切以当前代码逻辑为准。

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

说明：测试代码本身的有效性不在本系列范围，仅当 main 代码问题波及测试时顺带记录。

## 严重程度定义

- **P0**: 明确 bug，会造成数据错误、安全漏洞、崩溃或资源耗尽，且触发路径现实存在
- **P1**: 特定条件（并发、异常路径、边界输入、特定配置）下触发的正确性/资源/并发问题
- **P2**: 错误处理不当、规范违背、契约漂移、可维护性风险（当前无直接运行时危害）
- **P3**: 轻微问题、风格问题、低价值改进建议

## 检查单元与进度

状态: `pending` / `in-progress` / `done`。文件数为 `src/main/java` 下 `.java` 数（2026-08-23 统计，不含 target/test）。

### Phase 1 — 框架主干（其余一切模块的地基，优先级最高）

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-commons | `nop-kernel/nop-commons` | 403 | [nop-commons.md](nop-commons.md) | pending |
| nop-core | `nop-kernel/nop-core` | 757 | [nop-core.md](nop-core.md) | pending |
| nop-xlang | `nop-kernel/nop-xlang` | 885 | [nop-xlang.md](nop-xlang.md) | pending |
| xlang-java-truffle | `nop-kernel/nop-xlang-java` + `nop-xlang-truffle` + `nop-xlang-java-e2e` | 187 | [xlang-java-truffle.md](xlang-java-truffle.md) | pending |
| nop-api-core | `nop-kernel/nop-api-core` | 322 | [nop-api-core.md](nop-api-core.md) | pending |
| kernel-small | `nop-kernel` 下 codegen/javac/dataset/antlr4/markdown/record-mapping/kernel-cli（`nop-xdefs` 无 Java 源不单列） | 196 | [kernel-small.md](kernel-small.md) | pending |
| nop-core-framework | `nop-core-framework`（boot/config/ioc/log/plugin/security） | 230 | [nop-core-framework.md](nop-core-framework.md) | pending |
| nop-orm | `nop-persistence/nop-orm` | 152 | [nop-orm.md](nop-orm.md) | pending |
| nop-orm-eql | `nop-persistence/nop-orm-eql` | 221 | [nop-orm-eql.md](nop-orm-eql.md) | pending |
| orm-periph | `nop-persistence` 下 orm-model/orm-drivers/orm-pdm/orm-rpc/orm-data/orm-geo | 120 | [orm-periph.md](orm-periph.md) | pending |
| nop-dao | `nop-persistence/nop-dao` | 116 | [nop-dao.md](nop-dao.md) | pending |
| db-migration | `nop-persistence/nop-db-migration` + `nop-dbtool` | 109 | [db-migration.md](db-migration.md) | pending |
| nosql-cdc | `nop-persistence/nop-nosql` + `nop-cdc` | 38 | [nosql-cdc.md](nosql-cdc.md) | pending |
| nop-biz | `nop-service-framework/nop-biz` | 101 | [nop-biz.md](nop-biz.md) | pending |
| nop-graphql | `nop-service-framework/nop-graphql` | 239 | [nop-graphql.md](nop-graphql.md) | pending |
| gateway-bizauth | `nop-service-framework` 下 nop-gateway + biz-auth-api/core + biz-file-core | 161 | [gateway-bizauth.md](gateway-bizauth.md) | pending |

### Phase 2 — 业务骨架模块

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-auth | `nop-auth`（api/dao/service/sso/oauth） | 287 | [nop-auth.md](nop-auth.md) | pending |
| nop-sys | `nop-sys` | 165 | [nop-sys.md](nop-sys.md) | pending |
| nop-job | `nop-job` | 169 | [nop-job.md](nop-job.md) | pending |
| nop-task | `nop-task`（core 为主） | 220 | [nop-task.md](nop-task.md) | pending |
| nop-wf | `nop-wf`（core/api 为主） | 257 | [nop-wf.md](nop-wf.md) | pending |

### Phase 3 — 可复用业务模块

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-report | `nop-report` | 153 | [nop-report.md](nop-report.md) | pending |
| nop-rule | `nop-rule` | 88 | [nop-rule.md](nop-rule.md) | pending |
| nop-batch | `nop-batch`（core/dsl 为主） | 250 | [nop-batch.md](nop-batch.md) | pending |
| nop-dyn | `nop-dyn` | 122 | [nop-dyn.md](nop-dyn.md) | pending |
| file-retry-tcc | `nop-file` + `nop-retry` + `nop-tcc` | 105 | [file-retry-tcc.md](file-retry-tcc.md) | pending |
| nop-metadata | `nop-metadata`（service/dao 为主，第一轮覆盖薄弱，本轮要求 core 包全量深读） | 282 | [nop-metadata.md](nop-metadata.md) | pending |
| nop-excel | `nop-format/nop-excel` | 332 | [nop-excel.md](nop-excel.md) | pending |
| format-record | `nop-format` 下 nop-record + nop-record-netty + nop-tablesaw | 154 | [format-record.md](format-record.md) | pending |
| format-pdf-svg | `nop-format` 下 nop-pdf + nop-svg + nop-chart-export | 148 | [format-pdf-svg.md](format-pdf-svg.md) | pending |
| format-office | `nop-format` 下 nop-ooxml + nop-office-model + nop-office-doc-model | 182 | [format-office.md](format-office.md) | pending |
| format-misc | `nop-format` 下 nop-converter + nop-mermaid + nop-markdown-ext + nop-chart-echarts | 101 | [format-misc.md](format-misc.md) | pending |

### Phase 4 — 大型子系统

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-ai-agent | `nop-ai/nop-ai-agent`（第一轮覆盖薄弱，本轮要求 core 包全量深读） | 536 | [nop-ai-agent.md](nop-ai-agent.md) | pending |
| ai-core-api | `nop-ai/nop-ai-core` + `nop-ai/nop-ai-api` | 270 | [ai-core-api.md](ai-core-api.md) | pending |
| ai-toolkit-skills | `nop-ai` 下 nop-ai-toolkit + nop-ai-skills + nop-ai-tools | 126 | [ai-toolkit-skills.md](ai-toolkit-skills.md) | pending |
| ai-rest | `nop-ai` 其余子模块（gateway/service/dao/coder/shell/maven/mcp-server/spring-mcp/rag/app/web 等） | 200 | [ai-rest.md](ai-rest.md) | pending |
| stream-core | `nop-stream/nop-stream-core`（含 Flink 兼容层） | 364 | [stream-core.md](stream-core.md) | pending |
| stream-runtime | `nop-stream/nop-stream-runtime` + `nop-stream-rocksdb` | 82 | [stream-runtime.md](stream-runtime.md) | pending |
| stream-cep | `nop-stream/nop-stream-cep` | 77 | [stream-cep.md](stream-cep.md) | pending |
| stream-flow-conn | `nop-stream` 下 flow + connector 系（含 debezium/jdbc/batch）+ fraud-example | ~138 | [stream-flow-conn.md](stream-flow-conn.md) | pending |
| nop-code | `nop-code`（core/service/api/lang 为主） | 201 | [nop-code.md](nop-code.md) | pending |
| nop-graph | `nop-graph` | 27 | [nop-graph.md](nop-graph.md) | pending |
| nop-datav | `nop-datav`（service/dao，第一轮覆盖薄弱，本轮要求全量深读） | 149 | [nop-datav.md](nop-datav.md) | pending |
| nop-search | `nop-search`（第一轮覆盖薄弱，本轮要求全量深读） | 17 | [nop-search.md](nop-search.md) | pending |

### Phase 5 — 集成与运行时外围

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| net-http-rpc | `nop-network` 下 http + rpc | 146 | [net-http-rpc.md](net-http-rpc.md) | pending |
| net-misc | `nop-network` 下 netty + codec + socket + vertx | 62 | [net-misc.md](net-misc.md) | pending |
| nop-integration | `nop-integration` | 61 | [nop-integration.md](nop-integration.md) | pending |
| msg-cluster-cred | `nop-message` + `nop-cluster` + `nop-credential` | 150 | [msg-cluster-cred.md](msg-cluster-cred.md) | pending |
| runner-cli | `nop-runner` | 37 | [runner-cli.md](runner-cli.md) | pending |
| spring-quarkus | `nop-spring` + `nop-quarkus` | 50 | [spring-quarkus.md](spring-quarkus.md) | pending |

### Phase 6 — 工具、测试基建与示例

| 单元 | 路径 | 文件数 | 报告 | 状态 |
|------|------|--------|------|------|
| nop-autotest | `nop-autotest` | 41 | [nop-autotest.md](nop-autotest.md) | pending |
| nop-utils | `nop-utils` | 119 | [nop-utils.md](nop-utils.md) | pending |
| dev-tools | `nop-dev-tools`（idea-plugin 为主） | 155 | [dev-tools.md](dev-tools.md) | pending |
| frontend-benchmark | `nop-frontend-support` + `nop-benchmark` | 174 | [frontend-benchmark.md](frontend-benchmark.md) | pending |
| demo-migration | `nop-demo` + `nop-migration`（第一轮覆盖薄弱，本轮要求 demo 全量深读） | 105 | [demo-migration.md](demo-migration.md) | pending |

### Phase 7 — 汇总、复检与修复回归

| 单元 | 内容 | 产出 | 状态 |
|------|------|------|------|
| SUMMARY | 汇总全部单元的 P0/P1 发现，按模块与主题聚类，与第一轮做发现数对照（不合并结论） | [SUMMARY.md](SUMMARY.md) | pending |
| fix-regression | 对 fix-ai-check 分支历次修复 commit 做回归性 diff 复审（唯一允许读 git 历史的单元），检查修复是否引入新缺陷、修复测试是否真的能红 | [fix-regression.md](fix-regression.md) | pending |
| 复检批次 | 对 P0 密集、覆盖率声明不足（<60% 深读）或发现异常少的单元二次检查 | 各单元报告追加章节 | pending |

## 执行规则

1. **顺序**: 严格按 Phase 1 → 6 推进；同 Phase 内按表序。框架主干的问题会放大到所有下游模块，先查地基再查上层。
2. **并行**: 每批同时派发 3-4 个子代理，每个子代理只负责一个单元、只写自己的报告文件。
3. **进度维护**: 单元派发时把状态改为 `in-progress`，报告落盘后改 `done`。禁止事后批量补状态。
4. **报告模板**: 统一使用下述结构（子代理 prompt 中内嵌）：

```markdown
# {unit} 实现代码检查报告（check2）

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
   - 不读 `ai-dev/` 下任何历史记录（audits/plans/bugs/lessons/skills），结论只来自 live code
   - 每条发现先读上下文验证再写入；grep 命中不等于发现
   - 大模块（>400 文件）允许按风险优先级取舍，但必须在覆盖范围声明中如实说明
   - 报告落盘后只返回简短摘要（统计数字 + P0 标题清单），不返回报告全文
6. **发现修复衔接**: 本系列只记录不修复。P0/P1 修复需另建 plan（`ai-dev/plans/`），引用对应 check2 报告作为 baseline。
7. **提交节奏**: roadmap 创建即提交；每完成一个 Phase（或批次）提交一次，避免丢失进度。

## 与既有审计体系的关系

- `ai-dev/audits/check/`（第一轮，2026-08-19~21）与本系列相互独立：check2 不读取、不引用其发现；历史未修复的 P1/P2/P3 应在 check2 中再次独立出现，两轮交集可提高置信度。
- `ai-dev/audits/` 下 nop-stream / nop-code / nop-job / nop-metadata / nop-ai-agent 等模块的多轮 deep-audit 记录同样不在子代理阅读范围。
- check2 的单轮扫描不替代 deep-audit 的多轮深挖 + 独立复核。

## 进度日志

- 2026-08-23: roadmap 创建，单元划分沿用第一轮 54 单元并细化（nop-xlang 拆出 xlang-java-truffle、ai-rest 覆盖面显式化为全部剩余 nop-ai 子模块、第一轮覆盖薄弱单元加注深读要求），共 56 个检查单元。启动 Phase 1。
