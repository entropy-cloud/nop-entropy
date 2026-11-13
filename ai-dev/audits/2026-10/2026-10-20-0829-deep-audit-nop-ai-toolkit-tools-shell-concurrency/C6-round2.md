# C6 深挖第 2 轮 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计日期：2026-10-20（深挖第 2 轮，合并批 C1/C4/C6/C8）
- 定位：第 1 轮 C6 共 8 条（C6-001…C6-008）集中于沙箱收尾/docker 清理/管道死锁；本轮按派发单指定邻接面深挖，编号续接 C6-009。

## 深挖范围与方法

| # | 指定深挖面 | 核对方法与结论（file:line 为 live code） |
|---|---|---|
| 1 | mavenvfs 句柄（VfsMavenCli 进程流） | 逐文件核实：`VfsMavenCli` 全文 170 行——`execute` 为 stub（VfsMavenCli.java:129-142 仅返回命令列表，注释明示"实际执行需要使用ProcessBuilder"），**无进程、无流、无句柄**；`DeltaVirtualFileSystem` 的流全部为工厂式即时返回（`Files.newInputStream/newOutputStream`，:139/:164），生命周期归调用方；`DeltaWorkspaceReader` 无流操作。第 1 轮"该子包未发现 C6 缺陷"结论复核成立。 |
| 2 | 临时目录/临时文件创建-清理配对 | 全三根 grep `createTempDirectory|createTempFile|deleteOnExit|\.tmp|createDirectories`：临时文件仅两处生成点——`LocalToolFileSystem.atomicReplace:193`（`target.getName()+".tmp"+UUID`）与 `ThoughtStorage.atomicWriteText:216`（同型）——二者均在 finally `Files.deleteIfExists` 配对（LocalToolFileSystem:204-210 / ThoughtStorage:227-233），**配对通过**；目录创建点（DeltaVirtualFileSystem:68,160,187、LocalToolFileSystem.mkdirs:245-251、CreateDirectoryExecutor:35）为目标目录语义而非临时目录，无清理义务。无新发现。 |
| 3 | LocalToolFileSystem 与 toolkit 文件操作的流配对全清单 | `LocalToolFileSystem` 逐方法核对读流配对：`readText:104-114`（finally safeClose）、`readLines:132-145`（同）、`countLines:157-168`（同）、`grepInFile:397-417`（finally safeCloseObject）——**全部通过**；写路径 `writeText:172-179`（append 走平台 `FileHelper.writeText` 内部关闭；overwrite 走 atomicReplace）；`globRecursive:332`（try-with-resources Stream）。toolkit 17 个 executor 无一处直开 `newInputStream/newBufferedReader/readAllBytes`（grep 证实），文件 IO 全部收敛到 `IToolFileSystem`/平台 helper。无新发现。 |
| 4 | ThoughtStorage 文件句柄 | 全文核对（243 行）：读经 `FileHelper.readText`（:92，平台内部关闭）；写经 `atomicWriteText`（:214-234，temp + finally deleteIfExists 配对）；`exportSession/importSession` 经平台 `FileHelper.writeText/readText`（:156,:167）。模块自身不持有任何打开的流对象——**句柄面干净**；存储目录只增不删的生命周期问题已由第 1 轮 C6-007 立案。 |

追加核对：`ShellCommandExecutor` 的流所有权（`RedirectedStreams` owned 列表 ：419-448 + finally close :414-416 配对通过；MERGE 修复注释 ：534-542 确认同实例单次登记）；`handleFdOutputRedirect` 的 DuplexShellOutput 非持有包装（:512，close 不下传，所有权归外层，通过）；`TeeOutput.close` 只关 owned（:55-61，通过）。

---

## 新增发现

