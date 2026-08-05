# 10: 补日志 ≠ 修根因 — 静默吞异常处只加 LOG.warn 掩盖真实缺陷

> Date: 2026-08-05
> Severity: Medium — nop-metadata P2-MA5-401：`getWfNameFromMeta` 用 `getProp("wf:wfName")` 恒返回 null，R2.11 只补 LOG.warn（"记录日志"作为修复手段），自动提审功能静默失效一个多月后才由 R3.6 根因修复

## 场景

nop-metadata 审计发现两处静默吞异常（P2-MA4-001/002），MR2 R2.11 的"修复"是在 catch 分支补 `LOG.warn`：

```java
// R2.11 前：catch 后什么都不做（静默吞掉）
// R2.11 后：catch 后 LOG.warn（但功能仍然失效）
```

但 R3.6 才查明真根因：`NopMetaTagLabelBizModel.getWfNameFromMeta` 使用 `getProp("wf:wfName")` 读取 SchemaImpl 的只读 props map——**该 API 恒返回 null**，正确做法是 `prop_get("wf:wfName")`（IExtensibleObject 根属性访问，与 approval-support.xbiz:30 同机制）。

后果链：

1. R2.11 把 P2-MA4-001/002 标记为"已修复"（补了 LOG.warn），但功能缺陷（自动提审静默失效）原样保留
2. 修复记录显示"修复完成"，后续审计按"已修复"路径跳过，缺陷潜伏期被拉长
3. 直到 P2-MA5-401（getProp 恒 null）被单独审计出来，才触发 R3.6 根因修复 + 自动提审正路径测试

## 根因

1. **把"记录症状"当成"消除症状"**：catch 分支从空操作变成 LOG.warn，但业务路径仍失败——日志只是让失败可见，没有让功能恢复。
2. **没有根因定位就"修复"**：R2.11 只看到"异常被吞"，没有追问"异常为什么发生"；真实根因是 getProp API 误用（取错属性层级）。
3. **修复验证缺失**：补 LOG.warn 后没有正路径断言（自动提审是否真的触发），"修复"与"验证修复"脱节。

## 正确做法

1. **先定位根因再动代码**：静默吞异常处必须追到"为什么抛异常/为什么返回错误值"，根因是 API 误用还是资源缺失还是业务状态错误——定位后才决定修复方案。
2. **修复必须带行为验证**：功能修复（非纯日志）必须补正路径测试断言真实行为（如 R3.6 的 `testDerivedLabelAutoSubmitsForApproval`：save 后 approveStatus=SUBMITTED），而不是只验证"没有异常"。
3. **日志是诊断手段不是修复手段**：catch 后 LOG.warn 只解决"可观测性"，若业务路径仍失败，该 finding 不得标记 fixed，应继续追踪根因或显式登记残余。

## 判定规则

> **判定"修根因"的唯一标准：修复后正路径行为可被断言测试证明成立。** 若"修复"只是增加日志、改变返回空值、或绕过异常，而目标行为（业务结果）仍未达成，按未修复处理——不得标记 fixed。
>
> 补日志后的正确状态登记：若根因未消除，标 `in progress`（根因未明）或另立 finding；只有根因修复 + 行为测试通过才能标 `fixed`。

## 适用范围

- 静默吞异常（catch 空操作）类 finding 的修复
- 任何"补日志"作为唯一修复手段的场景
- 审计追踪表中 fixed 状态的判定

## 参考

- `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTagLabelBizModel.java`（getWfNameFromMeta，R3.6 修复后 `prop_get`）
- 审计：`ai-dev/audits/arm-index-nop-metadata.md`（P2-MA4-001/002 → R2.11；P2-MA5-401 → R3.6）
- 修复：roadmap R2.11（补 LOG.warn）/ R3.6（根因修复 + 测试）
