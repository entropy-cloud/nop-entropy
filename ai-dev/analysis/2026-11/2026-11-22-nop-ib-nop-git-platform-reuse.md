# nop-ib / nop-git 复用 nop 平台 commons/core 能力调研与迁移方案

> Status: resolved（迁移范围已裁定并实施，见 §六）
> Date: 2026-11-22
> Scope: nop-tools/nop-ib、nop-tools/nop-git 对 nop-commons / nop-core 的手写实现复用
> 关联: ai-dev/bugs/2026-11/2026-11-21-nop-ib-single-module-project-family.md（本次调研的直接诱因）

## 一、背景与动机

2026-11-21 黑盒实测暴露的 nop-ib 单模块缺陷族中，两个直接根因落在**手写解析器**上：

- `EffectivePomParser`（541 行，手写 StAX + DOM 混合解析）——StAXSource 越过 END_DOCUMENT 的 JDK quirk 未被防御，平台 XNodeParser 不存在此问题面；
- `JsonOut`/`JsonIn`（435 行手写 JSON）——类注释自承"If a document ever needs richer types, migrate to the platform JSON stack as one step and delete this class"。

用户裁定：不限于解析，nop-ib 与 nop-git 凡能复用 nop 平台 commons/core 的实现都要复用。本报告盘点两侧的手写实现、逐一对照平台设施、裁定迁移范围并实施。

## 二、依赖现状与约束

| 模块 | 现有平台依赖 | 部署契约 |
|---|---|---|
| nop-ib | nop-javac（→ nop-api-core, nop-commons）、nop-bytecode | shade 自包含单 jar；通用 Maven 项目工具，非"零平台依赖"声明 |
| nop-git | JGit、snakeyaml、slf4j-nop（**零 nop 平台依赖**） | design 00 §五 / Wave 1 裁定：core/cli **独立部署、JDK 21 基线、零平台依赖**（AI 工具 PATH 覆盖场景，分发最小化） |

关键事实：

- nop-commons 依赖 nop-api-core，**已在 nop-ib 依赖闭包内**（免费复用）；
- XNode/XNodeParser/JsonTool 均在 **nop-core**；nop-core 依赖 nop-api-core/nop-commons/nop-dataset/nop-javac——nop-javac 已在闭包内，新增净增主要 nop-core + nop-dataset；
- XNodeParser.instance() 无需 IoC/CoreInitialization（MavenModelHelper 同款直用，nop-codegen 先例）；
- nop-git 的"零平台依赖"是**记录在案的架构裁定**（roadmap item + design 00 §五），覆盖它需要显式决策。

## 三、平台设施盘点（对照表）

| 手写实现 | 位置/规模 | 平台候选 | 裁定 |
|---|---|---|---|
| `EffectivePomParser` | ib model/，541 行 StAX+DOM | nop-core `XNodeParser` / `XNode.parse`（nop-codegen MavenModelHelper 同款用法） | **迁移** |
| `JsonOut`/`JsonIn` | ib json/，435 行手写 JSON | nop-core `JsonTool`（parse/parseMap/stringify） | **迁移** |
| `GlobMatcher` | ib tasks/，~120 行段式 glob（`*`/`?`/`**`，fail-loud 拒正则元字符，`**` 吞尾斜杠语义） | ~~初判保留~~ → **引擎下沉 nop-commons `SegmentGlobPathMatcher implements IPathMatcher`**（用户裁定：语义原样迁移、fail-loud 契约留在 ib 侧；语义差异相对 AntPathMatcher 保留在新类的 javadoc 钉中） | **已迁移**（语义零漂移，回归钉 TestSegmentGlobPathMatcherTest 7 例 + ib 203/0） |
| `FastHash`（XXH64） | ib util/，141 行 | nop-commons `HashHelper`（md5/sha1/sha256/murmur3_64_string——**字符串键** murmur，无字节流 XXH64 等价物） | **保留**（指纹只做 ib 自身状态对比，XXH64 字节流 + 16 位 hex 是实测性能裁定，见 11-21 日志；平台无等价物） |
| `ModelStore.sha256` | ib model/，MessageDigest 直用 | nop-commons `HashHelper.sha256` | 微（保留亦可，无行为差异；不强迁） |
| 仓根发现/路径 | ib engine `discoverRepoRoot`（mvnw 标记 + pom 链攀升） | nop-commons `MavenDirHelper`（guessProjectDir 按 classes 目录猜，**语义不同**）、`ProjectDirHelper` | **保留**（ib 的发现规则是服务契约，无平台等价物） |
| `ServeHttp`/`NopIbMain` HTTP | com.sun.net.httpserver，手写路由 | 平台无轻量等价（nop-web/graphql 过重且引入 IoC/VFS） | **保留** |
| `GitPolicyLoader` YAML | nop-git，snakeyaml 直用 | nop-commons `YamlHelper`（同样包装 snakeyaml——引入它 = 用一个平台依赖换掉一个直依赖，零净收益） | **保留** |
| `DefaultGitPolicyEngine` glob/路径归一 | nop-git，gitignore 语义 glob | AntPathMatcher（Ant 语义 ≠ gitignore 语义，实测踩坑记录见 Wave 3 日志） | **保留** |
| XNode→对象绑定 | （ib 无此需求，IbModule.toMap/fromMap 是自有序列化） | nop-core 变换器族 | 不适用 |