### [C6-009] BashExecutionTracker.cancelExecution 先 remove 注册项后执行销毁，且无兄弟 killTree 的 UnsupportedOperationException/SecurityException 守卫——取消半途失败后注册项已丢失，取消不可重试、进程不可寻址

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/BashExecutionTracker.java:82-98`；守卫模板对照 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java:208-226`
- **证据片段**:
  ```java
  // BashExecutionTracker.java:82-98 —— remove 已生效后才开始销毁，销毁无守卫
  public boolean cancelExecution(String callId) {
      InFlight execution = callId == null ? null : inFlight.remove(callId);   // L83 先摘除
      if (execution == null) {
          return false;
      }
      execution.process.descendants().forEach(ProcessHandle::destroyForcibly); // L87 无守卫
      execution.process.destroyForcibly();                                      // L88 无守卫
      if (execution.cancelAction != null) { ... }                                // L89-96 仅捕 RuntimeException
      return true;
  }
  // 对照 HostBashSandbox.killTree:210-219 —— 同族兄弟对同一 API 显式防御：
  // } catch (UnsupportedOperationException | SecurityException ignored) { ... }
  ```
- **严重程度**: P2
- **并发场景**: 心跳/取消线程（agent 侧，经 `IBashSandbox.cancelExecution` 进入）与执行线程（`execute` finally 的 `unregister`）并发；取消线程在 L83 摘除注册项后执行进程树销毁，此窗口内抛出的任何异常使销毁半途而废。
- **触发时序**: T1 取消线程对 callId=X 调 `cancelExecution` → L83 成功摘除 InFlight（此刻起该执行对后续取消请求不可寻址）→ T1 执行 L87 `process.descendants()` 抛 `UnsupportedOperationException`（或 `SecurityException`/`destroyForcibly` 抛 SecurityException——同文件 killTree 的显式防御证明作者认定该 API 面存在此抛出模式）→ 异常穿出 `cancelExecution`，进程树未被杀（Host 后端）或 `cancelAction`（docker kill）未执行（Docker 后端）→ T2 任何后续 `cancelExecution(X)` 走 L84 返回 false（"无在途注册"）——**取消永久不可重试**，进程只能靠自身 wallSeconds 超时回收；返回值语义还把"已取消"与"注册项已丢失"混为一谈。
- **现状**: 第 1 轮 C8-009 失效面 ⑤ 曾将 cancelExecution 标注"已正确（descendants + process 双 destroy）"——正常路径成立，但其**顺序（remove-before-act）与守卫缺失**未被检查：这是 F8（清理不对称）的取消侧变体，失败路径既不回滚注册项也无重试通道。
- **风险**: 触发前置（ProcessHandle 不支持/SecurityManager 拒绝）罕见，故 P2；一旦触发，"放弃运行中命令"的操作静默失效且不可恢复，长时运行 agent 的失控进程只能等满 wall 帽（后台形态 1h）。
- **建议**: (1) 销毁成功后才 remove（或失败时回填 `inFlight.putIfAbsent` 保证可重试）；(2) 照抄 killTree 的守卫模式包住 L87-88（`UnsupportedOperationException | SecurityException` 降级为仅 `destroyForcibly`）；(3) `cancelAction` 的 catch(RuntimeException) 扩为宽口径兜底并保留失败标记，避免半取消状态不可见。
- **失效面枚举**（F3）：同族"进程树销毁"站点全清单——`BashExecutionTracker.cancelExecution:87-88`（**本条：无守卫 + remove-before-act**）；`HostBashSandbox.killTree:210-220`（**有守卫**，修复模板；remove 面不适用——不经注册表）；`DockerBashSandbox.doExecute 超时分支:137-141`（killContainer，失败吞没问题已由 C6-002 立案；不经注册表摘除，无本条顺序面）；`runShortCommand`（C6-003 立案）；`BashSandboxPaths`（纯校验无进程）。全族已覆盖。
- **信心水平**: 很可能（顺序缺陷与守卫缺失是代码事实；触发依赖 UOE/SecurityException 可达性——同文件 killTree 的防御性守卫是其可达性的最强内部证据）
- **误报排除**: 非"重审第 1 轮已通过站点"的翻案——第 1 轮检查的是"进程是否被杀"（正常路径成立），本条主张的是该函数自身的失败路径结构（顺序 + 守卫），属第 1 轮未检查的邻接面；亦非"best-effort 清理可接受"——注册项先摘除使失败不可重试，超出 best-effort 语义。
- **复核状态**: 保留(P2)｜新发现（第 2 轮深挖登记）——结构事实回码核验成立（L83 remove-before-act、L87-88 无 UnsupportedOperationException/SecurityException 守卫、HostBashSandbox.killTree L210-219 守卫模板对照属实）；一处口径修正：L89-96 为 catch(RuntimeException) 后 **rethrow**（注释明示 "cancellation must surface its failure"），cancelAction 失败是响亮的而非静默——缺陷本体是"任何半途失败后注册项已摘除、取消不可重试、返回值语义混淆"；建议 (3)"宽口径兜底"实为语义变更决策，留 7.1；与 WI 3.1 C3-011(MA-08 agent 侧取消不达工具进程) 为上下游同链两面、零重复立项；P2 维持（触发前置 UOE/SecurityManager 罕见）

