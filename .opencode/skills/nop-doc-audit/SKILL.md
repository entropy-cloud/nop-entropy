---
name: nop-doc-audit
description: 文档审计工作流 — 交叉验证docs文档与实际代码的一致性，修复不准确的引用和描述。触发词：文档审查、doc audit、核查文档、文档准确性。
---

# 文档审计工作流

交叉验证 `docs-for-ai/` 文档与实际代码的一致性，修复不准确、过时或缺失的内容。

## 什么时候用我

- `审查文档` / `doc audit` / `核查文档` — 验证文档准确性
- `文档引用检查` — 验证文档中的代码引用是否存在
- `文档一致性` — 检查多处文档是否描述一致

---

# AUDIT MODE

## 核心原则

1. **文档是 source of truth** — `docs-for-ai/` 是规范性文档
2. **代码是验证依据** — 文档必须与代码实际行为一致
3. **缺失的要删除** — 文档中描述但代码中不存在的内容，删除而非注释
4. **不一致的要修复** — 多处文档描述不一致时，以代码实际行为为准

## 标准流程

### Phase 1: 范围确定

```bash
# 确定审计范围
# 选项1：指定模块
MODULE="nop-auth"  # 或 nop-orm, nop-core 等

# 选项2：指定文档
DOC="docs-for-ai/02-core-guides/service-layer.md"

# 选项3：全量审计（谨慎使用）
# 扫描所有 docs-for-ai/ 下的 .md 文件
```

### Phase 2: 代码引用验证

```bash
# 1. 提取文档中的代码引用（类名、方法名、文件路径）
# 2. 在实际代码中搜索验证

# 示例：验证类名引用
CLASS_NAME="OrmEntityModelInitializer"
find . -name "*.java" | xargs grep -l "class $CLASS_NAME" 2>/dev/null

# 示例：验证方法名引用
METHOD_NAME="addInternalProps"
find . -name "*.java" | xargs grep -l "$METHOD_NAME" 2>/dev/null

# 示例：验证文件路径引用
test -f "nop-orm-model/src/main/resources/_vfs/nop/orm/imp/orm.imp.xml" && echo "EXISTS" || echo "MISSING"
```

### Phase 3: 行为一致性验证

```bash
# 1. 文档声称的行为 vs 代码实际行为
# 2. 检查参数、返回值、异常类型是否匹配
# 3. 检查配置项名称、默认值是否匹配

# 示例：验证配置项
grep -rn "nop.ai.timeout" --include="*.java" --include="*.xml" .

# 示例：验证方法签名
grep -n "public.*authenticate" nop-auth/service/src/main/java/**/*.java
```

### Phase 4: 交叉文档验证

```bash
# 1. 检查多处文档是否描述同一概念一致
# 2. 检查 INDEX.md 路由是否正确
# 3. 检查文档间的交叉引用是否有效

# 验证链接
grep -rn '\[.*\](.*\.md)' docs-for-ai/ | while read line; do
  # 检查链接目标是否存在
done
```

### Phase 5: 修复

根据审计结果，按优先级修复：

1. **High** - 删除代码中不存在的描述
2. **Medium** - 修复与代码行为不一致的描述
3. **Low** - 补充缺失但重要的信息

---

# 常见审计场景

## ORM 模型文档审计

```bash
# 验证 ORM 模型文档中的实体定义
# 1. 提取文档中声称的实体名
# 2. 在 *.orm.xml 中搜索验证
# 3. 检查字段、关系、域定义是否匹配

# 示例
grep -rn "entity.*name=" docs-for-ai/ | head -10
find . -name "*.orm.xml" | head -5
```

## API 文档审计

```bash
# 验证 API 文档中的接口定义
# 1. 提取文档中声称的 API 路径
# 2. 在 *.api.xml 或 GraphQL schema 中搜索验证
# 3. 检查参数、返回值是否匹配
```

## 配置文档审计

```bash
# 验证配置文档中的配置项
# 1. 提取文档中声称的配置项
# 2. 在 *.xml 或 *.properties 中搜索验证
# 3. 检查默认值是否匹配
```

---

# 审计报告格式

```markdown
# 文档审计报告 - [模块名]

## 审计范围
- 文档: `docs-for-ai/xxx.md`
- 代码: `nop-xxx/`

## 发现问题

### High - 不存在的描述
| 位置 | 问题 | 建议 |
|------|------|------|
| line 42 | 声称存在 `FooBar` 类，但代码中不存在 | 删除该段落 |

### Medium - 行为不一致
| 位置 | 文档描述 | 实际行为 | 建议 |
|------|---------|---------|------|
| line 78 | 返回 `List<String>` | 返回 `List<Integer>` | 更新文档 |

### Low - 缺失信息
| 位置 | 缺失内容 | 重要性 | 建议 |
|------|---------|--------|------|
| line 100 | 未说明异常处理 | 中 | 补充说明 |

## 修复建议
1. ...
2. ...
```

---

# 工具辅助

项目中已有的文档检查工具：

```bash
# 链接检查
node ai-dev/tools/check-doc-links.mjs --strict

# 文档索引检查
node ai-dev/tools/check-doc-index.mjs

# 文档乱码检查
node ai-dev/tools/check-docs-garbled.mjs

# 超大文件检查
node ai-dev/tools/check-oversized-files.mjs

# ORM图标检查
node ai-dev/tools/check-orm-icons.mjs
```

---

# 反模式

1. **不要只读文档不读代码** — 必须交叉验证
2. **不要保留不存在的描述** — 删除而非注释
3. **不要假设文档正确** — 文档可能过时
4. **不要一次性审计太多** — 分模块逐步进行

---

# 最终检查清单

- [ ] 确定了审计范围？
- [ ] 提取了文档中的代码引用？
- [ ] 在实际代码中验证了引用？
- [ ] 检查了行为一致性？
- [ ] 生成了审计报告？
- [ ] 修复了 High/Medium 问题？
- [ ] 运行了链接检查工具？
