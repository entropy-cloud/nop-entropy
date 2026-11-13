# Dispatch Log — CRA WI 5.1 nop-auth-credential 并发审计

- **Plan**: `ai-dev/plans/2482-cra-wi5.1-nop-auth-credential.md`（active；R1 agent_b139157b 4 Major+7 Minor 全修；R2 agent_ca38b1d8 1 Major[批次数不可复现→Phase 1 回填]+4 Minor 全修）
- **基线**: commit b6fe555935；基线文件 `_tmp/cra-baseline-nop-auth-credential.txt`（13 命令组 × 14 源码根 = 4,996 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-auth ask-first——审计只读不受限；nop-auth 内 P0 即修须先询问用户

## 源码根实测（Phase 1 回填，src/main 口径）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-auth-api | — | 搜索 SPI 契约 |
| nop-auth-app | — | |
| nop-auth-dao | — | 含 entity/_gen |
| nop-auth-meta | — | |
| nop-auth-service | — | 主要并发面 |
| nop-auth-sso | — | JWKPublicKeyLocator（volatile CHM :43/:122） |
| nop-auth-web | — | |
| nop-auth-codegen | — | |
| nop-oauth（api/service/web/dao） | 24f/1,665 | |
| nop-credential-api | — | |
| nop-credential-core | — | 凭证加密/密钥管理 |
| **合计** | **~325f/~35,514**[Phase 1 回填] | 5 个 `_gen/` PkBuilder 生成文件——审计排除 |

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| JWKPublicKeyLocator | volatile CHM :43/:122 + synchronized :73/:89 | C1/C2 站点 |
| LocalSendCodeRateLimiter | Caffeine :55 + synchronized ×2 | C2 站点 |
| C4/ThreadLocal/orTimeout | 零命中 | 并发面极稀疏 |
| AGENTS.md Protected Area | ask-first（:145） | P0 修复须 ask-first |

## owner doc 漂移登记

- nop-auth.md 421 行/nop-credential.md 440 行——覆盖成立。

## 历史审计对照口径

- `ai-dev/audits/` 下 auth/credential **并发主题**直接审计 0 命中。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 3+4+C10+Phase 6 四阶段合并任务（2026-10-21，单次派发）

| 阶段 | 结果 |
|------|------|
| R2 深挖 | +6（C1-006 P3/C2-007 P3/C2-008 P3/C6-005 P2/CL-016/017）| 三维度终止 |
| 复核 | 19/19 全保留（勘误 4 项不改判级）| P1×2/P2×5/P3×12 |
| C10 | 会红 0/反向钉死 1/无测试 13 | 登记 C10-001~007 |
| 合并裁定 | 19 → 14 工单 + watch 4 | P0×0/P1×2/P2×5/P3×7 |
