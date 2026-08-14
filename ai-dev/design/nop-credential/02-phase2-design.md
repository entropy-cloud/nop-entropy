# nop-credential 二期设计（OAuth 流程引擎 + 外部 KMS/HSM + RBAC 授权/租户隔离）

> **占位文档（2026-08-14 创建）**：本文件是 `ai-dev/backlog/nop-credential-mfa-roadmap.md` W9-design 工作项的规划交付物，当前为空占位——内容由 W9-design 执行时产出（见 roadmap Stage 8）。
>
> **规划范围**：
> - OAuth 流程引擎（授权码换取/刷新闭环/自动续期；复用 `nop-auth-sso` `OAuthLoginServiceImpl` 既有 OAuth 客户端能力）
> - 外部 KMS/HSM 集成 SPI（`ICredentialKeyProvider` 扩展：Vault/云 KMS 主密钥来源，本地实现保留为默认）
> - RBAC 细粒度授权 + 租户隔离（凭证级授权模型 + 消费侧校验）
>
> 状态：W9-design `todo` → 产出本文件后经 review 标 `done`。
