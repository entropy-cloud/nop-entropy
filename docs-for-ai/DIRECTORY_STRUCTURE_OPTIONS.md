# docs-for-ai 目录结构候选对比

本文档汇总多个子 agent 独立调研后给出的 `docs-for-ai` 目录结构候选，用于为后续重构提供对照基线。

目标不是立即决定一次性大迁移，而是先回答两个问题：

1. 什么样的结构最适合 AI 自动生成符合 Nop 最佳实践的代码？
2. 什么样的迁移顺序风险最低、最适合当前仓库逐步落地？

---

## 一、候选来源

本次共收集 3 个独立候选：

1. **AI-first 候选**：优先优化 AI 的检索路径和任务命中率
2. **Source-first 候选**：优先优化规范权威性和源码校准
3. **Low-churn 候选**：优先优化低风险迁移和维护成本

这 3 个候选的结论并不完全相同，但核心方向高度一致：

- `INDEX.md` 必须缩回“路由入口”角色
- `12-tasks/` 必须成为 AI 主入口
- 默认规范必须从示例和 quick reference 中剥离出来
- `source-anchors` 必须升级为规范到源码的映射层
- 在内容冲突消除之前，不宜先做激进目录迁移

---

## 二、候选 A：AI-first

### 设计重点

- 最短路径：`INDEX -> Runbook -> Canonical Rule -> Reference/Example`
- 尽量减少 AI 在顶层目录的选择成本
- 把“如何做任务”放在比“按主题浏览”更高的优先级

### 顶层结构草案

```text
docs-for-ai/
├── INDEX.md
├── RULES.md
├── 10-runbooks/
├── 20-canonical/
├── 30-concepts/
├── 40-reference/
├── 50-examples/
└── 90-maintenance/
```

### 优点

1. AI 检索路径最清晰
2. runbook 与规范层明确分离
3. 示例天然被降权

### 缺点

1. 物理迁移量大
2. 路径变化多，断链修复成本高
3. 对现有文档和引用关系冲击最大

### 适用场景

适合在内容已经稳定、愿意接受大规模目录迁移时采用。

---

## 三、候选 B：Source-first

### 设计重点

- 规范权威性高于覆盖面
- 每个主题只保留少量 canonical rule docs
- 所有规范文档都要能回到源码锚点

### 顶层结构草案

```text
docs-for-ai/
├─ INDEX.md
├─ 01-rules/
├─ 02-runbooks/
├─ 03-concepts/
├─ 04-architecture/
├─ 05-reference/
├─ 06-examples/
└─ MAINTENANCE.md
```

### 优点

1. 规范来源最清晰
2. 非规范材料不容易反向污染默认写法
3. 很适合长期治理和持续校准源码

### 缺点

1. 需要拆分和合并大量现有文档
2. 现有目录体系会被较大幅度重构
3. 初期治理成本较高

### 适用场景

适合把 `docs-for-ai` 作为长期稳定产品资产来建设时采用。

---

## 四、候选 C：Low-churn

### 设计重点

- 尽量保留现有高价值路径
- 先改文档角色，再改物理目录
- 通过“规范归属”解决冲突，而不是先靠目录改名解决冲突

### 顶层结构草案

保留现有大部分目录，仅重新定义其逻辑角色：

- `INDEX.md`：薄路由层
- `12-tasks/`：主 runbook 层
- `03-development-guide/`：规范主干
- `04-core-components/`：Nop 特有组件规则
- `07-best-practices/`：跨主题最佳实践
- `11-test-and-debug/`：测试与调试规范
- `13-reference/`：源码锚点与兼容索引
- `08-examples/`、`09-quick-reference/`：降级为参考层
- `10-meta/`：已并入 `MAINTENANCE.md`

### 优点

1. 迁移风险最低
2. 链接断裂最少
3. 适合边治理边交付

### 缺点

1. 目录表面上仍然不够“整洁”
2. 需要通过写作纪律维持角色边界
3. 一段时间内会保留历史痕迹

### 适用场景

适合当前仓库这种文档体量较大、已有较多交叉引用、且需要尽快开始实际治理的情况。

---

## 五、三种候选的共识

无论采用哪一种物理结构，以下结论都是共识：

### 1. `INDEX.md` 必须瘦身

不能再兼任：

- 决策入口
- 长篇教程
- 代码模式示例集
- 目录映射总表

