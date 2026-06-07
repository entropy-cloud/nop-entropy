# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] nop-job-coordinator 和 nop-job-retry-adapter 缺少 _module 文件

- **文件**: `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/_module` (不存在)
- **证据片段**:
  对比 dao/service/meta/web 子模块均有空 `_module` 文件。coordinator 和 retry-adapter 缺失。
  ```
  nop-job-dao/src/main/resources/_vfs/nop/job/_module        ← 存在
  nop-job-service/src/main/resources/_vfs/nop/job/_module    ← 存在
  nop-job-coordinator/src/main/resources/_vfs/nop/job/_module ← 不存在
  nop-job-retry-adapter/src/main/resources/_vfs/nop/job/_module ← 不存在
  ```

- **严重程度**: P2
- **现状**: coordinator 和 retry-adapter 在 _vfs 下没有 _module 文件。缺少 _module 意味着该子模块的 VFS 路径可能无法被 Nop 平台的模块扫描机制发现。
- **风险**: 若应用仅引入 coordinator 而未引入兄弟模块，app-engine.beans.xml 可能不会被 VFS 发现。实际部署中通常通过上层依赖间接引入，影响有限。
- **建议**: 创建空 _module 文件，保持与其他子模块一致。
- **信心水平**: 确定
- **误报排除**: 已确认其他 4 个子模块（dao, service, meta, web）均有 _module 文件。worker 使用 _delta 机制，不需要自己的 _module。
- **复核状态**: 未复核

### [维度08-02] IJobRetryBridge 的 bean ID 在 coordinator 和 retry-adapter 中重复注册

- **文件**: `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:11-12` 和 `nop-job/nop-job-retry-adapter/src/main/resources/_vfs/nop/job/beans/app-retry-adapter.beans.xml:8-10`
- **证据片段**:
  ```xml
  <!-- coordinator -->
  <bean id="io.nop.job.api.retry.IJobRetryBridge" class="io.nop.job.coordinator.retry.NoOpJobRetryBridge"/>
  <!-- retry-adapter -->
  <bean id="io.nop.job.api.retry.IJobRetryBridge" class="io.nop.job.retry.adapter.NopRetryJobRetryBridge" ioc:container="default"/>
  ```

- **严重程度**: P3
- **现状**: 两个模块注册了相同 bean ID 但 class 不同。设计意图是 retry-adapter 作为可选覆盖替换 NoOp 默认实现。覆盖依赖 VFS 文件加载顺序，语义不够明确。
- **风险**: 若调整模块依赖顺序，覆盖行为可能翻转。
- **建议**: 在 retry-adapter 的 beans.xml 中添加注释说明覆盖语义，或使用 ioc:default 显式声明默认性。
- **信心水平**: 很可能
- **误报排除**: 已确认这是有意的"默认实现+可选覆盖"模式，功能上正确。
- **复核状态**: 未复核

## 审计通过项

- **生成文件无手写篡改**: _dao.beans.xml(空壳)、_service.beans.xml(代码生成)、_engine.beans.xml(空壳)均未手动修改
- **注入方式正确**: 87 处 @Inject 全部使用 setter 注入 + private 字段（NopIoC 标准模式），无 @Autowired/@Value
- **@InjectValue 语法正确**: 23 处均使用 @cfg:property.name|defaultValue 格式
- **Bean 命名遵循约定**: 接口类型用 FQN，业务 bean 用功能命名
- **import 路径正确**: 所有 beans.xml 中的 import 路径指向存在的文件
