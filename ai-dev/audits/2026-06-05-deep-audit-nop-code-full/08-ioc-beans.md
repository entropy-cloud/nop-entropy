# 维度 08：IoC 与 Bean 配置 — nop-code 模块

## 第 1 轮（初审）

### [维度08-01] FlowDetector/DeadCodeDetector/ChangeAnalyzer 缺少 ioc:type 声明

- **文件**: `nop-code-service/.../beans/app-service.beans.xml:21-28`
- **证据片段**: 三个 bean 缺少 `ioc:type` 属性，而语言适配器有 `ioc:type="io.nop.code.core.analyzer.ILanguageAdapter"`。
- **严重程度**: P3
- **建议**: 添加显式 `ioc:type` 属性。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度08-02] _lang-*.beans.xml 使用 `_` 前缀但不是生成文件

- **文件**: `_lang-java.beans.xml`、`_lang-typescript.beans.xml`、`_lang-python.beans.xml`
- **证据片段**: 文件以 `_` 前缀命名（按约定为生成文件），但 codegen 脚本不生成这些文件，实际是手写的。
- **严重程度**: P2
- **现状**: 维护者可能误认为这些文件是自动生成的不敢修改。
- **建议**: 重命名为不含 `_` 前缀的名称（如 `lang-java.beans.xml`）。
- **信心水平**: 确定
- **误报排除**: AGENTS.md 明确定义 `_` 前缀为"生成文件"标记。codegen 脚本未引用这些文件。
- **复核状态**: 未复核

### [维度08-03] CodeIndexService 中 FlowDetector/ChangeAnalyzer 交叉初始化依赖

- **文件**: `CodeIndexService.java:131-149`
- **证据片段**: setter 注入中手动管理交叉引用，通过 null 检查处理初始化顺序。
- **严重程度**: P2
- **建议**: 让 ChangeAnalyzer 直接通过 @Inject 注入 IFlowDetector，或使用 @PostConstruct。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度08-04] CodeIndexService.setRegistry() 中使用 BeanContainer.instance() 程序化查找

- **文件**: `CodeIndexService.java:174-184`
- **严重程度**: P3
- **现状**: 使用服务定位器模式而非声明式 IoC 注入。
- **建议**: 在 beans.xml 中声明属性注入或使用 @PostConstruct。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度08-05] LanguageAdapterRegistry bean 注册在 service 层而非 core 层

- **文件**: `app-service.beans.xml:15-16`（类在 nop-code-core）
- **严重程度**: P3（可能为误报，标准 Nop 分层模式）
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 08-01 | P3 | app-service.beans.xml | 三个 bean 缺少 ioc:type |
| 08-02 | P2 | _lang-*.beans.xml | 手写文件使用生成文件前缀 |
| 08-03 | P2 | CodeIndexService.java:131 | 交叉初始化依赖 |
| 08-04 | P3 | CodeIndexService.java:174 | 程序化 bean 查找 |
| 08-05 | P3 | app-service.beans.xml | bean 注册层级 |
