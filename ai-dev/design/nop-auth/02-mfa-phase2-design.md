# nop-auth MFA 二期设计（操作级 MFA + 角色级强制策略 + 因子扩展 + 可信设备）

> **占位文档（2026-08-14 创建）**：本文件是 `ai-dev/backlog/nop-credential-mfa-roadmap.md` W12-design 工作项的规划交付物，当前为空占位——内容由 W12-design 执行时产出（见 roadmap Stage 13）。
>
> **规划范围**：
> - 操作级 MFA（会话内敏感操作二次验证，请求级钩子；复用登录级 `mfaVerify` 链路）
> - 角色级 MFA 强制策略引擎（策略模型：存储/继承/评估；一期全局开关 + 用户级启用兼容）
> - 因子扩展：WebAuthn/FIDO2（`MfaType` 枚举扩展位）+ 邮件验证码（既有 `IEmailSender`）+ 外部 MFA 服务（Authy/Duo 评估）
> - 可信设备/记住此设备（设备指纹 + 会话策略）
>
> 状态：W12-design `todo` → 产出本文件后经 review 标 `done`。
