# 02 · 全仓自动化扫描结果

> 扫描范围：全仓 `src/main/java`（排除 `_gen/`、`target/`、`_dump/`、`_tmp/`、src/test）。
> 脚本：`_tmp/conformance-scan.sh`；逐命中文件清单见 `evidence/S*.files`（22 份）。
> 定性说明：**框架内部模块**（nop-kernel / nop-core-framework / nop-persistence / nop-service-framework / nop-utils / nop-format）部分命中属依赖层级客观限制（如 nop-commons 无法使用 nop-api-core 的 NopException），不计违规；**业务模块**命中按下表定性。

## 结果总览

| 扫描 | 命中 | 主要分布（业务模块） | 定性 |
|------|------|---------------------|------|
| S01 `@Inject private` | **0 文件** | — | ✅ 全仓通过（NopIoC 规则落地良好） |
| S02 `@BizMutation`+`@Transactional` | **0 文件** | — | ✅ 全仓通过 |
| S03 裸时间 API（`System.currentTimeMillis/nanoTime`、`LocalDateTime.now`、`new Date()` 等） | **202 文件** | nop-ai-agent 42、stream-runtime 18、datav-service 16、auth-service 15、stream-core 12、sys-dao 7、credential-service 5 | ⚠️ **P1 级面状问题**：TestClock 注入对这些代码失效；持久化时间戳（datav ExportTaskRecovery、ai DbUsageRecorder）直接破坏 IClock 时间线 |
| S04 直接 `new ObjectMapper`/Gson | 4 文件 | 仅 nop-benchmark-json | ✅ 通过（benchmark 属测试工具） |
| S05 Commons-lang3/Guava import | 12 文件 | nop-kernel 10（框架自身）、**nop-stream-cep `SharedBuffer.java` 1** | ⚠️ P3：stream-cep 应换 StringHelper |
| S06 `IDaoProvider`/`IOrmTemplate` | 159 文件 | datav-service 25、metadata-service 20、auth-service 18、sys-dao 9、code-service 6、credential-service 5 | ⚠️ 需分层定性：nop-auth/nop-sys 命中多为参照基线允许的 store/遗留形态（§2.3）；**datav 25 个文件未经深审定性，列为待查热点**（见 06） |
| S07 `@SqlLibMapper` | 11 文件 | auth-dao 7、job-dao 1 | ℹ️ 合规（原子 SQL 场景，SQL lib 机制本身） |
| S08 `extends RuntimeException/IAE/ISE` | 12 文件 | 框架 9 + `BashSyntaxParser`(ai-shell)、`BashSandboxException`(ai-toolkit)、`StreamRuntimeException` 注：后者实际 extends NopException | ⚠️ P2：ai-shell/ai-toolkit 两个解析器异常应挂到模块异常体系 |
| S09 `throw new RuntimeException/IAE/ISE/Exception` | 398 文件 | stream-core 32、ai-agent 27、stream-runtime 16、ai-core 7、ai-toolkit 6、ai-shell 6、datav-service 3（框架模块占大头但业务模块合计显著） | ⚠️ **P1 级面状问题**（详见 04/05 分报告） |
| S10 直连 `Files.read*`/`FileInputStream` | 25 文件 | ai-agent 3（Checkpoint/SessionFileReader）、stream-connector file 族 2、ai-code-analyzer 1（框架 resource 实现类属豁免） | ⚠️ P2：非本地文件契约场景应走 VFS `IResource` |
| S11 转型注入的 BizModel | 1 文件 | 仅框架 `CrudBizModel` 自身 | ✅ 通过 |
| S12 Spring `@Value(` | 2 文件 | 仅 nop-spring 桥接模块 | ✅ 通过（桥接层职责） |
| S13 `.getBytes()` 无字符集 | 8 文件 | **nop-ai 3**（ai-toolkit HttpRequestExecutor 凭据、ai-coder、ai-skills） | ⚠️ P2：凭据场景跨平台数据不一致风险 |
| S14 `class *Controller/*ServiceImpl` | 12 文件 | demo 2（真实 Spring 示例，豁免）；auth 4、wf/rule 1、ai 2 均实现内部 SPI，踩线但合规（基线 §2.6） | ℹ️ |
| S15 `BeanContainer.` | 55 文件 | 框架启动器/web/task-core 占大头；**实体类内 0 处 ✅**；但 3 个 BizModel 使用：`NopJobFireBizModel`、`NopJobTaskLogBizModel`、`NopMetaQualityCheckpointBizModel` | ⚠️ P3：BizModel 内 service-locator 式取 bean，应改注入（待逐处定性，见 06） |
| S16 `dao().getEntityById/findAllByQuery/saveEntity` 模板 | 21 文件 | **metadata-service 8**、auth-service 6、credential-service 3、sys-service 1、ai-service 1、ai-gateway 1（框架 CrudBizModel 自身 1） | ⚠️ P2：多数有注释的降级场景，metadata 侧并入 P1-1 整改 |
| S17 `__XGEN_FORCE_OVERRIDE__` | 479 文件 | 全部位于 `-api`/`-demo` 模块的已提交生成物 | ℹ️ 信息项：这些文件手改会被 codegen 覆盖，无法用 grep 区分是否被手改过；无证据表明被手改，不立案 |
| S18 `printStackTrace` | 10 文件 | benchmark 4、cli 1、**stream `StreamMaintenanceMain` 1**、pdf/excel 等 | ⚠️ P3：应换 LOG.error |
| S19 Spring 注解（@Autowired/@Component/@Service） | 14 文件 | nop-spring 桥接 7、demo 3、benchmark 3 | ✅ 通过（桥接层职责） |
| S20 `Thread.sleep`（src/main） | 23 文件 | kernel 5、stream-runtime 3、datav 2、ai-agent 2... | ⚠️ P2：等待逻辑应换 CompletableFuture/回调（引擎测试钩子除外） |
| S21 `@SingleSession` | 13 文件 | 全部位于 job store/coordinator、retry-engine、sys-dao、dyn/auth service 的非 BizModel 类 | ✅ 通过（non-BizModel ORM 访问的合法注解，无一标在 @BizModel 上） |
| S22 跨层 import（dao/core 引 service、任何模块引 web） | 初扫 178 文件 | 修正后：`io.nop.commons.service.LifeCycleSupport` 系合法 commons 包；真实跨层违规 **0**；唯一 `io.nop.*.web.*` 引用来自 demo | ✅ 通过（DAG 合规） |

