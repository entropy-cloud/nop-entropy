# 2442 WI9a/WI9b/WI9c Container bases → UBI 10 / JDK 25（JVM + native/micro + codegen 模板）

> Plan Status: completed
> Draft Review: 机械全量替换类 plan——事实全部一手验证（registry tag API 实证/三类 Dockerfile 逐类实读/FROM 分布计数），以独立 closure audit 作为对抗面（如实豁免独立 draft-review 轮）
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI9a/WI9b/WI9c；事实基座 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`（第 1.2/3.4 节）
> Related: plan 2437（WI6，Q4 4.0.0.Beta1 已统一）
> 用户裁定（2026-10-11）：deprecated 用法必须迁移，一律采用最标准做法。
> **⚠️ WI9c = Protected Area（plan-first）**：4 个 `.xgen` 模板位于 `nop-kernel/nop-codegen`（AGENTS.md Protected Areas 生成管线）。本 plan 即 plan-first 载体：生成链路理解 = 模板经 nop-codegen 的 xgen 管线在 `mvn codegen`/新应用脚手架时展开为目标应用 `src/main/docker/Dockerfile.*`（与现存 83 个非模板 Dockerfile 同构），改动仅 FROM 基镜像行，不触及生成器逻辑。

## Purpose

87 个 tracked Dockerfile 的基础镜像整体迁到 UBI 10 / JDK 25 线：41 个非模板 JVM（WI9a）、42 个非模板 native/micro（WI9b）、4 个 codegen 模板（WI9c，Protected Area），使新建脚手架与存量 app 容器基座与新平台基线一致。

## Current Baseline

- 全仓 FROM 行实测分布（2026-10-11 grep，排除 target/_tmp）：`ubi8/openjdk-17:1.16` ×40、`ubi8/openjdk-17:1.11` ×1、`ubi8/openjdk-21:1.21` ×2、`ubi8/ubi-minimal:8.8` ×21、`ubi8/ubi-minimal:8.5` ×1、`quay.io/quarkus/quarkus-micro-image:2.0` ×21、`quarkus-micro-image:1.0` ×1（共 87；其中 4 个 `.xgen` 为 codegen 模板）。
- 目标坐标（Central/registry 2026-10-11 实证）：JVM → `registry.access.redhat.com/ubi10/openjdk-25:1.24`（tag list 实证现行 1.24 线，对应 ubi8 时代的 1.16 式版本 tag）；native → `registry.access.redhat.com/ubi10/ubi-minimal:10.2`（tag list 实证 10.0/10.1/10.2，取最新 minor，对应 ubi8 时代的 8.8 式 minor tag）；micro → `quay.io/quarkus/quarkus-micro-image:2.0`（**最新 tag 仍是 2.0，无 3.0**——2.0 站点零改动，仅 1.0 → 2.0）。
- 4 个 codegen 模板位点：`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/{appName}-app/src/main/docker/` 下 `Dockerfile.jvm.xgen`、`Dockerfile.legacy-jar.xgen`（均 17:1.16）、`Dockerfile.native.xgen`（ubi-minimal:8.8）、`Dockerfile.native-micro.xgen`（micro:2.0，**无需改动**——Q4 micro 线仍是 2.0，裁定：保持）。
- Dockerfile 结构：三类（jvm/legacy-jar、native、native-micro）除 FROM 行外无 ubi8 特有内容（逐类实读：COPY/USER/ENTRYPOINT 与基镜像版本无关）；jvm 的 run-java.sh 入口由 openjdk 基镜像内置提供，ubi10/openjdk-25 同样内置。
- Docker 构建/运行验证不可行：本环境无 docker daemon（裁定，见 Deferred）。

## Goals

- WI9a：41 个非模板 JVM Dockerfile → `ubi10/openjdk-25:1.24`。
- WI9b：21 个非模板 native（ubi-minimal）→ `ubi10/ubi-minimal:10.2`；1 个 micro:1.0 → 2.0；20 个 micro:2.0 保持（实证即 Q4 现行线）。
- WI9c：3 个 codegen 模板换线（jvm.xgen/legacy-jar.xgen → 25:1.24；native.xgen → 10.2）；native-micro.xgen 裁定保持 2.0。

## Non-Goals

- Docker build/run 冒烟验证（无 docker daemon；镜像有效性经 registry tag 实证 + 结构等价论证）。
- 非 Dockerfile 的容器相关文件（如 .dockerignore、k8s yaml）。
- legacy docs/ 目录中的容器文档。

## Scope

### In Scope

- 83 个非模板 Dockerfile 的 FROM 行（WI9a 41 + WI9b 42，其中 20 个 micro:2.0 零改动）。
- 3 个 codegen `.xgen` 模板 FROM 行（native-micro.xgen 裁定保持并记录）。

### Out Of Scope

- 生成器 Java 逻辑 / xgen 模板非 FROM 行内容。
- UBI10 镜像内 run-java.sh 行为差异实测（无环境）。

## Execution Plan

### Phase 1 - WI9a JVM 基座（41 文件）

Status: completed
Targets: 41 个 `src/main/docker/Dockerfile.jvm|legacy-jar`

- Item Types: `Fix`（ubi8/17/21 基座为已确认的基线漂移）

- [x] `ubi8/openjdk-17:1.16|1.11`、`ubi8/openjdk-21:1.21` → `ubi10/openjdk-25:1.24`（全量替换；执行期发现：openjdk-21 位点在 nop-metadata-app 双文件，首轮收集 pattern `openjdk-1` 漏匹配已补齐）
- [x] codegen 模板 2 个（jvm.xgen/legacy-jar.xgen）由 Phase 3 处理

Exit Criteria:

- [x] 非 codegen 文件中 `ubi8/openjdk` 零命中；`ubi10/openjdk-25:1.24` 非 codegen 恰 41 命中（实测 41）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - WI9b native/micro 基座（42 文件）

Status: completed
Targets: 21 个 `Dockerfile.native|legacy native`（ubi-minimal）+ 21 个 `Dockerfile.native-micro`

- Item Types: `Fix`（同上）

- [x] `ubi8/ubi-minimal:8.8|8.5` → `ubi10/ubi-minimal:10.2`（21 文件）
- [x] `quarkus-micro-image:1.0` → `2.0`（1 文件）；`:2.0` 的 20 个非模板文件保持
- [x] codegen 模板由 Phase 3 处理

Exit Criteria:

- [x] 非 codegen 文件 `ubi8/ubi-minimal` 与 `micro-image:1.0` 零命中
- [x] 计数核对：`ubi10/ubi-minimal:10.2` = 22（21 非模板 + native.xgen）、非模板 `micro-image:2.0` = 20
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - WI9c codegen 模板（Protected Area）

Status: completed
Targets: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/{appName}-app/src/main/docker/*.xgen`

