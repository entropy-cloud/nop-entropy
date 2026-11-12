# Gradle 通道决定（R-G 治理批产出物⑥）

> Decision ID：R-G-DEC-001（6.5-CL-301 通道决定收口文书）
> 衔接：plan 2533（R3-10，roadmap 8.40）已落档裁定；batch-proposal §四.1 三选一通道写入验收
> 落档日期：2026-11-12（plan 2567）

## Decision

**通道 = 构建门禁内（通道②：可测逻辑下沉 plain-JVM seam，验证通道收归 Maven 门禁）为主通道；通道①（`./gradlew test` IntelliJ harness 纳入 CI）为补 CI 接线建议（后续独立计划，不在本批实施）。**

## 依据（引指针，不重写裁定）

1. **CL-301 缺口本体**：idea-plugin Gradle 构建隔离=测试门禁盲区，4 站点修复验证不得依赖 `./mvnw test`（cra-index 6.5 计数口径块；`nop-dev-tools/pom.xml:20-24` 无 IntelliJ harness 的 Maven 测试面（modules 列表含 nop-maven-shaded-plugin 属构建插件非测试通道——措辞收紧，audit Minor-3））。
2. **三选一通道**（batch-proposal §四.1）：① gradlew harness 入 CI；② 可测逻辑下沉 plain-JVM seam；③ 工单行显式登记「验证通道=Gradle/harness」。
3. **plan 2533 已兑现通道②**（roadmap 8.40 行收敛注）：W-02 connector 物理下沉 `io.nop.xlang.debugger`（nop-xlang-debugger，零平台依赖 sink）——验证通道**更新为 Maven 门禁内**；W-03 插入点登记子 Disposable+通道③ harness 写入留痕（Maven 侧变异/回退结构论证承载）；W-05 SharedCoreInitializerRegistrar 引用计数的组合行为=Gradle 验证盲区**如实登记**（审计 M-2）。W-07 watch 级随批宣读。
4. **残余盲区**：W-05 组合行为与任何未来仅可在 harness 内表达的行为断言仍属 Gradle 通道——这些面**不得**用 Maven 绿冒充已验证（产出物② E4 检查项）。

## 补 CI 接线建议（后续计划输入，Non-Goal 本批）

- 建议 CI 增设独立 job：`cd nop-dev-tools/nop-idea-plugin && ./gradlew test`（IntelliJ harness），覆盖 W-03/W-05/W-07 三个 harness 站点与未来插件面回归；job 失败不得阻断 Maven 主门禁（独立通道语义）。
- 立项载体：roadmap 9.1 对账议题或独立 CI 基建 plan；本批仅落决定，不实施。

## 状态

- CL-301 收口状态：**已收口**（通道②兑现 + 通道③留痕 + 通道①建议登记）；cra-index 6.5 CL 台账 CL-301 行「独立线索治理轨」随本决定闭合登记（本文件为落档位）。
