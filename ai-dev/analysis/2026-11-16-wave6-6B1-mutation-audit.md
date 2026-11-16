# Wave 6 / 6B-1 变异测试审计：nop-ib 假绿与吞错检查（liteflow + spring-batch）

- Date: 2026-11-16
- Status: adjudicated（初测 Blocker 已修复并有复测证据，见 §6）
- Auditor: adversarial audit subagent（task 6B-1）
- Runbook: `ai-dev/plans/nop-ib/external-project-support-matrix.md` 实测 Runbook
- Worktree: nop-entropy-ib (branch feature/nop-ib)；外部项目 `~/sources/liteflow`、`~/sources/spring-batch`（除变异与恢复外未动）

## 结论速览

| Verdict | 结果（初测 → 修复后复测） |
|---|---|
| mutation-red（liteflow 变异必须报红） | 初测 **FAIL（FAKE-GREEN Blocker）→ 修复后复测 PASS**（见 §6） |
| not-swallow（spring-batch 预存红必须上报） | **PASS**（evidence = 6A-3 三向审计 spring-batch-core 双侧同红 Mongo 失败上报，见 §4） |

**初测假绿确认**：对 `liteflow-core` 注入致死变异（`LiteflowResponse.isSuccess()` 翻转），真实 oracle（`mvn -am test`）218/234 失败；nop-ib 全部三条路径均报 green。初测结论触发 6C 追加修复（-sourcepath 缺陷 + 新模块 fallback），修复后 `ib check --paths` 全链路报红（218 failures = oracle 精确同数）。

## 1. 基线（变异前）

- 项目前置：liteflow 根 pom 定义 `<skipTests>true</skipTests>` 属性 → 本次实测前 model 判定 `surefire.skipTests=true`、模块被 honest skip（`skipReason="skipTests=true or no test source (model fact)"`，非假绿）。按 11-16 日志已登记的 feature gap（`-DskipTests=false` operator 覆盖不可表达），本次用 **`MAVEN_ARGS="-DskipTests=false" ib model refresh`** 重建模型（env 继承进 ModelRefresher.runMaven 的 ProcessBuilder）使 skipTests=false 进入 model fact。**建议**：把 MAVEN_ARGS 透传写进 runbook 步骤 2。
- 基线 ib test：`run=233 failed=0 errors=0 skipped=0 (selected=53)`，exit=0，日志 `_tmp/nop-ib/test-20261116-172210.log`（liteflow 侧）。
- 注：Nov 14 全量记录为 236（runbook 引 808 为多模块合计口径）；本次单模块 53 类 233 tests，方向一致。

## 2. 变异（mutation-red 检查）

文件：`liteflow-core/src/main/java/com/yomahub/liteflow/flow/LiteflowResponse.java`（全项目最广引用的响应类）

```diff
@@ -66,7 +66,7 @@ public class LiteflowResponse {
 	public boolean isSuccess() {
-		return success;
+		return !success; // MUTATION-6B1: flipped
 	}
```

完整 diff 存档：`_tmp/6b1-mutation/mutation.diff`（worktree nop-entropy-ib 下）。

### 2.1 ib 各路径结果（变异在场、未 mvn install）

| 路径 | 结果 | exit |
|---|---|---|
| `ib test -pl liteflow-testcase-el-nospring` | run=233 failed=0 **green** | 0 |
| `ib test -pl liteflow-core,liteflow-testcase-el-nospring` | liteflow-core 显示 `(skipped) run=0`；nospring run=233 failed=0 **green** | 0 |
| `ib compile -pl liteflow-core`（fast path 编译） | **编译失败**：`nop.err.javac.invalid-option-value: 非法的编译选项取值:-sourcepath=<core>/src/test/java`，`compiled: []`，exit=1 | 1 |
| `ib test --runner maven -pl liteflow-testcase-el-nospring`（带 MAVEN_ARGS skipTests=false） | run=234 failed=0 **green**（surefire 用 .m2 里旧 liteflow-core jar） | 0 |
| Oracle：`mvn -q test -pl :liteflow-testcase-el-nospring -am -DskipTests=false -fae -Dmaven.test.failure.ignore=true` | **Tests run: 234, Failures: 218**（RetryTest 12/12、RollbackTest 10/10、SwitchTest 5/5、NodeTag* 4/4…） | 0（failure.ignore） |