## 四、方案对比（主要迁移点）

### A. EffectivePomParser：StAX/DOM 手写 vs XNodeParser

- **现状风险面**：流式 transform 与文档形态强耦合（`<projects>` 包裹 vs 裸 project、尾随内容有无），2026-11-21 缺陷族两根因都在这个耦合面上；每 project DOM 物化 + TransformerFactory 实例化也是每模块开销。
- **XNode 方案**：`XNodeParser.instance().parseFromResource(...)` 一次成树，`childByTagName`/`attrText`/`childByLocalName` 导航；解析器自带 looseMode、深度上限、实体防御（ERR_XML_* 全家），平台全仓 xdef/xmeta 解析在同一代码路径上被持续打磨。
- **代价**：整文档成树（原实现整文档不成树、逐 project 物化 DOM）。量化：effective-pom dump 主仓量级 ~10MB XML / XNode 树数十 MB 峰值，refresh 本身是分钟级 Maven 子进程流程，树构建 <1s——可接受；单模块项目（本次缺陷族受害者）dump 仅 KB 级。
- **行为保持**：`hasProjectsWrapper()`（单模块形态识别）与空 basedir 推导等 11-21 修复语义必须原样保留，回归钉（TestEffectivePomParser 9 例 + Wave6 6 例）全量绿为验收门。

### B. JsonOut/JsonIn：手写 JSON vs JsonTool

- 手写版为最小栈自守（含未配对代理项替换逻辑），但 **435 行 == 435 行测试欠账**；JsonTool 是平台自身 JSON 权威栈（xdef/xmeta/beans 全走它），stringify/parseMap 与 ib 现有 Map<String,Object> 模型同构。
- 迁移后删除 io.nop.ib.json 整包；ModelStore/IbModel.toMap/fromMap 边界不变（仍旧 Map 进出），行为兼容风险集中在**数字/转义形态差异**——model.json 仅被 ib 自身读写（同版本），一次性重写无兼容负担。

### C. nop-git 是否引入 nop 平台依赖

- 引入 nop-commons（或为 XNode 引 nop-core）将打破 design 00 §五「core/cli 独立部署零平台依赖」与 JDK 21 最小分发契约；且盘点显示 nop-git 的手写点（gitignore glob、YAML、路径归一）**没有一个有语义等价的平台设施**——迁移收益趋零、分发体积与依赖面净增。
- 裁定：**nop-git 维持零平台依赖契约不变**（其"可复用集合"为空是盘点结论，非豁免）；若未来平台沉淀出 gitignore 语义 glob 或轻量 YAML 前端，再评估。

## 五、风险与控制

1. XNode/JsonTool 首次进入 nop-ib shade jar——需验证 XNodeParser/JsonTool **脱离 CoreInitialization 可独立工作**（p1/p2 端到端 + 全量回归覆盖）。
2. shade 体积增长（nop-core + nop-dataset 净增）：接受（仍自包含单 jar；nop-javac/nop-bytecode 本就在包内）。
3. 行为回归：TestEffectivePomParser/Wave6/ModelRefresherParse/TestJsonOut 等现有钉全量绿；p1（单模块）/p2（多模块）黑盒端到端复验。
4. AGENTS「避免 noisy refactor」：迁移严格限于 A/B 两点 + 必要 import 调整，不顺手改无关代码。

## 六、裁定结论（已实施）

| # | 动作 | 模块 |
|---|---|---|
| 1 | `EffectivePomParser` 重写为 XNode 实现，保留 11-21 修复语义与公共签名 | nop-ib |
| 2 | `JsonOut`/`JsonIn` 删除，全调用点迁 `JsonTool` | nop-ib |
| 3 | pom 新增 nop-core 依赖 | nop-ib |
| 4 | FastHash/discoverRepoRoot/ServeHttp：保留并记录裁定理由（§三表）；GlobMatcher 引擎经用户裁定下沉 commons `SegmentGlobPathMatcher`（IPathMatcher 实现），ib 侧保留 fail-loud 契约薄壳 | nop-ib + nop-commons |
| 5 | nop-git 维持零平台依赖（§四 C） | nop-git |

验收门：nop-ib 全模块回归 0 失败 + p1/p2 黑盒端到端 + 重打包 ~/nop-ib 复验。
