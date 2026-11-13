# C6 资源生命周期与泄漏 — 深挖第 2 轮

- 方法：本轮沿“删除路径的对面”（缓存失效面）与“手工生命周期与自动生命周期的缝隙”挖掘；第 1 轮已覆盖的获取-释放配对不重复。

## 发现条目

### [C6-101] 动态模块/实体/属性删除后无任何缓存失效面：已删除模型在 codeCache 与 VFS in-memory 层继续服务直至重启

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynModuleBizModel.java`（仅重写 saveEntity :61-65 与 generateByAI/importExcel，**无 afterEntityChange**）；`NopDynEntityBizModel.java`/`NopDynEntityMetaBizModel.java`（无变更钩子）；对照唯一 wired 的层级 `NopDynFunctionMetaBizModel.java:31-47`（afterEntityChange→generateBizModel）
- **证据片段**:
  ```java
  // NopDynModuleBizModel：无 afterEntityChange —— CRUD delete 一个已发布模块后：
  //   InMemoryCodeCache.enabledModules/bizModels/moduleStores、
  //   VirtualFileSystem in-memory layer（reloadModel 时发布的 mergedStore）、
  //   bizObjectManager 的动态模型集合，全部保持原样
  ```
- **严重程度**: P2
- **并发场景**: 管理员删除（或改状态为未发布）动态模块/实体 × 全部运行期动态模型请求
- **触发时序**: 删除操作提交事务 → 无任何回调 → 请求线程继续从 bizModels/VFS 命中已删除实体的 xbiz/xmeta/orm 模型 → 已删除业务对象的 API 继续可调用（含其数据权限定义），DB 侧行已删 → 面向已删模型的调用要么报错要么操作残留数据。此状态持续到下一次 generateForAllModules/reloadModel/重启。
- **现状**: 函数级（NopDynFunctionMeta）删除有失效（且注释详尽），实体级与模块级删除零失效——同一“删除需重生成”问题三个层级的处理不一致（F3 修实例不修类别的镜像形态：修了最小粒度、漏了更大粒度）。`removeDynModule`（DynCodeGen :367-369）存在但全仓库无调用方（grep 证据），失效入口实际悬空。
- **风险**: 已删除模块的代码继续执行意味着其 xbiz 中的权限/校验逻辑仍生效——安全与数据一致性双重窗口；运维侧表现为“删了还在”。
- **建议**: NopDynModuleBizModel/NopDynEntityMetaBizModel 补 afterEntityChange：删除时 codeCache.removeModule / genBizObjFiles 重建 + reloadModel（照抄 NopDynFunctionMetaBizModel 形态）；或在文档声明“删除动态对象后必须手动重发布”。
- **失效面枚举**: 变更钩子覆盖矩阵：NopDynFunctionMeta ✅（有 afterEntityChange）、NopDynEntityMeta ❌、NopDynModule ❌（仅 save 面有遮蔽检查，删除面无）、NopSysCodeRule ✅（clearCache）、NopSysSequence ✅（removeCache）、NopSysCompactExtField ✅（refreshCache）——6 个层级逐一登记，2 个 ❌。
- **信心水平**: 确定（钩子缺失是代码事实；后果链依赖删除操作实际发生）
- **误报排除**: 非已声明边界——无任何“删除后需手动刷新”的文档/错误提示；函数级的对偶修复证明该失效语义是本模块自己认定的契约。
- **复核状态**: 已保留

### [C6-102] initTenantCache 全量初始化发生在请求线程且持 per-ref 锁：初始化风暴无并发去重与预算

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:151-168`（DCL 等价：竞争线程在 synchronized(ref) 上阻塞等待而非快速失败）、`:130-136`（initTenantCache：全模块 DB 查询+逐模块 addModule 生成）
- **证据片段**:
  ```java
  synchronized (ref) {
      cache = ref.get();
      if (cache == null) {
          cache = initTenantCache(tenantId);   // 全模块 DB 查询 + 逐模块代码生成，秒级
          ref.set(cache);
      }
      return cache;
  }
  ```
- **严重程度**: P3（与 C6-002 驱逐问题组合后升级为可用性风险）
- **并发场景**: 冷启动后某租户的首批并发请求（或 C6-002 驱逐后的再访问）；N 个线程同租户并发
- **触发时序**: T1 进入 synchronized(ref) 执行 initTenantCache（设 5s）→ T2..Tn 在 monitor 上阻塞 5s（占住 globalWorker/HTTP 线程）→ 单次初始化正确去重（✅，TestDynCodeGenTenantCacheInit 钉死）但**等待面无预算**：初始化时长直接转化为 N-1 个线程的同步阻塞时长；100 租户全部冷启动时形成逐租户串行风暴。
- **现状**: 单飞正确性有测试；吞吐面（等待线程占用）无预算/无异步化。
- **风险**: 租户冷启动长尾放大为线程池占用；与 C6-002（驱逐触发重初始化）叠加后周期性发生。
- **建议**: 初始化挪到后台 executor + 请求线程短暂自旋/拒绝；或至少文档声明容器上限需 ≥ 实际活跃租户数。
- **失效面枚举**: 请求线程重初始化入口全集：getTenantCodeCache :151-168（本条）、getTenantResourceStore :203-207（同经 getTenantCodeCache）、getTenantBizModel :408-428（同）——单点修复覆盖全部。
- **信心水平**: 确定
- **误报排除**: 非低频路径——首访/驱逐后均为用户请求线程；判 P3 因正确性无虞（有测试），纯可用性代价。
- **复核状态**: 已保留

## 第 2 轮排查后维持原判/不立项的项

- SysI18nMessageLoader 无 destroy 注销：registerMessages 按 (locale,key) 覆盖写，幂等，无泄漏增长（仅启动一次）。不立项。
- GptCodeGen/OrmModelToDynEntityMeta/DynEntityMetaToOrmModel：无状态或线程封闭，无资源面。
- NopSysDaoConfigs/NopDynConfigs：纯配置常量持有。
- importExcel 的 fileStore 临时文件：detachFile :90 主动移交，失败路径（:76-88 抛异常时文件留在 TEMP 区）由文件存储自身过期机制兜底（平台惯例），watch。
