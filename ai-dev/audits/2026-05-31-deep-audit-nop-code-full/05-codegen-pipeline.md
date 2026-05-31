# 审核维度 05：生成管线完整性

## 第 1 轮（初审）

**结论：未发现问题。**

生成管线从 model/nop-code.orm.xml 出发，经过 4 个 xgen 脚本和 5 层模块（codegen→dao→meta→service→web），所有 11 个实体在各层的生成产物数量正确、名称匹配、引用路径有效、Delta 继承关系正确、POM 依赖链闭合。

### 验证详情

- 源模型 11 个实体 → DAO 11 个 _gen/*.java + 11 个手写 entity → Meta 11 个 _NopCode*.xmeta + 11 个手写 delta xmeta → Service 11 个 _NopCode*.xbiz + 11 个手写 delta xbiz → Web 11 个 _gen/_NopCode*.view.xml
- 6 个 dict → 6 个 dict/*.yaml → _NopCodeDaoConstants.java 中 40 个常量值一致
- gen-orm.xgen 引用 ../../model/nop-code.orm.xml 正确
- gen-meta.xgen 引用 /nop/code/orm/app.orm.xml (VFS) 正确
- POM 依赖链闭合：dao(test→codegen), meta(test→codegen+dao), web(test→codegen), service(test→codegen)
