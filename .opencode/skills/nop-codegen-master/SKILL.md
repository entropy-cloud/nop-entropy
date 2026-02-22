---
name: nop-codegen-master
description: 使用 nop-cli gen 命令从 ORM 模型文件生成 Nop 平台初始项目脚手架（仅初次生成）。生成后通过 mvn install 迭代。触发词：代码生成、gen、生成项目、脚手架、初始化项目。
---

# nop-codegen-master Skill

使用 Nop 平台的代码生成器从 ORM 模型文件**初次生成**项目脚手架。

## 什么时候用我

| 场景 | 触发关键词 |
|------|-----------|
| 从零开始创建新项目 | "生成项目", "脚手架", "初始化" |
| 有 ORM 模型，需要生成完整工程 | "gen", "代码生成" |

## 前置条件

1. **nop-cli 已安装**：全局命令行可用 `nop-cli`
2. **ORM 模型文件就绪**：`model/xxx.orm.xml`

## 核心命令

```bash
nop-cli gen <模型文件> -t=/nop/templates/orm [-o=<输出目录>]
```

### 参数说明

| 参数 | 必填 | 说明 |
|------|------|------|
| `<模型文件>` | 是 | ORM 模型文件路径 |
| `-t` | 是 | 模板路径，初次生成用 `/nop/templates/orm` |
| `-o` | 否 | 输出目录（默认当前目录） |
| `-F` | 否 | 强制覆盖已存在文件 |

## 使用示例

### 基本用法

```bash
# 从 XML 模型生成完整项目
nop-cli gen model/app-demo.orm.xml -t=/nop/templates/orm -o=.
```

### 生成结果

```
├─app-demo-api        # 对外接口定义和消息定义
├─app-demo-codegen    # 代码生成辅助工程（后续迭代用）
├─app-demo-dao        # 数据库实体定义和 ORM 模型
├─app-demo-service    # GraphQL 服务实现
├─app-demo-web        # AMIS 页面文件以及 View 模型定义
├─app-demo-app        # 测试使用的打包工程
└─deploy          # 数据库建表语句
```

## 模板定制

在执行目录下创建 `_vfs` 目录，放入自定义模板：

```
├─ _vfs/
│  └─ my/
│     └─ templates/
│        └─ orm/      # 自定义模板
```

```bash
# 使用自定义模板
nop-cli gen model/app-demo.orm.xml -t=/my/templates/orm -o=.
```

## 后续迭代流程

**初次生成后，不再需要 nop-cli**

```
1. 修改模型文件 → model/app-demo.orm.xml
2. 执行构建 → cd app-demo-codegen && mvn install
3. 自动重新生成 → _gen/ 目录更新，手写代码保留
```

## 文件覆盖规则

| 类型 | 规则 | 行为 |
|------|------|------|
| 自动生成 | `_gen/` 目录、`_` 前缀文件 | **始终覆盖**，禁止编辑 |
| 手写代码 | 无下划线前缀 | **永久保留** |

## 常见问题

### Q: 重新 gen 会覆盖手写代码吗？

不会。手写代码放在非 `_` 前缀的文件中即可。

### Q: 后续如何更新代码？

修改模型后执行 `mvn install`，`xxx-codegen` 模块会自动触发代码生成。

## 排障清单

1. **nop-cli 命令不存在** → 提示用户安装 nop-cli 到全局 PATH
2. **模板找不到** → 确认模板路径正确，自定义模板放在 `_vfs/` 下
3. **模型解析失败** → 确认模型文件格式正确

