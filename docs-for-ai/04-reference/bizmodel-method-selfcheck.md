# BizModel 方法自检清单

> 本文件是 BizModel 方法写完后的实时校验清单。与 `safe-api-reference.md`（写前速查用什么 API）互补。

## 用途

每写完一个 BizModel 上的 public 方法（`@BizQuery`/`@BizMutation`/`@BizAction`）后，逐条校验。private helper 方法不在校验范围内。

## 根因背景

反模式规则已写在必读文档中，但反复出现的问题是：

1. Agent 读了文档并标记 pre-flight checkbox `[x]`
2. 编码时没有逐方法对照规则
3. 闭包审计才发现违反（如参数用 `Object` 代替 `@DataBean`）

**根本原因：** "已读"不等于"已校验"。文档阅读和代码编写之间存在执行间隙。本清单强制在每个方法写完后插入校验步骤，关闭这个间隙。

## 校验清单

每写完一个方法，逐条回答以下问题。**任何一条不通过都必须立即修复后才能继续写下一个方法。**

### 第一组：接口与注解（来自 `service-layer.md`）

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 1 | 方法是否声明在 `I*Biz` 接口上？ | 是 → 通过。否 → **立即添加到接口** |
| 2 | 接口方法是否有 `@BizQuery`/`@BizMutation`/`@BizAction` 注解？ | 是 → 通过。否 → **立即添加** |
| 3 | 接口方法参数是否有 `@Name` 或 `@RequestBean`？ | 是 → 通过。否 → **立即添加** |
| 4 | BizModel 实现方法是否有 `@Override`？ | 是 → 通过。否 → **立即添加** |

### 第二组：参数与返回值（来自 `service-layer.md` 第53-58行）

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 5 | 参数数量 ≤ 5 且用 `@Name`？或参数 > 5 且用 `@RequestBean` + `@DataBean`？ | 符合其中一条 → 通过。否则 → **重构为 DTO** |
| 6 | 如果是自定义业务方法（非继承 CrudBizModel 的标准 CRUD），参数类型是否避免了 `Object` 和 raw `Map`？ | 标准CRUD用Map → 通过。自定义方法用Object/Map → **替换为具体类型或 `@DataBean` DTO** |
| 7 | 返回值是实体还是 DTO？如果只是限制字段可见性，是否用 xmeta 而非 DTO？ | 返回类型合理 → 通过 |

### 第三组：实体操作（来自 `safe-api-reference.md`）

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 8 | 是否使用 `new Entity()` 创建实体？ | 否 → 通过。是 → **改为 `newEntity()`** |
| 9 | 是否直接调用 `dao().getEntityById()` / `dao().saveEntity()` / `dao().findAllByQuery()`？ | 否 → 通过。是 → **改为 `requireEntity()` / `saveEntity()` / `findList()`** |
| 10 | 跨实体访问是否注入 `I*Biz` 接口？ | 是 → 通过。否且无理由 → **改为注入 I*Biz**。如有理由（如批量 SQL）→ 确认已写注释说明 |

### 第四组：异常处理（来自 `error-handling.md`）

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 11 | 抛出的异常是否继承 `NopException`？ | 是 → 通过。否 → **改为 NopException + ErrorCode** |
| 12 | 是否使用 `ErrorCode` + `NopException`，而非直接 `new NopException("字符串")`？ | 用 ErrorCode → 通过。直接传字符串 → **改为先定义 ErrorCode 再使用** |
| 13 | 错误码是否已定义在 `*Errors.java` 中？ | 是 → 通过。否 → **先定义错误码再使用** |
| 14 | `ErrorCode.define()` 描述是否使用中文？ | 是 → 通过 |

### 第五组：事务与注入（来自 `service-layer.md`）

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 15 | `@BizMutation` 方法上是否有 `@Transactional`？ | 否 → 通过。是 → **移除**（`@BizMutation` 已自动事务） |
| 16 | `@Inject` 字段是否为 `private`？ | 否 → 通过。是 → **改为 package-private** |
| 17 | 方法名是否与 `ICrudBiz` 标准方法重名（`get`/`save`/`update`/`delete`/`findPage`）？ | 否 → 通过。是 → **重命名方法** |

### 第六组：平台工具类（来自 `common-java-helpers.md`）

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 18 | 是否用了 `System.currentTimeMillis()` / `System.nanoTime()` / `LocalDateTime.now()` / `LocalDate.now()` / `new Date()` / `new Timestamp(...)`？ | 否 → 通过。是 → **按返回类型改为对应的 `CoreMetrics` 方法**（`currentTimeMillis`/`nanoTime`/`currentDateTime`/`currentDate`/`currentTimestamp`，详见 `common-java-helpers.md`）。所有获取当前时间的写法都必须走 `CoreMetrics`，否则自外于 `IClock`/`TestClock` 时间线，导致与 ORM 自动时间戳不同源 |
| 19 | 是否用了第三方 JSON 库？ | 否 → 通过。是 → **改为 `JsonTool`** |
| 20 | 是否用了 `Apache Commons StringXxx`？ | 否 → 通过。是 → **改为 `StringHelper`** |

## 执行纪律

1. **写完一个方法 → 立即执行本清单 → 全部通过 → 写下一个方法**
2. 如果在同一个写码 session 中连续写多个方法，**不能跳过中间方法的自检**
3. 如果发现需要新增 ErrorCode 或修改接口，**先完成这些前置步骤再继续写方法**
4. 自检结果不需要写入文件，但修复的动作必须在代码中体现

## 与已有流程的关系

| 流程步骤 | 本清单的位置 |
|---------|-------------|
| Pre-flight 阅读 `00-required-reading-backend.md` | 阅读在前，本清单是阅读后的执行保障 |
| Plan 中标记 `Pre-flight` checkbox | 标记阅读完成 ≠ 自检完成。本清单是编码时的实时自检 |
| Closure audit | 本清单是前置防线，closure audit 是后置验证 |

## 不要这样用

- 不要批量写完所有方法后统一自检——那样和闭包审计没有区别
- 不要把自检结果当文档维护——自检是实时执行纪律，不是产出物
- 不要用本清单替代阅读必读文档——本清单假设你已经读过

## 相关文档

- `../02-core-guides/service-layer.md` — 第一、二、五组规则来源
- `./safe-api-reference.md` — 第三组规则来源（写前速查）
- `../02-core-guides/error-handling.md` — 第四组规则来源
- `./common-java-helpers.md` — 第六组规则来源
