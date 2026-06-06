# Adversarial Review: nop-code — Summary

> **日期**: 2026-06-06
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查（含 3 轮深挖）

## 总体评价

自 2026-05-29 三轮对抗性审查以来，nop-code 模块经历了大规模重构和系统性修复。之前报告的 22 个 P0/P1 问题中，绝大多数已修复。深挖轮次从 ORM 会话语义和性能模式角度发现了 4 个新问题，其中最严重的是 `CodeCacheManager` 在超过缓存上限时静默返回不完整数据（AR-108）——这是 AR-76 修复后引入的退步，比原来的空缓存更危险。

第 3 轮深挖从搜索/查询功能和语义边提取器两个全新视角切入，发现了 12 个新问题。最意外的是 `CodeSearchService.filterByFilePattern` 的 glob 匹配完全失效（AR-112）——`Pattern.quote` 阻止了通配符替换，所有文件过滤搜索无过滤效果。语义边提取器的性能炸弹（AR-113 无上限 O(N²)、AR-114 冗余内循环）是此前审查从未触及的领域。`CodeQueryService` 的 `imports` 字段丢失（AR-115）是一个简单的遗漏但影响所有文件查询 API。

## 新发现统计（含第 3 轮追加）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 全模块零事务（AR-94） |
| P1      | 10   | Leiden directed（AR-95）、Python 装饰器（AR-96）、SpringEvent（AR-97）、社区丢节点（AR-98）、脏会话偏移（AR-107）、缓存截断不可见（AR-108）、**glob 匹配失效（AR-112）**、**注解 O(N²)（AR-113）**、**imports 丢失（AR-115）**、**ID 不一致（AR-116）** |
| P2      | 13   | startsWith 错配（AR-99）、cascadeDelete（AR-100）、persistFlows OOME（AR-101）、线程泄漏（AR-102）、CallGraph 线程安全（AR-103）、NOT NULL（AR-105）、依赖图 3x 加载（AR-109）、缓存刷新竞态（AR-110）、依赖图 OOME（AR-111）、**语义边冗余计算（AR-114）**、**双缓存竞态（AR-117）**、**EdgeKey NPE（AR-118）**、**KnowledgeGap NPE（AR-119）**、**git 错误静默（AR-120）**、**死代码参数（AR-121）**、**BC 无超时（AR-123）** |
| P3      | 3    | 模板 Javadoc（AR-104）、凝聚力偏倚（AR-106）、**评分无区分度（AR-122）** |

## Top 5 关键发现

1. **AR-94 (P0)**: 全模块 5 个写操作全部使用 `runInSession`，无事务原子性保证——任何写入异常都会导致部分提交的数据不一致
2. **AR-112 (P1)**: `filterByFilePattern` 的 glob 匹配完全失效——`Pattern.quote` 阻止了通配符替换，所有带文件过滤的搜索无过滤效果
3. **AR-113 (P1)**: `AnnotationPatternExtractor` 无 MAX_SYMBOLS 上限，`@Autowired` 等常见注解可触发百万级语义边生成
4. **AR-115 (P1)**: `entityToFileResult` 未恢复 imports 字段，所有文件查询 API 的 imports 永远为 null
5. **AR-108 (P1)**: `CodeCacheManager` 超限后返回不完整的 SymbolTable/CallGraph，但 API 无任何截断标记

## 已修复确认

22 个 P0/P1 问题中，19 个已确认修复，2 个修复不完整（升级为 AR-94/AR-107），1 个修复引入退步（AR-76 → AR-108）。第 2-3 轮发现的所有问题（AR-107~AR-123）均仍存在。

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（25 条发现，含 3 轮深挖）

## 结论

`<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>`
