# 维度 05：生成管线完整性

## 第 1 轮（初审）

### 检查范围

检查从 `model→codegen→dao→meta→service→web` 的完整生成链路，涵盖 6 个核心生成子模块（codegen、dao、meta、service、web、app）。

### 结论：零发现

生成管线完整闭合，所有检查项均通过：

1. **源模型**：`model/nop-code.orm.xml` 格式正确，定义 12 个 domain、8 个 dict、11 个 entity
2. **codegen 脚本**：`postcompile/gen-orm.xgen` 正确引用源模型，两步生成策略正确
3. **dao 产物**：11 个 `_gen/_Xxx.java`、11 个保留层 `Xxx.java`、11 个 `IXxxBiz.java`、`_app.orm.xml` — 全部与源模型一致
4. **meta 产物**：11 对 xmeta + i18n（en/zh-CN）覆盖全部实体和属性
5. **web 产物**：11 对 view.xml + page.yaml + lib.xjs + action-auth.xml + web i18n
6. **service 产物**：11 对 xbiz + 11 个 BizModel + `_service.beans.xml` + `app-service.beans.xml`
7. **Maven 插件配置**：4 个执行阶段（precompile/precompile2/aop/postcompile）正确配置

## 最终保留项

无保留项。
