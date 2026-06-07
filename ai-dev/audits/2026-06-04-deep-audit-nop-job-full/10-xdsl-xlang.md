# 维度 10：XDSL 与 XLang 正确性

## 审计范围

约 30 个 XDSL 文件（orm.xml, xmeta, xbiz, beans.xml, view.xml, action-auth, data-auth, Delta）。

## 第 1 轮（初审）发现

### [维度10-01] action-auth.xml 中 NopJobFire-main 的 displayName 使用英文而非中文

- **文件**: `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/auth/nop-job.action-auth.xml:46`
- **证据片段**:
  ```xml
  <resource id="NopJobFire-main" displayName="Trigger Batch" i18n-en:displayName="Trigger Batch"
  ```
- **严重程度**: P3
- **现状**: 同文件其他资源使用 `displayName=中文` + `i18n-en:displayName=英文` 模式，但 NopJobFire-main 两处均为英文。生成基线正确使用了 `displayName="触发批次"`。
- **风险**: 中文环境下权限管理页面显示英文名称，破坏 i18n 一致性。
- **建议**: 改为 `displayName="触发批次" i18n-en:displayName="Trigger Batch"`。
- **信心水平**: 高
- **误报排除**: 生成基线和同文件其他条目均为中文 displayName，此处是明确的手写覆盖错误。
- **复核状态**: 未复核

### [维度10-02] rpcBroadcast executor 复用 RpcJobInvoker 但 bean 命名暗示广播逻辑

- **文件**: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/beans/app-service.beans.xml:10-11`
- **证据片段**:
  ```xml
  <bean id="nopJobInvoker_rpc" class="io.nop.job.service.executor.RpcJobInvoker"/>
  <bean id="nopJobInvoker_rpcBroadcast" class="io.nop.job.service.executor.RpcJobInvoker"/>
  ```
- **严重程度**: P3
- **现状**: 两个 bean 使用相同类 `RpcJobInvoker`，但 rpcBroadcast 命名暗示 Invoker 本身包含广播逻辑。实际上广播在 TaskBuilder 层完成（RpcBroadcastTaskBuilder 创建多个 task 分片），Invoker 只处理单个 RPC 调用。
- **风险**: 维护者可能误认为 RpcJobInvoker 包含广播逻辑，导致理解偏差。
- **建议**: 在 bean 定义中增加注释说明广播逻辑在 TaskBuilder 层而非 Invoker 层。
- **信心水平**: 高
- **误报排除**: 功能正确（bean id 用于路由，不包含广播逻辑是正确的），但语义命名有歧义。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 10-01 | P3 | nop-job.action-auth.xml:46 | NopJobFire displayName 中英文错误 |
| 10-02 | P3 | app-service.beans.xml:10-11 | rpcBroadcast bean 命名歧义 |
