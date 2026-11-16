# Wave 9 — `--cache` 测试结果缓存实测验证（含附带缺陷发现）

- Date: 2026-11-16
- Status: adjudicated
- 语料: `~/sources/liteflow` liteflow-testcase-el-nospring（53 类 / 233 tests，绿）；raw 证据 `~/sources/liteflow/_tmp/nop-ib/test-20261116-*.log` 与 `~/sources/liteflow/.nop-ib/test-cache.json`

## 三态实测（--cache 开关）

| 轮次 | 命令 | kind | wall | exit |
|---|---|---|---|---|
| run1（首跑/记录） | `test -pl :nospring --cache` | junit-fork | 43s | 0 |
| run2（命中） | 同上 | **cached-up-to-date** | **<1s** | 0 |
| run3（逃逸） | `--cache --force` | junit-fork | 44s | 0 |

cached 输出显式：人类行 `(cached-up-to-date)` + warning `cache hit (--cache): fingerprint unchanged since last GREEN run; use --force to re-execute`；RunLog JSON 含 kind 与 warning——不冒充执行结果。

## 失效验证（变异探针）

1. 注释级变异（不改字节码）→ compile 后类文件未变 → 缓存命中=**正确**（mvn 同场景也跑同样的类）。
2. 字节码级变异（新增实例字段）→ **首测暴露真缺陷**：fast-path `compileModule` 把 test 源编译进 `target/classes`（`-d` 证据），`target/test-classes` 从不更新 → 派发 classpath（test-classes 优先）持续用旧字节码——缓存命中只是表象，`ib check` 的 test-source 增量编译本身在写错目录。
3. **修复**（`IncrementalCompileExecutor.compileModule`）：testCompile 输出目录改 `testOutputDir`（删除清扫同步改树）；修复后复测：变异 compile → test-classes mtime 更新（22:56）→ `--cache` **正确 MISS**（junit-fork）；恢复源码 + recompile → 亦正确 miss-and-record。
4. 附带影响评估：该缺陷使 wave2 以来所有 test-source 的 fast-path 编译产物落在 target/classes（main 污染 + test-classes 陈旧）。对既有 parity 结论无影响（parity 用 mvn 产物对拍；fork 执行面 test-classes 优先且未经过 fast-path test 编译的场景不受影响），但对 `check --paths <test-source>` 的增量语义是真缺陷——本次修复，无 parity 回归（77/77 + liteflow 实测全绿）。

## 指纹成分与不可见输入（披露）

- 在指纹内：模块全部类 contentHash+ABI（单元索引）、传递依赖模块 ABI、externalArtifacts、surefire 全 facts（argLine/skip/includes/excludes/groups/excludedGroups/sysprops/env）。
- **不在指纹内**：外部环境（DB/容器/host 状态）、依赖模块非 ABI 方法体（inlineConstants 差异由 ABI+常量传播覆盖主面）、**reactor 依赖模块无索引时的 ABI 表**（此时该依赖 ABI 变化不入指纹——外部 jar 面由 externalArtifacts 兜底；audit m3 披露）。cached 绿的环境失效由 `--force` 兜底；预存红不入缓存（天然每次真跑）。
- 索引缺失/模型过期/指纹不可得 → 不命中（fail-safe）。
