# 前端页面开发教训速查（源自 nop-app-erp 实战返工）

> **用途：** 前端/页面任务（view.xml、page.yaml、页面范式迁移、E2E/视觉断言）动手前**先通读本页**。全部条目来自 nop-app-erp 真实返工，每条附可执行检测方式；**写 plan 时应把相关检测命令并入 Verification**。后端任务读 `application-project-pitfalls-backend.md`（流程/协作规则也在该篇）。

## 前端铁律

1. **`_gen/` 生成视图永不手改**——手写 `<Entity>.view.xml` 以 bounded-merge 覆盖，改模型源或 Delta 后重新生成（与后端同源的纪律，违者下次生成静默还原）。
2. **页面取数优先平台声明式范式**（data-source / `@query:` / `@mutation:` 前缀，REST `/r/`）——手写 GraphQL 字符串是自创范式，必踩模板引擎与契约漂移双坑（erp 74 文件迁移 + 67 处 `$` 转义修复）。
3. **范式迁移必须同步扫全部消费端**：E2E 等待谓词、渲染器源码（key 契约如 `content:` vs `html:`）、打包配置、视觉基线——页面 schema 与消费端是松耦合契约，失效全是静默降级（erp flux 翻转 780 文件后 5 类次生故障）。
4. **核心 UI 库锁单实例**：根 `pnpm.overrides` 强制；验证看 `realpath` 收敛不看版本号——双实例重复注册 = fresh boot 全红。
5. **凡触及 `*.view.xml` / `*.page.yaml`，验证必须含页面模型校验组**（应用项目的 app-all 页面解析测试）——服务层测试全绿挡不住 view.xml 层损坏（erp 出现同文件追加 22 份残缺拷贝，4196 行 vs 基线 335 行）。
6. **快照/像素基线禁止内嵌日历派生字面值与跨月漂移**：日期派生列用冻结参考日或 `*` 掩码；基线计数以 reporter 权威口径为准，禁手工转录。

## 分维度教训表

### 页面 / 前端面

| 教训 | 最终规则 | 检测 |
|---|---|---|
| AMIS 手写 GraphQL 的 `$` 被模板引擎吃掉，KPI 恒 0（67 处） | 取数用平台声明式范式；必须手写时 `${'$'}` 转义 + E2E 断言真实请求体 | `rg '\\$[a-zA-Z]' **/*.page.yaml`（排除 `${` 声明）；断言 `request.postData()` 而非仅响应码 |
| 范式迁移只改页面不改消费端，5 类次生静默故障（30 spec 全红一天） | 迁移计划附消费端清单逐项勾销；收口跑带 fixtures 守卫的全量套件，禁只跑调试 spec | 迁移前 `rg '<旧范式关键词>' tests/e2e/` 列受影响 spec 清单进 plan |
| pnpm 多实例：同库两物理实例重复注册，每次 fresh boot 必抛 pageerror | 核心 UI 库根 `pnpm.overrides` 锁单实例；修复验证以 realpath 收敛为准 | `find node_modules -name amis -maxdepth 6 -type l \| xargs realpath \| sort -u` 仅一行；fresh-boot pageerror 冒烟常驻 |
| view.xml 层损坏服务层测试全绿，Post-Closure 才爆 | 铁律 5；审计对行号/计数做量级交叉核对 | 12 倍于基线行数即量级异常，停下 |
| 平台"文档承诺"与引擎实现有落差，20 复杂页路线偏离 | 涉平台新特性的 plan，Phase 0 必须最小 PoC（实际渲染一页）验证能力存在 | 计划模板 Phase 0 必填 PoC 证据 |

### E2E / 视觉断言面

| 教训 | 最终规则 | 检测 |
|---|---|---|
| 像素/快照基线跨月漂移、渲染器单行 CSS 漂移 | 基线变更必须能归因到已知提交；不可归因的漂移按假红排查（先查兄弟仓 SNAPSHOT 重建） | 基线 diff 逐条归因，归因不了的回滚 |
| 基线失败清单人工转录吞掉 14 项真失败 | 计数一律引用 surefire XML / playwright reporter 权威口径 | known-good 基线行必须附权威来源链接 |
| 迁移波纹漏改测试谓词（/graphql→/r/ 30 用例全红） | 铁律 3；E2E 数据访问统一走 REST `/r/`（`RpcClient`），禁断言 GraphQL | `rg '/graphql' tests/` 应为零 |

> 流程/AI 协作教训（closure 独立审计、文档收口分歧、门禁自证、契约回摆裁决等）与后端通用，见 `application-project-pitfalls-backend.md` §流程 / AI 协作面。erp 业务特有教训查 nop-app-erp `docs/lessons/`。

## 落地动作

1. **写 plan 时**：把本页"检测"列命令并入计划 Verification；页面迁移计划附消费端清单。
2. **页面收口**：页面模型校验组 + fresh-boot pageerror 冒烟 + 带守卫全量 E2E。
3. **基线变更**：逐条归因到已知提交，权威口径计数。
