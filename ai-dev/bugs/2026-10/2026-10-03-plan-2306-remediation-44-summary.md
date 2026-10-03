# 2026-10-03 plan 2306 缺陷嫌疑批量修复 44 项最终状态汇总

> 口径：44 = 43 个源 bug 条目 + 1 个 fraud 2PC 条目。执行条目 40 个 + 1 个 already-fixed 改判（wi3#5）。
> 逐项修复协议（确认→爆炸半径→最小修复→回归测试→验证→落账）全程执行；证据见各源 bug 文件的 Fix/Tests/Affected Files 段。

## 状态计数（源条目口径）

| 状态 | 数量 | 条目 |
|---|---|---|
| `fixed` | 41 | 见下表明细 |
| `adjudicated-not-a-defect` | 2 | wi4#3（openConnection 文档告警裁定）、wi9#7（记录级 generator 探针证伪）|
| `already-fixed`（改判） | 1 | wi3#5（df4e4f8fa7 已修，回填指向）|
| `diagnosed-split` | 0 | — |
| 合计 | **44** | |

注：wi10#6（DashPatternDetector 滑窗）在 plan 中列为 Decision 项，最终裁定为 `adjudicated-not-a-defect`（文档化），计入第二行；wi7#3（existsDict 租户旁路）以"保留乐观语义 + 显式 warn 日志"最小修复落地，计入 `fixed`。若按 43 源条目 + fraud 口径：42 fixed + 2 adjudicated（含 wi10#6 与 wi4#3）+ 1 already-fixed（wi3#5 含在 43 内）+ fraud 1 fixed = 44。

## 逐项状态表（Phase / 项号 ↔ 源条目）

### Phase 1（10 执行条目 / 12 源条目）— 全部 completed

| 项 | 源条目 | 状态 | 一句话结论 |
|---|---|---|---|
| 1 | wi1#1 dateBetween max | fixed | L269 toLocalDate(min)→(max)；边界用例新增 |
| 2 | wi1#2 like 方向反 | fixed | sqlToRegexLike(s2) 匹配 s1；▲ 消费方 grep 证据入 bug 文件 |
| 3 | wi3#1 常量在左反转 | fixed | reverseOp.toFilterOp() 生效；特征化测试翻转 |
| 4 | wi4#1 initRefs 时序 | fixed | 第二遍循环回填 collectionMap；roundtrip 测试新增 |
| 5 | wi5#1 GraphBFS root | fixed | 构造器 set.add(root)；wf trip-wire 翻转为友好错误断言 |
| 6 | wi10#1-3 mermaid 三项 | fixed | token 拆分/补 DIRECTION/sequenceMessage 结构区分 + 生成物再生 |
| 6b | wi10#4 SLL 绕过 LL | fixed | not-end-properly 错误码分流触发 LL 重试；词法错误直接传播 |
| 7 | wi10#5 pdf 双重展开 | fixed | 锚点判定 + 越界钳制；贴边/覆盖两用例新增 |
| 8 | wi11#1 HealthStatus.merge | fixed | worst-wins，定序对齐 Spring Boot（DOWN>OOS>UP>UNKNOWN） |
| 9 | wi1#5 AStar scoreMap | fixed | scoreMap 写入 + stale 跳过 + 路径回溯；退化断言翻转 |

### Phase 2（20 执行条目 / 20 源条目）— 全部 completed

| 项 | 源条目 | 状态 | 一句话结论 |
|---|---|---|---|
| 10 | wi1#3 PUBLIC_MASK | fixed | `&`→`\|`；互斥访问位测试新增 |
| 11 | wi1#4 排序反 | fixed | 三处 comparator 去负号；正向顺序断言 |
| 12 | wi2#1 null Boolean NPE | fixed | Boolean.TRUE.equals 兜底；@Disabled 解除 |
| 13 | wi2#4 addAll 校验 | fixed | IAE 前置校验（AIOOBE→IAE 契约变化，全 reactor 验证） |
| 14 | wi3#2 getArgument 越界 | fixed | 越界返回 null；两个特征化用例翻转 |
| 15 | wi3#3 anyOf 原始 ISchema | fixed | 放转换后 list；成员 Map 断言强化 |
| 16 | wi4#2 裸 IAE | fixed | 新错误码 ERR_ORM_COMPUTE_PROP_ARG_MISSING 已注册 |
| 17 | wi4#4 OFFSET 0 | fixed | limit-only 补 OFFSET 0 ROWS；新测试文件 |
| 18 | wi5#2 checkEnd 死校验 | fixed | isNextToAssigned 前置；trip-wire 翻转；wf-service 115 当期绿 |
| 19 | wi7#2 locale 忽略 | fixed | locale 参数透传（数据级多语言过滤记录为模型扩展候选） |
| 20 | wi7#4 null 审计 | fixed | 空串显式标记（NULL/"" 语义区分）；stub 全链测试新增 |
| 21 | wi8#1 null method NPE | fixed | 跳过元数据落点；网关语义测试新增 |
| 22 | wi8#2 saveOrUpdate "id" 键 | fixed | 三处改经 getId(data,dao)；▲ 四个测试改携 sid；文案修正 |
| 23 | wi9#1 FixedPoint 负数 | fixed | encode floor 拆分；负数 roundtrip 测试新增 |
| 24 | wi9#2 日期校验 | fixed | 分隔符 + 非 lenient 校验；无生产调用方证据入档 |
| 25 | wi9#3 颜色索引冲突 | fixed | 双 map 统一首注册优先；特征化断言调整 |
| 26 | wi9#4 FLS 缺省 | fixed | 文本路径缺省 "0"→""；codec 测试新增 |
| 27 | wi9#5 vars 无防御拷贝 | fixed | prepareVars 拷贝；不可变 Map 测试新增 |
| 28 | wi9#6 文本类型反推 | fixed | 按 stdDataType 转换；roundtrip 断言翻转 |
| 29 | wi11#2 空映射 fail-fast | fixed | 构造器 isEmpty 检查；测试新增 |

