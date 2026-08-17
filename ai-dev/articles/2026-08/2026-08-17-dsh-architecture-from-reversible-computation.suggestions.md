# 《从可逆计算看 DeepSeek Harness 的架构设计》改进意见

> 审阅日期：2026-08-17
> 说明：本文档只给出改进建议，不直接修改原文。建议项按优先级排列：A 类为必须修订的事实/概念问题，B 类为建议修订的结构与论证问题，C 类为可选增强。

## 总体判断

文章选题好、论证密度高，核心贡献是把 dsh 的可逆性精确定位到"运行时结构空间"、区分了可逆性（能力）与响应式（治理）、并指出 dsh 配置层是"粗粒度替换"而 Nop 是"细粒度差量代数"。问题主要集中在：个别数字与概念边界需要重新核实（尤其 `ctx.effect` 计数与静态 patch 层/运行时 loader 层的混淆）、"用户问题"未落地导致多处指代悬空、以及部分引文缺乏可复现出处。

---

## A 类：事实核验与概念边界问题

### A1. `ctx.effect` 的调用次数"241 处"不可复现，建议改为可复现口径

原文两处（摘要与正文）均写"全部 241 处 `ctx.effect` 调用"。但在本机 `~/ai/deepseek-harness` 当前工作区中，统计结果为：

```bash
grep -rn "ctx\.effect" --include='*.ts' --include='*.tsx' . | wc -l
# 244

grep -rn "ctx\.effect" --include='*.ts' --include='*.tsx' . | grep -v '/tests/' | grep -v '/test/' | wc -l
# 205
```

建议：

1. 将数字更新为可复现口径，并说明统计范围。例如："截至 2026-08-17 源码，`ctx.effect` 在全部 .ts/.tsx 文件中共 244 处（排除 test/tests 目录后 205 处）"。
2. 或改用约数"约 240 处"并附统计命令，避免把易随版本漂移的数字写成精确值。
3. 最好在文末增加一个"源码核实附录"，给出 dsh 仓库路径、统计日期/commit 和统计命令，让读者能自行复算。

### A2. §5.2 混淆了静态 patch 层与运行时 loader 的 reconciliation（最重要）

§5.2 的核心论点是"论文说 reconciliation 是 keyed diff，但 dsh 实际实现（vendor/include 的 `applyEntryPatches`）是整行替换 + insert，并没有 diff 级合并"。这个对比把两个不同层面放在了一起：

- `vendor/include/src/index.ts` 的 `applyEntryPatches` 是**启动期/离线 patch 文件**（`cordis.patch.yml`、bundle 层、user 层、`--patch` overlay）的合并语义，确实对 `config` 等顶层字段做整体覆盖，没有深合并。
- 论文 §5.2.1 描述的 reconciliation 是**运行时 loader** 的行为，对应 `vendor/loader/src/config/entry.ts` 的 `Entry.update()`。该实现按字段分发：`id/name/url` 重建、`isolate` 重配 realm、`intercept` 原地更新、`config` 交给组件决定（group 的 config 就是 child 列表，做 keyed diff）、`disabled` 卸载/重载；并且 `Entry.update` 对传入字段做的是**浅合并 + null 删除**，不是"整行替换"。

因此原文的"错位"判断需要改写：

- 准确的说法是：dsh 的**静态配置定制层**（`cordis.patch.yml` overlay）是"entry id 坐标 + 整段 config 覆盖 + insert"，未形成差量代数；这印证了"结构空间定制能力弱"的结论。
- 论文 §5.2 的 reconciliation 在运行时 loader 中确有对应实现，而且比静态 patch 层更细（per-field dispatch、group keyed diff）。建议把"论文的 keyed diff 只发生在 group 子列表这一级，对 config 内容整体替换"修改为类似："论文的 reconciliation 在运行期 loader 中有 per-field 实现；但静态 patch 文件层的 `applyEntryPatches` 仍是粗粒度的整段 config 覆盖，两者处于不同层面"。

另有一处小精度问题：`applyEntryPatches` 对非 insert patch 实际是"对 patch 中指定的顶层字段做整体赋值"——未指定的字段（如 `disabled`/`inject`）不受影响，只有被指定的 `config` 等字段被整体替换。"整行替换"的说法可能让读者误以为未指定字段会被清空，建议微调措辞。

修正后，§5.2 的最终结论（dsh 结构层未达到差量代数标准）依然成立，但论证更严谨，也避免了被读者指出"比较错了对象"。

### A3. "README 只有一句话值得玩味"不准确

