# unit-test-coverage-roadmap WI0 — 全仓覆盖率基线与分模块目标裁定

> Status: resolved
> Date: 2026-10-02
> Scope: 全仓 318 个含主代码模块（jacoco 0.8.14，root pom coverage profile 排除口径）
> Conclusion: 全仓加权行覆盖 56.92%（有报告模块）；分层基线 kernel 44.38% / engine 52.62% / periphery 67.14%；分层目标采纳 roadmap 预设（kernel ≥55% / engine ≥45% / periphery ≥30%），nop-jq、nop-xlang 按语义基线裁定；9 个零测试引擎模块与 4 个 format/可靠性模块为最大缺口；基线快照 `coverage-baseline-2026-10-02.json` 可由 `ai-dev/tools/coverage-baseline.sh` 重复产出。

## Context

- roadmap：`ai-dev/backlog/unit-test-coverage-roadmap.md` WI0（承载 plan：`ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md`）。
- 要回答的问题：各模块 jacoco 实测行/分支覆盖是多少？分层目标按预设还是按实测推翻？文件数比粗筛哪些是误判？
- 约束：只读消费既有 jacoco 管线，不改产品代码、不改任何 pom、不调整排除口径。

## 管线与数据源

- 脚本：`ai-dev/tools/coverage-baseline.sh`（编排）+ `ai-dev/tools/coverage-baseline.mjs`（解析）。执行序：exec 清理 → `mvnq -- -Pcoverage test -T 1C -fae` → 缺口模块逐个 `jacoco:prepare-agent + test -pl :<module>` 补跑 → `-Pcoverage jacoco:report -T 1C -fae` 逐模块报告 → 解析出 JSON + md 快照。
- 数据源：逐模块 `target/site/jacoco/jacoco.xml`（2026-10-02 当日 exec 生成，无陈旧数据）。**不使用** `tests/` 聚合报告（其 report-aggregate 只看直接依赖，约 135/409 模块，结构性缺 nop-jq、nop-retry、nop-graph、nop-format 大部等）。
- 实施期发现的三个管线结构性事实（对后续 CI/sonar 口径有意义）：
  1. **nop-kernel 组 pom 无 `<parent>` 声明**，整组子模块不继承 root pom 的 coverage profile → 常规 reactor 构建下 nop-core / nop-xlang / nop-commons / nop-api-core / nop-dataset / nop-codegen / nop-javac / nop-jpath / nop-markdown / nop-record-mapping / nop-xlang-* 从不产出 exec（sonar 聚合路径 `tests/target/site/jacoco-aggregate` 同样拿不到它们）。
  2. 组 pom 内 JDK 触发 profile 被激活会停用祖先 pom 的 activeByDefault profile（nop-demo 的 `build-quarkus-modules-on-jdk17-plus`，波及 nop-quarkus-demo / nop-spring-demo / nop-spring-gateway）。
  3. root pom surefire 显式 `testFailureIgnore=false`，`-Dmaven.test.failure.ignore=true` 是死参数。
  - 以上通过缺口补跑闭环（显式前置 `jacoco:prepare-agent` + `-pl :<module>`）绕过，未改任何 pom；如需彻底修复属独立 build-infra 立项。

## 基线快照（2026-10-02）

全仓 318 个含主代码模块：198 个有 jacoco 报告；110 个无测试（0%）；10 个有测试但无报告（见「无报告模块裁定」）。全仓加权行覆盖 56.92%（有报告模块口径）。

### 分层汇总

| 分层 | 模块数 | 有报告 | 加权行覆盖 | 达标(非语义) | 预设目标 |
|---|---:|---:|---:|---:|---:|
| kernel（nop-kernel + nop-persistence + nop-core-framework） | 41 | 35 | 44.38% | 17/34 | ≥55% |
| engine（nop-wf/batch/sys/rule/dyn/service-framework） | 40 | 22 | 52.62% | 13/22 | ≥45% |
| periphery（其余全部） | 237 | 140 | 67.14% | 111/139 | ≥30% |

### 关键模块基线（roadmap 缺口榜 + 达标参照系，行覆盖%/分支覆盖%）

