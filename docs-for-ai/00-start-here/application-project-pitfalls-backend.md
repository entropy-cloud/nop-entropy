# 后端开发教训速查（源自 nop-app-erp 实战返工）

> **用途：** 后端任务（ORM 建模、BizModel/xbiz、权限/菜单、服务测试、新建业务域模块）动手前**先通读本页**。全部条目来自 nop-app-erp 真实返工（1754 commits、79 篇日志、40 篇 bug、18 篇 lessons），每条附可执行检测方式；**写 plan 时应把相关检测命令并入 Verification**。前端/页面任务读 `application-project-pitfalls-frontend.md`。

## 结构性根因：模型驱动架构把一切"省略"静默化

Nop 的模型驱动架构下，**缺标注→平台猜默认值、缺注册→不可达、缺声明→不检查、缺写点→死状态、缺重验→伪事实**——省略型缺陷不产生任何运行时信号，暴露点离根因很远（建模错、运行时炸；模块错、聚合炸）。本页所有规则本质是同一件事：**在静默的地方强制制造显式信号**。

## 后端铁律

1. **`_` 前缀 / `_gen/` / `__XGEN_FORCE_OVERRIDE__` 产物永不手改**——改模型源或保留层 Delta。手改会被下次生成静默还原，两案各花 3 轮审计才定位。
2. **新实体建模即补 tagSet 五件套**：`disp`（FK 显示列）/ `clock`（业务日期）/ `var`（编码列）/ 主键 seq 策略显式声明 / `parent`（树结构）。缺省代价：826 处 @BizLoader 拆除、230+179 列补标、全域重生成。
3. **主键/命名选型前先通读平台 `orm-model-design.md` 强制规则**（如主键必须 `stdDataType="string"`，BIGINT 在 JS 侧静默截断精度）。选错的二次返工：1605 列 + E2E 880 处。
4. **权限按 enforcement=ON 假设工作**：xbiz `<mutation>` 首子元素 `<auth permissions>`、FNPT 权限点、菜单 `roles`（**只认逗号分隔**）、角色种子——四层当场补齐。缺声明零信号，enforcement 翻转窗口集中爆雷（117 处补齐）。
5. **跨层字段/契约删除前，生产侧和消费侧必须都 grep**——静默降级零信号就是最大信号（picker 五步误判链，6 文件还原）。
6. **VFS delta 覆盖平台 beans 必带 `x:extends="super"`**，单 bean 覆盖点用 `x:override="replace"`——缺 super 是整文件替换，平台 bean 静默丢失（五域潜伏）。
7. **"代码没变测试变红"先查兄弟仓 SNAPSHOT**：`~/.m2` jar mtime + 兄弟仓 `git log -5`，排除外部漂移再归因（erp 三连假红）。
8. **快照禁止内嵌日历派生字面值**：日期派生列用冻结时钟或 `*` 掩码，并发路径 id 用 `@var:` 引用运行时值——否则每月 1 日准时爆炸（月初 13 errors，前日全绿）。
9. **Plan completed = 内部一致 + 独立子代理 fresh-session closure audit 留证据指针**；需求分歧的关闭载体只能是代码行为，文档收口是高利贷（整批需求二次重开）。
10. **文档事实断言（"零 X / 计数 N"）引用前必须 grep 实仓重验**，计数一律指针化指向权威源（surefire XML），禁手工转录——一次转录误差吞掉 14 项真失败。

## 分维度教训表

### 模型面（ORM）

| 教训 | 最终规则 | 检测 |
|---|---|---|
| tagSet 缺失 | 建模检查单固定五项（铁律 2） | `rg -L 'tagSet="disp"' module-*/model/*.orm.xml`；FK 密度高的域零 disp 即高危 |
| 跨域实体本地重声明 tableName 拼错并广播 7 域 | 跨域引用只用 `refEntityName`，禁止本地重声明；桩由脚本从源域派生，禁手抄 | 门禁比对本域桩 tableName ≡ 源域权威表名 |
| UK 只设 `name=` 未设 `constraint=` = DB 层无唯一约束 | 唯一键 `name=` + `constraint=` 双属性；并发路径补 duplicate-key 重试 | `rg '<unique-key' -A2 module-*/model/ \| rg -v 'constraint='` |
| dict 声明永不出现的状态（死状态跨 8+ 域） | dict 每个值要么有 setStatus writer，要么显式裁决删除/Deferred+触发条件 | 对每个值 `rg "setStatus\\(.*<状态>"` 零命中即死状态 |

### 权限 / 菜单面

