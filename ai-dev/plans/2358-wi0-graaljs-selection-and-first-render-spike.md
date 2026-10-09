# 2358 WI0 GraalJS 选型裁定与裸环境首图 spike

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI0（M0 可行性与选型裁定）。编号 2358：master 已提交最大编号 2356，2357 为另一工作流未提交的进行中计划（stream WI11），为避免冲突顺延取 2358。
> Related: 后续 WI1–WI10 均消费本计划的裁定结果

## Purpose

收口 roadmap WI0：裁定 GraalJS Maven 坐标与版本（与 nop-xlang-truffle 钉定的 truffle 25.2.4 对齐取舍）、裁定 echarts 资源获取方式（Maven 依赖 vs dist 入库）、在 JVM 内以最小裸环境加载 dist/echarts.js 渲出第一张含中文 bar PNG，并完成中文字体可得性初验。产出：裁定报告（ai-dev/analysis/2026-10/）+ BOM 依赖坐标落位 + 可重复运行的 PoC 测试。

## Current Baseline

以下事实已于 2026-10-09 在 live repo（分支 echarts-server-render @ cad0c20976）逐一核验：

- nop-kernel/nop-dependencies/pom.xml 有 `graalvm.version=23.1.2` 属性（该属性仅被本文件内部引用，仓库内无其他 pom 消费；无活跃 graal 依赖条目，仅注释残留）。
- nop-kernel/nop-xlang-truffle/pom.xml 钉 `truffle.version=25.2.4`（org.graalvm.truffle:truffle-api / truffle-dsl-processor 同线）。
- nop-frontend-support/nop-js（被 nop-frontend-support/pom.xml 注释禁用、不在 reactor）以硬编码版本使用 org.graalvm.polyglot:js-community:23.1.2 + org.graalvm.polyglot:polyglot:23.1.2，不可作为依赖来源，仅作先例参考。
- Maven Central（2026-10-09 实测）：org.graalvm.polyglot:js:25.2.4 存在（packaging=pom），解析为 org.graalvm.js:js-language:25.2.4 + org.graalvm.truffle:truffle-runtime:25.2.4 —— truffle 与 nop-xlang-truffle 完全同线；org.graalvm.polyglot:js-community 坐标官方描述已标注 Deprecated（引导改用 org.graalvm.polyglot:js）。
- Maven Central：org.mvnpm:echarts:6.1.0 存在（jar 约 13MB），dist/echarts.js 位于 META-INF/resources/_static/echarts/6.1.0/dist/echarts.js，大小 3,847,293 字节，与 ~/sources/echarts/dist/echarts.js 字节大小一致；pom 声明 org.mvnpm:tslib、org.mvnpm:zrender 两个传递依赖（服务端只需要 bundled dist，需排除）。
- CI（.github/workflows/maven.yml、compliance.yml）运行 JDK 21；本地机器默认 JDK 26（Zulu 26.0.1），另有 Oracle JDK 25.0.1，无本地 JDK 21（验证时需临时下载 JDK 21 tarball）。
- 根 pom maven.compiler.release=17；仓库构建产物需运行于 JDK 21+。
- nop-format/nop-chart-echarts 为空壳 pom（parent=nop-format，依赖 nop-excel + junit，无源码、未列入本计划改动前的 BOM graal 条目）。
- jfreechart.version=1.5.6 在 nop-dependencies/pom.xml（WI6 职责，本计划不动）。
- ~/sources/echarts v6.1.0 dist/echarts.js 在位；roadmap Current Baseline 记录其已在 Node vm + Proxy 假 canvas 实测跑通全部图表类型（仅需 global/timer/console/setPlatformAPI/假 canvas 根，默认 createCanvas 返回 false 需注入，loadImage 需 defer，保 env.node → animation:false）。dist 源码核验：UMD 头使用 globalThis（无需注入 global/self）；环境探测不引用 process，裸环境自动走 env.node 分支；setPlatformAPI 默认 createCanvas 在无 document 时返回 false。
- **诚实声明**：上述实测证据全部来自 Node vm；仓库内没有任何 GraalJS 侧实测记录。GraalJS host-interop（Java 宿主对象被 zrender 写属性所需的 host access 形态、fake canvas 根的属性探测行为）与 zrender 实际使用的 ctx 方法面在 JVM 内未实测，Node vm 证据不直接迁移——Phase 2 以迭代方式收敛，plan 不预设一步成功。
- BOM 链路：根 pom → import nop-bom → import nop-dependencies；nop-bom 与 nop-dependencies 是独立 reactor 模块，不在 `-pl <module> -am` 的上游闭包内，改动后必须显式 install 才能被下游从本地仓库解析到。

