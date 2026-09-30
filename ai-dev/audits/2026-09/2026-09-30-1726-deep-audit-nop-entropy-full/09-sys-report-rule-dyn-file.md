# G9: nop-sys/report/rule/dyn/file 深度审计（首轮）

- 审计日期：2026-09-30
- 审计子代理：G9 首轮（维度 04 / 07 / 09 / 16）
- 仓库：nop-entropy-master（live code 为准，静态审计，未运行 mvn/test）

## 审计范围

模块组 G9 全部手写代码（排除 target/、`_` 前缀生成文件、.m2-repo、_tmp）：

- `nop-sys/`：序列号生成（SysSequenceGenerator）、分布式锁（SysDaoResourceLockManager）、事件队列（SysDaoMessageService/BroadcastEventProcessor/NonBroadcastEventProcessor）、编码规则（SysCodeRuleGenerator/DefaultCodeRule）、字典/i18n 装载（SysDictLoader/SysI18nMessageLoader）、Maker-Checker 实体与 BizModel、19 个 BizModel。
- `nop-report/`：report-core（ExpandedSheetGenerator/Evaluator、ExcelRecordOutput/Input、ReportEngine、renderer）、report-pdf（PdfReportRenderer、FontManager）、report-docx、report-service BizModel。
- `nop-rule/`：rule-core（RuleManager、RuleModelCompiler、ExecutableMatrixRule/DecisionTree、RuleServiceImpl）、rule-dao（DaoRuleModelLoader/Saver）、rule-service BizModel。
- `nop-dyn/`：DynCodeGen/InMemoryCodeCache/GptCodeGen（运行时建模+代码生成）、DynEntityMetaToOrmModel/OrmModelToDynEntityMeta、15 个 BizModel。
- `nop-file/`：DaoResourceFileStore（上传/下载/去重/临时文件清理）。

覆盖维度：04（ORM 模型与实体设计）、07（BizModel 规范遵循）、09（错误处理与错误码）、16（测试覆盖与质量）。

### 机械基线核实结论（主 agent 提供）

- 裸异常（nop-sys 3、nop-rule 2）：main 代码仅 1 处真实项——`NopSysDaoException extends RuntimeException`（已记为 [G9-09-01]）；其余为 `catch (RuntimeException)` 转译（非违规）与 test 断言。
- System.out/printStackTrace（nop-report 28、nop-rule 8）：**全部位于 src/test**，main 代码零命中（`grep -rn "System\.out\.println\|printStackTrace" nop-report/*/src/main nop-rule/*/src/main` 为空）。test 代码不报。
- @Inject private：本模块组 main 代码零命中，与全仓结论一致。

### 深读但未立项的文件（近期已被加固，注释带审计修复痕迹，本轮无新发现）

SysSequenceGenerator（序列号并发、defaultCache TTL、cacheSize null 拆箱防护）、SysDaoResourceLockManager（holderId/version 条件删除、duplicate-key vs DB 故障分级日志）、SysDaoMessageService（周期任务异常保护、cleanup、多订阅者告警）、SysCodeRuleGenerator（bounded cache）、DaoResourceFileStore 的 newPath 路径穿越防御、ExcelRecordOutput 的句柄/临时目录清理、PdfReportRenderer 的 PDDocument finally close、FontManager 的 CJK 回退、DynCodeGen 的租户缓存并发初始化。这些文件质量显著高于模块组平均水平。

---

## 发现

### [G9-04-01] nop-file FILE_HASH 列全链路无写入，宣传的 "Hash 去重" 能力不存在

- **文件**: `nop-file/model/nop-file.orm.xml:52-53`；`nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java:168-223`
- **证据片段**:
  ```xml
  <!-- nop-file/model/nop-file.orm.xml:52 -->
  <column code="FILE_HASH" displayName="文件摘要" name="fileHash" precision="200" propId="11"
          stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="File Hash"/>
  ```
  ```java
  // DaoResourceFileStore.saveFile —— 唯一的常规上传写路径，通篇没有任何 fileHash 计算/赋值
  String fileId = newFileId();                       // UUID
  String filePath = newPath(record.getBizObjName(), fileId, entity.getFileExt());
  entity.setFileId(fileId);
  entity.setOriginFileId(fileId);
  entity.setFilePath(filePath);
  ...
  entity.setFilePath(filePath);
  dao.saveEntity(entity);
  ```
- **严重程度**: P1
- **现状**: `nop_file_record.FILE_HASH` 列在源模型中定义、生成的 xmeta 中以 `queryable/sortable/insertable/updatable` 完整暴露（`_NopFileRecord.xmeta` prop name="fileHash"），但全仓无任何写入者：`DaoResourceFileStore.saveFile` 不计算摘要，上传链上游（nop-biz-file-core 的 `UploadRequestBean`/`NopFileStoreBizModel`/`IFileRecord`）也无 hash 字段与摘要计算；`fileHash` 全仓唯一赋值点是 `copyFile()` 中 `newRecord.setFileHash(record.getFileHash())`（复制一个恒为 null 的值）。`docs-for-ai/03-modules/reusable-modules-overview.md:19,58` 将该模块能力宣传为 "Hash 去重"。
- **风险**: (1) 选型误导：用户按文档预期获得上传级物理去重，实际每次上传都落新物理文件，存储随重复上传线性膨胀，且 `isUniqueRef` 的物理文件删除判断只依赖 `originFileId`，与 hash 无关；(2) GraphQL/xmeta 暴露一个恒为 null 的 "文件摘要" 字段，前端与调用方会基于它构建不存在的功能；(3) 缺 hash 使基于内容审计/秒传/防篡改的后续功能无地基。
- **建议**: 二选一并对齐文档：a) 实现去重——上传时流式计算 SHA-256 写入 fileHash，命中已有记录时复用 filePath（copyFile 已有引用计数语义可复用）；b) 若近期不打算实现，在 reusable-modules-overview.md 与 nop-file.md 中移除 "Hash 去重" 宣传，并考虑在 xmeta 上将 fileHash 降为 `published="false"`。
- **信心水平**: 确定（`grep -rn "fileHash" nop-file/`、`grep hash UploadRequestBean/NopFileStoreBizModel/IFileRecord/DaoFileRecord` 均无写入点）
- **误报排除**: 不是 "列暂留待未来使用" 的合理预留——文档把该能力列为模块选型卖点，且 xmeta 已把它作为可查询字段公开给前端；能力宣称与行为不符属公开面问题。
- **复核状态**: 未复核