## 三个面状问题（跨模块共性问题）

### F-A 裸时间 API（S03，202 文件）——全仓最大共性偏差

业务模块 top：nop-ai-agent(42)、nop-stream-runtime(18)、nop-datav-service(16)、nop-auth-service(15)、nop-stream-core(12)。典型有害形态是**写库时间戳**绕过 CoreMetrics：

```java
// nop-datav-service/.../export/NopDatavExportTaskRecovery.java:121
Timestamp now = new Timestamp(System.currentTimeMillis());
// nop-ai-agent/.../usage/DbUsageRecorder.java:131
Timestamp now = new Timestamp(System.currentTimeMillis());
```

后果：autotest `TestClock` 时间线对这些记录失效（过期/超时类查询测试 count=0 的已知根因），生产环境多时钟源不一致。注意参照模块 nop-auth 的 MFA store 族也有 26 处（见 01 §3）——**这是历史惯性问题，不是单个模块的问题**，建议立项统一整改（机械替换 + CoreMetrics 已有 `timeoutToExpireTime` 等辅助）。

### F-B 裸异常（S09，业务模块约 110 文件）

集中区：nop-stream（core 32 + runtime 16 + cep 2）、nop-ai（agent 27 + core 7 + toolkit 6 + shell 6）、nop-datav 5。与 S08 的自定义异常类缺失叠加。已有反面案例证明危害：`StreamOpsHttpServer:338` 靠 `instanceof IllegalStateException` + 消息嗅探区分 HTTP 409（见 04）。

### F-C BizModel 层 DAO 直连（S06+S16）

nop-metadata 实证 85 处（P1，见 05）；nop-datav 25 文件待定性（见 06）；nop-auth/nop-sys/nop-job 命中属基线允许形态。

## 证据索引

`evidence/` 下 22 份 `S*.files` 为逐扫描命中文件清单（绝对相对路径，可直接复查）。复扫命令见 `_tmp/conformance-scan.sh`。
