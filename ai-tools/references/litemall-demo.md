# 以Litemall项目为例验证AI自动开发

## 1. 生成requirements

采用如下提示词反向根据litemall项目的代码生成与具体技术框架无关的需求文档。

```markdown
@ai-tools\commands\simple-reverse-gen-requirements.md 分析c:/can/nop/litemall项目，保存到litemall-requirements.md中
```

## 2. 生成openspec

在nop-app-mall项目下执行如下命令

```markdown
/openspec-proposal  根据 requirements/litemall-requirements.md 需求，使用Nop平台技术来实现。目前数据库已经生成，实体定义和BizModel， view.xml,xmeta等模型文件都已经自动生成，不需要再调整数据库，并且已经有少量功能实现代码。需要参考docs-for-ai目录下的Nop平台文档，并且在design.md中一定要注明参考这些文档来设计。所有design和implemetation都必须符合docs-for-ai的设计规范和最佳实践.
```

为了方便AI查找，将nop-entropy目录下的文档拷贝到nop-app-mall项目下。如果是指定绝对路径也可以读取，但是AI在项目外的目录操作容易出现一些理解偏差。

## 3. 检查design符合Nop平台框架规范并遵循最佳实践