它应只保留：

1. AI 查找顺序
2. 高频任务入口
3. 全局反模式总表
4. 关键规范入口

### 2. `12-tasks/` 必须扩容

至少要新增这些高频 runbook：

1. `choose-entity-vs-bizmodel-vs-processor.md`
2. `change-model-and-regenerate.md`
3. `add-cross-module-biz-interface.md`
4. `create-request-response-dto.md`
5. `prefer-delta-over-direct-modification.md`
6. `add-dict-and-constants.md`
7. `add-bizloader-field.md`
8. `write-integration-test-with-noptestconfig.md`
9. `add-test-mock-bean.md`
10. `debug-codegen-and-generated-files.md`

### 3. 规范文档必须有唯一 owner

建议形成如下 canonical owner：

| 主题 | canonical owner |
|------|-----------------|
| BizModel / 服务层默认写法 | `03-development-guide/bizmodel-guide.md` |
| 项目结构 / codegen 流程 | `03-development-guide/project-structure.md` |
| IoC / 注入 / 配置 | `04-core-components/ioc-container.md` |
| 异常 / 错误码 | `04-core-components/exception-handling.md` 或 `07-best-practices/error-handling.md` 二选一并收敛 |
| 测试 / AutoTest | `11-test-and-debug/autotest-guide.md` |

### 4. 示例和 quick reference 必须降权

`08-examples/` 和 `09-quick-reference/` 不能继续承担默认规范职责。

必须明确标注：

- 示例仅用于辅助理解
- quick reference 仅可收录“安全默认 API”
- 不得再把直接 `dao()` 访问写成普通 BizModel 默认做法

### 5. `source-anchors` 必须升级

`13-reference/source-anchors.md` 不能只列类名，而应成为：

- 规则 ID
- 规则说明
- 源码锚点
- 规范文档 owner
- 关联 runbook

---

## 六、推荐采用的方案

当前最推荐的不是候选 A 或 B 的一次性物理重组，而是：

**以候选 C 作为当前执行方案，以候选 A/B 作为长期目标参考。**

具体含义是：

1. **当前阶段采用 Low-churn 路线**
   - 保留大部分现有物理路径
   - 先修正文档冲突
   - 先收敛 AI 路由和规范 owner

2. **逻辑结构吸收 AI-first 的优点**
   - 让 `INDEX -> 12-tasks -> canonical docs` 成为 AI 默认路径

3. **规范治理吸收 Source-first 的优点**
   - 用 `source-anchors` 建立规范到源码的映射
   - 让每个主题只有一个规范主入口

也就是说，推荐路线不是“选一个候选并全盘照搬”，而是：

**短期低风险落地，长期逐步向 AI-first + source-first 的目标形态靠拢。**

---

## 七、阶段性落地建议

### Phase 1

不改大目录，只做内容校准：

1. 重写 `INDEX.md`
2. 重写 `01-core-concepts/ai-development.md`
3. 重写 `03-development-guide/project-structure.md`
4. 重写 `09-quick-reference/api-reference.md`
5. 重写 `04-core-components/exception-handling.md`
6. 扩充 `13-reference/source-anchors.md`
7. 更新 `MAINTENANCE.md`

### Phase 2

增强 `12-tasks/`：

1. 新增缺失 runbook
2. 重写 `12-tasks/README.md`
3. 让 `INDEX.md` 直接路由到 runbook

### Phase 3

统一规范 owner：

1. 为每个主题指定 canonical doc
2. 其他文档改为引用而不是重复定义
3. 在示例和 quick reference 中增加非规范标记

### Phase 4

条件成熟后再做物理收敛：

1. `10-meta/` 已并入 `MAINTENANCE.md`
2. 视情况收拢 `06-utilities/`、`09-quick-reference/`、`13-reference/`
3. 如果必要，再进入大规模目录重命名

---

## 八、结论

可以而且应该使用子 agent 独立读取 docs 和源码，再生成多个目录结构候选做对比。

本次对比的最终结论是：

- **短期执行**：采用低 churn 路线
- **逻辑目标**：采用 AI-first 的主路径设计
- **规范治理**：采用 source-first 的权威模型

因此，后续重构将按以下原则推进：

1. 先治内容，不先治目录名
2. 先做 `INDEX + 12-tasks + canonical rules + source anchors`
3. 目录迁移只在内容稳定后再做
