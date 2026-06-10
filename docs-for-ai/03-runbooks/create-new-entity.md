# 新建实体

## 适用场景

- 新增数据库表对应的实体。
- 需要为新实体生成标准 CRUD 能力。

## AI 决策提示

- 优先定义 ORM 模型，然后再生成。
- 简单 CRUD 往往不需要额外手写 Java。
- 不要先手写 Entity、DAO、Biz 接口或 `_service.beans.xml`。

## 最小闭环

### 1. 在 `model/{app}.orm.xml` 中定义实体

优先写源 ORM 模型，而不是直接写 Entity 类。

### 2. 生成或再生成项目产物

- 首次建模块：`nop-cli gen model/{app}.orm.xml -t=/nop/templates/orm -o=.`
- 后续迭代：`./mvnw clean install -T 1C`

### 3. 判断是否真的需要手写 BizModel

如果只是标准 CRUD，通常 `CrudBizModel` 的默认能力已经足够。

### 4. 验证

至少做一项：

1. 对应模块构建通过。
2. GraphQL / REST 能看到新对象的标准能力。
3. 加一条针对性测试。

## 生成结果一般会出现在哪里

| 类型 | 典型位置 |
|------|---------|
| Entity 生成物 | `*-dao/.../_gen/...` |
| 保留层 Entity | `*-dao/.../entity/Xxx.java` |
| Biz 接口 | `*-dao/.../biz/I*Biz.java` |
| BizModel | `*-service/.../*BizModel.java` |
| xbiz | `*-service/src/main/resources/_vfs/.../*.xbiz` |
| 页面文件 | `*-web/src/main/resources/_vfs/.../*.view.xml`、`*.page.yaml` |

## 常见坑

1. 编辑 `_gen/` 或其他下划线生成物。
2. 改了模型却没有重新构建。
3. 把业务骨架理解成手工搭目录，而不是从模板生成。
4. 使用 `TINYINT` / `SMALLINT` 类型。整数字段统一用 `INTEGER`（int），布尔字段直接用 `BOOLEAN`。详见 `../02-core-guides/orm-model-design.md`。

## 相关文档

- `../02-core-guides/model-first-development.md`
- `../01-repo-map/domain-module-pattern.md`
- `../04-reference/source-anchors.md`