## Goals

- 裁定 GraalJS Maven 坐标与版本，并与 nop-xlang-truffle 的 truffle 25.2.4 形成平台单一 truffle 线；裁定结果以 BOM 属性 + dependencyManagement 条目形式落入 nop-kernel/nop-dependencies/pom.xml。
- 裁定 echarts 资源获取方式（org.mvnpm:echarts:6.1.0 依赖并排除传递依赖 vs 提交 dist/echarts.js 入库），并给出理由。
- 交付可重复运行的 PoC：JVM 内 GraalJS 裸环境（仅注入 global/console/timer/setPlatformAPI + 最小 Java2D 假 canvas，**不定义** window/document/navigator）加载 dist/echarts.js，setOption 渲染含中文标签的 bar PNG。
- PoC 在 JDK 21（对齐 CI）、JDK 25、JDK 26（本地默认）三点运行通过，取得三点证据。
- 中文字体可得性初验：确认 JDK 默认逻辑字体在本地环境渲染中文的能力，并记录对 WI2 FontMapper 设计的输入约束。
- 裁定报告落 ai-dev/analysis/2026-10/，包含每个裁定的选项、结论、被拒选项与理由。

## Non-Goals

- 不创建 nop-canvas2d 模块，不实现生产级 Canvas2D shim（WI1–WI3）。
- 不实现生产引擎：Context 池、模块级错误码、OptionNormalizer、EChartsRenderer 统一入口、progressive 泵均为 WI4 职责。
- PoC 的假 canvas 是 throwaway spike 代码，仅存在于测试范围（test scope），不构成生产 API。
- 不动 xlsx 原生 OOXML 图表路径，不删 JFreeChart（WI6）。
- 不建 golden 基线与像素容差工具（WI5）。
- 不做字体确定性配置（FontMapper 属 WI2）；本计划只初验可得性。

## Scope

### In Scope

- nop-kernel/nop-dependencies/pom.xml：新增裁定后的 graal polyglot 属性与 dependencyManagement 条目（polyglot + js 两坐标），并删除陈旧 graal 残留（`graalvm.version`、`graalvm-js.version`、注释依赖块，见 Phase 2 执行项）。
- nop-format/nop-chart-echarts：test 范围新增 GraalJS 与 echarts 依赖（经 BOM），新增 PoC 测试类与最小假 canvas spike 代码（src/test 下）。
- ai-dev/analysis/2026-10/：裁定报告（含首图 PNG 证据文件）。
- ai-dev/backlog/echarts-server-render-roadmap.md：WI0 勾选（audit 通过后）。
- ai-dev/logs/：执行记录（预写为 2026/10-09.md；实际执行跨本地零点，条目落在 ai-dev/logs/2026/10-10.md，closure audit A4/B7 已按「对应日期」裁定满足）。

### Out Of Scope

- 任何 src/main 生产代码改动。
- nop-frontend-support/nop-js 的启用、改造或删除。
- echarts/zrender 源码的迁移、移植或 fork。
- CI 工作流文件改动。

## Execution Plan

### Phase 1 - 选型研究与裁定报告

Status: completed
Targets: ai-dev/analysis/2026-10/2026-10-09-echarts-graaljs-render-decision.md

- Item Types: `Decision`

- [x] GraalJS 坐标裁定：对比 org.graalvm.polyglot:js:25.2.4（truffle 25.2.4 同线）与 org.graalvm.polyglot:js-community:23.1.2（nop-js 先例、与 truffle 25.2.4 跨线冲突风险），核对 deprecated 标注（polyglot:js-community 已被官方标注 Deprecated，引导改用 polyglot:js）、传递依赖闭包、JDK 21/26 兼容声明，得出结论
- [x] echarts 资源裁定：对比 org.mvnpm:echarts:6.1.0 依赖（排除 tslib/zrender、versioned 资源路径）与 dist/echarts.js 3.8MB 入库（构建可复现性、git 体积、许可、升级路径），得出结论
- [x] 中文字体可得性初验：在本地 JDK 默认环境下用 Java2D 检查逻辑字体（SansSerif）与可用物理中文字体对中文的渲染能力，记录结果与 WI2 FontMapper 输入约束
- [x] 裁定报告写入 ai-dev/analysis/2026-10/2026-10-09-echarts-graaljs-render-decision.md：每个裁定含选项、结论、被拒选项与理由；Status: resolved

