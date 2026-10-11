# 2411 WI2 quickstart 生成器跨平台修复与模板版本对齐

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI2，Phase A）
> Related: ai-dev/plans/2410-wi1-nop-bom-dangling-entries.md

## Purpose

quickstart 是仓库唯一的一键入门入口，其 generate.sh 在 GNU/Linux 上第一步即断（BSD 专用 `sed -i ''`）。本 WI 把生成脚本改为跨平台可移植实现，并对模板 pom 的三个版本疑点做 plan 级实测裁定（裁定的证据链落在本计划）；verify.sh 的既有 install-skip 启发式按 roadmap 要求在 README 显式标注失效场景与 FORCE_REBUILD 用法。

## Current Baseline

- `nop-stream/quickstart/generate.sh:89` 使用 `xargs -0 sed -i ''`：BSD sed 下 `''` 是就地编辑后缀；GNU sed 的 `-i` 后缀必须连写，独立词 `''` 被当作文件操作数导致失败。quickstart 内唯一一处 sed 用法（已 grep 复核）。
- `nop-stream/quickstart/template/pom.xml` 三个版本点实测：
  - `maven.compiler.release` = 17（:14）；根 pom `java.version`/`source`/`target`/`release` 全为 17（:25-30）——**平台真实基线是 17**，AGENTS.md 自述「Java 21」与根 pom 冲突，按 roadmap 以根 pom 为准。
  - `junit-jupiter` = 6.0.3（:40）；平台测试类路径实际解析 6.0.3（实测 `./mvnw -o dependency:tree -pl nop-stream/nop-stream-rocksdb`，经 quarkus.platform 3.35.1 import 链传递管理；nop-dependencies 中 5.7.0 属性已注释废弃）——roadmap v1.2 所述「quarkus BOM 传递管理 5.x 线、模板漂移」与 live repo 不符，**模板与平台解析版本一致，无漂移**。
  - `maven-surefire-plugin` = 3.2.2（:50）；根 pom `<build><plugins>` 生效版本同为 3.2.2（:171-174 字面量；:61 的 3.0.0-M4 属性全仓零引用）——**已对齐**（注意：该条目在 build/plugins 不在 pluginManagement，:272-414 的 pluginManagement 不含 surefire）。
  - 模板无 parent，第三方版本只能显式钉或自行引 BOM；现状钉版本形态保留。
- `nop-stream/quickstart/verify.sh:26-37` 在 jar 存在时跳过 install（注释 :26-27 自述 AR-28/AR-1 已知）；README「一键验证」段（:42-48）未标注该启发式失效场景。
- macOS（BSD sed）环境；Docker 29.4.3 可用（GNU 覆盖用）；本机 `~/.m2` 已含 nop-stream 2.0.0-SNAPSHOT 构件（verify.sh 走跳过路径）。

## Goals

- generate.sh 占位符替换改为可移植实现（临时文件替换，无平台分支），BSD 与 GNU 下行为一致，失败路径保持非零退出。
- 模板 pom 三版本点完成实测裁定并留档（裁定结果：三项均维持现状，不改模板）。
- README 标注 verify.sh install-skip 启发式的失效场景与 `FORCE_REBUILD=1` 用法。

## Non-Goals

- 不做平台 Java 基线升级（17 → 21 属平台级变更，roadmap 明示另立条目）。
- 不把 quickstart 纳入 reactor（E5：设计如此）。
- 不改 verify.sh 的跳过启发式本身（保留，仅文档化）。

## Scope

### In Scope

- `nop-stream/quickstart/generate.sh`：:88-94 的 sed 替换块改为临时文件替换循环（`find -print0 | while IFS= read -r -d '' f` 形态——不用 `find -exec`/`xargs` 包循环体，GNU/BSD find 均忽略被调命令退出码会静默吞错；bash 3.2 兼容，不用 `mapfile -d` 等 bash-4 构造）。
- `nop-stream/quickstart/README.md`：一键验证段补 FORCE_REBUILD 说明与失效场景。
- 模板三版本裁定记录（本计划即裁定载体，无模板文件改动）。
- roadmap WI2 条目 done 括注中订正「quarkus BOM 管理 5.x 线」为实测事实（junit-jupiter 平台解析 6.0.3，模板无漂移）——按 E4 防僵尸文本。

### Out Of Scope

- verify.sh 行为变更；模板 pom 任何内容变更；AGENTS.md 的 Java 21 表述修正（属平台基线裁定，非本 WI）。

## Execution Plan

### Phase 1 - 可移植化与文档化

Status: completed
Targets: `nop-stream/quickstart/generate.sh`、`nop-stream/quickstart/README.md`

- Item Types: `Fix`（GNU 下生成器首步即断为已确认缺陷）；模板版本点为 `Decision`（实测裁定，证据在本计划）