原文："它的 README 只有一句话值得玩味：'一切皆插件'"。实际上 dsh 的 README 并非只有一句话，而是**用一句话概括了架构**（"It uses an architecture where **everything is a plugin**"）。建议改为："它的 README 用一句话概括了架构：'一切皆插件'"或"README 中最值得玩味的是这句架构宣言"。

### A4. "canonical"应给出全称，并建议与硬件"可逆计算机"做区分

"可逆计算理论（Reversible Computation）由 canonical 于 2007 年前后提出"中的 `canonical` 是博客作者名（Canonical Entropy，gitee 账号 `canonical-entropy`）。建议首次出现时写"canonical（Canonical Entropy）"并链接到 `docs/theory/what-does-reversible-mean.md`。

同时可加一句"注意与硬件领域的 Reversible Computing（可逆计算机）区分"。这与 `docs/theory/discussion-about-reversible-computation.md` 的既有澄清一致，也能帮助读者避开常见混淆。

### A5. "VSCode 87/100 的扩展需要重启宿主才能卸载"缺少出处

该数字来自 Cordis 论文 §1.2.1（"Among the top 100 extensions by install count, however, 87 contain executable code"，数据取自 VSCode Marketplace，2026-06-09）。建议在正文标注"（论文 §1.2.1）"。本文其他多处引用了论文章节号（§3.1、§4.1、§5.1.1、§6.1），唯独这里没有标注，显得突兀。

### A6. "agent-presets：standing scope 挂载"过于简略

§5.9 表格中"agent-presets：standing scope 挂载"直接使用了 dsh 仓库的英文术语，中文读者不易理解。建议改写为："agent-presets：每个 preset 在进程级挂载一个 standing scope（`agent.cordis.yml` 只挂载一次），每个会话的 agent scope 通过 scope 父链加入该挂载点（agent → preset → global）"。可引用 `packages/preset/agent-presets/README.md`。

---

## B 类：结构与论证完整性

### B1. "用户问题"悬空，建议在开头落地

全文多处出现"用户的问题非常尖锐""用户的核心洞察""用户的判断""用户提出的折中方案"等表述，但文章没有交代这个"用户"是谁、原始问题是什么。读者会产生强烈的缺失感：作者在与一个看不见的对话对象对话。

建议二选一：

1. 在摘要后或第一节前增加一个简短小节"背景与问题"，原文引用或转述用户提出的问题（例如：dsh 的可逆到底是每个行为都可逆，还是 plugin 划分 scope 后的粗粒度隔离？dsh 的文章有没有理清结构层与运行时层的关系？等）；或
2. 把所有"用户"改写为"一种常见质疑""有读者可能会问""一个关键问题"等无主表述。

鉴于文章副标题和摘要都像在回应一组具体问题，方案 1 更好。

### B2. 缺少目录，长文导航困难

文章约 2.7 万字、七个大节、18 个小节，建议在标题下增加 TOC（可折叠或简单列表），至少列到二级标题。

### B3. 引用方式不统一

正文大量引用论文章节号（§3.1、§4.1、§5.1.1、§6.1、Theorem 5/61/63/66 等），但全部是"裸引"，与文末参考文献没有锚点。建议：

- 为 Cordis 论文引入编号引用（如 [1]），正文用"[1, §5.1.1]"；
- 理论文章引用（`docs/theory/...`）同样编号化；
- 至少保证每节首次引用时给全路径链接，后续可用简称。

### B4. 第四节和第五节过长，可考虑拆分或增加"本节结论"

第四节有 9 个小节，第五节有 9 个小节，合计超过全文三分之二。建议：

- 为 4.1-4.8 的每个小节增加一句"结论标签"（如"4.6 结论：坐标是 entry id，粒度是整行配置"），便于快速阅读；
- 或者把第五节中与 Nop 深度对照的部分（5.4、5.8、5.9）独立为第六节，把"回到哲学"顺延。

### B5. 摘要密度过高

摘要一段话塞进了范围、粒度、坐标、配置层、三层划分、正交性、谱系、分界线等十多个信息点。建议把摘要精简为 3-4 句"结论摘要"，把详细清单留给正文（正文已有 4.9 和结论中的两个 bullet 清单，重复度较高）。

### B6. 结论与 4.9 存在重复

第七节结论的 bullet 清单与 4.9 总结列表内容高度重叠。建议保留一处为"速览清单"，另一处改为"论证后的结论陈述"；例如 4.9 保留精炼列表，结论改为连贯段落并回应用户问题。

---

## C 类：可选增强

