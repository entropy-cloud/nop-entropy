# 跨模块代码生成

## 正确的构建顺序

```bash
# 根目录构建（推荐）
mvn clean install

# 手动构建
cd xxx-codegen && mvn install
cd ../xxx-dao && mvn install
cd ../xxx-meta && mvn install    # ⭐ 关键步骤
cd ../xxx-service && mvn install
cd ../xxx-web && mvn install
cd ../xxx-app && mvn install
```

## 修改模型后重新生成

```bash
cd xxx-codegen && mvn clean install
cd ../xxx-dao && mvn clean install
cd ../xxx-meta && mvn clean install    # ⭐ 关键步骤
cd ../xxx-service && mvn clean install
cd ../xxx-web && mvn clean install
cd ../xxx-app && mvn clean install
```

## 执行时机

| 模块 | Maven 命令 | 触发的代码生成 | 生成的目标模块 |
|------|-----------|--------------|--------------|
| xxx-codegen | `mvn install` | gen-orm.xgen | xxx-dao |
| xxx-meta | `mvn install` | gen-meta.xgen | xxx-service |
| xxx-web | `mvn install` | gen-page.xgen | xxx-web（自身） |

## xgen脚本位置

```
xxx-codegen/
└── postcompile/
    └── gen-orm.xgen              # 生成 dao 模块的代码

xxx-meta/
├── precompile/
│   └── gen-meta.xgen              # 生成 xmeta 文件
└── _templates/
    └── *.json                     # biz文件模板

xxx-web/
└── precompile/
    └── gen-page.xgen              # 生成页面文件 ⭐
```

## 代码生成的内容

| 模块 | 生成内容 | 生成来源 |
|------|---------|---------|
| xxx-dao | Entity.java, IEntityDao, EntityDaoImpl | xxx-codegen |
| xxx-service | BizModel.java | xxx-meta ⭐ |
| xxx-web | view.xml, page.yaml | xxx-web（自身） ⭐ |

## 相关文档

- [差量化软件生产线](./codegen-concepts.md)
- [Delta定制基础](../delta/delta-basics.md)
- [10分钟快速上手](../quickstart/10-min-quickstart.md)

---

**文档版本**: 1.0
**最后更新**: 2026-01-21
