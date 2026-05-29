# 调试代码生成与生成文件

## 适用场景

- 生成结果不符合预期。
- 你想确认某个文件是从哪里来的。
- 你想判断应该改模型、模板还是保留层文件。

## AI 决策提示

- 先判断它是不是生成物。
- 先回到源模型、`.xgen` 或模板，不要直接改输出文件。
- 如果链路不清楚，先看 `*_codegen`、`*_meta`、`*_web` 的生成职责。
- 先确认当前模块的 `pom.xml` 是否显式声明了 `exec-maven-plugin`；父 POM 只提供 execution 配置，子模块未声明插件时，`precompile` / `precompile2` / `postcompile` 不会运行。

## 最小闭环

### 1. 判断文件是不是生成物

高概率生成物包括：

- `_gen/`
- `_` 前缀文件
- `_app.orm.xml`
- `_service.beans.xml`
- `_*.xbiz`

### 2. 追溯来源

| 目标文件 | 先看哪里 |
|----------|---------|
| Entity / `I*Biz` / 业务骨架 | `*-codegen/postcompile/gen-orm.xgen`、`/nop/templates/orm` |
| XMeta / `module-meta.json` | `*-meta/precompile/gen-meta.xgen`、`/nop/templates/meta` |
| i18n | `*-meta/postcompile/gen-i18n.xgen` |
| view / page | `*-web/precompile/gen-page.xgen`、`/nop/templates/orm-web` |

### 3. 重新生成

在重跑之前，先确认 Maven 生命周期是否真的会触发对应生成阶段。

#### 3.1 先检查插件绑定

1. 看父 POM 是否定义了 `exec-maven-plugin` 的 execution 绑定。
2. 再看当前模块的 `build/plugins` 是否显式声明 `<artifactId>exec-maven-plugin</artifactId>`。
3. 如果模块没有声明该插件，即使父 POM已经配置了 `precompile` / `precompile2` / `postcompile`，这些阶段也不会执行。

#### 3.2 三个阶段的时机与 classpath 差异

| 阶段 | Maven phase | `addResourcesToClasspath` | `addOutputToClasspath` | 默认用途 | 排查重点 |
|------|-------------|---------------------------|------------------------|----------|----------|
| `precompile` | `generate-sources` | `false` | `false` | 最早期生成，避免过早看到未编译输出 | 适合依赖 source 模型和模板；不要假设能看到本模块 resources/classes |
| `precompile2` | `generate-sources` | `true` | `true` | 第二轮预生成 | 可以读取本模块 resources / output；适合需要消费第一轮生成结果的任务 |
| `postcompile` | `generate-test-resources` | `true` | `true` | 编译后生成 | 适合依赖编译结果、resources 或下游衍生文件的生成任务 |

如果现象是“明明有 `precompile` 目录但没执行”或“模板里读不到某个资源”，先不要猜模板问题，先判断：

1. 当前模块是否声明了 `exec-maven-plugin`。
2. 目标任务实际挂在 `precompile`、`precompile2` 还是 `postcompile`。
3. 当前阶段的 classpath 是否包含你期望读取的 resources / output。

默认优先从项目根目录执行：

```bash
./mvnw clean install -T 1C
```

### 4. 检查下游结果

至少检查一个下游点位：

1. `*-dao` 下的 ORM / Entity / 接口。
2. `*-meta` 下的 XMeta / i18n。
3. `*-meta` 下的 `/{moduleId}/model/module-meta.json`（如果页面或菜单逻辑依赖模块级元数据）。
4. `*-web` 下的页面资源。
5. 如果还有手写 source `*.action-auth.xml` 覆盖文件，确认显式 `TOPM` / `SUBM` 资源也带有 `icon`。

## 常见坑

1. 直接改生成文件。
2. 误把 `*-meta` 当成 service / web 的唯一上游。
3. 忘记页面模板读取的可能是 `module-meta.json`，而不是直接读取 `/{moduleId}/orm/app.orm.xml`。
4. 页面不对时去改输出文件，而不是回到 xmeta / `module-meta.json` / page 生成链路。
5. 只重跑下游模块，没有刷新上游生成物。
6. 以为只有 ORM entity 需要 icon，忘了根 `<orm ext:icon>` 也会直接影响 generated TOPM 菜单。
7. 手写 `*.action-auth.xml` 中声明了 `TOPM` / `SUBM`，却没显式写 `icon`。
8. 以为父 POM 配了 `exec-maven-plugin` execution，子模块就一定会跑；实际上还要看子模块是否声明插件。
9. 把 `precompile`、`precompile2`、`postcompile` 当成等价阶段，忽略它们看到的 classpath 不同。

## 相关文档

- `./change-model-and-regenerate.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/debugging-and-diagnostics.md`
- `../04-reference/source-anchors.md`
