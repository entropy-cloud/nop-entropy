# 06: 凭证字段跨层暴露 — 收敛必须从 ORM 源模型开始

> Date: 2026-07-31
> Severity: High — NopAiModel.apiKey 在 ORM 源模型 → 生成 xmeta → Delta xmeta → GraphQL DTO 多层暴露，MR1/MR2/MR3 三个 MR 各自触及同一面才最终闭合

## 场景

nop-ai 审计发现 `NopAiModel.apiKey`（LLM 模型凭证）存在完整的多层暴露链：

1. **ORM 源模型**（`nop-ai/model/nop-ai.orm.xml`）：apiKey 列无任何查询/发布限制
2. **生成的 `_NopAiModel.xmeta`**：codegen 从源模型生成，暴露 `queryable="true" sortable="true"`
3. **Delta xmeta**：运行时合并层做了部分限制（`insertable="false" updatable="false"`），但作为兜底脆弱
4. **GraphQL DTO**（`NopAiModelOutputBean`）：序列化含 apiKey 字段，API 响应直接暴露

该链被 3 个 MR 分头修复：MR1（P1-MA2-023 限制 queryable/sortable）、MR2（P1-MA3-021 xmeta 暴露）、MR3（P1-MA5.5-004 / P1-MA6.1-002 DTO 输出）。MR4 核验发现 MR1 的限制未下沉到 ORM 源模型 — 生成的 `_NopAiModel.xmeta` 仍暴露 `queryable="true"`，运行时仅靠 Delta xmeta 兜底，属 fragile 状态。

## 根因

1. **把生成物当修复位置**：xmeta 限制写在 Delta xmeta（生成链下游），而生成源（ORM 模型）未变 — 下次 codegen 重新生成 xmeta 时限制漂移。
2. **同一 finding 被各 MR 按各自视角局部修复**：ORM 审计修 ORM、安全审计修 xmeta、敏感泄露审计修 DTO — 没有一条"从源到出口"的完整链核验。
3. **没有用运行时证据验证**：GraphQL schema 由合并后 xmeta 驱动，只在代码层看字段属性，未验证最终 API 响应是否输出。

## 正确做法

1. **凭证/敏感字段的限制写在 ORM 源模型**：`tagSet="enc,not-query,not-sort,not-pub"` + `ui:show="X"` 直接落在 `model/*.orm.xml` 的列上 — 生成物（`_NopAiModel.xmeta`）自动继承，运行时合并 xmeta 后完全限制，DTO 生成时自动剔除。
2. **`enc` tag 与加密绑定器联动**：ORM 列级 AES 加密（`DefaultOrmColumnBinderEnhancer`）+ `NopAiModel.toString()` 掩码 apiKey，DB 层不存明文。
3. **修复后跑"链式验证"**：源模型 → 生成 xmeta → 合并 xmeta → GraphQL schema/DTO 全链抽查，而不是只验证被修改的那一层。nop-ai-meta 的 `TestNopAiModelApiKeyXmeta`（3 方法：base xmeta 限制、merged xmeta 完全限制、非凭证字段不受影响）是标准做法。
4. **跨 MR 同面修复必须合并裁定**：多个 MR 触及同一 finding 时，由后续 MR 统一核验全链一致性（MR4 §1 的做法），避免"每层都修了一部分、没有一层修完整"。

## 判定规则

> **凭证字段的暴露判定以"运行时最终可见性"为准**，即 GraphQL 响应/API DTO 实际输出的字段集合 — 不是某层模型的属性标注。
>
> **收敛位置判定**：只要该字段由 codegen 派生（ORM → xmeta → DTO），限制就必须写在 ORM 源模型；写在任何生成物上的限制按"会被覆盖的脆弱修复"处理。

## 适用范围

- 含 apiKey/secret/token/password 等凭证字段的 ORM 实体
- 涉及生成管线的字段可见性修复
- 审计修复的跨层核验

## 参考

- `nop-ai/model/nop-ai.orm.xml`（apiKey 列 `tagSet="enc,not-query,not-sort,not-pub"`）
- `ai-dev/audits/arm-index.md` §MR4 裁定 #1（三层暴露链裁定）
- `nop-ai-meta/src/test/java/io/nop/ai/meta/TestNopAiModelApiKeyXmeta.java`