---

## 维度集外线索（overlay）

### [nop-ai-toolkit-tools-shell-CL-020] [C9/F9] `>` 重定向目标的写失败被 IoHelper.safeClose 静默吞掉：命令退出码 0、输出无痕丢弃

- 位置：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/FileShellOutput.java:43-48`（close→flush→writeText 抛出）＋吞没点 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:414-416, 440-447`（`RedirectedStreams.close` 经 `IoHelper.safeClose` 收口）。
- 形态：`echo hi > <sandbox 外路径>`（或任何 `fileSystem.writeText` 抛"Path not allowed"的写失败）发生在命令**返回后的 close-flush** 里：`FileShellOutput.close` 先置 `closed=true` 再 `flush()`，`flush` 内 `fileSystem.writeText` 抛 `NopAiException` → 异常沿 `RedirectedStreams.close` → `IoHelper.safeClose` 被吞（无 WARN、无计数）→ `executeSimpleCommandWithContext` 正常返回 0 → 命令"成功"，重定向内容无痕丢失。`FileShellOutput.close` 先置标志再 flush 的顺序还使重试语义不可能（二次 close 直接短路）。
- 为何超本维度：非句柄泄漏（流配对正确），是失败路径的异常可见性（F9）——"吞异常导致状态与外部世界不一致"。供 C9 维度复核。
- 建议：`RedirectedStreams.close` 对 owned 输出的 close 异常至少记 WARN 并向命令结果注入 stderr 提示；或 `FileShellOutput.flush` 失败时把异常挂到实例状态、由 `executeSimpleCommandWithContext` 转非零退出码。

---

## 继续判定

本轮新增 1 条（P2×1：C6-009）+ 1 条 overlay（CL-020）。派发单四个深挖面中：mavenvfs 句柄（stub 无进程）、临时目录/文件配对（两处生成点均 finally 配对）、LocalToolFileSystem/toolkit 文件操作流配对（全清单通过）、ThoughtStorage 句柄（平台 helper 内关闭）全部收敛——**新增为 P2 且配对表有明确收敛证据**，输出 **终止信号**（C6 维度建议收敛，不再开第 3 轮）。

---

## 复核结论（WI 3.2 C6 维度独立复核）

- C6-009 **保留(P2)**：remove-before-act 与守卫缺失两项结构事实回码成立；口径修正一处——cancelAction 的 catch 为 rethrow，失败响亮而非静默，缺陷本体收窄为"半途失败不可重试 + 返回值语义混淆"。触发前置（UOE/SecurityManager）罕见，P2 校准成立。
- 本轮四个深挖面收敛结论**复核成立**（抽查 VfsMavenCli stub、DeltaVirtualFileSystem 工厂式流、ThoughtStorage.atomicWriteText finally 配对，与表中记录一致）；终止信号认可，C6 维度不再开第 3 轮。
- C6 维度 9 条（C6-001…009）复核判定汇总（判定表、跨维指针意见、高风险子项说明详见 `C6-resource-lifecycle.md` ## 维度复核结论）：**保留 7 / 降级 2 / 驳回 0；最终 P1×4（C6-001/002/003/008）+ P2×5（C6-004/005 条件化 + C6-006/007/009）**。零驳回、零同案重复立项；两处判级分歧（沙箱族 vs WI 3.1 MA-17、shell 管道族条件化 vs C4/C8 侧初裁 P1）已登记为 7.1 终裁输入。
