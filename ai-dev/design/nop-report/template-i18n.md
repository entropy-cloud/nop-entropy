# 报表模板 i18n 设计

**范围**：`nop-report/nop-report-core`（XptModelInitializer、IXptRuntime）
**状态**：active（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 模板文本 i18n **复用平台 `@i18n:` 约定**（CoreConstants.I18N_VAR_PREFIX + `I18nMessageManager.resolveI18nVar`：多 key 逗号回退 + `|缺省值`），不另造前缀语义。
2. 单元格静态文本以 `@i18n:` 开头时，XptModelInitializer 包装为 `xptRt.i18nVar("...")` valueExpr；求值期按 `ContextProvider.currentLocale()` 解析，**无 `|` 缺省时回退原文本**。
3. 表达式内可调用 `xptRt.i18n(key, default)`（`getMessage` + currentLocale；**不经 normalizeLocale**——enabled-locales 未配置时会静默重置 locale）。
4. 字典翻译（`dict`/`domain`）本就 locale 感知（getDict 用 currentLocale），无新机制。
5. **格式（数字/日期）locale 化不做**：formatExpr 表达式已可表达（residual 登记 roadmap）；模板文件 locale 变体加载不做（平台无 VFS locale 变体通用机制，residual）。

## 二、背景与动机

报表标题/列头等静态文本需按用户 locale 切换；平台 i18n 消息体系（`/nop/locale/*.i18n.yaml`）已存在，模板应直接消费而非另建翻译表。

## 三、核心设计

- `@i18n:test.report.title|默认标题` → 求值期 resolveI18nVar：key 命中返回消息，未命中返回 `|` 后缺省，再未声明回退原文本。
- `xptRt.i18n(key, default)` / `xptRt.i18nVar(message)` 供 formatExpr/processExpr 等表达式场景。
- 测试注意：`registerMessages` 注册的消息跨用例存活（不随 clear 清理），测试用唯一前缀 key 规避。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 自造 `@i18n:` 平行语义（仅单 key、回退原文本） | 与平台既有前缀冲突，两套语义并存必漂移；复用即得多 key/缺省值能力 |
| 模板文件 locale 变体加载 | 平台无 VFS locale 变体通用机制；文本级覆盖主场景（residual） |
| normalizeLocale 参与 | enabled-locales 未配置时静默重置 locale，回退语义被破坏 |

## 五、与已有设计的关系

- `parameter-model.md`/`subreport.md`：i18n 与参数传递正交；子模板渲染按子模板自身文本约定解析。
- 平台 i18n：`I18nMessageManager`（`/nop/locale/*.i18n.yaml`）为消息 canonical 来源。
