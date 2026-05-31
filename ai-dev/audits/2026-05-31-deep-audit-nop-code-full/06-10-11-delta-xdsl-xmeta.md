# 维度06：Delta 定制合规性 / 维度10：XDSL 与 XLang 正确性 / 维度11：XMeta 与 BizModel 对齐

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 维度06：Delta 定制合规性

**零发现**。nop-code 模块不存在 Delta 定制层（无 `_vfs/_delta/` 目录）。

---

## 维度10：XDSL 与 XLang 正确性

### [维度10-01] _lang-typescript.beans.xml 缺少 xsi 命名空间声明

- **文件**: `nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:3-4`
- **证据片段**:
  ```xml
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc"
         xmlns="http://www.springframework.org/schema/beans">
  ```
  对比 lang-java/lang-python 都有 `xmlns:xsi` 和 `xsi:schemaLocation`。
- **严重程度**: P3
- **现状**: 风格不一致。xsi 声明在 NopIoC 中不参与实际解析，无功能影响。
- **建议**: 与其他文件统一。
- **信心水平**: 95%
- **误报排除**: 确认的风格不一致，无功能影响。
- **复核状态**: 未复核

---

## 维度11：XMeta 与 BizModel 对齐

### [维度11-01] NopCodeIndex BizModel 20+ 自定义方法未在 xbiz 中声明

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:43-260`
- **证据片段**:
  ```java
  @BizQuery
  public CommunityDetectionResultDTO detectCommunities(...) { ... }
  @BizQuery
  public GraphAnalysisResultDTO getGraphAnalysis(...) { ... }
  // ... 约 20 个 @BizQuery/@BizMutation 方法
  ```
  对比 xbiz：
  ```xml
  <biz x:schema="/nop/schema/biz/xbiz.xdef" x:extends="_NopCodeIndex.xbiz">
      <actions/>
  </biz>
  ```
- **严重程度**: P2
- **现状**: 方法完全依赖 Java 注解驱动，xbiz `<actions/>` 为空。合法但不利于 API 契约发现。
- **风险**: 审计 API 契约须读 Java 源码而非 xbiz，降低 XDSL 作为 single source of truth 的价值。
- **建议**: 考虑在 xbiz 中声明 action，至少包含方法名和返回类型。
- **信心水平**: 90%
- **误报排除**: Java 注解驱动是合法模式，标记为维护成本。
- **复核状态**: 未复核

---

### [维度11-02] NopCodeSymbol BizModel 的 usages BizLoader 返回类型与 xmeta 不一致

- **文件**: `NopCodeSymbolBizModel.java:97-107`
- **证据片段**:
  ```java
  @BizLoader(forType = SymbolDTO.class)
  public List<AnnotationUsageDTO> usages(@ContextSource SymbolDTO symbol, ...) { ... }
  ```
  xmeta 声明：`<item bizObjName="NopCodeUsage"/>`（ORM relation）。
- **严重程度**: P2
- **现状**: 同一 prop 名 "usages" 在不同上下文返回不同类型（ORM entity vs DTO）。
- **建议**: 将 BizLoader 方法名改为 `usageAnnotations` 以避免混淆。
- **信心水平**: 70%
- **误报排除**: Nop 框架可能优先使用 BizLoader，但类型不一致是事实。
- **复核状态**: 未复核

---

### [维度11-03] NopCodeFile sourceCode 权限策略不一致：xmeta published=false 但 BizLoader 仍可暴露

- **文件**: `nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeFile/NopCodeFile.xmeta:5`
- **证据片段**:
  ```xml
  <prop name="sourceCode" published="false" queryable="false" sortable="false"/>
  ```
  对比 BizModel：
  ```java
  @BizLoader(forType = CodeFileAnalysisResult.class)
  @Auth(permissions = "code-source-read")
  public String sourceCode(@ContextSource CodeFileAnalysisResult file) { ... }
  ```
- **严重程度**: P2
- **现状**: 两层控制冗余且可能矛盾。xmeta 隐藏字段，但 BizLoader 提供了另一获取路径。
- **风险**: 维护者认为 `published=false` 阻止暴露，但 BizLoader 路径仍可用。
- **建议**: 选择一种控制策略，不要两层共存。
- **信心水平**: 85%
- **误报排除**: 确认的权限策略不一致。
- **复核状态**: 未复核

---

### [维度11-04] ORM 模型引用的 5 个 dict 在 nop-code 内无对应定义文件

- **文件**: `nop-code/model/nop-code.orm.xml:110,239,245,417,555,727`
- **证据片段**:
  ```xml
  <column ... ext:dict="code/index_status"/>
  <column ... ext:dict="code/symbol_kind"/>
  ```
- **严重程度**: P2
- **现状**: 引用了 5 个 `code/` 前缀 dict，但 nop-code 内无 `*.dict.xml` 文件。可能在运行时通过全局注册或 xlib 提供。
- **风险**: dict 定义丢失导致下拉框/选项列表为空。
- **建议**: 确认 dict 实际定义位置。
- **信心水平**: 60%
- **误报排除**: 如果 dict 在其他已部署模块中始终可用，则降级为 P3。
- **复核状态**: 未复核