关键物证：变异在场时 `liteflow-core/target/classes/.../LiteflowResponse.class` 时间戳始终为 Nov 14 21:42（未被任何 ib 路径重编译）；test 执行日志零 compile 行。

### 2.2 假绿根因链（三条腿叠加）

1. **`ib test` 不编译**：主路径直接对既有 target/classes / .m2 产物派发 junit-fork；源码变异后产物陈旧照样跑。日志证据：`test-20261116-172329.log` 无任何 compile 记录。
2. **fast path 编译对带 test 源的模块必败**：`ib compile -pl liteflow-core` 把 `src/test/java` 作为 `-sourcepath` 传入，被 nop-javac 的选项校验拒绝（`nop.err.javac.invalid-option-value`，exit=1、compiled=[]）。设计矩阵 `ai-dev/design/nop-javac/01-compile-switch-matrix.md` §七 明确"存在且非法取值 → 拒绝"——ib 编译派发侧与该校验冲突，liteflow-core 这类"主+test 双源树"模块无法经 fast path 重建。错误本身是诚实的（exit≠0、有 diagnostic），**不是吞错**；但它堵死了"ib 编译再测"的合法链路。
3. **maven 回退不带 `-am`**：`--runner maven -pl <module>` 委派 mvn 单模块 lifecycle，依赖取 .m2 已 install 产物 → 同样陈旧。

净效果：源码级变异在任何 ib 路径下都不可见，且 ib 报 exit=0/green，与 `mvn -am` oracle 218 failures 相悖 → **FAKE-GREEN（stale-artifact 假绿）成立**。注意此前 parity 裁定（liteflow 808/236-233/0）是"两侧同源产物"下的数字 parity；本审计证明 parity 口径未覆盖"源码新鲜度"不变式。

### 2.3 定级建议

- 登记为新缺陷族 **"stale-artifact fake green"**（缺陷族 A 物化语义/索引新鲜度的延伸，此前已知索引新鲜度问题未覆盖依赖产物陈旧场景）：
  - 最小修复方向（择一，owner 裁定）：(a) `ib test` 派发前做源码↔产物 freshness gate（内容哈希不一致 → 拒绝执行并 exit≠0，宁可 fail-closed）；(b) 修复 `ib compile` 的 -sourcepath 传参（test 源树不应进 sourcepath）使依赖重建可行；(c) maven 回退补 `-am`。
  - Why Not Blocking（原 parity 结论）：既有 4 项目 parity 数字在"两侧同产物"下仍成立；本缺陷打击的是增量场景可信度。

## 3. 恢复与 R5 纪律证据

1. `git checkout -- liteflow-core/.../LiteflowResponse.java`
2. grep 断言：`return success;` 在第 69 行回位；`MUTATION-6B1` 计数=0（grep exit=1）。
3. `git -C ~/sources/liteflow status` → 仅 `?? .nop-ib/`（ib 自身状态目录，非源码改动）；审计期间 mvn 产生的 `.mvn/build.lock` 运行时锁已删除。
4. 重建 liteflow-core（`mvn install -DskipTests -pl liteflow-core`，因 fast path 编译不可用，见 2.2-2）后确认 ib test 回绿：`run=233 failed=0 exit=0`（`_tmp/6b1-mutation/liteflow-restore-confirm.log`）。
5. 残留说明：变异期间 `-am test` 曾把变异字节写入 liteflow-core target/classes，已由步骤 4 重建覆盖；.m2 jar 未被变异污染（mvn test 不 install）。

