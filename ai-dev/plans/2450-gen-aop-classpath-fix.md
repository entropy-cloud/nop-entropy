# 2450 gen-aop 代理编译 classpath 修复（根治 chronic 家族）

> Plan Status: active
> Last Reviewed: 2026-10-13
> Source: 用户质询"gen-aop 怎么会有问题，仔细研究一下执行原理"触发的在位探针实证（2026-10-13，本 plan Current Baseline）
> Related: jdk25-springboot4-quarkus4-roadmap.md 的 chronic gen-aop 家族登记（WI10-WI14 各 Gate 容忍面）

## Purpose

根治构建期的 chronic `gen-aop-proxy-fail` 家族（{job-dao, tcc-dao, sys-dao, retry-engine}）：代理补编译拿到的 javac classpath 是**错的**（Maven wrapper jar 一个文件），改为从执行环境（TCCL 类加载器链）收集真实 classpath，使任何模块的 AOP 代理在 clean 构建下都能编译产出。

## Current Baseline

（全部为 2026-10-13 在位探针/命令实证）

- **执行原理**（用户描述正确）：exec-maven-plugin `aop` 执行（compile 阶段，进程内 `exec:java`，mainClass=CodeGenTask 分发到 GenAopProxy）→ 扫描 target/classes 下带注册 AOP 注解的类 → 生成 `Xxx__aop.java` 代理源码落盘 target/generated-sources → 用 `JdkJavaCompiler` 内存编译出代理 class。
- **断点实证**：`JdkJavaCompiler.getDefaultClassPaths()` = `System.getProperty("java.class.path")`，而 exec:java 进程内该属性 = **仅 `.mvn/wrapper/maven-wrapper.jar` 一个文件**（`help:evaluate -Dexpression=java.class.path` 实证）。javac `-classpath` 解析不了任何项目类 → 代理源码里的 `io.nop.core.reflect.aop.*`/项目内类型全部"找不到符号"→ `ERR_GEN_AOP_PROXY_FAIL`。
- **环境实证（在位探针，临时补丁已还原）**：exec:java 运行期 TCCL = 普通 `java.net.URLClassLoader`，`getURLs()` 含 target/classes、src/main/resources、target/test-classes 与全部依赖 jar（含 nop-core/nop-commons 等）——**真实 classpath 就挂在 TCCL 上**，只是从未传给 javac。
- **flakiness 机制**：`GenAopProxy.saveCode` 按内容去重——失败构建已把代理源码落盘，后续构建内容相同 → 跳过编译 → aop "成功"但代理 class 从未产出（实证：nop-tcc-dao target/classes 零 Proxy class）。`clean` 构建 wipe 掉已落盘源码 → 必然重新编译 → 必然失败。 chronic 家族"时有时无"由此而来。
- **影响面**：`getDefaultClassPaths` 生产调用方仅 GenAopProxy 一个；其余 30+ 处全部是测试（surefire 下 TCCL 为 AppClassLoader，非 URLClassLoader，修复的收集逻辑自然回退到现行为 java.class.path = 完整测试 classpath，测试语义零变化）。
- 修复落点 `nop-kernel/nop-javac`（受保护核心区，本 plan 即 plan-first 载体；探针已给出理解生成链路所需的全部证据）。
- **近因考古（审查 M1 实证）**：原始 getDefaultClassPaths 本就查 `JdkJavaCompiler.class.getClassLoader() instanceof URLClassLoader` 并返回 realm getURLs——commit b8157644a6（plan 2349，2026-10-09）以"JDK 9+ 应用类加载器永不是 URLClassLoader……实测始终走 java.class.path 回退"为由删除。该实测只在 surefire 上下文成立（AppClassLoader 非 URLClassLoader）；exec:java 下定义类加载器是 ClassRealm（URLClassLoader 子类）分支恒真——**删除是 chronic 硬失败的近因**。本 plan 显式推翻该裁定并重写其遗留注释。

## Goals

- `getDefaultClassPaths()` 改为：遍历 TCCL 父链，收集所有 `URLClassLoader`（含 ClassRealm 子类）的 URL 转**存在的**文件路径并去重；收集结果为空或全部不存在时回退现行为（java.class.path）。顺序保持"TCCL 优先"。
- 新增单元测试：TCCL 为自定义 URLClassLoader 时返回其 URL 路径；TCCL 为非 URLClassLoader 时回退 java.class.path。
- 四个 chronic 模块 clean install 全过、代理 class 真实产出；全量 Gate A 不再需要 gen-aop 容忍面。

## Non-Goals

- 不做 forked-compile 预案（本修复后进程内编译即可靠，预案作废）。
- 不改 saveCode 去重语义（class 修复后去重恢复其正当的增量意义）。
- 不动 createFileManager/ClassLoaderImpl（javac 经 getClassLoader 的符号解析机制与本修复正交）。
- 不处理 IChatService IoC 双候选（独立诊断已闭环，归 nop-ai 栈）。

## Rulings