### C1. 增加"源码核实方法"附录

文章的价值很大程度上来自"核对了 dsh 源码"。建议在参考文献前增加附录，至少包含：

- dsh 仓库本地路径（`~/ai/deepseek-harness`）、统计日期/commit；
- 关键源码文件与文章结论的对应表（reflect.ts ↔ 服务发布、events.ts ↔ 事件分发、fiber.ts ↔ epoch/inertia、vendor/include/src/index.ts ↔ applyEntryPatches、vendor/loader/src/config/entry.ts ↔ runtime reconciliation、packages/core/tools/src/index.ts ↔ agent scope tools）；
- 复算命令（如 `grep -rn "ctx\.effect" --include='*.ts' --include='*.tsx' .`）。

### C2. 增加图示

以下三个图可以显著提升可理解性：

1. 三层空间图（结构空间 → 运行时结构空间 → 运行时具体状态空间），标出 dsh 可逆边界；
2. 服务坐标（单写多读）与事件坐标（多写 + dispatch mode）的对比图；
3. Nop（被动 loader，结构空间消除时间性）与 dsh（主动组装，运行时空间显式管理时间性）的对偶图。

### C3. 增加 `ctx.effect` 的分类统计表

第三节说"高度集中在同一个类别"，但只列了类别，没有给每个类别的计数。建议做一张表：服务发布 n 处、事件监听 m 处、资源获取/释放 k 处、注册表写入 j 处、会话结构创建等。哪怕计数是近似值，也会让论证更有说服力。

### C4. 增加关键术语表

文章大量使用 fiber、coeffect、effect、realm、entry、scope、standing scope、inertia、emission 等英文术语。建议在文末增加术语表（中英对照 + 一句话解释），降低阅读门槛。

### C5. 补充 agent scope 的机制细节

§5.9 是全文最有原创性的部分之一，但目前对 scope 链的描述只有一句。建议补充：

- `createScope` 如何 mint scope key（`packages/client/runtime/src/client/agents/scope.ts`）；
- agent scope 的视图解析链（agent → preset → global，nearest shadowing farthest）；
- `tools.presentAs`/`tools.restrict`/`tools.guard` 三者在 scope 链上的不同行为（声明式选择/注册时条件/执行时 guard），并说明它们分别落在"结构空间可定/注册时刻可定/执行时刻可定"的哪个位置。

### C6. 修正与补充公式

§5.6 中 `RuntimeStructure = Σ Register_i` 建议改为更正式的表达，例如 `RuntimeStructure = ⊕_i Register_i`（⊕ 表示在运行时结构坐标系上的带逆叠加），并在正文说明"Σ 不是普通求和，而是 effect 的 twisted composition 叠加"，避免被读成"所有注册的简单并集"。

### C7. 结论中"dsh 的文章没有理清"可拆成两层回答

原文结论回答"dsh 的文章有没有理清结构层与运行时层的关系"时，把"论文"和"dsh 实现"混在一起说"没有"。建议明确拆成：

1. 论文层面：§5.2 描述了 declarative configuration 和 reconciliation，但没有把配置层 override 形式化为差量代数，也没有给出配置坐标与运行时 effect 坐标的统一；
2. 实现层面：静态 patch 层（`applyEntryPatches`）是粗粒度整段覆盖，运行时 loader 层（`Entry.update`）有 per-field 雏形，但插件内部仍是命令式代码。

这样两层回答与 A2 的修正保持一致。

---

## 附：已核实为准确的关键论断（可保留）

以下论断经与 dsh 源码及 Cordis 论文对照，基本准确，建议保留：

- `ctx.provide` 同 realm 重复注册抛错（`vendor/cordis/src/reflect.ts`）；
- `ctx.on` 监听器作为 effect 存入 fiber，卸载自动注销（`vendor/cordis/src/events.ts`）；
- 五类 dispatch mode（emit/parallel/serial/bail/waterfall）语义与顺序关系（`vendor/cordis/src/events.ts`）；
- epoch 检查 `runner.epoch !== oldEpoch` 与 inertia 机制（`vendor/cordis/src/fiber.ts`）；
- realm 隔离：`isolate: true` → `#id`，`isolate: <label>` → `@label`（`vendor/loader/src/config/isolate.ts`）；
- patch 文档引文 "replaces whole row configs" 等（`packages/bundle/base/README.md`、`packages/boot/app-boot/README.md`）；
- xbiz/xwf/task 三处 `xdef:key-attr` 的字段级坐标（`nop-kernel/nop-xdefs` 与各 `_dump` 副本）。
