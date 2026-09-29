# 资源泄漏配对 v1 已知命中集对照记录（roadmap item 7 / gap-ledger G2）

> 日期: 2026-09-29 · 性质: HC3 证据记录——SpotBugs fixture 对照（主腿）+ 全语料腿 + plan 23 案例集归属 + 已知限制
> 语料: fixture（9 形态 ResourceToy，v61）+ nop-jq `target/classes`（97 classes / 761 methods，资源面为零——grep 证实 0 处 close/0 个自定义 Closeable）
> 分析口径: 资源义务分析 v1（两类注册表：Closeable NEW + JDBC 工厂 acquire；锁归 follow-up；所有权转移豁免；DEF 丢边守卫豁免）
> 重估触发: item 8 并行对照期扩展语料；SpotBugs 版本升级；白名单/注册表扩充

## 一、SpotBugs fixture 对照（主腿——有信息量的对照）

- 命令（standalone 只读，产出落 `_tmp/`，仓库接线零改动——HC1 相容）:
  `java -cp "<spotbugs-4.9.8 闭包>/*" edu.umd.cs.findbugs.FindBugs2 -low -xml:withMessages -output <out>.xml <fixture-classes>`
- 结果（`fixture-sb.xml` 在档）:

| 形态 | 本通道 | SpotBugs OBL 族 | SpotBugs OS 族 |
|---|---|---|---|
| unclosed（return 路径泄漏） | 命中 | **命中** | **命中** |
| conditionalLeak（路径敏感） | 命中 | **命中** | **命中** |
| declarativeTwr | 豁免 | 干净 | 干净 |
| existingVarTwr（Java 9 守卫形态） | 豁免 | 干净 | 干净 |
| guardedFinallyClose | 豁免 | 干净 | 干净 |
| aliasClose | 豁免（全帧扫描解除） | 干净 | 干净 |
| fieldTransfer | 豁免（所有权转移） | 干净（URF 报的是未读字段——异缺陷面） | 干净 |
| ownershipReturn | 豁免（所有权转移） | 干净 | 干净 |
| whitelistWrapper | 豁免（白名单） | 干净（DM_DEFAULT_ENCODING 报默认编码——异缺陷面） | 干净 |

- **delta 裁定: 零 diff（限本九方法语料）**——泄漏判定面（2 真阳性 + 豁免形态）与 SpotBugs OBL/OS 族完全一致（OBL 命中 unclosed+conditionalLeak，豁免形态干净）；SpotBugs 额外产出的 THROWS_CLAUSE/URF/DM_DEFAULT_ENCODING 属异缺陷面（范围锚外），非资源泄漏面分歧。
- **closure audit 修复后扩充**（ARETURN 残留扫描 + 白名单接线 + JDBC 工厂）: leak-then-ARETURN 残留扫描、两步白名单（fr 豁免 + br 需自身 close）、JDBC 工厂 acquire 泄漏/转移四新形态已入 fixture 锁定——SpotBugs fixture 对照未覆盖此四个新形态（可后续补跑），已知语义推演见测试断言。
- 附注: SpotBugs 的 OBL/OS 在全量语料上以**漏报倾向**著称（保守分配模拟）；本通道在其零触发形态上（existing-var TWR/守卫 finally）与其豁免判定一致，且两通道的真阳性集合相同。

## 二、SpotBugs 全语料腿

- nop-jq 重跑: 6 findings（SF_SWITCH_FALLTHROUGH / UPM / EQ / IC），**OBL/OS 族零命中**——与 grep 证实的"语料资源面为零"一致；本通道同跑资源命中 = **0**。两通道在零资源面语料上同为空集（平凡一致，证据力弱，如实记录）。

## 三、plan 23 案例集归属（源码 lane closeable-not-closed 保守面 vs 本通道路径敏感面）

- 源码 lane v1 = Option B 保守面：声明类型后缀判定 + 三豁免（TWR/finally/转移），warning 档，`ResourceSuffix` 形态集（Stream/Reader/Writer/Channel/Connection/Statement/ResultSet/Session 后缀）。
- 归属裁定: 两通道**同缺陷面、异判定机制**——源码 lane 报"声明类型 + 无 close 文本"（pattern+scope），本通道报"路径敏感义务残留"（字节码义务分析）。路径敏感差异场景（条件泄漏：close 仅在分支上）——源码 lane Option B 会报（无 close 文本），本通道报（分支残留）——同位双报，按 gap-ledger §三归 Wave 4 item 8 对照裁决。
- plan 23 Option A（L3 路径敏感配对分析器）Deferred 面由本通道承接——源码 lane 引擎侧无需重启该面（gap-ledger G2 触发条款）。

## 四、已知限制（FN/FP 面）

**FN 面**: ①隐式异常传播退出（无 handler 的调用抛出——CFG 无退出指令）不报；②非名单 acquire 形态（自定义 Closeable 实现、工厂方法不在名单）不追踪；③store-over（ASTORE 覆盖旧引用）丢 token；④链式 acquire（`new F(x).read()` 中间 token 未绑局部——经 handler/出口的残留不报）；⑤非白名单包装器构造器实参消耗 token。

**FP 面**: 普通方法实参逃逸（token 传入非豁免方法不解除——plan 23 audit 同裁定）→ 方向为 **FP**（把可能已关闭的引用继续报开）。

**范围声明**: 注册表 v1 = 两类（Lock 归 follow-up——需 lock/unlock 独立配对规格）；类型解析 = 名单制保守面（全量层次推导归 follow-up）。

## 五、复现命令

```bash
# 本通道
./mvnw test-compile -pl nop-bytecode
java -cp "nop-bytecode/target/classes:$(cat nop-bytecode/target/test-classpath.txt 2>/dev/null || echo test-cp-missing)" \
  io.nop.bytecode.cli.NopBytecodeMain <fixture-classes> --json
# SpotBugs fixture 腿
java -cp "<_tmp/nop-bytecode-poc/poc-spotbugs-standalone/libs>/*" edu.umd.cs.findbugs.FindBugs2 \
  -low -xml:withMessages -output /tmp/resource-fixture-sb.xml <fixture-classes>
```
原始数据: `_tmp/nop-bytecode-resource-compare/`（fixture 源码/编译产物/spotbugs xml/log）。
