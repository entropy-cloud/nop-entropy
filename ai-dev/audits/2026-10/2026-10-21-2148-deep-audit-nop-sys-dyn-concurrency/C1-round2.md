# C1 共享可变状态与可见性 — 深挖第 2 轮

- 方法：对第 1 轮未覆盖的载体（生命周期交错发布、跨组件暴露的活集合、返回值逸出）做定向挖掘；不复述第 1 轮发现。

## 发现条目

### [C1-101] DynCodeGen.destroy 与在途 getTenantCodeCache 初始化竞态：租户缓存被摘除后仍完成初始化，成为孤儿实例

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:97-113`（destroy 逐租户 removeTenantCache+clear）、`:151-168`（getTenantCodeCache DCL）
- **证据片段**:
  ```java
  // destroy
  for (String tenantId : tenantCache.getAllKeys()) {
      InMemoryCodeCache cache = removeTenantCache(tenantId);   // get+remove 非原子（两步）
      if (cache != null) cache.clear();
  }
  // getTenantCodeCache
  synchronized (ref) {
      cache = ref.get();
      if (cache == null) { cache = initTenantCache(tenantId); ref.set(cache); }
  ```
- **严重程度**: P3
- **并发场景**: 停机序列 destroy × 业务线程首次访问某租户触发 initTenantCache
- **触发时序**: T1（业务线程）`tenantCache.get(tenantId)` 取得 ref（loader 建 AtomicReference 并放入 map）→ T2（destroy）`getAllKeys` 快照不含该租户或 `removeTenantCache` 已执行 remove → T1 完成 initTenantCache（全模块 DB 查询+生成）并把实例 `ref.set(cache)` —— ref 已脱离 tenantCache → 孤儿缓存完成全套 on-load 但永远不会被 clear()（其 on-unload 也不再有机会执行）。停机阶段后果有限（进程即将退出），但 destroy 契约（“返回后不再有任何入口能取到该缓存实例”，:217 注释）在窗口内不成立。
- **现状**: removeTenantCache 的 get+remove 两步非原子；注释自述的排他性保证依赖无并发假设。
- **风险**: 停机窗口内 on-load 副作用泄漏；测试/热重载场景下出现不可清理的缓存实例。
- **建议**: destroy 内循环改 `tenantCache.asMap().computeIfPresent` 或接受窗口并在 destroy 末尾对残留 getAllKeys 复扫一次。
- **失效面枚举**: 遍历-删除族：destroy 主循环 :105-110（本条）；clearForTenant :210-214 按名直达无遍历（安全）；removeTenantCache :219-223 两步组合的唯一另一调用方即 destroy 主循环（已并）。
- **信心水平**: 确定
- **误报排除**: 非理论竞态——XLang/batch 请求线程与 PreDestroy 的交错在真实停机中常见；判 P3 因进程随即退出。
- **复核状态**: 已保留

### [C1-102] useTenant 模式下无租户上下文请求落入从未初始化的共享 codeCache 并向其生成模块

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:142-148`（getCodeCache）、`:80-95`（init 仅 `!useTenant` 时填充共享缓存）
- **证据片段**:
  ```java
  public InMemoryCodeCache getCodeCache() {
      String tenantId = ContextProvider.currentTenantId();
      if (StringHelper.isEmpty(tenantId) || !isUseTenant()) {
          return codeCache;          // useTenant=true 但当前线程无租户上下文时也走这里
      }
      return getTenantCodeCache(tenantId);
  }
  ```
- **严重程度**: P3
- **并发场景**: 租户模式部署（useTenant=true）中，后台线程/启动任务/未带租户头的管理调用（如 getDynModule :371-381、generateForModule 被 generateByAI 调用）触达 getCodeCache
- **触发时序**: 无租户上下文线程调 getDynModule → getCodeCache 返回共享 codeCache（init 时未填充）→ loadAndCacheModuleModel→getEnabledModule(...,loadModule) → 以**无租户过滤的 DB 查询**结果向共享缓存 addModule + 生成资源到 `v:/`（非租户路径）→ 共享缓存成为内容不明的旁路缓存；后续其他无上下文调用复用其内容，与各租户缓存（`v:/_tenant/{id}`）形成两套事实。
- **现状**: 分叉行为无文档声明；是否可达取决于部署是否同时使用租户模式与无上下文管理调用（generateByAI 的 mutation 若无租户头即落入）。
- **风险**: 租户模式下管理操作生成的模型进入全局旁路，随后的租户请求看不到（或误看到）。
- **建议**: useTenant 且无租户上下文时 fail-fast（ERR_TENANT_REQUIRED）或显式声明共享缓存的语义边界。
- **失效面枚举**: getCodeCache 调用点全集：getTenantBizObjNames :122、getDynamicEntityModel :179、getEnabledTenantModules :194、generateForApp :240、generateForModule :255、generateForAllModules :278、generateBizModel :295、removeDynModule :368、getDynModule/loadAndCacheModuleModel :384、reloadModel :395、getTenantBizModel :409——逐一核对，均经同一分叉。
- **信心水平**: 很可能（可达性依赖部署形态）
- **误报排除**: 非已声明边界——代码与配置均无共享缓存语义声明；判 P3 因主路径（带租户头）不受影响。
- **复核状态**: 已保留

## 第 2 轮排查后维持原判的载体（不立项）

- `getEnabledTenantModules`/`getEnabledModules` 返回活 CHM（:115-117/:193-195）：消费方 ModuleManager 仅枚举，CHM 弱一致迭代安全。
- `bizModels` 值对象 GraphQLBizModel 跨线程共享：注册后仅读（bizPath/metaPath 等 final 语义字段）；generateBizModel 每次新建替换，无原地修改点。
- `assignedPartitions`（IntRangeSet）：final ranges，配置期注入后只读。
- `retryPolicy/topicRetryPolicies`：配置期注入，CHM 读安全。
- `SysDaoMessageService.localService` 匿名内部类捕获 this：字段初始化期构造，未逃逸到其他线程构造外使用，安全发布。