### [G9-04-02] nop_rule_definition 业务键 (RULE_NAME, RULE_VERSION) 无唯一约束且无索引

- **文件**: `nop-rule/model/nop-rule.orm.xml:43-49, 79-96`；`nop-rule/nop-rule-dao/src/main/java/io/nop/rule/dao/model/DaoRuleModelLoader.java:92-107`
- **证据片段**:
  ```xml
  <!-- 唯一的主键是代理键 RULE_ID；RULE_NAME/RULE_VERSION 无 <indexes>、无 unique-key -->
  <column code="RULE_ID" displayName="主键" mandatory="true" name="ruleId" precision="32" primary="true" .../>
  <column code="RULE_NAME" displayName="规则名称" mandatory="true" name="ruleName" precision="500" propId="2" .../>
  <column code="RULE_VERSION" displayName="规则版本" mandatory="true" name="ruleVersion" propId="3"
          stdDataType="long" stdSqlType="BIGINT" .../>
  ```
  ```java
  // DaoRuleModelLoader.loadRuleDefinition —— 规则解析的必经路径
  filters.add(eq(PROP_NAME_ruleName, ruleName));
  filters.add(eq(PROP_NAME_status, RULE_STATUS_ACTIVE));
  if (ruleVersion != null && ruleVersion > 0) {
      filters.add(eq(PROP_NAME_ruleVersion, ruleVersion));
  }
  QueryBean query = new QueryBean();
  query.addFilter(FilterBeans.and(filters));
  query.addOrderField(PROP_NAME_ruleVersion, true);
  NopRuleDefinition entity = dao.findFirstByQuery(query);
  ```
- **严重程度**: P2
- **现状**: 规则引擎对外解析契约是 `ruleName(+ruleVersion)`（`RuleManager.getRule` → `RuleServiceHelper.buildResolveRulePath` → `DaoRuleModelLoader.loadRuleDefinition`），但表上没有 (RULE_NAME, RULE_VERSION) 唯一约束，也没有任何二级索引（整个 nop-rule.orm.xml 的 `<index>` 计数为 0）。
- **风险**: (1) 每次规则装载（组件缓存 miss 时）按 RULE_NAME 过滤是全表扫描，RULE_NAME precision=500 的大字段过滤代价随规则数增长；(2) 无唯一约束时同 (name, version, status=active) 可存在多行——Excel 导入/并发保存/手工 SQL 都可能产生重复，`findFirstByQuery` 的选择结果不确定，规则解析结果不可复现；版本相同时排序无法裁决。(3) 与 nop-auth（7 个索引）/nop-job（12 个索引）的仓库惯例偏离。
- **建议**: 在 `nop-rule/model/nop-rule.orm.xml` 为 NopRuleDefinition 增加 `<indexes><index name="IX_NOP_RULE_DEF_NAME_VER" unique="true"><column name="ruleName"/><column name="ruleVersion"/></index></indexes>`（MySQL 下 RULE_NAME(500) 需前缀索引长度，可在 index column 上限定）；同时评估对 status 的过滤是否应纳入键语义（active 唯一 vs 历史版本共存，取决于 DaoRuleModelSaver 的版本分配约定）。
- **信心水平**: 确定（模型与查询代码均已核实；唯一约束语义需产品确认）
- **误报排除**: 不是 "数据量小无需索引" 的合理省略——规则表是规则引擎主解析入口，且缺少的是**唯一性约束**（数据完整性）而不仅是性能索引；对照 nop-auth/nop-job 同类模型均有显式索引。
- **复核状态**: 未复核

### [G9-04-03] nop-file/nop-rule/nop-report/nop-dyn 四模块 ORM 源模型零索引定义

- **文件**: `nop-file/model/nop-file.orm.xml`（全文无 `<index>`）；`nop-rule/model/nop-rule.orm.xml`（同）；`nop-report/model/nop-report.orm.xml`（同）；`nop-dyn/model/nop-dyn.orm.xml`（同）
- **证据片段**:
  ```bash
  $ grep -c "<index " nop-file/model/nop-file.orm.xml nop-rule/model/nop-rule.orm.xml \
      nop-report/model/nop-report.orm.xml nop-dyn/model/nop-dyn.orm.xml
  0 / 0 / 0 / 0        # 对照: nop-auth=7, nop-job=12, nop-sys=2
  ```
  ```java
  // DaoResourceFileStore.isUniqueRef —— 每次 detachFile/removeTempFileByOwner 必经
  TreeBean filter = FilterBeans.and(
          eq(NopFileRecord.PROP_NAME_originFileId, record.getOriginFileId()),
          ne(NopFileRecord.PROP_NAME_fileId, record.getFileId()));
  query.addFilter(filter);
  return dao.findFirstByQuery(query) == null;   // nop_file_record.originFileId 无索引 → 全表扫描
  ```