Exit Criteria:

- [x] 裁定报告存在于上述路径，三大裁定（GraalJS 坐标、echarts 资源、字体初验结论）各有结论与证据
- [x] 报告含被拒选项及否决理由（非空）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 完成时至少记录裁定概要一行；收口细节与 Phase 2 合并记录）

### Phase 2 - BOM 坐标落位与首图 PoC

Status: completed
Targets: nop-kernel/nop-dependencies/pom.xml, nop-format/nop-chart-echarts

- Item Types: `Proof`（BOM 条目为裁定落位，PoC 为可行性证明）

- [x] nop-dependencies：按 Phase 1 裁定新增版本属性与 dependencyManagement 条目（org.graalvm.polyglot:polyglot、org.graalvm.polyglot:js）；**同计划内删除陈旧 graal 残留**——`graalvm.version`（23.1.2）、`graalvm-js.version` 别名属性、被注释的 org.graalvm.js 依赖块，使 BOM 中 graal 坐标唯一指向裁定结果（本计划即 graal 选型 owner，残留属性与裁定结论并存会破坏"平台单一 truffle 线"目标）
- [x] BOM install 前置步骤：修改 nop-dependencies 后按序 `./mvnw install -pl nop-kernel/nop-dependencies` → `./mvnw install -pl nop-bom -DskipTests`（nop-bom import nop-dependencies），否则下游从本地仓库解析到旧 BOM
- [x] nop-chart-echarts：test 范围引入 polyglot + js + echarts（经 BOM 管理版本，echarts 排除 org.mvnpm:tslib 与 org.mvnpm:zrender）
- [x] PoC 测试：GraalJS Context 裸环境（不定义 window/document/navigator；global/console/timer 注入；setPlatformAPI 注入 createCanvas=false 覆盖、loadImage defer、measureText、getTime），从 classpath 加载 dist/echarts.js，setOption 含中文类目标签的 bar option，经最小 Java2D 假 canvas 输出 PNG。**Interop 风险处置指引**（GraalJS 侧无实测先例，允许迭代收敛）：(a) host access 需允许 zrender 对 Java 对象写属性（ctx.fillStyle 等），否则 PolyglotException——优先评估 allowAllAccess 或限定 mutable host access；(b) fake canvas 形态优先复刻已验证的 Node spike 形态：JS 层 Proxy 包 Java2D 后端，而非裸 Java 对象直传；(c) zrender 使用的 ctx 方法以运行时 PolyglotException 逐个补齐（roadmap 估约 30 个），bar 图只需其中子集。执行偏差：allowAllAccess 下 Java 对象属性写可用，但**属性读不映射 getter**（measureText .width 事件），数据形态值改用纯 JS 对象封装（详见裁定报告 Verification 小节）
- [x] PoC 测试断言：PNG 生成成功、尺寸正确、非空白（非白像素数 > 0）；测试把 PNG 写到 target/ 下，执行者拷贝至裁定报告目录作为附件并记录相对路径
- [x] 三点 JDK 验证：JDK 26（本地默认）、JDK 25（本机）、JDK 21（下载 macos aarch64 Zulu/Adoptium 21 tarball 至 _tmp/ 后以 JAVA_HOME 指定运行；下载 URL 与解压路径记录入报告）；`-pl ... -am` 在 JDK 21 点位会连带构建上游模块，耗时长属预期
- [x] 裁定报告追加验证小节：PoC 命令、三点 JDK 结果、PNG 路径

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 全绿（本地默认 JDK 下，全 reactor 17 模块 SUCCESS）
- [x] PoC 测试在 JDK 21/25/26 三点各自运行通过，证据（命令 + 结果摘要）写入裁定报告
- [x] **裸环境红线**：PoC 源码与经 JS 层注入的全局对象中无 window/document/navigator 定义（代码审查 + 测试内断言 globalThis 无这三个属性）
- [x] **无静默跳过**：echarts.js 加载失败、setOption 异常、PNG 为空白时测试显式失败（断言非白像素数 > 0）
- [x] BOM 管理来源验证：`./mvnw dependency:tree -pl nop-format/nop-chart-echarts`（在 BOM install 前置步骤之后运行）输出中 polyglot/js/echarts 均显示 BOM 裁定版本；nop-chart-echarts pom 不出现硬编码版本号
- [x] No owner-doc update required（本计划不改变平台使用契约；GraalJS 版本裁定属 roadmap WI4 前置决策，owner doc 在 WI10 收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 三大裁定（GraalJS 坐标、echarts 资源、字体初验）已写入裁定报告且结论明确
- [x] BOM 属性与 dependencyManagement 条目已落位且被 nop-chart-echarts 消费（无硬编码版本）
- [x] 首图 PoC（含中文 bar PNG）可重复运行，三点 JDK 证据齐全
- [x] 裸环境红线（无 window/document/navigator、不用 ssr:true）在 PoC 中成立
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required（理由见 Phase 2 exit criteria）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 PoC 端到端路径真实连通（classpath 加载 → JS 执行 → setOption → canvas 写入 → PNG 落盘），非空壳/非手工造图
- [x] `./mvnw test -pl nop-format/nop-chart-echarts -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-chart-echarts --severity high` 退出码 0（注：工具只扫 src/main，本计划仅改 src/test，检测力为零系空转通过；等效 Anti-Hollow 检测由 closure audit 人工完成）
- [x] checkstyle / 代码规范检查：imports 分组（io.nop.* → 第三方 → java.*）、4 空格缩进、错误消息 English（PoC 为测试范围代码，无生产错误码要求）；audit Minor-3 指出的 javax.imageio 位置已按 AGENTS.md 惯例修正（javax 组置于 java.* 前）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无。若三点 JDK 验证中某一点环境不可得，必须在裁定报告记录失败原因并回退为显式 scope 变更，不得静默降级。）

