# 维度10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] _lang-typescript.beans.xml 缺少 xsi 声明

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml`
- **行号**: L1-8
- **证据片段**:
  ```xml
  <?xml version="1.0" encoding="UTF-8" ?>
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc"
         xmlns="http://www.springframework.org/schema/beans">
  ```
- **严重程度**: P3
- **现状**: 此文件缺少 xmlns:xsi 和 xsi:schemaLocation 声明，而其他 beans.xml（lang-java、lang-python、dao、service）都包含。
- **风险**: 功能无影响（NopIoC 不依赖 xsi:schemaLocation），但风格不一致。
- **建议**: 统一格式。
- **信心水平**: 确定
- **误报排除**: 与其他同模式文件直接对比即可确认不一致。
- **复核状态**: 未复核

## 通过项

1. 所有 XDSL 文件的 x:schema 引用正确
2. x:extends 链路完整（_service.beans.xml → service-base.beans.xml 等）
3. beans.xml 中 bean 定义与 Java 类路径一致
4. xbiz 文件方法声明与 BizModel 兼容（xbiz 是声明性元数据）
5. 所有 xmeta 的 x:extends 和 x:schema 正确
