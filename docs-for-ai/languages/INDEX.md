# Languages Documentation Index

## 概述

本目录包含Nop平台使用的各种语言和DSL的相关文档。

## 文档列表

### XLang语言
- [XLang编程指南](../getting-started/xlang/xlang-guide.md) - XLang语言完整指南
- [XDef核心概念](../getting-started/xlang/xdef-core-concepts.md) - XDef核心概念速查
- [XDSL与Delta合并](../getting-started/xlang/xdsl-delta.md) - ⭐ XDSL文件格式、Delta合并、x:override/x:extends/x:gen-extends/x:post-extends
- [XPL模板语言](../getting-started/xlang/xpl.md) - XPL模板语言语法
- [XScript脚本语言](../getting-started/xlang/xscript.md) - XScript脚本语言语法
- [元编程指南](../getting-started/xlang/meta-programming-guide.md) - 元编程

### XDSL概念

**XDSL**（X Domain Specific Language）是Nop平台中所有领域特定语言的统一框架，提供：
- **统一的XDef元模型**：自动生成解析器、验证器、IDE支持
- **统一的Delta合并机制**：支持模型的继承、复用、定制
- **统一的语法结构**：所有DSL都基于XML格式，使用相同的扩展语法
- **差量化定制**：通过Delta格式实现可逆计算理论要求的App = Delta x-extends Generator<DSL>范式

**常用XDSL文件**：orm.xml、beans.xml、api.xml、app.orm.xml、app.api.xml、xview.xml、task.xml、batch.xdef等
> 注意：model目录下使用模块名（如nop-auth.orm.xml），_vfs目录下使用app.orm.xml等命名
**XDSL文件格式与Delta合并**：详见[XDSL与Delta合并文档](../getting-started/xlang/xdsl-delta.md)

### 元编程
- [代码生成](./code-generation.md) - 代码生成机制
- [模型驱动开发](./model-driven-development.md) - 模型驱动开发模式

## 快速链接

- [元编程指南](../getting-started/xlang/meta-programming-guide.md)
- [快速参考](../quick-reference/)

## 状态

- 🔄 计划中
- 📝 待补充

## 相关文档

- [开发文档总览](../INDEX.md)
- [快速参考](../quick-reference/)