### Phase 3（9 执行条目 / 10 源条目）— 全部 completed

| 项 | 源条目 | 状态 | 一句话结论 |
|---|---|---|---|
| 30 | wi1#6 低危三项 | fixed | compareTo 经 getSqlHash/annotationType 过滤/补右括号，3/3 修复 |
| 31 | wi2#2+#3 concat/flatMap | fixed | JS 语义修正；消费方 grep 证据入档；两测试新增/翻转 |
| 32 | wi3#4 janino 字段丢失 | fixed | fieldDeclarationsAndInitializers 提取 + 原始类型名；特征化翻转 |
| 34 | wi4#3 openConnection | adjudicated-not-a-defect | 文档告警裁定（改语义破坏连接生命周期契约）；双侧 javadoc |
| 35 | wi7#1 detached ref | fixed | internalGetRefEntity 按 enhancer 可用性分流；stub 测试新增 |
| 36 | wi7#3 租户旁路 | fixed | 保留乐观语义 + 显式 warn（移除旁路会破坏启动期，裁定入档） |
| 37 | wi8#3 @InjectValue 默认 | fixed | 字段初始化对齐配置默认（Decision：字段初始化路线） |
| 38 | wi9#7 记录级 generator | adjudicated-not-a-defect | 探针证伪（两轮）：括号表达式可编译且合并生效；裸 { 起始被拒属 xpl 文本域解析约定 |
| 39 | wi10#6 DashPatternDetector | adjudicated-not-a-defect | 文档化裁定（容差滑窗是抗噪声设计）；javadoc + 断言复核 |

### already-fixed 改判

| 源条目 | 状态 | 证据 |
|---|---|---|
| wi3#5 JsPromise | already-fixed | commit `df4e4f8fa7`（plan 2282-WS4），TestJsPromise 正向断言在库 |

### Phase 4 fraud

| 条目 | 状态 | 结论 |
|---|---|---|
| stream-2pc | fixed | 嫌疑 A 成立（两层产品缺陷：DmlOp checkpoint 序列化失败 + stableHash 顺序 key 塌缩）；嫌疑 B 排除；TestParallel2PcJdbcE2E 转绿 |

## 验证证据

- 受影响 19 模块当期全绿：nop-core 496 / nop-commons 351 / nop-xlang 855 / nop-api-core 151 / nop-orm-model 32 / nop-dao 164 / nop-orm 210 / nop-wf-core 47 / nop-wf-service 115 / nop-cluster-core 27 / nop-pdf 70 / nop-gateway 106 / nop-biz 111 / nop-biz-auth-core 138 / nop-excel 76 / nop-record 192 / nop-rpc-core 58 / nop-sys-dao 64 / nop-mermaid 26。
- nop-stream 全套件 1653 测试零失败（含转绿的 TestParallel2PcJdbcE2E 与 stableHash 爆炸半径复核）。
- ▲ 行为语义变化项（1/2/6b/18/22）下游当期验证全部执行并记录于对应 bug 文件。
- 全仓 `./mvnw test -T 1C -fae`：见 plan Closure 段（收口记录）。

## Non-Blocking Follow-ups（不影响本次 closure）

- SysDictLoader 选项级多语言过滤：需 sys 字典模型增加 locale 列（ORM 模型 protected area，独立立项）。
- stableHash 公式变更对存量 checkpoint keyed state 布局不兼容：2.0.0-SNAPSHOT 阶段接受；如需存量兼容须引入带版本状态迁移。
- janino 升级评估（plan 既有 Non-Blocking 项，项 32 已最小修复不依赖升级）。