- **严重程度**: P2
- **现状**: 四个模块的 ORM 源模型都没有定义任何二级索引，而其中存在已知热点查询路径：`nop_file_record.originFileId`（isUniqueRef，每次文件解绑/临时清理执行）、`nop_file_record.(createdBy,bizObjId)`（removeTempFileByOwner）、`nop_rule_log.ruleId`（日志表按规则查询，append-only 增长）、`nop_report_result_file.rptId`（结果文件按报表查询，PK 是代理键 sid）、`nop_report_definition_auth.rptId`/`nop_report_dataset_ref`（复合 PK 已覆盖）、nop-dyn 的各 `moduleId` 外键列（NopDynPage/NopDynEntityMeta/NopDynPropMeta 按模块级联装载）。
- **风险**: 文件表/日志表/结果文件表都是随业务运行单调增长的表，无索引的按列过滤会随数据量退化为全表扫描；nop-file 的 isUniqueRef 位于每次文件解绑路径上，文件量大的系统会感受到明显抖动。
- **建议**: 按实际查询补索引：nop_file_record(originFileId)、nop_file_record(createdBy,bizObjId)、nop_rule_log(ruleId, createTime)、nop_report_result_file(rptId)、nop-dyn 外键列按装载路径评估；与 [G9-04-02] 一并在源模型修改后重新生成。
- **信心水平**: 确定（模型零索引 + 查询代码路径均核实）
- **误报排除**: 不是生成链缺失 `<indexes>` 支持——nop-sys/nop-auth/nop-job 的同结构模型已用 `<indexes>` 落地（nop-sys 事件表索引注释明确说明为消费轮询补建），说明链路可用且这是模块间一致性缺口。
- **复核状态**: 未复核

### [G9-04-04] nop_report_datasource.datasourceConfig 凭据类字段未按平台收敛约定标注 enc/not-pub

- **文件**: `nop-report/model/nop-report.orm.xml:262-264`；生成暴露链 `nop-report/nop-report-meta/src/main/resources/_vfs/nop/report/model/NopReportDatasource/_NopReportDatasource.xmeta:34-37`
- **证据片段**:
  ```xml
  <!-- ORM 源模型：无 tagSet="enc,not-query,not-sort,not-pub" -->
  <column code="DATASOURCE_CONFIG" displayName="数据源配置" mandatory="true" name="datasourceConfig"
          precision="4000" propId="4" stdDataType="string" stdSqlType="VARCHAR"
          i18n-en:displayName="Config"/>
  ```
  ```xml
  <!-- 生成的 xmeta：完整公开 -->
  <prop name="datasourceConfig" displayName="数据源配置" propId="4" ... mandatory="true"
        queryable="true" sortable="true" insertable="true" updatable="true">
      <schema type="java.lang.String" precision="4000"/>
  </prop>
  ```
- **严重程度**: P2
- **现状**: 报表数据源实体的连接配置列（JDBC URL/用户名/密码所在字段，comment 即 "数据源配置"）以明文存储（无 `enc` 列级加密），且生成 xmeta 将其公开为可查询/可排序字段，API OutputBean（`NopReportDatasourceOutputBean.getDatasourceConfig`）同样输出。`docs-for-ai/02-core-guides/model-first-development.md` 的「凭证/敏感字段的多层收敛约定」要求此类字段在 **ORM 源模型** 标注 `tagSet="enc,not-query,not-sort,not-pub"`（nop-ai 的 apiKey 已按此收敛并有回归测试先例）。
- **风险**: 一旦应用录入真实外部库连接（该实体的存在目的），密码明文落库、可经 GraphQL 查询/排序面被具备读权限的用户批量拉取；写在生成物/Delta 上的补救会在下次 codegen 被覆盖（平台已明确的脆弱修复模式）。
- **建议**: 回源修改 `nop-report.orm.xml`：`DATASOURCE_CONFIG` 加 `tagSet="enc,not-query,not-sort,not-pub"`（配合 `ui:show="X"`），重新生成 meta/api；如连接串需在保存时加密还原，参考 nop-ai apiKey 的 `enc` 列绑定（`DefaultOrmColumnBinderEnhancer`）。
- **信心水平**: 很可能（"该列承载密码" 依实体语义推断；无加密标注与公开暴露是确定事实）
- **误报排除**: 不是 "尚未被消费所以无害"——列已可经管理端录入、xmeta/API 已公开，约束必须在数据进入前建好；且平台文档明确该类限制必须写在 ORM 源模型而非生成物。
- **复核状态**: 未复核

### [G9-04-05] nop-sys displayName 本地化错置与状态字典缺失

- **文件**: `nop-sys/model/nop-sys.orm.xml:117-121, 138-140, 232-233`
- **证据片段**:
  ```xml
  <!-- 1) 实体级英文显示名拼写错误 -->
  <entity className="io.nop.sys.dao.entity.NopSysDictOption" ... displayName="字典明细" ...
          i18n-en:displayName="Dictionay Item">        <!-- 应为 Dictionary Item -->

  <!-- 2) 列级英文显示名复制粘贴错置：IS_DEPRECATED 配了 IS_PARENT 的英文文案 -->
  <column code="IS_DEPRECATED" defaultValue="0" displayName="是否已废弃" domain="boolFlag" mandatory="true"
          name="isDeprecated" propId="8" stdDataType="byte" stdSqlType="TINYINT"
          i18n-en:displayName="Is Parent"/>

  <!-- 3) Maker-Checker 审批状态无字典绑定（对照 NopSysEvent.eventStatus 有 ext:dict="core/sys-event-status"） -->
  <column code="STATUS" displayName="审批状态" mandatory="true" name="status" propId="14" stdDataType="int"
          stdSqlType="INTEGER" i18n-en:displayName="Status"/>
  ```
