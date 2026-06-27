# 生成业务编码 / 单据编号（CodeRule）

## 适用场景

- 业务系统需要按规则自动生成业务编码：订单号、卡号、单据号、流水号等。
- 想让编号在保存实体时**自动生成**，业务代码零侵入。

## AI 决策提示

- 平台已内建 `nop-sys` 的 CodeRule + Sequence，**不要自建编号生成**（不要 `SELECT MAX(no)`、不要在 `defaultPrepareSave` 里手写取号、不要塞数据库触发器）。
- 标准路径是 `tagSet="code"` → autoExpr 自动集成，下面三种方式按优先级选用。

## 核心机制

CodeRule = **模式字符串**（`codePattern`）+ **底层 Sequence**（真正递增的计数器）。

- `ISequenceGenerator`（`io.nop.dao.seq.ISequenceGenerator`）：序列号原子引擎，按 `seqName` 取号。实现 `SysSequenceGenerator` 用 **DB 行锁 + `cacheSize` 批量取号**，保证无重号、高吞吐；snowflake/uuid 作兜底；取号在 **`REQUIRES_NEW` 独立事务**中执行，不被外层业务事务回滚（刻意设计，避免回滚导致重号）。
- `ICodeRuleGenerator`（`io.nop.dao.coderule.ICodeRuleGenerator`）：`generate(ruleName, bean)` 是调用入口。
- `DefaultCodeRule`：模式引擎，解析 `{@type:options}` 占位符。

### 模式语法（`codePattern`）

| 变量 | 写法 | 说明 |
|------|------|------|
| `year` | `{@year}` | 4 位年份 |
| `month` | `{@month}` | 2 位月份（补零） |
| `dayOfMonth` | `{@dayOfMonth}` | 月内日 1-31 |
| `hour` | `{@hour}` | 2 位小时 |
| `minute` | `{@minute}` | 2 位分钟 |
| `second` | `{@second}` | 2 位秒 |
| `seq` | `{@seq:5}` | 递增序号，固定取 5 位（不足补零，超出从右截取回绕） |
| `randNumber` | `{@randNumber:3}` | 3 位随机数字（安全随机源） |
| `prop` | `{@prop:entity.orgCode,4}` | 从上下文 bean 的属性取值，可带长度截断/补零 |

> `seq` 和 `randNumber` 的长度上限是 20（`MAX_COUNT`），超过抛 `ERR_SYS_CHAR_COUNT_EXCEED_LIMIT`。

示例：`D{@year}{@month}{@seq:5}` 在 2026 年 6 月生成 `D20260600001`、`D20260600002`……

## 三种使用方式（按优先级）

### 方式 1：autoExpr 自动集成（推荐，业务无感知）

业务单据编号的标准路径，业务代码完全不用关心编号：

1. 在 ORM 模型的列上加 `tagSet="code"`：

```xml
<column name="orderNo" code="ORDER_NO" ... tagSet="code"/>
```

2. codegen 自动在生成的 `_{Entity}.xmeta` 里写入 `biz:codeRule="对象短名@字段名"`（codegen 模板：`col.tagSet.contains('code') ? entityModel.shortName + '@' + col.name`）。
3. `CrudBizModel.doSave` 执行时按 xmeta 的 autoExpr 配置自动调用 `ICodeRuleGenerator.generate(ruleName, entity)` 回填字段。
4. 在 `nop_sys_code_rule` 插规则记录，在 `nop_sys_sequence` 插 `seqName` 指向的序列。

### 方式 2：在派生 xmeta 中显式声明 / 覆盖

不用 `tagSet="code"` 的默认规则名，或对特定字段单独指定规则，在派生（非下划线）`{Entity}.xmeta` 里写：

```xml
<prop name="orderNo" published="true" biz:codeRule="my-order-rule" />
```

### 方式 3：BizModel 显式调用（少数场景）

需要在"非 save 时机生成"或"编号依赖复杂上下文"时直接注入：

```java
@Inject
ICodeRuleGenerator codeRuleGenerator;

// 第二参数是上下文 bean，{@prop:...} 通过 BeanVariableScope 从它取属性
// 可传 entity 本身，或传 IEvalScope（XLang.newEvalScope() + setLocalValue("entity", entity)）
String code = codeRuleGenerator.generate("order-rule", entity);
entity.setOrderNo(code);
```