- Item Types: `Fix`（模板基座漂移；生成链路理解见头部声明）

- [x] `Dockerfile.jvm.xgen` + `Dockerfile.legacy-jar.xgen` → `ubi10/openjdk-25:1.24`
- [x] `Dockerfile.native.xgen` → `ubi10/ubi-minimal:10.2`
- [x] `Dockerfile.native-micro.xgen` 裁定保持 2.0（Q4 micro 线即 2.0，registry 实证无 3.0）
- [x] 模板非 FROM 行零改动（生成语义保持）

Exit Criteria:

- [x] 4 个 `.xgen` 中无 ubi8 基镜像；FROM 行数与文件数不变（3 换 1 保持）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（87 个 tracked Dockerfile 基座全部处于 UBI10/JDK25/Q4-micro 线）
- [x] 行为/契约结果已达成（新脚手架与存量 app 容器基座一致）
- [x] 必要 focused verification 已完成（FROM 全分布终态计数核对）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步——docs-for-ai 无容器基座表述（grep 复核后填写）：**No owner-doc update required**
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：全仓 FROM 分布终态 = {ubi10/openjdk-25:1.24 ×43, ubi10/ubi-minimal:10.2 ×22, micro:2.0 ×22}（实测精确达标）
- [x] 全量构建不受影响（Dockerfile 非 Java 构建面，无 pom/代码改动）
- [x] 代码风格符合本仓约定（纯 FROM 行替换）

## Deferred But Adjudicated

### Docker build/run 冒烟验证

- Classification: `watch-only residual`（已由 closure audit 实测消解）
- Why Not Blocking Closure: **勘误（audit 发现）**——plan 原记"本环境无 docker daemon"不成立（audit 时点 docker daemon v29.4.3 可达）；closure audit 已代执行冒烟：三基镜像 pull+run 全绿，openjdk-25:1.24 实测 `java -version` = OpenJDK 25.0.4.1 LTS（Red Hat）且 `/opt/jboss/container/java/run/run-java.sh` 实测存在（jvm ENTRYPOINT load-bearing 主张升格为实测），ubi-minimal:10.2 实测 RHEL 10.2，micro:2.0 可运行。真实应用镜像构建仍随 WI16 容器口径。
- Successor Required: `no`
- Successor Path: WI16 全量回归如含容器口径

## Non-Blocking Follow-ups

- ubi10/openjdk-25 tag 1.24 的后续更新（1.25+）发布时是否跟进——版本策略随 WI10 治理文档化。

## Closure

Status Note: WI9a/b/c 全部落地：87 个 tracked Dockerfile 基座收敛到 UBI10/JDK25/Q4-micro 三线（终态分布 43/22/22 精确达标，ubi8 零残留）；4 个 codegen 模板中 3 个换线、native-micro.xgen 裁定保持（Q4 micro 线即 2.0）；执行期抓漏 openjdk-21 双文件（收集 pattern 缺陷）并补齐。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 closure auditor（fresh session）
- Audit Session: agent_45e791cb-9c3e-4f4b-9b04-ac0f13df09ea
- Evidence:
  - FROM 全分布终态计数核对（43/22/22，ubi8/micro:1.0 零残留）
  - 4 个 .xgen 模板逐个核对（3 换 1 保持，非 FROM 行零改动）
  - Deferred（docker 冒烟）裁定复核——audit 实测消解：三镜像 pull/run + JDK 25.0.4.1 + run-java.sh 全绿（勘误入档）
  - `check-plan-checklist.mjs --strict` exit 0

Follow-up:

- ubi10/openjdk-25 后续 tag 跟进随 WI10 版本策略
