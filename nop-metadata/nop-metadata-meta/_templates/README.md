# nop-metadata `_templates/` 目录说明

> Status: plan 2026-07-19-1250-3 Phase 4 维度05-01 裁定（watch-only residual）

## 用途

本目录下的 `_NopMeta*.json` 文件（共 32 个）是 nop-cli 代码生成管线在首次 `gen` 一个 nop-metadata
项目骨架时使用的模板元数据快照。每个文件以 `_` 前缀标识，按 AGENTS.md "Hard Stop: Generated Files"
规则：**不允许手工编辑**——若需要修改，请改源 ORM 模型（`/model/*.orm.xml`）或 codegen 模板，
然后重新生成。

## 裁定

- **Classification**: `watch-only residual`
- **Why Not Blocking Closure**: 这些 `_*.json` 是 codegen 管线的可选输入（非 build 必需），由 nop-cli
  团队在未来确认实际消费方。当前保留以兼容潜在 codegen 入口；移除可能导致首次脚手架生成失败。
- **Successor Required**: `no`

## 移除评估

未来 codegen 团队可执行下列步骤确认是否可移除：

1. 在干净环境运行 `nop-cli gen` 生成 nop-metadata 项目骨架，确认本目录未被读取；
2. 若未被读取，安全移除整个 `_templates/` 目录；
3. 若被读取，更新本 README 说明实际消费路径。

在确认前，本目录作为 codegen 兼容性保留，不动。