- **严重程度**: P3
- **现状**: 英文 locale 下字典明细实体显示为 "Dictionay Item"（拼写错误）；`isDeprecated` 字段英文标签显示 "Is Parent"（从旧字段复制残留）；`NopSysCheckerRecord.status`（Maker-Checker 审批状态，int 状态机）在 `<dicts>` 中无对应字典也未 `ext:dict` 绑定，而同文件的 `core/sys-event-status` 为同类状态列建立了完整范式。
- **风险**: 英文界面标签错误直接面向用户；审批状态无字典意味着前端只能显示裸数字 0/10/20，且无法做状态过滤下拉；后续给状态赋予语义时缺少单一事实源。
- **建议**: 修正两处 i18n-en 文案；在 nop-sys.orm.xml `<dicts>` 增加 `core/sys-checker-status`（WAITING/APPROVED/REJECTED 等，按框架 try-action 回调语义定值）并给 STATUS 列加 `ext:dict`。
- **信心水平**: 确定（文案错置）；字典缺失为规范差距（很可能）
- **误报排除**: 不是纯格式偏好——i18n-en 文案是生成 xmeta/页面的用户可见标签；状态列无字典违反维度 04 检查项 "实体的 status 字段是否有对应字典定义"，且同文件已有正确范式可对照。
- **复核状态**: 未复核

### [G9-07-01] BizModel 自定义 public 方法未同步到 I*Biz 接口（4 模块 6 个类 13 个方法）

- **文件**: `nop-rule/nop-rule-dao/src/main/java/io/nop/rule/biz/INopRuleDefinitionBiz.java`（空接口）对照 `nop-rule/nop-rule-service/src/main/java/io/nop/rule/service/entity/NopRuleDefinitionBizModel.java:54-105`；同类：`INopSysUserVariableBiz`（getVar/setVar）、`INopDynPageBiz`（getPage/getPageJson/savePageJson）、`INopDynModuleBiz`（exportExcel/importExcel/generateByAI）、`INopDynEntityMetaBiz`（allProps）、`INopRuleNodeBiz`（predicateLabel）
- **证据片段**:
  ```java
  // INopRuleDefinitionBiz —— 仅继承 ICrudBiz，无任何自定义方法声明
  public interface INopRuleDefinitionBiz extends ICrudBiz<NopRuleDefinition>{
  }
  ```
  ```java
  // NopRuleDefinitionBizModel —— 3 个 public 方法均不在接口上
  @BizQuery
  public ConditionFieldsResponse getConditionFields(
          @Name(NopRuleConstants.RULE_ID_NAME) @Optional String ruleId, IServiceContext context) { ... }

  @BizQuery
  public DictBean getOutputFields(...) { ... }

  @BizQuery
  public WebContentBean getInputJsonSchema(@RequestBean RuleKeyBean ruleKey, IServiceContext context) { ... }
  ```
- **严重程度**: P2
- **现状**: `docs-for-ai/02-core-guides/service-layer.md` 将 "BizModel 新增 public 方法必须同步到 I*Biz 接口" 列为强制规则（`BizProxyFactoryBean` 代理只识别接口方法，接口是能力清单）。G9 模块组的 I*Biz 接口全部是 codegen 生成的空壳（仅 extends ICrudBiz），所有手写自定义 public 方法都未回填接口。对照：nop-datav 与 nop-ai 模块族已按规则把自定义方法声明到 I*Biz（`grep -rl "@BizQuery" --include="I*Biz.java"` 命中 nop-datav 8 个、nop-ai 1 个）。
- **风险**: 通过注入 `I*Biz` 代理调用这些方法会抛 unsupported-method；接口作为能力清单与实现漂移，跨模块调用方（如未来批处理/集成模块想复用 `generateByAI`、`getConditionFields`）无法以契约方式发现能力。当前全仓无调用方注入这 6 个接口的自定义方法，故未构成运行期破坏。
- **建议**: 按平台规则把 6 个类的 13 个自定义 public 方法回填到对应 I*Biz 接口（带 `@BizQuery/@BizMutation` + `@Name`/`@RequestBean` 注解）；或如模块组决定不开放跨模块调用，改用 `@BizAction` 收窄语义并在文档登记例外。
- **信心水平**: 确定（规则文本、接口内容、方法清单三方可交叉验证）
- **误报排除**: 不是 "GraphQL 能调就不算问题"——service-layer.md 明确 I*Biz 是对外能力的唯一清单且规则为强制；也不是生成物不可改问题：接口文件在 `*/src/main/java/.../biz/` 非 `_` 前缀路径，属可手写补充的保留层。
- **复核状态**: 未复核

### [G9-07-02] NopSysMakerCheckerRecord 实体/BizModel/stale _gen 三件孤儿残留（重命名后未清理）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/entity/NopSysMakerCheckerRecord.java`；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/entity/_gen/_NopSysMakerCheckerRecord.java`（2026-03-25 生成）；`nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/entity/NopSysMakerCheckerRecordBizModel.java`
- **证据片段**:
  ```java
  // NopSysMakerCheckerRecordBizModel —— 未注册到任何 beans.xml
  @BizModel("NopSysMakerCheckerRecord")
  public class NopSysMakerCheckerRecordBizModel extends CrudBizModel<NopSysMakerCheckerRecord>{
      public NopSysMakerCheckerRecordBizModel(){
          setEntityName(NopSysMakerCheckerRecord.class.getName());
      }
  }
  ```
  ```bash
  # 源模型与生成聚合模型均已不含该实体（只剩重命名后的 NopSysCheckerRecord）
  $ grep -c "NopSysMakerCheckerRecord" nop-sys/model/nop-sys.orm.xml \
      nop-sys/nop-sys-dao/src/main/resources/_vfs/nop/sys/orm/_app.orm.xml   # 均为 0
  $ grep -n "MakerChecker" nop-sys/.../beans/_service.beans.xml              # 仅注册 NopSysCheckerRecordBizModel
  ```
