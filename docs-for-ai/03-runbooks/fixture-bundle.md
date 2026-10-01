# Fixture Bundle（测试夹具可移植包）Runbook

> 状态：**定稿**（2026-10-01，M1.1 导出侧 + M1.2 导入侧 + M1.3 三条验收用例落位——plan `nop-app-erp/docs/plans/2026-10-01-2049-1` / `2026-10-01-2142-1-m12-fixture-bundle-importer.md` / `2026-10-01-2255-1-m13-fixture-bundle-acceptance-runbook.md`；验收①③载体 = nop-autotest-core `TestFixtureBundleImport`，验收②载体 = nop-app-erp `TestErpFixtureBundleDirtyImport`）。
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
| `columnFingerprint` | 表条目 | 列名集排序摘要（SHA-256，两侧同源 `FixtureBundleImporter.fingerprint`）——导入前比对目标 EntityModel（fail-fast 缺省 / tolerant 告警）；缺省（旧 manifest）跳过并告警 |
| `includeLogicalDeleted` | 表条目 | 是否含逻辑删除行声明（导出按行集写入，缺省 false）——true 的包导入即拒（导入器永不重建已删行） |

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

## 5. 导入 API（M1.2）

```java
FixtureBundleImportResult result = new FixtureBundleImporter()
        .importBundle(bundleDir, ormTemplate);
// 可选：自定义 requires 解析器与漂移 tolerant 模式
new FixtureBundleImporter().importBundle(bundleDir, orm, requiresPredicate, /*tolerant*/ true);
new FixtureBundleValidator().validate(bundleDir);   // 导入前独立校验仍推荐
```

语义契约（M1.2，全部有验收测试承载）：

- **两趟事务**：base 趟（业务键对账）整趟单事务——目标已存在同业务键行则登记映射**不覆盖**，缺失行剥 PK 平台生成；payload 趟整趟单事务、失败整体回滚，base 失败不进入 payload 趟。
- **ID 重映射**：`oldId→newId` 内存映射贯穿两趟；payload to-one FK 列按映射重写；映射外且目标库不存在的引用值 → `nop.err.fixture-bundle.dangling-ref` 拒绝（直插不触发平台引用校验——导入器是唯一兜底，M0.1 Decision ⑤）。
- **装载序**：按 manifest `loadOrder`（导出时子集拓扑序）装载。
- **不可导入面**：系统表/序列表（`io.nop.sys.*`/`nop_sys_*` 形）入口即拒（`sys-table-forbidden`）；`includeLogicalDeleted=true` 的包拒（`logical-deleted-declared`——导入器永不重建已删行）；复合主键表拒（`composite-pk-payload`，base 与 payload 两趟同拒——两趟插入均剥 PK 依赖平台单列生成语义，复合主键无生成路径；真实需求出现时走结构变更审查）。
- **版本列不随包携带**：`VERSION`/`DEL_VERSION` 列值不读包值——非模型列在 `normalizeRow` 中剥离，模型自有列强制置 0（与平台缺省同向）。
- **requires**：缺失 → `requires-missing`；默认解析器 = bundle 同级目录下 `<name>/manifest.json5` 存在，可注入自定义谓词。
- **漂移校验**：manifest `columnFingerprint`（列名集排序摘要，两侧同源 `FixtureBundleImporter.fingerprint`）比对目标 `EntityModel`——默认 fail-fast（`schema-drift`），tolerant 模式登记告警继续；旧 manifest 无指纹字段则跳过并告警（前向兼容：`Manifest.read` 对未知字段做已知键过滤，`formatVersion` 保持 1）。
- **幂等口径**：base 对账天然幂等；同包重复业务键 → `uk-conflict` 拒绝（逐行对账会将重复键静默合并进第一行——执行期发现，包缺陷必须显式拒绝）；payload 幂等 = 同一 Importer 实例运行内跳过；**跨进程重复导入 payload 会产生重复行**——消费纪律：bundle 一次性导入 + 测试 fresh 库语义。并发窗口的 duplicate-key（flush at commit）仍被 wrap 为 `uk-conflict`。
- **序列对齐（M0.1 Decision E）**：框架不内置序列改写——payload 新 ID 由目标平台序列生成自然推进；结果报告 `maxNewIds` 供消费方取号无冲突断言；非单调序列（uuid/snowflake）以经验口径断言。
- **已知边界**：no-PK 实体不可导出故不在导入面；JDBC 直操构造的数据不经 hook 不可见（导出侧约束的导入侧回响）。
- **M1.3 执行期增补**：nop-sys 环境下 ID 取号会经 ORM 读 `NopSysSequence` 行——导出 hook 会收集到系统表行，导出器按「系统表不随包导入」**静默跳过**（不要求配置）；CSV 字符串值在 `normalizeRow` 按列 `stdDataType` 转换后装载（工厂级全局实体缓存下未转换实例会以 String 形态被读回）；验收①单调序列环境 = 测试 beans 显式装载（`nop.ioc.app-beans.files` 点分键 + `ioc:allow-override="true"`——文件名扫描仅认 `app*.beans.xml`，静默摆放不生效）。
