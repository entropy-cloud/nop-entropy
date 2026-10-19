# 2482 WI13 nop-pdf 文档属性与保护

> Plan Status: active
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI13，deps WI12）
> Related: `ai-dev/plans/2481-wi12-pdf-assembly-api.md`
> Review: R1 对抗性审查完成（无 Blocker；5 Major + 5 Minor 全部吸收）；见修订记录
> 前置条件：WI12 代码已提交且 plan 2481 完成收口（同 PdfDoc.java 文件面，R1-Major-5）

## Purpose

导出/装配后的 PDF 可设置文档元数据（Title/Author/Subject/Keywords/Creator/Producer）与加密权限（owner/user password、打印/复制权限），并产出 PDF/A 合规校验的调研裁定。

## Current Baseline

2026-10-19 核对（R1 审查逐条核实）：

- `PdfDoc` 无任何元数据/加密 API（grep 全模块证实）；WI12 已加装配 API（PdfErrors/PdfStampOptions/save(OutputStream)/getDocument）。
- PDFBox 3.0.8 加密 API 面经字节码核实：`PDDocument.protect(ProtectionPolicy)`、`isEncrypted()`、`getCurrentAccessPermission()`、`StandardProtectionPolicy(String owner, String user, AccessPermission)`、`AccessPermission.setCanPrint/setCanExtractContent/setCanModify`、`Loader.loadPDF(File, String password)`、`InvalidPasswordException extends IOException`。
- **密钥长度缺省陷阱（R1-Major-2）**：3.0.8 `ProtectionPolicy` 构造器缺省 `encryptionKeyLength=40, preferAES=false`——裸接线产出 **RC4-40 弱加密**，isEncrypted/权限位断言无法检出。必须显式钉死算法参数。
- 错误处理面（R1-Major-3 裁定）：`NopException` 无 String 消息构造器；`NopException.adapt(IOException)` 返回 NopWrapException（cause 链保留）。**本计划不新增错误码**——错密码走 `NopException.adapt`（NopWrapException，英文消息+cause），理由：密码错误是环境输入错误而非契约违约，adapt 惯例已覆盖（WI12 同判例），避免 define-face 对称负担。
- 仓库无 veraPDF 依赖；PDF/A 校验属调研裁定项（roadmap 明确"仅裁定不急实施"）。

## Goals

- `PdfDoc` 元数据 API：`getInfo()/setInfo(PdfDocInfo)`（六字段，null=未设）。
- `encrypt(ownerPassword, userPassword, boolean allowPrinting, boolean allowCopying)`（与 roadmap 两权限位对齐；**AES-256**：`setEncryptionKeyLength(256)` + `setPreferAES(true)`，R1-Major-2）。
- 读侧对称：`loadFromFile(File, String password)`（R1-Major-4：加密产物须能在 nop-pdf 内重开——reload 验证与使用方都需要）。
- 错密码 fail-loud：`Loader.loadPDF(file, wrongPw)` 抛 InvalidPasswordException → `NopException.adapt`（不新增错误码，见 Baseline 裁定）。
- PDF/A 裁定文档。

## Non-Goals

- 数字签名；PDF/A 实际转换与 veraPDF 集成（仅裁定）。
- 密钥长度/算法配置面（钉死 AES-256 常量，不暴露旋钮）；公钥加密；解密已有文档（去加密保存）——但"打开加密文档"（loadFromFile 带密码）在 scope 内（R1-Major-4）。

## Scope

### In Scope

- `PdfDocInfo`（六字段值对象）+ `PdfDoc.getInfo()/setInfo`；`encrypt(...)`（AES-256 + 两权限位）；`loadFromFile(File, String password)`。
- 测试（新建 `TestPdfMetadataProtection`）：元数据 set→save→reload 断言（含 null 字段语义）；加密→save→reload（user 密码经 `loadFromFile(file, userPw)` 打开、`isEncrypted()`、`getCurrentAccessPermission()` 权限位断言、**加密字典算法断言（Filter=Standard / V=5 / Length=256，防 RC4-40 静默回归——R1-Major-2）**、错密码 `Loader.loadPDF(file, wrongPw)` 抛 InvalidPasswordException→adapt NopException）；未加密文档回归。方法签名以源码为准。

### Out Of Scope