- **严重程度**: P2
- **现状**: 实体经历过 `NopSysMakerCheckerRecord` → `NopSysCheckerRecord` 的重命名（源模型现实体名 NopSysCheckerRecord，displayName 仍为 "MakerChecker审批记录"；web/action-auth/beans 均已切换）。但旧名三件套残留：保留层实体、BizModel、以及 `_gen/_NopSysMakerCheckerRecord.java`（生成时间早于当前模型）。旧 BizModel 未注册 beans（NopIoC 无注解扫描，永不实例化），旧实体不在运行时 ORM 模型中。
- **风险**: (1) 同包内两个几乎同名的实体（NopSysMakerCheckerRecord/NopSysCheckerRecord）持续误导后续开发与 AI 阅读；(2) stale `_gen` 文件永不再生成，随平台模板演进悄然漂移；(3) 若有人误注册/误注入该 BizModel，运行期因实体不在 ORM 模型而启动失败，排查成本高。
- **建议**: 删除三个残留文件（`NopSysMakerCheckerRecord.java`、`_gen/_NopSysMakerCheckerRecord.java`、`NopSysMakerCheckerRecordBizModel.java`），全仓引用已确认为零。
- **信心水平**: 确定（beans 注册、源模型、生成模型、全仓引用四方核实）
- **误报排除**: 不是 "保留层给 Delta 定制预留"——无任何 Delta/外部引用，且实体已不在 ORM 模型中无法参与运行时；重命名迁移漏清理是结构性残留而非设计。
- **复核状态**: 未复核

### [G9-07-03] NopDynPageBizModel.getPage 在 moduleId 未命中时静默丢弃模块过滤条件

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynPageBizModel.java:111-126`
- **证据片段**:
  ```java
  NopDynModule module = moduleDao.findFirstByExample(moduleExample);   // 按 path 中的 nopModuleId 查

  QueryBean query = new QueryBean();
  query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_pageName, pageName));
  query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_pageGroup, pageGroup));
  if (module != null)
      query.addFilter(FilterBeans.eq(NopDynPage.PROP_NAME_moduleId, module.getModuleId()));
  // module == null 时：按任意模块的同名页面匹配

  NopDynPage entity = findFirst(query, null, context);
  if (entity == null)
      throw new NopException(ERR_DYN_PAGE_NOT_EXISTS).param(ARG_PATH, path);
  ```
- **严重程度**: P3
- **现状**: 页面路径中的 moduleId 在 NopDynModule 表查不到时（模块已下线/拼写错误/租户隔离场景），模块过滤被静默省略，查询退化为全局 (pageName, pageGroup) 匹配，命中后返回**其他模块**的同名页面内容。周围代码（pageGroup 解析、pageName 校验）都有显式 ERR_DYN_INVALID_PAGE_PATH 防护并带审计注释，唯独该分支静默放宽。
- **风险**: 跨模块页面名冲突时返回错误内容（前端渲染出别的模块页面）；多租户动态模块场景下可能越过模块归属读到不该返回的页面配置。请求方拿到 200 + 错误页面而非明确的 page-not-exists，排障困难。
- **建议**: `module == null` 时直接抛 `ERR_DYN_PAGE_NOT_EXISTS`（或专门的 unknown-module 错误码），与该方法内其他路径校验的 fail-fast 风格一致。
- **信心水平**: 确定（行为推演自代码，无补偿逻辑）
- **误报排除**: 不是有意的宽松兼容——同方法对路径格式、分组段、pageName 空值全部显式抛错，唯独模块缺失放行；且注释显示该方法近期刚做过加固，此分支属遗漏。
- **复核状态**: 未复核

### [G9-09-01] NopSysDaoException 直接继承 RuntimeException，绕过框架异常体系

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/NopSysDaoException.java:3-10`；使用点 `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:481-483`
- **证据片段**:
  ```java
  // NopSysDaoException.java —— 未继承 NopException
  public class NopSysDaoException extends RuntimeException {
      public NopSysDaoException(String message) {
          super(message);
      }
      public NopSysDaoException(String message, Throwable cause) {
          super(message, cause);
      }
  }
  ```
  ```java
  // SysDaoMessageService.invokeDurableConsumers —— 唯一抛出点（防御性守卫）
  if (broadcast) {
      throw new NopSysDaoException("Broadcast durable path should invoke one consumer at a time");
  }
  ```
- **严重程度**: P2
- **现状**: `docs-for-ai/02-core-guides/error-handling.md` 反模式清单明确："自定义异常类不继承 NopException（如 extends RuntimeException）——所有业务异常必须直接或间接继承 NopException"。该类只有 (String)/(String, Throwable) 构造器（无 ErrorCode 构造器），是文档点名的违规形态。当前唯一抛出点在广播持久订阅的防御路径。
- **风险**: 该异常逃逸到 BizModel/GraphQL 边界时无法映射为结构化错误响应（无 errorCode、无 param、无 i18n），前端拿到的是裸 500；模块异常类范本缺失也会诱导后续新增代码继续沿错误基类扩展。
- **建议**: 改为 `extends NopException` 并按平台范本提供 `(String)` 与 `(ErrorCode)` 双构造器（对照 `NopAiException`/`StreamException` 模式）；顺带可将该守卫改为 ErrorCode 定义以便区分。
- **信心水平**: 确定
- **误报排除**: 不是 "内部防御性异常无所谓"——error-handling.md 的约束针对类型本身而非抛出频率；且同模块其余错误全部走 NopSysErrors + NopException，唯此一处偏离。
- **复核状态**: 未复核

