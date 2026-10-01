# Fixture Bundle（测试夹具可移植包）Runbook

> 状态：M1.1 导出侧定稿（2026-10-01，plan `nop-app-erp/docs/plans/2026-10-01-2049-1-m11-fixture-bundle-format-exporter.md`）；导入节为 M1.2 占位。
> 代码位置：`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/bundle/`

## 1. 是什么

Fixture bundle 是**脱离 TestClass 归属、可跨环境搬运的自包含夹具包**：从构造会话观测式导出（`AutoTestOrmHook` 收集 + 按 ID 重载全行），落地为 `manifest.json5 + base/ + snapshots/` 目录包，供任意 Nop 应用的测试装载。与既有 `_cases/<TestClass>/<method>/` 方法私有快照是**旁路关系**（CHECKING 模式零改动）；真增量 = 跨类共享 + 跨环境搬运。

## 2. 包格式与目录布局

```text
<bundle-dir>/
  manifest.json5                        # 发现入口 + 唯一加载序源（Decision C）
  base/<table>.csv                      # base 层：业务键对账导入（不覆盖已存在行）
  snapshots/<snapshot-name>/<table>.csv # payload 层（即 roadmap「payload 层」的目录实现，Decision B）
```

## 3. Manifest schema（Decision A）

| 字段 | 层级 | 说明 |
|------|------|------|
| `formatVersion` | 顶层 | 恒 `1`；前向扩展须受控（M1.2 列指纹走 M1.2 自己的审查扩展） |
| `bundleName` | 顶层 | bundle 标识 |
| `requires` | 顶层 | 依赖的其他 bundle 名数组（M1.2 导入时校验缺失报错） |
| `baseTables[]` | 顶层 | base 层表条目 |
| `snapshots[].name` / `snapshots[].tables[]` | 顶层 | 命名业务快照及其 payload 表条目 |
| `table` / `layer` / `businessKeys` | 表条目 | 实体名 / `base`\|`payload` / 业务键列（**base 必填**——M1.2 对账契约） |
| `loadOrder` / `rowCount` / `csv` | 表条目 | bundle 子集拓扑序号（唯一序源）/ 行数 / 相对 CSV 路径 |
| `sha256` | 表条目 | CSV 完整性，**用途限定 = 检测手改/截断，非防篡改安全保证** |
| `maskedColumns` | 表条目 | 敏感列名单（导出时写 `MASKED-BUNDLE-SEED` 占位） |
| `source` / `captureGaps` | 表条目 | 来源标记（observed-session）/ 会话内消失行数（导出行数 + gaps = 收集行数） |

## 4. 导出 API

```java
FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
session.run(hook -> {
    // 构造 body：须走 @BizMutation / GraphQL 业务逻辑入口（JDBC 直操不经 hook 不可见）
    ...构造调用...
});
FixtureBundleExportConfig config = new FixtureBundleExportConfig("bundle-name", "snapshot-name");
config.addBaseTable("app.erp.md.dao.entity.ErpMdCurrency", List.of("CODE"), List.of("SECRET_COL"));
config.addPayloadTable("app.erp.sal.dao.entity.ErpSalOrder");
FixtureBundleManifest manifest = new FixtureBundleExporter()
        .export(bundleDir, config, session, ormTemplate);
new FixtureBundleValidator().validate(bundleDir);   // 独立校验（只读磁盘态）
```

约束与边界（M0.1 六 Decision + M1.1 实现口径）：

- **会话独占**：同 JVM 同时只允许一个导出会话（tryLock，冲突抛 `nop.err.fixture-bundle.export-session-busy`）；注册/注销 try/finally 对称兜底。
- **全行语义**：onSave 行仅含已设列，导出前在 fresh session 按 ID 重载完整行（复合主键经 `OrmCompositePk.parse` 可逆）；「全行」= 触及行完整内容，**非全表全量**。
- **captureGaps**：会话内删除的已持久化行不出现在 CSV、记入 gaps；同会话 save+delete 抵消的行从未入库，不产生 gap。
- **敏感纪律**：masked 列占位导出；`FixtureBundleSensitiveRules` 可扩展（内建 PASSWORD/SALT 形列默认规则），校验器拒绝未标记的敏感列。**repo 级门控（防真实密钥列入库）是消费方 plan 义务**，框架层仅为代码级扫描。
- **已知边界**：no-PK 实体（`isNoPrimaryKey`）在 `orm_idString()` 键控下不可收集（既有 `AutoTestOrmHook` 共有约束）；跨数据库方言回放为 Non-Goal（见 roadmap）。
- **载体可见性**（M0.1 Decision ⑥）：bundle 布局在消费方 test-scope classpath（建议 `_vfs/<module-short>/fixture-bundle/<bundle-name>/`）；JUnit/集成测试可见，runner.jar E2E 不可见。

## 5. 导入（M1.2 占位）

分层导入器（base 业务键对账 + payload 剥 PK 重映射 + 引用重写 + requires 校验）由 roadmap M1.2 交付；本节届时扩写为导入 API 与事务边界文档。
