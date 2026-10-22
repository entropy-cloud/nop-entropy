# 报表条件格式规则集 设计

**范围**：`nop-kernel/nop-xdefs`（excel-table.xdef cell model）、`nop-format/nop-excel`（XptCondStyleModel）、`nop-report/nop-report-core`（求值）
**状态**：active（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 条件格式规则集内嵌 XptCellModel（`<condStyles>` 有序列表），声明式：表达式谓词或数值区间 + 目标 styleId；**首个命中规则生效**并覆盖 `styleIdExpr` 结果，全部未命中保持原样式。
2. 求值在展开求值期（styleIdExpr 之后、格式化之前），产物是 styleId——经既有样式表机制被 xlsx（全样式）、html（全样式）、pdf（**仅字体属性**，`PdfTableRenderer.getStyleFont`）消费。
3. 规则谓词异常 **fail-loud**：向上传播（xlang 异常保留原错误码；包装异常 `nop.err.xpt.cond-style-expr-error` 带 cell/sheet 参数），与 rowTestExpr 既有语义一致。
4. **数据条/色阶/图标集不做**（roadmap WI17 承载）：需 OOXML conditionalFormatting 写出部件 + html/pdf 可视化等价，绘制保真量级；表达式/区间规则覆盖条件样式主场景。

## 二、背景与动机

单一 `styleIdExpr` 要求模板作者在表达式里写完整分支逻辑（值→样式映射），不可声明、不可复用；规则集让「梯度着色」类需求成为纯声明。

## 三、核心设计

### 规则声明（cell model 内）

```xml
<condStyles>
    <condStyle styleId="high-style">
        <expr>cellValue &gt;= 90</expr>
    </condStyle>
    <condStyle styleId="mid-style">
        <min>50</min>
        <max>90</max>
    </condStyle>
</condStyles>
```

- `expr`（xpl-predicate）与 `min`/`max`（可单边）二选一；区间闭区间，min>max 视为不命中；单元格值不可转数值时区间规则不命中。
- `expr` 求值上下文注入局部变量 `cellValue`=当前单元格值（求值后清理，不泄漏）。
- `cellValue` 无命名冲突（report-core 无既有用法）；规则覆盖 styleId 后 number format 随之改变（渲染器按 styleId 取格式）——期望行为。

### 兼容性

`condStyles` 为 cell `<model>` 可选子元素，对既有 xlsx/xpt 模板解析无影响（xlsx 导入路径不产生 model 元素）。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 数据条/色阶/图标集随本能力一起实现 | OOXML conditionalFormatting 部件 + 三端可视化等价为绘制保真量级，独立交付（WI17） |
| 行级条件规则 | cell 级覆盖典型场景；行级样式已有 rowStyleIdExpr 既有语义 |
| 规则求值异常静默跳过 | 模板作者表达式错误被吞=样式静默缺失；fail-loud 与 rowTestExpr 一致 |

## 五、与已有设计的关系

- `html-pagination.md`/`large-dataset.md`：正交（分页/防线在样式求值之外的层）。
- pdf 侧样式消费的字体子集口径与姊妹 roadmap 的绘制保真工作衔接。