| 教训 | 最终规则 | 检测 |
|---|---|---|
| 菜单不可见三根因：聚合器漏注册 / FNPT 缺失 cascade-up / fixStatus 塌缩 | 页面五件套同登记：page.yaml + 保留层 action-auth + `app-*-all` 聚合器 `x:extends` + 角色种子 + per-entity FNPT | 门禁比对聚合器 `x:extends` 集合 vs `module-*/` 目录集合；合并后跑菜单可达性冒烟 |
| fail-closed 机制叠加（掩码×授权）产生组合真空，所有账号都看不到数据 | 引入任何 fail-closed 机制先证明"至少一个生产角色能走通完整路径" | 收口门禁问题："哪个角色能看到明文？"答不出即阻塞 |

### 代码生成 / Delta / 平台机制面

| 教训 | 最终规则 | 检测 |
|---|---|---|
| delta beans 缺 `x:extends="super"` | 铁律 6；复制先例前先读文件头注记 | `rg -L 'x:extends="super"' **/_delta/**/beans/*.xml` |
| xbiz XScript 无 try/catch，多步编排硬写必返工（两案定稿） | xbiz 只做薄委托（守卫+状态写回+一行 inject），编排/失败隔离下沉 Java Bean | xbiz diff 见 `try` 即打回 |
| 平台"文档承诺"与引擎实现有落差（20 复杂页路线偏离） | 涉平台新特性的 plan，Phase 0 必须最小 PoC 实测；未实现禁止文档写"已解决" | 计划模板 Phase 0 必填 PoC 证据 |

### 测试面（服务层）

| 教训 | 最终规则 | 检测 |
|---|---|---|
| 快照内嵌日历字面值，月初炸弹 | 铁律 8；每月 1 日全量回归作为制度 | `rg -l 'today\\(\\)' src/test` × `_cases` 含 `\\d{4}-\\d{2}` 字面 → 需冻结/掩码 |
| 并发路径快照录字面 id，~50% flake | 并发/多候选路径用 `@var:` 引用运行时 id；录制后 ≥8 连发稳定门 | review 检查 `_cases` 字面数字 id × 并发用例 |
| 共享 JVM 并行测试 × 全局静态生命周期 = 整类 unknown-operation | 容器型测试模块显式覆写 surefire `reuseForks=false` | 全量跑 vs 单类跑结果不一致即此症 |
| 种子 id 与序列生成器起点冲突 | 种子装载后同步序列起点到 max(id)+1；E2E 写测试不复用种子 id 区间 | seed 脚本末尾断言序列当前值 > 种子 max id |
| 基线计数人工转录吞掉真失败；信号源跳变是工具链问题 | 铁律 10 权威计数口径；全红/全绿突变先怀疑 JDK/依赖 mtime | known-good 基线行必须附权威来源 |

### 流程 / AI 协作面

| 教训 | 最终规则 | 检测 |
|---|---|---|
| closure-pending 三波复发：自我审计盲区 | 铁律 9；completed 由审计派生，不由执行者填写 | `grep -L 'Independent Closure Audit' docs/plans/*completed*.md` 应为空 |
| 文档收口需求分歧 = 高利贷，成批重开 | P0/P1 分歧关闭载体只有代码；P2 简化需独立审计/人工批准/scope 裁剪三选一 | closure 前逐 finding 问"行为在 HEAD 实仓存在吗？" |
| 门禁脚本静默死亡，死得很像"零漂移" | 门禁零匹配降级为空集继续；输出末尾自证完整性（规则计数+汇总表） | checker 末尾完整性断言；改模型属性后重跑门禁确认模式仍命中 |
| 审计"文档 vs 文档"不下代码结论 = 假阳性工厂 | code-vs-design 审计先读代码；事务/并发判断必须引用 class:method/file:line | 审计提示词强制 ≥1 条源码引用字段 |
| 平台机制结论只看一层证据就动手 | 三层证据：官方定义 + 平台源码/内置用法 + 测试实证；契约用静态检查工具而非试错 | BizModel 提交前跑契约检查（erp：check-ibiz-interfaces.mjs） |
| AI 在"让测试变绿"压力下腐蚀架构裁决，三次回摆 | 破坏性契约重设计把"旧测试必须迁移"写进 exit criteria（红=迁移清单非失败信号）；契约回摆必须落 Human Adjudication Log | closure 审计 diff 对比：已裁决移除的符号是否复现 |

> erp 业务特有（业财过账、会计期间、多币种、UK 大表并发补偿等）不在此页，需要时查 nop-app-erp 应用仓库的 docs/lessons 目录。前端/页面教训见 `application-project-pitfalls-frontend.md`。

## 落地动作

1. **建模期**：ORM 骨架生成前通读平台 `orm-model-design.md` 强制规则；每实体过 tagSet 五件套。
2. **写 plan 时**：把本页相关"检测"列命令并入计划 Verification。
3. **收口时**：铁律 9/10 —— 独立 closure audit 留指针，事实断言实仓重验。
