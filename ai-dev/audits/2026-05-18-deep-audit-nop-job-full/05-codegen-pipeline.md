# 维度05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] codegen 脚本引用生成产物而非源模型作为输入

- **文件**: nop-job/nop-job-codegen/postcompile/gen-orm.xgen:4-8
- **证据片段**:
```xml
1: <?xml version="1.0" encoding="UTF-8" ?>
2: <c:script>
3: // 根据ORM模型生成dao/entity/xbiz
4: codeGenerator.withTargetDir("../").renderModel('../../model/nop-job.orm.xml','/nop/templates/orm', '/',$scope);
5: codeGenerator.withTargetDir("../nop-job-dao/src/main/java").renderModel('../../nop-job-dao/src/main/resources/_vfs/nop/job/orm/app.orm.xml',
6:     '/nop/templates/orm-entity','/',$scope);
7: codeGenerator.withTargetDir("../").renderModel('../../nop-job-dao/src/main/resources/_vfs/nop/job/orm/app.orm.xml',
8:    '/nop/templates/orm-model','/', $scope);
9: </c:script>
```
- **严重程度**: P1
- **现状**: 第 4 行使用源模型 `../../model/nop-job.orm.xml` 生成到 `../`（nop-job 根目录），但第 5 行和第 7 行却引用了 `../../nop-job-dao/src/main/resources/_vfs/nop/job/orm/app.orm.xml` 作为输入源。这意味着代码生成逻辑存在级联依赖：第一步从源模型生成 app.orm.xml，后续步骤使用已生成的 app.orm.xml 再次生成 entity 和 biz 文件。
- **风险**: 1) 如果 `app.orm.xml` 不存在或过时，会导致 Entity 和 biz 文件生成失败或内容不一致；2) 构建顺序依赖关系复杂，可能导致增量构建时产物未及时更新；3) 如果第一步的输出路径或文件名变更，后续步骤的输入引用会断裂。
- **建议**: 1) 确认这是 Nop 平台的级联生成标准模式——先从 orm.xml 生成 app.orm.xml，再从 app.orm.xml 生成 entity 和 biz 文件；2) 如果是标准模式，在脚本中添加注释说明级联关系；3) 确保 Maven 构建配置中 gen-orm.xgen 的执行顺序正确。
- **误报排除**: 经对比 Nop 平台其他模块（如 nop-auth），这种级联生成模式是平台标准做法。gen-orm.xgen 第一步生成 _app.orm.xml，后续步骤基于合并后的 app.orm.xml（含 delta 定制）再次生成。降级为确认项，非缺陷。

---

### [维度05-02] meta 模块依赖仅 test scope，但构建需要 codegen 和 dao

- **文件**: nop-job/nop-job-meta/pom.xml:15-27
- **证据片段**:
```xml
15:     <dependencies>
16:         <dependency>
17:             <artifactId>nop-job-codegen</artifactId>
18:             <groupId>io.github.entropy-cloud</groupId>
19:             <version>2.0.0-SNAPSHOT</version>
20:             <scope>test</scope>
21:         </dependency>
22:         <dependency>
23:             <artifactId>nop-job-dao</artifactId>
24:             <groupId>io.github.entropy-cloud</groupId>
25:             <scope>test</scope>
26:         </dependency>
27:     </dependencies>
```
- **严重程度**: P2
- **现状**: nop-job-meta 模块依赖 nop-job-dao 来获取实体定义用于生成 XMeta 文件，但这些依赖都是 test scope。构建通过 exec-maven-plugin 的 `classpathScope=test` 配置（pom.xml:35）在 precompile 阶段运行 gen-meta.xgen 脚本。
- **风险**: 如果 Maven exec 插件配置不当（如缺少 `classpathScope=test`），gen-meta.xgen 脚本将找不到依赖的类，导致构建失败。
- **建议**: 这是 Nop 平台 meta 模块的标准模式，无需修改。确认 exec-maven-plugin 配置正确即可。
- **误报排除**: 这是 Nop 平台标准模式，meta 模块通过 test scope + classpathScope=test 运行构建时代码生成。不是缺陷。

---

### [维度05-03] web 子模块 gen-page.xgen 生成脚本正确性确认

- **文件**: nop-job/nop-job-web/precompile/gen-page.xgen
- **证据片段**: （脚本内容遵循平台标准模式）
- **严重程度**: N/A（确认项）
- **现状**: web 子模块的 gen-page.xgen 脚本使用标准模板路径生成 view/page 文件。生成产物（page.yaml）与 XView 模型对应。
- **风险**: 无。
- **建议**: 无需修改。确认生成管线在 web 层正确闭合。
- **误报排除**: 不适用。

---

### [维度05-04] xbiz 文件与 BizModel 方法的对应关系确认

- **文件**: nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xbiz
- **文件**: nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java
- **严重程度**: N/A（确认项）
- **现状**: NopJobSchedule.xbiz 是代码生成的声明性元数据文件，与 NopJobScheduleBizModel.java 的 BizModel 方法通过 GraphQL 反射机制对应。xbiz 中的方法签名不需要与 Java 方法完全一致——这是 Nop 平台的标准模式（GraphQL 引擎通过反射调用，xbiz 是声明性元数据）。
- **风险**: 无。
- **建议**: 无需修改。
- **误报排除**: xbiz 方法签名与 BizModel Java 方法不完全一致是平台标准模式，不属于缺陷。

---

## 总结

生成管线从 model→codegen→dao→meta→service→web 基本闭合。主要发现：

1. **P1（确认项）**: gen-orm.xgen 使用级联生成模式（先从 orm.xml 生成 app.orm.xml，再从 app.orm.xml 生成 entity/biz）。这是 Nop 平台标准做法，非缺陷。
2. **P2（确认项）**: meta 模块通过 test scope + classpathScope=test 运行构建时生成。标准模式。

**无 P0 或 P1 缺陷。** 生成管线整体完整，符合 Nop 平台模型驱动开发的标准模式。
