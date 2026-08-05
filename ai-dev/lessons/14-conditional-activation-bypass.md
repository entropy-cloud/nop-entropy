# 14: 条件激活的旁路面 — "开关默认 false" 必须实证，不能想当然

> Date: 2026-08-05
> Severity: Medium — nop-metadata P2-MA3-02/P2-MA7.2-02：entity 路径自定义查询绕过 data-auth 合并，判定"无实际暴露"必须靠双开关默认值 + 判定链 + 调用路径三方实证，缺一不可

## 场景

nop-metadata 审计发现 entity 路径自定义查询（queryTableData/queryJoinData/queryAggregation）绕过 Biz 层 data-auth 过滤合并（裸 DAO/EQL 路径零 appendFilter）。这是潜在的数据权限旁路。

R2.14 裁决为"当前无实际暴露"的依据（MA3.3 复核实证 + MR4 终局复核）：

1. `nop.auth.use-data-auth-table` 默认 false（NopAuthConfigs.java:69-70）
2. `nop.auth.enable-data-auth` 默认 false（biz-defaults.beans.xml:16）
3. application.yaml:17 仅配 data-auth-config-path，未开启任一开关
4. DefaultDataAuthChecker 判定链两路均 false（isUseTenant 等）
5. 调用路径实证：NopMetaTableBizModel:208/:234/:254 零 appendFilter

**关键点**：判定不是"想当然安全"，而是**逐项核验了激活条件全部为 false** + **调用路径实证**。若只写"当前配置未开启，应该安全"而没有上述证据链，裁决无效。

## 根因

1. **条件激活缺陷的暴露面 = 激活条件 × 缺陷路径**：旁路面缺陷（绕过过滤）只有在"特性被激活"且"路径真实执行"时才构成暴露；二者任一为 false 即无实际暴露，但必须分别实证。
2. **想当然 vs 实证**：不核查配置默认值、不追踪判定链、不读调用路径，只凭"应该没开"下结论，是条件激活类 finding 最常见的裁决错误。
3. **裁决复用链**：P2-MA7.2-02 与 P2-MA3-02 同族同依据——第二处 finding 的裁决必须重新核实（配置/判定链/路径），不能只抄上一处结论。

## 正确做法

1. **条件激活类 finding 的三方实证模板**：
   - 配置面：相关开关的默认值（CFG 定义处）+ 实际配置文件（yaml/properties）是否开启
   - 判定链：运行时判定逻辑（如 DefaultDataAuthChecker 的 isUseTenant/enableDataAuth 链）逐路求值
   - 路径面：可疑旁路路径（裸 DAO/EQL 调用点）是否真实存在、是否带过滤合并
2. **裁决记录必须可复核**：把三方证据写进裁决（Why Not Blocking Closure），后续审计/复核能按证据重跑，而不是重新推导。
3. **条件激活缺陷 ≠ deferred**：激活条件当前为 false 只说明"当前无暴露"（watch-only residual），缺陷路径仍真实存在——登记为 watch-only 而非"不是问题"，配置一旦开启需重新评估（MR4 终局裁决模式）。

## 判定规则

> **条件激活旁路面的"无暴露"判定，必须同时满足三个可复核证据：开关默认值/配置均为关闭 + 判定链求值为 false + 旁路路径实证存在。** 三者缺一 = 裁决证据不完整，不得标 deferred。
>
> 证据充分时归类 watch-only residual（不是 fixed）——路径与缺陷仍在，仅激活条件未满足；激活条件可变时需定期复核。

## 适用范围

- data-auth / 数据权限 / 条件开启特性（双开关、feature flag）的旁路面审计
- 条件激活缺陷的 deferred 裁决
- 审计报告"无实际暴露"结论的证据要求

## 参考

- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/NopAuthConfigs.java`（:69-70 默认 false）
- `nop-biz/src/main/resources/_vfs/nop/biz/defaults/biz-defaults.beans.xml`（:16 enableDataAuth 默认 false）
- `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java`（:208/:234/:254 零 appendFilter 实证）
- 裁决：roadmap R2.14（P2-MA3-02）+ MR4 终局裁决记录（P2-MA3-02/P2-MA7.2-02，watch-only residual）
- 相关教训：08（校验函数存在≠接线——同理，路径存在≠暴露）
