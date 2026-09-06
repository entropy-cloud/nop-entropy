# 16: XLang DSL 调用点对 Java 向 grep 不可见 — "零调用方"判定必须覆盖 DSL 资源

> Date: 2026-08-28
> Severity: High — check2/nop-sys P3：`claimNonBroadcastEvents`/`processClaimedNonBroadcastEvent` 被两轮独立"全仓库 grep 零调用"判定为死代码并删除，实际被 nop-batch-sys 主资源的 batch.xml 以 XLang 表达式调用；删除后 nop-batch-sys 5 测试 `NopEvalException(no-obj-method)`，且首个失败现象被增量构建的陈旧 `__aop` 生成类干扰，两步才定位到真根因

## 场景

nop-sys 审计条目判定 `SysDaoMessageService.claimNonBroadcastEvents` 为死代码：

- 审计代理（check2，2026-08-23）："全仓库 grep 确认零调用"
- 处置代理（plan346，2026-08-28）独立复核：同样"零调用"，删除两方法
- 实际调用方：`nop-batch/nop-batch-sys/src/main/resources/_vfs/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml` 第 37/46 行

```xml
<const claimed = svc.claimNonBroadcastEvents(items);/>
...
<svc.processClaimedNonBroadcastEvent(item);/>
```

Java 方法经 XLang 表达式（`svc!.method(...)`）动态调用，**Java 向 grep（`*.java`）永远命中不了 `.batch.xml`/`.xbiz`/`.xlib` 里的调用**。删除后 beans 容器初始化 `SysDaoMessageService__aop` 时 `NoSuchMethodException` → nop-batch-sys 5 测试挂；主会话全 reactor 验证才暴露（处置代理只跑了 nop-sys 自己的模块测试，其测试直接 new 服务类、不经 beans 装配，故绿）。

## 根因

1. **"无调用方"的搜索面默认只有 Java**：grep 习惯性限定 `--include="*.java"`，而 Nop 平台大量跨模块调用发生在 DSL 资源（batch.xml 的表达式、xbiz 的 action 委托、xlib 标签）里。
2. **两轮独立复核共享同一盲区**：审计与处置是不同代理、不同日期，但用同一搜索范式——"独立复核"只对**方法**独立、不对**搜索面**独立时，系统性盲区原样传递。
3. **模块本地测试不覆盖跨模块调用面**：被删方法的调用方在另一个模块，删除者所在模块的测试全绿给了假确认。

## 正确做法

1. 判定"死代码/无调用方"前，除 Java 外必须 grep DSL 资源中的方法名：
   ```bash
   grep -rn "methodName" --include="*.xml" --include="*.xlib" --include="*.xbiz" \
        --include="*.batch.xml" --include="*.xrun" <scope>
   ```
   XLang 表达式形态（`obj.method(`、`svc!.method(`）没有 import/类型信息，只有名字匹配可用——**按方法名字符串全资源搜索**是唯一可靠手段。
2. 删除"死代码"后的验证必须超出本模块：至少跑直接依赖本模块的下游模块测试（本例 nop-batch-sys 的 beans 装配在删除者模块测试中不可达）。
3. 平台层面：方法被 DSL 引用无法由编译器保证——这属于可逆计算平台的固有限制，删除 public 方法（尤其 beans.xml 装配的服务的 public 方法）默认按"可能被 DSL 调用"处理。

## 判定规则

- 删除任何 public 方法前，方法名字符串必须在**全部文本资源**（不限于 .java）中零命中，且跑过直接下游模块测试。
- 看到 `no-obj-method`/`NoSuchMethodException` 指向 beans 装配的服务时，先怀疑"方法被删但 DSL/AOP 引用残留"，再怀疑增量构建产物（本例两者叠加：陈旧 `__aop` 类先挡了一道）。

## 关联

- 处置过程：`ai-dev/audits/check2/nop-sys.md` P3 死代码条目标注（2026-08-28 修正）、plan346 Non-Blocking Follow-ups、`ai-dev/logs/2026/08-28.md`
