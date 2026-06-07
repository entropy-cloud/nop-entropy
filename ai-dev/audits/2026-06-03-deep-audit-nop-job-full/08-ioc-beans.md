# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] coordinator 的 app-engine.beans.xml 中 jobPartitionResolver bean id 命名风格不一致

- **文件**: `nop-job/nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:9`
- **证据片段**:
  ```xml
  <bean id="jobPartitionResolver" class="io.nop.job.coordinator.engine.JobPartitionResolver"/>
  <!-- 对比同文件其他 bean: -->
  <bean id="io.nop.job.coordinator.engine.IJobPlannerScanner"
        class="io.nop.job.coordinator.engine.JobPlannerScannerImpl">
  ```
- **严重程度**: P3
- **现状**: `jobPartitionResolver` 使用了 camelCase 短名，而同文件中其他 bean 一致使用接口全限定名或类全限定名作为 id。
- **风险**: 命名不一致会在维护时造成认知负担。功能不受影响。
- **建议**: 可考虑统一为全限定名以与同文件其他 bean 保持一致。
- **信心水平**: 高
- **误报排除**: `nopJobTaskBuilder_default` 等使用了 nop* 前缀短名是策略 bean 多实例命名，符合约定。只有 `jobPartitionResolver` 是既非全限定名又无 nop* 前缀的 bean id。
- **复核状态**: 未复核

### [维度08-02] coordinator、worker、retry-adapter 子模块缺少 _module 文件

- **文件**: coordinator/worker/retry-adapter 的 `src/main/resources/_vfs/nop/job/` 目录（文件不存在）
- **证据片段**: meta/service/dao/web 四个子模块在 `_vfs/nop/job/` 下放置了空 `_module` 文件（0 bytes），而 coordinator、worker、retry-adapter 三个子模块未放置。
- **严重程度**: P3
- **现状**: coordinator、worker、retry-adapter 缺少 `_module` 文件。Nop VFS 的自动发现机制能正确找到 beans.xml，功能正常。
- **风险**: 低。缺少 `_module` 不影响运行时功能，但与同项目其他模块不一致。
- **建议**: 可选添加空 `_module` 文件以保持一致性。
- **信心水平**: 中
- **误报排除**: 已确认 meta/service/dao/web 的 `_module` 文件确实是空文件（0 bytes）。
- **复核状态**: 未复核

## 无问题确认清单

| 检查项 | 结论 |
|--------|------|
| 手写修改生成文件 | 未发现违规 |
| 手写 beans.xml 语法 | 全部正确 |
| @Inject 使用 public setter | 全部 35 处均合规 |
| @InjectValue 语法 | 全部 23 处格式统一 |
| Spring 注解误用 | 无 @Value、@Autowired |
| beans.xml import 路径 | 全部正确 |
| bean 命名 nop* 前缀 | 策略 bean 正确使用 nop 前缀 |
