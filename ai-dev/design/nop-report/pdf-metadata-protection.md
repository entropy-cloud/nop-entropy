# nop-pdf 文档元数据与保护

> Status: active
> Last Reviewed: 2026-10-19
> Source: plan 2482（`ai-dev/backlog/nop-pdf-report-export-roadmap.md` WI13）

## 决策

### D1 元数据语义

- `PdfDocInfo` 六字段（title/author/subject/keywords/creator/producer）映射 Document Information Dictionary；`setInfo` 对 null 字段不覆盖（支持分次补充）；`getInfo` 缺失键返回 null。加密文档的 Info dict 在 user 密码打开后可读（PDF 语义：Info 随文档体加密）。

### D2 加密语义

- **算法钉死 AES-256**：`setEncryptionKeyLength(256) + setPreferAES(true)`。3.0.8 `ProtectionPolicy` 缺省是 RC4-40 弱加密且无法从 isEncrypted/权限位断言检出——加密字典断言（Filter=Standard/V=5/Length=256）作为回归钉写入测试。
- **protect 先于 save**：`encrypt(...)` 只设置保护策略，加密字典在 `save` 时写出；内存态 `isEncrypted()` 仅反映加载态（load 加密文档为 true），加密是否生效以 reload 后断言为准。
- **密码语义**：ownerPassword 拥有全部权限；userPassword 受 AccessPermission 限制，空 userPassword 是合法的"无密码受限打开"主用例（权限位仍生效）；空 ownerPassword 不安全但允许（调用方自担，javadoc 注明）。
- **权限面**：与 roadmap 对齐仅暴露打印/复制两位（`allowPrinting`→canPrint、`allowCopying`→canExtractContent）；修改位不暴露。
- **读侧对称**：`loadFromFile(File, password)` 支持打开加密文档；错密码（InvalidPasswordException）→ `NopException.adapt`（NopWrapException 保留 cause 链）。**不新增错误码**——密码错误是环境输入错误而非契约违约，adapt 惯例已覆盖（WI12 同判例），避免 define-face 对称负担。

### D3 PDF/A 裁定

**不引入 veraPDF，不实现 PDF/A 转换。** 理由：(1) veraPDF 为外部校验器（约 500MB 带规则库的依赖面），引入即重依赖；(2) 仓库当前无 PDF/A 合规需求方（roadmap 明确"仅裁定不急实施"）；(3) PDFBox 自身不产出 PDF/A 合规文档（需缺色标/XMP 元数据等附加面），half-measure 无价值。**重启条件**：出现明确 PDF/A 合规需求（归档交付义务）时另立计划，届时评估 veraPDF 集成（CLI 外部校验 or 库内嵌入）。

## 涉及实现

- `nop-pdf/.../core/PdfDoc.java`（getInfo/setInfo/encrypt/isEncrypted/loadFromFile(File,password)）
- `nop-pdf/.../core/PdfDocInfo.java`
- 测试：`TestPdfMetadataProtection`（元数据 roundtrip / AES-256 加密字典断言 / 权限位 / 错密码 / 未加密回归）

## 验证

- `TestPdfMetadataProtection`：六字段 roundtrip（含 null 字段不覆盖语义）、加密 save→reload（isEncrypted/权限位/加密字典 Filter/V=5/Length=256）、owner 全权、错密码与无密码打开均 fail-loud、未加密文档回归。
