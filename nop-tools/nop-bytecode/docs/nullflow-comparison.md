# null-flow v1 已知命中集对照记录（roadmap item 6 / gap-ledger G1）

> 日期: 2026-09-29 · 性质: HC3 证据记录——SpotBugs 同语料对照 + plan 24 案例集裁定 + 误报控制数据
> 语料: nop-jq `target/classes`（97 classes / 761 concrete methods，v61）
> 分析口径: null-flow v1 正式口径（IFNULL/IFNONNULL + ACMP-null 精化 + requireNonNull 门控 + 断言恒启用建模）
> 重估触发: item 8 并行对照期扩展语料；SpotBugs 版本升级；本通道口径变更

## 一、SpotBugs 同语料对照

- 命令（只读，既有 qa 接线零改动）: `./mvnw -pl nop-jq -Pqa spotbugs:spotbugs`
- SpotBugs 4.9.8.3 结果: **6 findings（SF_SWITCH_FALLTHROUGH×4 / UPM×2 / IC / EQ —— NP 族零命中）**，原始 `nop-jq/target/spotbugsXml.xml` + `_tmp/nop-bytecode-nullflow-compare/spotbugs-run.log`
- 本通道结果: `nullflow/may-null-deref` **2791 findings**（`--json` 原始输出同目录）
- **delta 裁定**: SpotBugs 在本语料的 null-deref 对照基线 = **空集** → 不存在重复/误报对冲条目；2791 条全部为**本通道互补面**（方法内路径敏感判空，SpotBugs 在该语料零 NP 触发）。互补条目的真伪定性见 §三分层抽检。
- 命令均可复现；重跑不依赖本记录的历史数据。

## 二、plan 24 案例集裁定（源码 lane pattern 面 5 条过本通道）

fixture: `_tmp/nop-bytecode-nullflow-compare/pattern-cases/`（5 形态源码 + 编译产物 + CLI 输出在档）

| 源码 lane pattern 规则 | 形态 | 本通道命中 | 归属裁定 |
|---|---|---|---|
| throw-null | 显式 `throw new NullPointerException()` | 否（无解引用） | **源码 lane 单报** |
| equals-null | 无守卫 `s.equals("x")` | **是**（recv MAYNULL） | **双报面**——平凡路径子集重叠，按 gap-ledger §三归 Wave 4 item 8 对照裁决 |
| no-throw-npe | `if (s.isEmpty()) throw new NPE(...)` | 是（`s.isEmpty()` recv MAYNULL） | **两通道异缺陷面单报**（源码 lane 报异常类型选择，本通道报解引用路径）——非同位双报 |
| catch-npe | try 内解引用 + catch NPE | 是（解引用在 try 内） | **本通道已知误报**——catch-NPE 即空值处理路径，豁免面缺口（见 §三 FP 声明）；源码 lane catch-npe 规则覆盖该面 |
| no-return-null | 返回 null | 否（无解引用） | **源码 lane 单报** |

## 三、误报控制数据

**豁免计数**（分析器统计接口，761 方法实跑，`exemption-counters.txt` 在档）:
- 守卫分支精化（IFNULL/IFNONNULL provenance 精化）: **86 次**
- `Objects.requireNonNull` 语义门控: **4 次**
- ACMP-null 精化: **0 次**（本语料 3 处 ACMP 全为 enum 比较——正式口径新增面在本语料零触发，其验证来自 javac fixture `ExemptionFaceTest`）

**分层抽检**（2791 条：field 5 / array 15 / method 2771；各类抽 3 条，逐条 source-line 定性）:

| 类别 | 样本 | 定性 | 依据 |
|---|---|---|---|
| field | JqArray.equals `items` | **FP** | `private final` + 构造器初始化非空（JqValues.java:87-96）；字段载荷保守 MAYNULL（无构造器非空推导） |
| field | JqBoolean.equals `value` | **FP** | 同 face（final 字段） |
| field | JqExecutor$1.visitBind `destructurer` | **FP** | 字段初始化器直连 new（JqExecutor.java:21） |
| array | JqParser$1.`<clinit>` [array] | **FP** | 合成 switch-map 数组：clinit 内新建并写穿（静态 final 保守面） |
| array | JqParser.checkAny [array] | **FP** | varargs 参数（Java 调用语法下编译器保证分配） |
| array | JqBuiltins.dispatchPlain [array] | **推定 FP**（同 varargs/静态族，未逐行） | face 一致 |
| method | JqDirectQuery.apply `executor.execute` | **FP** | `private final JqExecutor executor = new JqExecutor()`（JqDirectQuery.java:17,22） |
| method | JqEngine.compile `cache.get` | **FP** | 静态 final cache 字段（GETSTATIC 保守面） |
| method | JqLexer.keywordType `String.hashCode` | **TP**（保守参数面） | switch(ident) 的隐式 hashCode：传 null 即 NPE；本仓调用点不传 null 但签名可空 |

**FP 面声明**（v1 已知误报方向）: ①字段/静态字段载荷保守 MAYNULL（无构造器/clinit 非空推导）——主导 FP 面；②varargs 参数保守 MAYNULL；③catch-NPE 控制流未建模豁免；④参数保守 MAYNULL（签名级真、上下文可安全）。控制面现状 = 守卫/门控豁免（86+4 次生效）+ 降配保守语义；后续 FP 收敛（字段非空推导 / NPE-catch 豁免）为 Wave 2 后续优化项，以本记录为对照基线。

**ACMP 口径注记**: 本对照语料 ACMP-null 零命中——对照数据不覆盖 ACMP 精化效果；该口径的验证 = `ExemptionFaceTest` 两组用例：`null == x`（NULL 侧真阳性 + NONNULL 侧守卫）与**双极性 × 三构型锁定**（IF_ACMPEQ/IF_ACMPNE × local0/local 非 0/实例方法共 6 形态全豁免——closure audit Blocker 1/2 修复后的回归锁定）。

## 四、结论

- SpotBugs 基线空集 → M2a "已知命中集对照在档" 以【SpotBugs 空集 + 本通道互补面 2791 条 + 分层抽检定性】+【plan 24 案例集 5 条逐条裁定】构成。
- 已知误报面 4 类显式声明（FP 方向可解释、可收敛）；豁免面生效数据在档。

## 六、item 8 双跑收敛回填（2026-09-29）

equals-null 双报面（247 条 equals 接收者子集）裁决 = **保留双报（命名空间隔离）**，豁免会连带丢失参数 may-null 真阳性；重估触发 = 源码 lane equals-null 语义精化或通道 FP 收敛。收敛报告: [dual-run-convergence.md](dual-run-convergence.md)。
