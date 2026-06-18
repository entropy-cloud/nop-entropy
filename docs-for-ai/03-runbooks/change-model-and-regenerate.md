# 修改模型后重新生成

## 适用场景

- 你修改了 `model/*.orm.xml`。
- 你想确认哪些模块会接收新的生成结果。

## AI 决策提示

- 最简单可靠的做法是从项目根目录触发 Maven 构建。
- 如果只改了生成物而没回到源模型，先停下来修正路径。

## 最小闭环

### 1. 确认你改的是源模型

优先改 `model/{app}.orm.xml`，而不是 `_app.orm.xml` 或 `_gen` 文件。

### 2. 重新构建

```bash
./mvnw clean install -T 1C
```

### 3. 理解生成职责

| 模块 | 作用 |
|------|------|
| `*-codegen` | 刷新项目级生成产物 |
| `*-dao` | 刷新 ORM、Entity、接口等 |
| `*-meta` | 刷新 XMeta 与 i18n |
| `*-web` | 刷新页面文件 |

文件级生成顺序（以 `*-codegen/postcompile/gen-orm.xgen` 为核心）：

```text
model/{app}.orm.xml                          ← 唯一源（手编辑入口）
    │  gen-orm.xgen 第 1 步 (/nop/templates/orm)
    ▼
{app}-dao/_vfs/.../orm/_app.orm.xml          ← 生成物（聚合 ORM，不可手改）
    │  app.orm.xml 通过 x:extends 继承 _app.orm.xml
    │  gen-orm.xgen 第 2 步 (/nop/templates/orm-entity)
    ▼
{app}-dao/.../entity/_gen/_Nop*.java          ← 生成物（实体类，不可手改）
```

> **绝对不要手改 `_app.orm.xml`**。它含完整实体/字段/dict 定义，看起来像“源”，但它是 gen-orm.xgen 第 1 步从 `model/*.orm.xml` 生成的聚合 ORM，手改会在重新构建时被覆盖。改字段只改 `model/*.orm.xml`，然后重新生成。

### 4. 检查结果

至少检查一个下游点位是否更新：

1. `*-dao` 下的 ORM / Entity / 接口
2. `*-meta` 下的 XMeta / i18n
3. `*-web` 下的页面资源

### 5. XDef / XMeta 特例

- 如果改的是 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/**/*.xdef` 这类 XDef 源模型，不要只重建 `nop-xdefs`。
- `obj-schema.xdef` 这类 schema 还会通过 `nop-kernel/nop-xlang/precompile/gen-xlang-xdsl.xgen` 生成 `nop-xlang/src/main/java/**/_gen/*.java`。
- 这类改动至少要继续验证 `nop-xlang` 的生成结果和相关测试，例如 `TestXMetaRef`。
- 注意同名结构可能在多个 xdef 中重复定义，例如 `obj-schema.xdef` 里的 `props` 改动，往往还需要同步检查 `schema.xdef`。

## 常见坑

1. 只重跑下游模块，没有刷新上游生成物。
2. 以为 `*-meta` 会直接生成全部 service / web 代码。
3. 手改生成物后再去构建，结果改动被覆盖。
4. 只改了 `obj-schema.xdef`，却漏掉 `schema.xdef` 等共享定义点，导致运行时解析出的 xdef/xmeta 仍然缺字段。
5. 修改了 `ext:basePackageName` / `ext:entityPackageName`，却只更新了 orm.xml 内的引用，没有同步迁移 Java 源文件目录、package 声明和 import，也没有删除旧 `_gen/` 让 codegen 重新生成。详见 `../02-core-guides/model-first-development.md` 的"修改包名的影响与迁移步骤"一节。

## 相关文档

- `../02-core-guides/model-first-development.md`
- `../01-repo-map/domain-module-pattern.md`
- `../04-reference/source-anchors.md`