## Non-Blocking Follow-ups

- WI4 实现生产引擎时可复用或删除 PoC spike 测试代码（该裁决归 WI4 plan，不影响本计划 closure）。

## Closure

Status Note: 三大裁定（GraalJS polyglot:js 25.2.4 同线 truffle 25.2.4 / org.mvnpm:echarts:6.1.0 排除传递依赖 / 字体初验）已落报告；BOM 坐标落位并被消费（dependency:tree 实证全 25.2.4 单线）；首图 PoC 端到端可复现（三点 JDK 全绿，中文 bar PNG 像素断言具备证伪力）；裸环境红线成立。独立 closure audit APPROVE（0 Blocker / 0 Major / 3 Minor，Minor 均已在收口时修正）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（general-purpose）
- Audit Session: agent_45813832-675c-45f2-be0a-5cdcd670acdb
- Evidence:
  - Phase 1 Exit Criteria 4/4 PASS（报告三大裁定+被拒选项；check-doc-links 亲跑 0 错；日志在 ai-dev/logs/2026/10-10.md）
  - Phase 2 Exit Criteria 7/7 PASS（audit 亲跑 `-am` BUILD SUCCESS 17 模块；dependency:tree 亲跑核对 25.2.4/6.1.0 且模块 pom 无硬编码版本；裸环境红线代码级审查 + 运行时断言；无静默跳过逐方法核对 shim 全部显式抛错）
  - Closure Gates 12/12 PASS（其中 scan-hollow gate 空转通过已如实注记，等效 Anti-Hollow 由 audit 人工深查替代）
  - Anti-Hollow：端到端链路逐环验证（classpath 读 dist → eval shim/echarts/render → setPlatformAPI/init/setOption → zrender 经 shim 调 SpikeCanvasBackend → ImageIO 落盘 → 重解码断言）；SpikeCanvasBackend/SpikeGradient/SpikeHosts 无 stub；断言组（nonWhite>1000 ∧ barPixels>500 ∧ darkPixels>50 ∧ __spikeChartReady）对「JS 未执行/未写入/手工造图」均必红
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-chart-echarts --severity high` 退出码 0
  - Deferred 项分类检查：Deferred 区为空；Non-Blocking Follow-ups 仅 WI4 对 spike 代码的复用/删除裁决，无 in-scope live defect 降级
  - audit 发现的 3 条 Minor（模块计数 15→17、In-Scope 日志路径注记、javax import 顺序）已在收口时全部修正

Follow-up:

- WI4 实现生产引擎时可复用或删除 PoC spike 测试代码（裁决归 WI4 plan）。