| 模块 | 行% | 分支% | 行数 | 目标 | 备注 |
|---|---:|---:|---:|---:|---|
| nop-core | 36.59 | 29.21 | 30072 | 55 | WI1 主目标 |
| nop-xlang | 47.70 | 35.33 | 44949 | semantic | 数据驱动修正，见下 |
| nop-commons | 23.97 | 18.76 | 19057 | 55 | WI2 主目标，缺口最大内核模块 |
| nop-api-core | 29.29 | 27.16 | 7357 | 55 | WI2 |
| nop-dataset | 21.63 | 21.35 | 906 | 55 | WI2 |
| nop-codegen | 26.07 | 25.57 | 1289 | 55 | WI2 |
| nop-record-mapping | 58.16 | 51.51 | 1709 | 55 | 已达标 |
| nop-markdown | 56.28 | 46.00 | 1768 | 55 | 已达标 |
| nop-orm-eql | 53.24 | 34.40 | 6046 | 55 | WI4 主目标 |
| nop-orm-model | 40.61 | 33.33 | 1709 | 55 | WI4 |
| nop-db-migration | 79.24 | 59.46 | 1474 | 55 | 已达标 |
| nop-dao | 45.81 | 39.54 | 3495 | 55 | WI4 |
| nop-orm | 55.20 | 44.54 | 8734 | 55 | 已达标（参照模块） |
| nop-wf-core | 0（无测试） | — | — | 45 | **全仓最大零测试引擎模块**，WI5 |
| nop-wf-api / nop-wf-dao | 0（无测试） | — | — | 45 | WI5 |
| nop-wf-service | 82.44 | 67.48 | 917 | 45 | 模式参照 |
| nop-batch-core | 55.40 | 48.17 | 2018 | 45 | 已达标 |
| nop-batch-dsl | 44.35 | 37.17 | 699 | 45 | WI6 |
| nop-batch-exp | 65.74 | 45.75 | 721 | 45 | 已达标 |
| nop-biz | 44.10 | 33.32 | 3769 | 45 | WI8 |
| nop-biz-auth-core | 51.79 | 42.56 | 1707 | 45 | WI8 |
| nop-biz-auth-api | 0（无测试） | — | — | 45 | WI8 |
| nop-gateway | 62.55 | 46.59 | 1709 | 45 | 已达标 |
| nop-sys-api | 0（无测试） | — | — | 45 | WI7 |
| nop-sys-dao | 67.99 | 50.74 | 1331 | 45 | 已达标 |
| nop-rule-core | 71.19 | 62.69 | 1194 | 45 | 已达标 |
| nop-rule-api / nop-rule-dao | 0（无测试） | — | — | 45 | WI7 |
| nop-dyn-api | 0（无测试） | — | — | 45 | WI7 |
| nop-dyn-dao | 29.58 | 26.17 | 693 | 45 | WI7 |
| nop-excel | 16.44 | 14.79 | 4604 | 30 | WI9 主目标 |
| nop-record | 59.17 | 53.93 | 4519 | 30 | 已达标 |
| nop-pdf | 28.03 | 18.00 | 5619 | 30 | WI10 |
| nop-mermaid | 6.40 | 7.64 | 641 | 30 | WI10 |
| nop-converter | 9.30 | 11.18 | 731 | 30 | WI10 |
| nop-office-model / nop-office-doc-model | 0（无测试） | — | — | 30 | WI10 |
| nop-chart-export | 46.72 | 32.11 | 1828 | 30 | 已达标 |
| nop-cluster-core | 13.95 | 15.24 | 1104 | 30 | WI11 |
| nop-tcc-core | 6.11 | 9.47 | 540 | 30 | WI11 |
| nop-graph-core | 79.28 | 63.05 | 637 | 30 | 已达标（TarjanSCC 缺陷回归并入 WI11） |
| nop-autotest-core | 32.95 | 29.10 | 1909 | 30 | WI12（测试基建自身） |
| nop-report-core | 64.36 | 52.57 | 4658 | 30 | 已达标 |
| nop-jq | 73.24 | 63.44 | 4622 | semantic | 见下 |
| nop-lint-core | 90.33 | 82.58 | 5862 | 30 | 参照系，已达标 |
| nop-code-core | 55.09 | 45.96 | 1512 | 30 | 已达标 |

完整 318 模块数字与每模块低覆盖类清单（行覆盖 <40% 且 ≥30 行）见 `coverage-baseline-2026-10-02.json`（`lowCoverageClasses` 字段，供各 WI 直接定位增量测试目标）与 `coverage-baseline-2026-10-02.md`。

## 目标裁定（Decision）

- **采纳 roadmap 预设**：kernel ≥55% / engine ≥45% / periphery ≥30% 行覆盖，逐模块目标见快照 JSON `targetPct` 字段。理由：实测显示缺口分布与三因子排序一致（内核被依赖最广且实测最低），预设不需要整体推翻；单个模块确需调整的，由对应 WI plan 携带理由申请，WI13 收口时统一复裁。
- **语义基线（推翻行覆盖强判，2 例）**：
  - nop-jq：官方 jq 1.7.1 套件 430/430 为语义基线；实测行覆盖 73.24%/分支 63.44%，远超文件数比暗示。
  - nop-xlang：734/98 文件比严重低估——xpl/xscript 数据用例集（`.test` 资源文件驱动）承担主要验证面；47.70% 行覆盖为该形态下的合理基线，增量以 WI0 快照 `lowCoverageClasses` 为准，不强求 55%。
- **零测试模块**：9 个引擎域零测试模块（nop-wf-core/api/dao、nop-biz-auth-api、nop-sys-api、nop-rule-api/dao、nop-dyn-api）与 4 个外围零测试模块（nop-office-model、nop-office-doc-model 等）以「从 0 到有结构性测试」为 WI 首要目标，行覆盖目标按所属层预设。