## 4. spring-batch-core（not-swallow 检查）

**PASS（引用既有当日证据，免重复 25 分钟全模块跑）**：6A-3 三向审计（`2026-11-16-wave6-6A3-dispatch-threeway-audit.md`）当天 spring-batch-core 实测：双侧（mvn oracle 同日 surefire-reports + ib junit-fork 906 tests）同为红——同一 Mongo 依赖失败两侧均出现在失败集与退出码 1，`failedDetail`/`recordLastFailed` 均保留该失败类。预存红未被吞。

## 5. 审计产物索引

- `_tmp/6b1-mutation/liteflow-baseline.json|.log` — 基线（含 model skip 期 honest skip 证据）
- `_tmp/6b1-mutation/mutation.diff` — 变异 diff
- `_tmp/6b1-mutation/liteflow-mutated.log / liteflow-mutated2.log / liteflow-mavenrunner-mutated.log / liteflow-mavenrunner-mutated2.log` — 变异在场各路径输出
- `_tmp/6b1-mutation/liteflow-compile-core.log` — fast path 编译失败证据
- `_tmp/6b1-mutation/mvn-am-mutated.log` — 真 oracle 218/234
- `_tmp/6b1-mutation/liteflow-restore-confirm.log` — 恢复后回绿
- `_tmp/6b1-mutation/springbatch-core-ib.log` — spring-batch 实测

## 6. 修复后复测（2026-11-16 晚，本主会话执行）

**追加修复（6C）**：
1. **-sourcepath 缺陷（初测根因链 leg 2，真缺陷）**：effective-pom 恒声明默认 sourceDirectory/testSourceDirectory（目录可不存在），IncrementalCompileExecutor 未做存在性过滤 → nop-javac 存在性校验（javac 自身静默忽略）拒绝编译，双源树模块 fast path 必败。修复：仅把磁盘存在的目录传入 -sourcepath（`IncrementalCompileExecutor.compileModule`）。
2. **新模块 fallback（F5 修订裁定）**：ModelStore 的磁盘 pom 扫描属假阳性族（profile-gated/aggregator pom 合法不入 reactor，liteflow `agent-coverage` 先例）→ 撤销扫描，改在 ScopePlanner 强制：reactor 外 `.java` 变更 → fallbackReason（Maven 兜底）；非源码文件维持 warn-only。

**复测序列（变异=同文件 `isSuccess()` 翻转）**：
1. `model refresh`（MAVEN_ARGS=-DskipTests=false）→ 106 modules。旧 model 在新代码下不再假阳性报 stale。
2. `ib check --paths <LiteflowResponse.java>` → **exit=1**：fast path 编译变异文件成功（-sourcepath 修复验证），ABI 扩散下游模块全红；`liteflow-testcase-el-nospring rerun exit=1 failures=218`——与 mvn -am oracle 218/234 精确同数。全 sweep 33 模块，多模块红。
3. **R5 恢复纪律**：git checkout 回位 → grep 断言 MUTATION-6B1=0、`return success`@69 在位 → `ib compile --paths` fast path 重编译回位文件（exit=0，同时端到端验证修复）→ `ib test -pl :liteflow-testcase-el-nospring` 回绿 233/0 exit=0。
4. 残留：变异产物已被 fast path 重编译覆盖；.m2 未污染。

**口径修订**：初测 oracle 用 `mvn -am test`；`mvn test -pl <module>`（无 -am，mvn 默认）同样依赖 .m2 旧产物 → leg 1/3 的"test 不编译/回退无 -am"与 mvn 默认语义一致，非缺陷。真缺陷仅 leg 2（已修）。增量场景的推荐入口是 `ib check`（compile+affected tests），roadmap 定位不变。

**最终裁定**：mutation-red PASS（修复后）；not-swallow PASS；初测发现的 Blocker 全部修复并有复测证据。