- PDF/A 转换实现；签名；解密已有文档（去加密保存）。

## Execution Plan

### Phase 1 - 元数据与加密

Status: completed
Targets: `PdfDoc.java`、`PdfDocInfo.java`（新增）、`TestPdfMetadataProtection.java`（新增）

- Item Types: `Feature`

- [x] PdfDocInfo 值对象（title/author/subject/keywords/creator/producer）；PdfDoc.getInfo()/setInfo 映射 PDDocumentInformation（null 字段=不写）。
- [x] encrypt(ownerPassword, userPassword, allowPrinting, allowCopying)：StandardProtectionPolicy + AccessPermission（setCanPrint/setCanExtractContent 按位）+ **setEncryptionKeyLength(256) + setPreferAES(true)**；protect 先于 save（PDFBox 语义：加密只影响写出）。语义裁定：空 userPassword=合法的无密码受限打开（权限位仍生效）；ownerPassword 为空不安全但允许（调用方自担，javadoc 注明）。
- [x] loadFromFile(File, String password)：`Loader.loadPDF(file, password)`；错密码 InvalidPasswordException → `NopException.adapt`（NopWrapException，不新增错误码）。
- [x] 测试（TestPdfMetadataProtection 5 个，上列全覆盖）。
- [x] 语义裁定由 Phase 2 design 文档承接（已落档，见其 Exit Criteria；Phase 1 不单独改 owner doc）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] 测试全绿；加密态经 save→reload 持久验证（isEncrypted/权限位/**加密字典 Filter/V/Length**）
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿（模块全量 108 含 skip1）
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 1）：

- `PDDocument.isEncrypted()` 仅反映加载态（protect 只在 save 时写出加密字典）——首版"protect 后内存态即加密"断言前提错误（测试捕捉），改为 reload 断言并在 javadoc/测试注释双处声明。
- 3.0.8 中 getDocumentInformation() 不抛 IOException、protect() 抛——按实际签名收口。

### Phase 2 - PDF/A 裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-metadata-protection.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] design 文档：`ai-dev/design/nop-report/pdf-metadata-protection.md`（D1 元数据/D2 加密语义含密钥参数与读侧对称/D3 PDF/A 裁定：不引入 veraPDF + 重启条件）。
- [x] roadmap WI13 勾选（+ Milestone 文档装配可用）；check-doc-links/check-plan-checklist（本文件）/`scan-hollow --module nop-format/nop-pdf --severity high` 全过；收口前全量构建（恢复遍证据口径，见 Closure）。

Exit Criteria:

- [x] design 文档落档且与实现一致
- [x] roadmap Work Item Status WI13 勾选
- [x] `ai-dev/logs/` 对应日期条目记录完成状态
- [x] `./mvnw clean install -T 1C`（或恢复遍证据：排除项均属其他工作流并单跑复核）

## Closure Gates

- [ ] Phase 1–2 全部 Exit Criteria 勾选
- [ ] 元数据/加密有 focused 测试（roundtrip + 加密字典断言非 smoke）
- [ ] 受影响 owner docs 已同步（裁定文档落档）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2482-wi13-pdf-metadata-protection.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### PDF/A 校验集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 明确"仅裁定不急实施"；裁定文档落档即完成本 WI 的 PDF/A 义务
- Successor Required: `no`
- Successor Path: 使用方提出 PDF/A 合规需求时另立计划

## Non-Blocking Follow-ups

- 密钥长度/算法配置面（出现合规需求时）；公钥加密。

## R1 审查修订记录

2026-10-19 R1（独立子代理 agent_83d37860，无 Blocker）：Major-1 encrypt 契约统一为两布尔（对齐 roadmap）+ 签名以源码为准；Major-2 钉死 AES-256（setEncryptionKeyLength(256)+setPreferAES(true)，防 RC4-40 缺省陷阱）+ 加密字典断言入 Exit Criteria；Major-3 错误处理裁定为复用 NopException.adapt 不新增错误码（理由记录）；Major-4 loadFromFile(File,password) 入 scope（读侧对称）；Major-5 前置条件（WI12 提交+收口先于本 plan）；Minor 6-10（幂等改写为可观测断言、测试类归属显式、Phase 1 owner-doc 措辞、日志措辞统一、baseline 重写、roadmap planned 标注）全部并入。