## Sequence 并发模型（选型必读）

`NopSysSequence` 关键字段：`seqName`(PK) / `seqType`(seq|snowflake) / `isUuid` / `nextValue` / `stepSize` / `cacheSize` / `maxValue` / `resetType`。

| 配置 | 行为 |
|------|------|
| `cacheSize > 0` | 进程内缓存连续号段，每次取号先吃缓存；耗尽才回库。**吞吐高、崩溃会跳号**。业务单据用。 |
| `cacheSize = 0` | 每次回库（慢但不断号）。**财务凭证等需连续的场景用**。 |
| `seqType = snowflake` | 不查库，走 `SnowflakeSequenceGeneator`（workerId 来自 `nop.sys.seq.snowflake-worker-id` 或 hostId 哈希） |
| `isUuid = 1` | 返回随机正 long 或 UUID 字符串 |
| seqName 不存在 | `useDefault=true` 时回退到名为 `default` 的序列 |

> **`resetType` 如实说明**：表上有 `resetType`(NONE/DAILY/MONTHLY/YEARLY) 字段，但 `SysSequenceGenerator` **当前实现不会按日/月/年自动归零**——`nextValue` 单调递增。若需"按年/月重新从 1 计数"（如 `PO-202606-00001` 每月重置），应在**应用层用多个 seqName**（如 `po_seq_202606`）+ 模式 `{@year}{@month}{@seq:5}` 实现。**不要假设 `resetType=MONTHLY` 会自动按月重置**。

## CodeRule 与 Sequence 的组合关系

`NopSysCodeRule.seqName` → `NopSysSequence.seqName` 是**逻辑外键**（非物理 FK）。一条 CodeRule 只消费一个 Sequence。`SysCodeRuleGenerator.generate()` 流程：按 `ruleName` 查 `NopSysCodeRule` 拿 `codePattern`+`seqName` → 构造惰性 `LongSupplier` → `codeRule.generate(pattern, now, seqSupplier, bean)`，遇 `{@seq:N}` 才取号。

## 注册自定义模式变量

内置 9 个变量不够时（如要"会计期间段"、"部门前缀"），在 beans.xml 注册：

```xml
<bean id="nopCodeRuleVariable_fiscalPeriod"
      class="app.erp.coderule.FiscalPeriodCodeRuleVariable"/>
```

- bean 名必须以 `nopCodeRuleVariable_` 为前缀，平台通过 `ioc:collect-beans` 自动收集到 `DefaultCodeRule.variables`。
- 实现 `ICodeRuleVariable`（`String resolve(String options, CodeRuleParams params)`）。

## 数据准备

首次启用时需在 `nop_sys_sequence` 和 `nop_sys_code_rule` 插入配置记录（seed SQL 或初始化脚本）。`SysSequenceGenerator.lazyInit()` 在 `CFG_SYS_INIT_DEFAULT_SEQUENCE=true` 时自动插入一条名为 `default` 的序列兜底。

## 反模式

- ❌ 在 `defaultPrepareSave` 里手写 `entity.setOrderNo(codeRuleGenerator.generate(...))`——autoExpr 已自动做，重复赋值冲突或覆盖。
- ❌ 用 `SELECT MAX(no) FROM ...` 自己算下一个号——并发会重号。
- ❌ 把编号生成塞进数据库触发器——绕过平台、无法走 CodeRule 配置和多租户。
- ❌ 假设 `resetType` 会自动按周期重置——当前实现不会。

## 仓库里的真实参考

- 手动调用：`nop-sys/nop-sys-service/src/test/java/io/nop/sys/service/TestCodeRule.java`
- autoExpr 端到端集成：`nop-sys/nop-sys-service/_cases/io/nop/sys/service/TestCodeRuleAutoExpr/`

## 相关文档

- `../02-core-guides/orm-model-design.md`（`tagSet`、主键策略）
- `../02-core-guides/service-layer.md`（autoExpr、`CrudBizModel.doSave`）
- `../03-modules/nop-sys.md`（nop-sys 模块概览：字典 / 序列号 / 编码规则 / 锁 / 事件队列）
- `../04-reference/source-anchors.md`（`CODE-001`~`CODE-004` 实现锚点）