## 文件数比误判修正（对 roadmap Current Baseline 的修正建议，WI13 回写）

| 模块 | 文件数比暗示 | jacoco 实测 | 裁定 |
|---|---|---|---|
| nop-jq | 9%（严重低估） | 73.24% 行 / 63.44% 分支 | 数据驱动误判，语义基线达标 |
| nop-xlang | 13%（低估） | 47.70% 行 | 数据驱动误判，语义基线 |
| nop-lint-core | 83%（参照系） | 90.33% 行 | 参照系有效 |
| nop-commons | 41/10（10%） | 23.97% 行 | 非误判，真实缺口 |
| nop-excel | 224/11（5%） | 16.44% 行 | 非误判，真实缺口 |

## 测试失败清单（2026-10-02 run，4 模块 8 个失败测试类）

| 模块 | 测试类 | 现象 | 归因 |
|---|---|---|---|
| nop-code-web | NopCodeWebPagesTest（1/1 error） | `nop.err.ioc.not-find-bean-with-type`：`$DEFAULT$nopChatService` 需要 `IHttpClient` bean，测试容器未注册 | 测试环境 bean 装配缺失（确定性失败）；归属 WI12/nop-code 域跟进 |
| nop-stream-fraud-example | TestParallel2PcJdbcE2E（120 测试中 1 failure） | epoch 由 2 个不同 subtask 提交的断言失败：ledger 所有分区仅 subtask-1 提交 | **产品缺陷嫌疑**：单独复跑确定性失败（非 flaky），已记 `ai-dev/bugs/2026-10/2026-10-02-stream-2pc-commit-key-single-subtask.md`，修复独立立项 |
| nop-rule-service | TestNopRuleDefinitionBizModel（5 测试中 3 errors） | 快照比对失败：`nop_file_record.FILE_HASH` 期望为空实际有值（运行时哈希） | 快照非确定性字段问题（testing.md「快照不匹配诊断」模式），归属 WI7 修复快照/字段标记 |
| nop-lint-nop | TestNopRuleSuites / TestProductionRuleCount / TestMetricsRuleSuites（24 测试中 3 failures） | 规则普查断言过期：期望 65 实际 69、期望 70 实际 74 | 新增 lint 规则未同步普查断言（测试维护债），归属 WI12/lint 跟进 |

## 无报告模块裁定（10 个有测试但无覆盖数据的模块）

| 模块 | 原因 | 裁定 |
|---|---|---|
| nop-kernel-cli | src/test 存在但无 JUnit 可执行测试类（surefire 0 匹配） | WI12 确认测试形态 |
| nop-js | 不在默认 reactor（JS 构建链） | 管线外，记录即可 |
| nop-lint-graphql / nop-lint-maven-plugin | 上游 SNAPSHOT（nop-lint-nop 等）从未 install 入 worktree .m2-repo，`-pl` 补跑无法解析依赖 | WI12 收尾时先 install 上游链再补测 |
| nop-lint-nop | 无主 Java 代码（lint 规则为 YAML 资源） | 覆盖率 N/A（非缺口） |
| nop-rg-cli / nop-rg-vector | 模块 pom 有意替换 surefire argLine（`--add-modules jdk.incubator.vector`），jacoco agent 无法挂载 | 设计如此，采集需 pom 调整 → 独立 build-infra 裁定，不在本 roadmap WI 内夹带 |
| nop-spring-demo / nop-spring-gateway | sibling demo 模块 SNAPSHOT 未安装，`-pl` 补跑失败 | demo 模块不进裁定，记录即可 |
| nop-graphql-grpc | grpc profile 门控，不在默认 reactor | 管线外，记录即可 |

## Conclusion

- 全仓基线快照已产出且可重复（`coverage-baseline-2026-10-02.json` + `.md`；重跑 `coverage-baseline.sh --skip-maven` 幂等再生）。
- 分层目标采纳预设 + 2 例语义基线裁定；WI1–WI12 以快照 JSON 的 `lowCoverageClasses` 为增量测试定位输入。
- 修复三个管线结构性发现所需的最小变更（如 nop-kernel 组 pom 补 parent/jacoco 声明）属 build-infra 独立立项，本 roadmap 只用补跑闭环绕过。
- 后续工作：WI1–WI12（deps WI0 已满足）→ WI13 收口复裁未达标模块。

## Open Questions

- [ ] nop-stream TestParallel2PcJdbcE2E 的 2PC commit-key 语义是产品缺陷还是测试期望错误（bug note 已建，待独立立项确认）
- [ ] nop-kernel 组 pom 补 parent 声明（消除覆盖率采集盲区）是否立独立 build-infra plan

## References

- `ai-dev/backlog/unit-test-coverage-roadmap.md`
- `ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md`
- `ai-dev/analysis/2026-10/coverage-baseline-2026-10-02.json` / `.md`
- `ai-dev/tools/coverage-baseline.sh` / `.mjs`
- `ai-dev/bugs/2026-10/2026-10-02-stream-2pc-commit-key-single-subtask.md`