- [x] 修复前先在 macOS 用现状脚本生成一份 golden 产物存 `_tmp/quickstart-golden`（回归等价锚）（实测 11 文件）
- [x] generate.sh :88-94 改为临时文件替换（逐文件 `sed -e ... "$f" > "$f.tmp"`；`-e` 表达式与顺序原样保留；fail-fast 用 `|| { rm -f "$f.tmp"; exit 1; }` 显式传播——初版 `&&` 形态被探针 2 实测否决：AND-OR 列表非末命令失败不触发 set -e，循环跑完全部文件残留 11 个 .tmp，详见 Exit Criteria 探针条）
- [x] README 一键验证段补：install-skip 启发式失效场景（模板/依赖变更后陈旧 jar 掩盖问题）与 `FORCE_REBUILD=1` 强制重建用法
- [x] 模板三版本裁定留档（见 Current Baseline 三条实测：release 17 / junit 6.0.3 / surefire 3.2.2 均与平台一致，模板不动）
- [x] roadmap WI2 状态行括注补 junit 6.0.3 实测订正

Exit Criteria:

- [x] generate.sh 中不再有 `sed -i` 用法（实测 grep 零命中）
- [x] **macOS 端到端**：`nop-stream/quickstart/verify.sh` 全流程通过（[verify] OK——生成、生成工程 mvn test 3 拓扑全绿、TestStreamSqlQuickstart BUILD SUCCESS）；新实现 macOS 产物与 golden 产物逐字节 diff 一致
- [x] **GNU/Linux 覆盖**：Docker Hub 不可达（registry 连接拒绝），按审查建议的镜像钉改用本地已有 Debian bookworm 镜像 `python:3.13-slim-bookworm`（GNU sed + bash，userland 等价）——`docker run --rm -v "$PWD:/repo" -w /repo python:3.13-slim-bookworm bash -c ".../generate.sh _tmp/quickstart-linux2 ..."` 与 macOS golden 逐字节 diff 一致、占位符校验零命中
- [x] **fail-fast 负路径（mutation probe，两轮）**：探针 1（@groupId@ 替换改为匹配不存在占位符）→ 非零退出（尾部占位符校验兜住）；探针 2（sed 表达式畸形使 sed 本身失败）→ 初版 `&&` 形态**实测暴露 fail-fast 回归**：循环跑完全部 11 文件、残留 11 个 .tmp，exit 1 来自占位符校验而非 sed 失败传播（AND-OR 列表非末命令失败被 set -e 忽略）→ 改为 `|| { rm -f "$f.tmp"; exit 1; }` 后探针 2 复跑：exit 1 + 零 .tmp 残留；恢复后 grep 断言 `|| { rm -f` 子句存在（count 1）+ happy path 复跑与 golden 逐字节一致
- [x] README 含 FORCE_REBUILD=1 说明（已补「关于构件跳过启发式」段）
- [x] No new test required: shell 脚本修复无 Java 代码路径；验证面为上述双平台实跑 + fail-fast 探针（verify.sh 本身即端到端验证器）
- [x] No owner-doc update required（quickstart README 即最近 owner 面，本次同步属本 WI 交付本体；nop-stream-user-guide.md 无生成器行为描述，不涉）
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI2 条目

## Closure Gates

- [x] GNU/Linux 生成路径实测通过（Debian bookworm 容器，镜像钉替换已记录），占位符校验零命中、与 macOS golden 逐字节一致
- [x] macOS verify.sh 端到端全绿（3 拓扑 + SQL quickstart）且与 golden 产物逐字节一致
- [x] fail-fast 负路径探针完成：探针 2 实测暴露初版 `&&` 形态的传播缺陷并已修复（`|| { rm -f; exit 1; }`），修复后探针 exit 1 + 零残留、恢复后复绿、恢复以 grep 断言
- [x] 模板三版本裁定证据链完整留档（实测命令 + 结论）
- [x] roadmap WI2 括注含 junit 6.0.3 实测订正
- [x] 无 in-scope live defect 被降级为 follow-up（探针发现的 fail-fast 缺陷当轮修复，未降级）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2411-wi2-quickstart-portable-sed-and-version-adjudication.md --strict` 退出码 0

## Closure

Status Note: 全部 Exit Criteria 与 Closure Gates 经独立审计 PASS（审计者以临时副本自做 fail-fast 探针复现失败传播：畸形 sed 表达式 → exit 1 + 零 .tmp 残留）。GNU 覆盖因 Docker Hub 不可达以本地 Debian bookworm 镜像替代（userland 等价，镜像钉替换留档）；探针 2 实测暴露并当轮修复初版 `&&` 形态的 fail-fast 回归（未降级为 follow-up）；模板三版本点实测裁定维持现状，roadmap v1.2 的 junit「5.x 漂移」表述已在 roadmap WI2 括注订正。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_e4f24cd9-5120-4bf2-b6e6-e2ee74225315
- Evidence:
  - Exit Criteria 1-8：全部 PASS（sed -i 零命中、替换块形态与 -e 顺序核对、README FORCE_REBUILD、三产物目录 diff -r 逐字节一致、探针副本独立复现、版本裁定三点重测、roadmap 括注合规性检查）
  - Closure Gates：全部 PASS（审计者 bash -n 语法检查 + 自做探针 + happy path 产物核验）
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（completed 态复跑确认）
  - Anti-Hollow：happy path（_tmp/quickstart-mac-new、_tmp/quickstart-linux2 实跑产物）与失败路径（审计者独立探针）双路径实证，非纸面声明
  - Deferred 项分类检查：无 in-scope live defect 被降级；探针发现的 `&&` 形态缺陷当轮修复闭环

Follow-up:

- no remaining plan-owned work