1. **修 getDefaultClassPaths 而非 GenAopProxy 调用处**：生产唯一调用方即 GenAopProxy，但该方法名即"默认 classpath"语义，在方法内修对环境探测是对所有未来进程内编译使用方的正确修复；测试调用方回退语义不变。
2. **URL 存在性过滤**：realm URL 中可能含目录（src/main/resources/）与不存在路径，仅保留 `new File(url.toURI())` 存在者；目录与 jar 都是 javac 合法 classpath 项，一律保留。单条 URL 转换异常（畸形/非层级 URL 如 jrt:）跳过并 warn，不中断收集。
3. **回退条件**：收集列表为空时回退 java.class.path（surefire/IDE 场景）；非空时**仍拼接** java.class.path 条目（防御性，exec:java 下 wrapper.jar 混入无害）。
4. **推翻 b8157644a6 裁定并区分新旧实现**：旧实现只查 JdkJavaCompiler 的定义类加载器（realm，缺 target/classes）且返回原始 url.toString()（buildOptions 的 Windows `file:/` 剥离 hack 由此而来）；新实现走 TCCL 全父链（exec:java 下 TCCL=realm 且含 output）+ File 解码路径，严格更优。ClassRealm extends URLClassLoader（plexus-classworlds 2.x），直接 instanceof 即可，无需反射。

## Execution Plan

### Phase 1 - 修复与验证

Status: completed
Targets: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/JdkJavaCompiler.java` + 新测试

- Item Types: `Fix`（live build 缺陷根治）

- [x] `getDefaultClassPaths()` 重写：TCCL 父链 URL 收集（URLClassLoader instanceof 即可，ClassRealm 是其子类）→ toURI→File 存在性过滤（单条异常跳过+warn）→ LinkedHashSet 去重 → 空则回退 java.class.path，非空则追加 java.class.path 条目
- [x] 删除/重写 `JdkJavaCompiler.java:49-51` 的过时注释（b8157644a6 遗留，与新实现自相矛盾）
- [x] 新增 `TestDefaultClassPaths`：TCCL=自定义 URLClassLoader 断言收集（含**文件 URL 与目录 URL** 两用例——目录是 exec:java 最常见形态）；TCCL=非 URLClassLoader 断言回退；TCCL 恢复（finally）
- [x] **前置**：`./mvnw install -pl nop-kernel/nop-javac -DskipTests`（exec:java 的 JdkJavaCompiler 由**插件依赖链**从本地 repo 加载，不经 reactor 解析——B1 实证；必须先装新 jar）
- [x] 验证：`./mvnw clean install -pl nop-kernel/nop-javac,nop-tcc/nop-tcc-dao,nop-job/nop-job-dao,nop-sys/nop-sys-dao,nop-retry/nop-retry-engine -DskipTests` 全 SUCCESS 且 target/classes 出现 `*__aop.class`
- [x] 验证：`./mvnw test -pl nop-kernel/nop-javac -am`（既有 javac 测试面零回归）

Exit Criteria:

- [x] 四模块 clean 构建代理 class 真实产出（ls 实证）
- [x] nop-javac 既有测试全绿 + 新测试绿
- [x] No owner-doc update required（构建工具内 Behavior 修复，无平台契约变化）
- [x] No new test required 不适用——新测试已列（Rule 25）
- [x] `ai-dev/logs/2026/10-13.md` 条目

### Phase 2 - 全量门与收口

Status: planned
Targets: 全仓构建、log

- Item Types: `Proof`

- [ ] Gate A：`./mvnw clean install -DskipTests -T 1C -fae` 全 SUCCESS（**无 gen-aop 容忍面**——本计划的验收标准就是容忍面清零；下游 service/app 模块首次完整编译）
- [ ] **Gate A 暴露面修复（执行期新增）**：nop-metadata-app 首次真正编译暴露自身配置缺陷（classpath 同存 h2+mysql JDBC 扩展未设 `quarkus.datasource.db-kind`）——application.yaml 补 `db-kind: h2`（与活跃 h2 配置一致；mysql 为注释的生产切换项）
- [ ] 白名单提交（nop-javac 主类+新测试、log、plan）
- [ ] 独立子 agent closure audit
- [ ] 三门：check-plan-checklist --strict / scan-hollow-implementations --module nop-kernel/nop-javac --severity high / check-doc-links --strict
- [ ] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [ ] Gate A 全 SUCCESS；commit hash 记录 Closure；审计证据写入；三门留痕

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（chronic gen-aop 家族 = 本 plan defect 面）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（javac classpath = 执行环境真实 classpath）
- [ ] 行为/契约结果已达成：四模块 clean 构建代理产出 + Gate A 全 SUCCESS
- [ ] 必要 focused verification 已完成（Phase 1 验证）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步（No owner-doc update required 已裁定）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：修复在真实失败现场验证（四模块 clean 构建），非单测假绿
- [ ] Gate A 全 SUCCESS（容忍面清零）
- [ ] Gate B：nop-javac 测试面全绿
- [ ] 代码规范：注释风格与仓内惯例一致

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- forked-compile 预案正式作废（本修复使其无必要）：roadmap chronic 登记处闭环注记。

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- 见 Non-Blocking Follow-ups