### [G9-09-02] XptErrors 三处错误码 ID 复制粘贴复用，行/列父格错误不可区分

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/XptErrors.java:39-44, 46-54, 56-62`
- **证据片段**:
  ```java
  ErrorCode ERR_XPT_INVALID_ROW_PARENT =
          define("nop.err.xpt.invalid-row-parent",
                  "表格[{sheetName}]的单元格[{cellPos}]的行父格[{rowParent}]必须配置为行展开", ...);

  ErrorCode ERR_XPT_INVALID_COL_PARENT =
          define("nop.err.xpt.invalid-row-parent",            // ← 复用 ROW 的 code，应为 invalid-col-parent
                  "表格[{sheetName}]的单元格[{cellPos}]的列父格[{colParent}]必须配置为列展开", ...);

  ErrorCode ERR_XPT_COL_PARENT_CONTAINS_LOOP =
          define("nop.err.xpt.row-parent-contains-loop",      // ← 复用 ROW 的 code；消息文案也仍是"行父格"
                  "表格[{sheetName}]的单元格[{cellPos}]的行父格[{colParent}]不能包含循环指向，...", ...);

  ErrorCode ERR_XPT_MISSING_VAR_CELL =
          define("nop.err.xpt.missing-var-ds",                // ← 复用数据集错误的 code
                  "未定义单元格变量cell");                      //   且未声明任何 ARG，共享 code 却期望 dsName
  ```
- **严重程度**: P2
- **现状**: `define("...")` 的错误码 ID 是 i18n key、日志匹配与前端错误码判断的唯一标识。三对常量共享同一 ID：列父格非法复用行父格 code（消息文案已改但 ID 未改）；列父格循环复用行父格循环 code（连描述文案也仍写 "行父格"）；missing-var-cell 复用 missing-var-ds 的 code（后者声明了 dsName 参数，前者无参数）。
- **风险**: (1) i18n 按 code 查翻译时列父格错误被翻译成行父格文案（en locale 下描述与实际参数不匹配）；(2) 调用方/运维按 errorCode 字符串定位问题时行/列混淆；(3) `ERR_XPT_MISSING_VAR_CELL` 与共享 code 的参数约定冲突（渲染 "数据集[null]不存在" 类畸形消息）。这些都是报表模板编译期错误，主要伤害是诊断质量与错误码契约漂移。
- **建议**: 分别改为 `nop.err.xpt.invalid-col-parent`、`nop.err.xpt.col-parent-contains-loop`（同步修正描述文案中的 "行父格"→"列父格"）、`nop.err.xpt.missing-var-cell`。修改 ID 属行为变更，需检查是否有 i18n yaml/前端按旧 ID 匹配（预期无，因为旧 ID 语义本就错位）。
- **信心水平**: 确定（`grep define(...) | sort | uniq -d` 三组重码可复现）
- **误报排除**: 不是刻意的错误合并（同 ID 但描述文案刻意区分了行列场景，说明本意是两个独立错误）；错误码 ID 重复在平台上无编译期检查，属典型复制粘贴缺陷。
- **复核状态**: 未复核

### [G9-09-03] 查询 API 对空参数返回 null（getConditionFields/getOutputFields）及空错误容器占位类

- **文件**: `nop-rule/nop-rule-service/src/main/java/io/nop/rule/service/entity/NopRuleDefinitionBizModel.java:54-58, 69-72`；占位类 `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/NopSysErrors.java`、`nop-file/nop-file-service/src/main/java/io/nop/file/service/NopFileErrors.java`、`nop-report/nop-report-service/src/main/java/io/nop/report/service/NopReportErrors.java`
- **证据片段**:
  ```java
  @BizQuery
  public ConditionFieldsResponse getConditionFields(
          @Name(NopRuleConstants.RULE_ID_NAME) @Optional String ruleId, IServiceContext context) {
      if (StringHelper.isEmpty(ruleId))
          return null;                          // 静默 null，无日志无错误
      ...
  ```
  ```java
  // nop-sys-service/NopSysErrors.java —— codegen 生成的空占位（三模块同形态）
  public interface NopSysErrors{
  }
  ```
- **严重程度**: P3
- **现状**: 两个 `@BizQuery` 以 `return null` 表达 "参数为空"（error-handling.md 禁止把异常/非法输入转成返回值；此处因参数标注 @Optional 属灰色地带，但 null 响应对 GraphQL 消费方是静默失败）。另三个 service 层 `Nop*Errors` 接口是零常量空壳，作为模块错误码容器无内容，属可忽略的脚手架噪音。
- **风险**: 前端拿到 `data: null` 无法区分 "没传 ruleId" 与 "服务异常"；空错误容器让后续贡献者不确定该往哪加错误码（nop-sys 实际错误定义在 nop-sys-dao 的 NopSysErrors，同名类两处易混淆）。
- **建议**: 空参数时抛参数校验错误（`checkMandatoryParam` 模式，参照同文件可用的平台工具）或返回空 fields 列表；空 `Nop*Errors` 如确认不再使用可删除或在 javadoc 指向真实容器（nop-sys-dao/NopSysErrors）。
- **信心水平**: 确定（现状）；建议方向为规范判断
- **误报排除**: @Optional 参数返回 null 有可辩护性，故仅 P3；但对照同模块 `NopSysUserVariableBizModel.setVar` 使用 `checkMandatoryParam` 的显式校验范式，静默 null 不是本仓库统一风格。
- **复核状态**: 未复核

### [G9-16-01] nop-file 核心存储 DaoResourceFileStore 零测试：安全修复无回归锁

- **文件**: `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java`（402 行）；测试目录现状：`find nop-file -path "*/src/test/*" -name "*.java"` 仅 3 个文件（NopFileWebPagesTest、NopFileWebCodeGen、NopFileCodeGen）
- **证据片段**:
  ```java
  // newPath —— 上一轮审计补的路径穿越纵深防御，无任何测试锁定
  protected String newPath(String bizObjName, String fileId, String fileExt) {
      // 纵深防御：bizObjName/fileExt 直接拼入存储路径，非受控调用方传入 ../ 等即可越出存储根目录
      if (!StringHelper.isValidSimpleVarName(bizObjName))
          throw new NopException(ERR_FILE_INVALID_BIZ_OBJ_NAME).param("bizObjName", bizObjName);
      if (!StringHelper.isEmpty(fileExt) && !fileExt.matches("[A-Za-z0-9]+"))
          throw new NopException(ERR_FILE_INVALID_FILE_EXT).param("fileExt", fileExt);
      ...
  ```
  ```java
  // getRecord —— 文件级访问控制（bizObjName/objId/fieldName 三元组校验），同样无测试
  if (!record.getBizObjName().equals(bizObjName) || !Objects.equals(objId, record.getBizObjId())
          || !Objects.equals(record.getFieldName(), fieldName))
      throw new NopException(ERR_FILE_NOT_ALLOW_ACCESS_FILE)...
  ```
- **严重程度**: P2
- **现状**: nop-file-dao（模块组内唯一含实质手写逻辑的文件层，402 行）没有单元/集成测试；模块仅有的 3 个 test 文件全是 codegen 冒烟与页面测试。文件上传相关的唯一真实测试在框架层 `nop-biz-file-core` 的 `TestNopFileStoreBizModel`（仅覆盖 checkFileExt 大小写归一 2 个用例），不触及 DaoResourceFileStore。
- **风险**: 路径穿越防御（newPath 校验）、文件访问控制（getRecord/getFileResource 三元组匹配）、引用计数式物理删除（isUniqueRef + removeResource，错删会破坏其他记录引用的文件）、临时文件清理（removeTempFileByOwner）都是安全/数据完整性关键路径，且部分是近期审计修复的产物——无回归测试意味着下一次重构可静默回退这些修复。
- **建议**: 为 DaoResourceFileStore 补 NopAutoTest/JUnit 用例，最低集：a) newPath 拒绝 `../`、非法 fileExt（含空、点、分隔符）；b) getRecord/getFileResource 对象三元组不匹配抛 ERR_FILE_NOT_ALLOW_ACCESS_FILE；c) detachFile 在有共同 originFileId 引用时不删物理文件、无引用时删除；d) removeTempFileByOwner 清理 DB 行与物理文件。
- **信心水平**: 确定
- **误报排除**: 不是 "逻辑简单无需测试"——4 个高危路径中 2 个带上一轮审计修复注释（修复无锁）；也不是框架层已覆盖——TestNopFileStoreBizModel 明确只测 NopFileStoreBizModel.checkFileExt。
- **复核状态**: 未复核

### [G9-16-02] nop-report-service 无测试目录，12 个 BizModel 零覆盖

- **文件**: `nop-report/nop-report-service/`（`ls nop-report/nop-report-service/src/test` 不存在）
- **证据片段**:
  ```bash
  $ find nop-report -path "*/src/test/*" -name "*.java" | grep -v target | grep -c "service"
  0     # 48 个测试文件全部位于 core/pdf/docx/demo/web，service 子模块为 0
  ```
- **严重程度**: P3
- **现状**: nop-report-service 的 12 个 BizModel（含 NopReportDefinitionBizModel、数据集/数据源/权限等）无任何测试。绝大多数是 21 行的平凡 CrudBizModel（风险低），但该层完全空白意味着报表定义/数据集保存链路（defaultPrepareSave 钩子等）无护栏。
- **风险**: 与 nop-rule-service（有 TestNopRuleDefinitionBizModel 覆盖 Excel 导入/校验链）对照，报表定义的同类导入链一旦增加逻辑将无测试地基；当前实际风险低（逻辑平凡）。
- **建议**: 至少对含钩子的 BizModel 补保存校验用例；或在模块内明确登记 "该层仅 CRUD、逻辑上移 report-core" 的约定以免后续堆逻辑。
- **信心水平**: 确定（目录缺失）；风险评级偏低是因实现确实平凡
- **误报排除**: 不是 "测试在别的模块覆盖了"——report 的 48 个测试都在 core/pdf/docx 引擎层，service 层无对应覆盖。
- **复核状态**: 未复核

---

## 零/低发现维度的检查说明

- **维度 09（错误处理）main 代码整体状况良好**：除上述 3 条外，抽查的 SysSequenceGenerator（duplicate-key 静默属初始化竞态的合理容忍且有日志）、SysDaoResourceLockManager（duplicate-key=trace/DB故障=warn 的分级日志）、DaoResourceFileStore（NopException.adapt 保留异常链）、RuleErrors/XptErrors/NopSysErrors 的参数常量与 `nop.err.{模块}.*` 命名、PDF/Excel 层 `IoHelper.safeClose` 使用均合规。基线中的 System.out/printStackTrace 与裸异常计数全部落位 test 代码。
- **维度 07（BizModel）其余面**：19+15+12+8+4 个 BizModel 的构造范式（setEntityName + extends CrudBizModel + implements I*Biz）、@Name/@RequestBean 参数、IServiceContext 末参、NopRuleDefinitionBizModel 的 defaultPrepareSave/Update 钩子 + validateModel（保存前可解析校验）、RuleServiceImpl 的 bean 级 xmeta（RuleResultBean 等）均符合平台规范；未发现伪 BizModel（RuleService 有生成 bean-meta 支撑）。
- **维度 04 其余面**：五模块实体的主键设计（sid varchar(32) tagSet=seq / 业务复合键）、审计字段（createTime/updateTime/createdBy/updatedBy）配置、snake_case 列名、i18n-en 覆盖率（除 [G9-04-05] 两处错置外全覆盖）、nop-sys 事件表双索引与租约列、级联删除（dict→dictOptions、ruleDefinition→ruleNodes/ruleRoles 均 cascadeDelete 且有对应 to-one 反向）均正常。

## 主 agent 汇总所需统计

| 维度 | 条目 | P0 | P1 | P2 | P3 |
|------|------|----|----|----|----|
| 04 ORM 模型 | G9-04-01..05 | 0 | 1 | 3 | 1 |
| 07 BizModel | G9-07-01..03 | 0 | 0 | 2 | 1 |
| 09 错误处理 | G9-09-01..03 | 0 | 0 | 2 | 1 |
| 16 测试覆盖 | G9-16-01..02 | 0 | 0 | 1 | 1 |
| 合计 | 13 | 0 | 1 | 8 | 4 |

- P0/P1：[G9-04-01] nop-file FILE_HASH 全链路无写入、宣传的 Hash 去重不存在（P1）。
- 纯风格条目：0（[G9-04-05] 含用户可见文案错误与字典缺失，不按纯风格计）。

## 子项复核结论

复核人：独立复核代理 R2（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G9-04-01] | 保留（维持 P1） | 逐项重开证据：`nop-file/model/nop-file.orm.xml:52` 确有 FILE_HASH 列；通读 `DaoResourceFileStore.saveFile`（:168-223）确认无任何摘要计算；全仓 `grep setFileHash` 中作用于 NopFileRecord 的只有 `DaoResourceFileStore.java:387`（copyFile 复制恒 null 值），nop-code 下的命中均为 NopCodeFile 实体无关；上游 `nop-service-framework/nop-biz-file-core` 的 UploadRequestBean/IFileRecord/NopFileStoreBizModel 均无 hash 字段；`docs-for-ai/03-modules/reusable-modules-overview.md:19,58` 确实宣传 "Hash 去重"；生成的 `_NopFileRecord.xmeta:62` 将 fileHash 以 queryable=true 公开。能力宣称与行为不符 + 公开恒 null 字段，符合 P1 "公开面问题" 判据。 |
| [G9-04-02] | 保留（维持 P2） | 重开 `nop-rule/model/nop-rule.orm.xml`：`grep -c "<index "` = 0，全文无 `unique-key`；`DaoRuleModelLoader.java:92-107` 确认 loadRuleDefinition 以 ruleName+status(+ruleVersion) 过滤后 `findFirstByQuery`，同 (name,version) 多行时结果不确定。数据完整性 + 性能双缺口，P2 恰当。 |
| [G9-04-03] | 保留（维持 P2） | 独立重跑计数：nop-file/nop-rule/nop-report/nop-dyn 四个源模型 `<index ` 均为 0，对照 nop-sys=2、nop-auth=7、nop-job=12 与报告完全一致；`DaoResourceFileStore.java:351-355` 确认 isUniqueRef 按 originFileId 过滤（:151/:341 两处调用均在解绑/清理路径）。 |
| [G9-04-04] | 保留（维持 P2） | 重开 `nop-report/model/nop-report.orm.xml:262-264`：DATASOURCE_CONFIG 无 tagSet；`_NopReportDatasource.xmeta:34-37` 确认 queryable/sortable/insertable/updatable 全开；`docs-for-ai/02-core-guides/model-first-development.md:355-380` 的「凭证/敏感字段的多层收敛约定」明确要求 ORM 源模型标注 `tagSet="enc,not-query,not-sort,not-pub"` 且附 nop-ai 回归测试先例。维持 P2（"该列承载密码"为语义推断，与首轮信心水平一致）。 |
| [G9-07-01] | 保留（维持 P2） | 重开 `INopRuleDefinitionBiz.java`：确为仅 extends ICrudBiz 的空接口；`NopRuleDefinitionBizModel.java:54-100` 确认 3 个 @BizQuery 方法不在接口上；`service-layer.md:104-112` 明确 I*Biz 是 BizProxyFactoryBean 动态代理的跨模块调用契约；另核 `BizProxyFactoryBean.build()`（nop-biz）委托 `bizObj.asProxy()`（JDK 接口代理），接口外方法确实无法经代理调用；nop-datav 5 个 I*Biz 带 @BizQuery 的先例属实。全仓无受影响调用方，P2 恰当。 |
| [G9-07-02] | 保留（维持 P2） | 三个残留文件实际存在（2026-03-25 时间戳）；`grep -c NopSysMakerCheckerRecord` 于源模型与 `_app.orm.xml` 均为 0；`_service.beans.xml:29-34` 只注册 NopSysCheckerRecordBizModel；全仓引用检索仅命中这 3 个文件自身。删除建议安全，P2 恰当。 |
| [G9-09-01] | 保留（维持 P2） | 重开 `NopSysDaoException.java`：确为 `extends RuntimeException` 且仅有 (String)/(String,Throwable) 构造器；全模块唯一抛出点 `SysDaoMessageService.java:482`；`error-handling.md:209` 反模式表逐字命中（"自定义异常类不继承 NopException"）。因该类是模块唯一异常基类、会被后续代码继承，P2（而非 P3）成立。 |
| [G9-09-02] | 保留（维持 P2） | 重开 `XptErrors.java:39-62` 并以 `grep -o 'define("nop.err.xpt...' | sort | uniq -d` 独立复现三组重码：invalid-row-parent（COL 复用 ROW）、row-parent-contains-loop（COL 复用 ROW 且描述文案仍写"行父格"）、missing-var-ds（CELL 复用 DS 且参数约定冲突）。诊断质量缺陷，P2 恰当。 |
| [G9-16-01] | 保留（维持 P2） | `find nop-file -path "*/src/test/*"` 仅 3 个文件（NopFileWebPagesTest/NopFileWebCodeGen/NopFileCodeGen），无一触及 DaoResourceFileStore；`TestNopFileStoreBizModel` 仅 2 个 @Test（checkFileExt）；`DaoResourceFileStore.java:247` "纵深防御" 审计修复注释与 :304/:324 访问控制抛错均无测试锁定。安全修复无回归锁，P2 恰当。 |

**R2 统计**：复核 9 条（P1×1、P2×8）——保留 9、降级 0、驳回 0。

### 复核中发现的附带线索

- 核对 [G9-04-01] 时确认 nop-code 的 `NopCodeFile.fileHash` 有正常写入（`CodeIndexService.java:1695,2745`），说明 hash 写入在平台内有现成范式可参照，nop-file 的修复成本低于首轮报告的预估——不影响判级，仅降低修复阻力。
