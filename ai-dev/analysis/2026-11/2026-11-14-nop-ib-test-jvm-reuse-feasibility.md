# nop-ib 测试 JVM/ClassLoader 复用可行性分析

> Status: resolved
> Date: 2026-11-14
> Scope: nop-tools/nop-ib（JUnit fork 执行器）× 全仓 294 个测试模块
> Conclusion: 采用"持久 worker JVM + 可插拔重置 SPI"模型——每模块 W 个 worker（进程复用，不再每类一个 fork），类间通过 System.properties 快照恢复 + `CoreInitialization.reinitialize()` 等 SPI 钩子重置；ClassLoader 本身不新建（复用 worker 的 AppClassLoader），per-class fork 降级为污染兜底手段。设计落 `ai-dev/design/nop-incremental-build/06-junit-worker-reuse-and-generalization-design.md`。

## Context

- 现状（per-class fork）：每类一个新 JVM。JVM 启动+类装载 ~0.4-0.6s，全仓 5168 个测试类 ≈ 34 分钟纯启动 CPU（÷15 并行 ≈ 2.3 分钟墙钟），对中小模块占比过半（nop-javac 2.4s 中约一半是 JVM 启动）。
- 用户提议：复用 ClassLoader，类间调用重置接口（如 `CoreInitialization.destroy()`），恢复 System.properties。
- 约束：零假绿不可放松；02-test-execution.md 的 cwd/classpath/快照/超时契约保持。

## Analysis

### 事实核查（live repo）

1. **root pom surefire 配置：`forkCount=4` + `reuseForks=true` + `parallel=classes`**（pom.xml:201-208）——即今天的 CI 里，每个模块的全部测试类就在 **同一个复用 JVM** 中顺序/并发执行。复用 JVM + 类间不新建 ClassLoader 是生产验证过的既有模型，不是新发明。
2. **`CoreInitialization.reinitialize()` 原生存在**（CoreInitialization.java:208，synchronized，支持 fromLevel）——Nop 平台自带"销毁+重初始化"的官方入口；全仓测试源码大量自调 `CoreInitialization.destroy()`（抽样 nop-kernel/nop-auth/nop-orm/nop-biz 122 处），即测试类普遍在自身 setup/teardown 里做 init/destroy——这意味着**类间复用同一 JVM 的兼容性已经被 surefire reuseForks 生产验证**。
3. **快照开关静态缓存**（设计 02 §5.1）：`nop.autotest.force-save-output`/`disable-snapshot` 首次访问即静态缓存，System.properties 单向不可撤销 → 复用时必须在类间做属性快照恢复，且不能依赖"设回 false"清除已缓存的 map——重置接口必须显式覆盖这两个开关为 false（禁用录制+禁用禁用校验）。
4. **child-first 隔离 ClassLoader 被禁止**（设计 02 §四）：Nop 测试基类按 class 文件位置反推目录并断言 `target`，宿主 loader 必须持有测试类；child-first per-class loader 会让资源定位返回空。→ **per-class 新建 ClassLoader 方案对本仓不可行**。

### 方案比较

| 方案 | 思路 | 优点 | 缺点 | 判定 |
|---|---|---|---|---|
| A. per-class fork（现状） | 每类新 JVM | 隔离最强，零污染 | 启动开销大；树删除类级长尾放大 | 保留为兜底 |
| B. 持久 worker + 重置 SPI | W 个常驻 worker JVM（W=核数-1），每个顺序消费类队列；类间执行重置钩子 | 消掉 5168 次 JVM 启动；与 surefire reuseForks 同构（生产已验证） | 跨类污染风险 → 需 SPI+差分重跑兜底 | **采用** |
| C. per-class 新建 URLClassLoader | 同 JVM 内换 loader | 理论上隔离更强 | 违反 02 §四（child-first 禁令）；loader 泄漏/元空间压力 | 否决 |
| D. 委托 surefire（阶段一） | 零自研 | 保真 | 骨架成本；无法纳入 nop-ib 反馈契约 | 已被阶段二取代 |

### 重置需要覆盖的状态面（复用的全部风险清单）

1. **System.properties**：类前快照、类后恢复（通用层，所有项目适用）；
2. **Nop 平台全局生命周期**：`CoreInitialization.reinitialize()`（Nop 专属钩子）；`BeanContainer` 全局注册随其销毁清理；
3. **快照开关**：显式置 `force-save-output=false` + `disable-snapshot=false`（防"静默禁用校验"假绿）；
4. **静态缓存**：不可枚举的第三方静态缓存 → 由 worker 轮换（每 N 类强制换新 JVM）+ 差分重跑兜底，不做穷举。

### 污染兜底（零假绿如何保住）

- **差分重跑**：worker 内失败的类自动在全新 fork 重跑一次。新鲜 fork 通过 → 判定污染（重置不足），以 fresh 结果为准并登记"重置不足"告警；fresh 仍失败 → 真实失败。
- **worker 轮换**：每 worker 执行 ≤K 类（默认 20）强制退役；重置钩子抛异常 → worker 立即退役。
- **默认隔离等价性**：与 surefire reuseForks 完全同构——surefire 下今天能绿的模块，同模型下没有理由变红；对照门复用 IB-G3 方法逐模块双跑。

### 性能账（最大化性能的参数）

- JVM 启动次数：5168 → 294×W÷并行 ≈ 数百次，启动 CPU 从 ~34 分钟降到 <1 分钟；
- 类间开销：属性快照恢复 + reinitialize（Nop 模块实测 reinitialize 为秒级以下? 待 E2E 实测，若 >1s 则该模块自动降级回 per-class fork——重置成本大于启动成本时复用反而不划算，判据写进执行器）；
- 队列模型：parent 按完成度拉式派发类名（worker stdin 协议），负载自动均衡；慢类不阻塞其他 worker。

## Conclusion

- 采用方案 B，ClassLoader 不新建（复用 worker AppClassLoader，方案 C 否决）。
- 重置接口设计为**可插拔 SPI**（`TestEnvironmentResetter`，由被测项目提供实现，ServiceLoader/配置发现），使 nop-ib 的执行模型推广到任何 Maven+JUnit Platform 项目：非 Nop 项目默认只有"System.properties 快照恢复"通用层，Nop 项目叠加 `reinitialize()` 钩子。
- 设计文档：`ai-dev/design/nop-incremental-build/06-junit-worker-reuse-and-generalization-design.md`；执行 plan 后续建。
- 被否决：方案 C（原因如上）；"穷举重置所有静态缓存"（不可枚举，用轮换+差分重跑替代）。

## Open Questions

- [ ] `reinitialize()` 在重初始化耗时 >1s 的模块上，复用 vs per-class fork 的盈亏平衡点需要 E2E 实测（执行器应内置该判据自动降级）
- [ ] treesitter 高并发长尾类（网络 vendoring）在 worker 模型下的表现需对照验证

## References

- `ai-dev/design/nop-incremental-build/02-test-execution.md`（隔离/快照/超时契约）
- root pom surefire 配置（forkCount/reuseForks/parallel）
- `nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:158,208`
