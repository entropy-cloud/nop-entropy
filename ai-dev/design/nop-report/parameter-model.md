# 报表参数模型 设计

**范围**：`nop-format/nop-excel`（XPT 模型层）、`nop-report/nop-report-core`（引擎取值）、`nop-report/nop-report-service`（查询表单接口）
**状态**：active（参数模型已落地）
**灵感来源**：主流报表引擎（FineReport 参数界面、润乾参数模板、JasperReport Parameters）均以声明式参数 + 查询表单为标准能力

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 报表参数采用**声明式参数模型**（ReportParameterModel），**内嵌 XPT 模型**（workbook 级内嵌 model，与 `XptWorkbookModel` 同机制），不引入外挂 JSON settings。
2. 模板内统一以 `${param.<name>}` 取参数值；引擎在展开前把解析后的参数注入求值 scope，应用侧不再需要手工 `IEvalScope.setLocalValues` 塞参数（后者仍是合法的逃生通道）。
3. 参数元数据（类型/控件/候选来源/联动）同时驱动两件事：后端参数校验/默认值/级联解析接口，以及自动生成的 AMIS 查询表单。
4. 参数参与报表结果缓存键（「报表 + 参数 + 数据版本」），见 `html-pagination.md` 同级的缓存约定与 result-file 契约。

## 二、背景与动机

参数是报表平台与报表库的分界：库只要求调用方自己准备数据上下文；平台必须让最终用户在浏览器里选择参数并安全地把选择传到取数与渲染链路。当前参数只能由应用代码硬塞 scope，无声明、无校验、无表单，模板作者无法表达「这个报表需要哪些输入」。

## 三、核心设计

### 参数声明面（模板作者）

| 属性 | 契约 |
|------|------|
| name | 参数名，模板内 `${param.name}` 引用；同 workbook 内唯一 |
| type | 基础标量类型（string/int/float/boolean/date/datetime）+ 可选列表形态 |
| label / 控件类型 | 展示名与 AMIS 控件（input/select/date/…），控件类型只影响表单生成，不改变后端语义 |
| required / default | 必填校验与默认值；默认值在无显式传参时生效 |
| options 来源 | `SQL`（以数据集形式执行取 label/value 两列）/ `字典`（平台 dict）/ `静态`（枚举列表）；供表单下拉与后端校验共用 |
| 联动 / 级联 | 参数可声明依赖其他参数（父参数变化 → 重新拉取 options）；级联只影响候选集，不自动改已选值 |

### 运行时契约

- 引擎解析模板时读取参数声明；未通过校验（缺 required、类型不符）→ `nop.err.report.param.*` 错误码快速失败（missing-required / type-error / unknown），英文消息指明参数名与原因。
- 校验通过后以 `param` 为根注入求值 scope；模板中 `${param.xxx}` 统一取值。未声明的 `${param.x}` 引用运行时取 null——原契约「未声明即引用 → 展开期报错」**不实现**（裁定：XPL 动态取值无法静态识别引用，静态校验必产生误报）；未声明的输入参数原样保留在 `param` Map 中。
- 候选集不做枚举闭合校验——原契约「options 越界 → 快速失败」**不实现**（裁定：候选集可动态来自 SQL/字典，输入合法性由控件与下游语义约束）。
- 参数解析/校验/级联候选接口由 `nop-report-service` 以标准 BizModel action 暴露（GraphQL/REST 同源），AMIS 查询表单由参数声明自动生成。
- 缓存键中的参数以「解析后的规范化值」参与，不携带控件元数据（缓存接线由 roadmap WI6 消费）。

### 边界

- 参数模型不改数据集标签（`xpt-rt.xlib`）的取数语义；行/列级数据过滤属数据级安全设计（见 `auth-wiring.md` 的边界段）。
- 控件类型与表单布局不进入 XPT 模型的展开语义；渲染器（xlsx/html/pdf）对参数无感知。
- 查询表单的可视化设计属 Web 设计器范畴，本契约只保证「声明 → 表单 schema」的映射稳定。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| 外挂 JSON settings 文件承载参数 | 与 workbook.xdef 内嵌 model 的既有做法相悖；模板可移植性断裂（模板与参数定义可分离失配）；delta 定制困难 |
| 维持「应用硬塞 IEvalScope」现状 | 无声明即无校验、无表单、无缓存键规范化；平台化无从谈起 |
| 参数校验只放前端表单 | 绕过表单直接调渲染接口即可越权注入；后端必须以同一份声明校验 |

## 五、与已有设计的关系

- `auth-wiring.md`：参数解析与授权检查同处渲染入口链路，授权先于参数取数生效。
- `nop-report` 运行时实体（`NopReportDefinition` 等）承载模板存放与 CRUD；参数声明内嵌于模板模型本身，不新建参数实体。
- 既有 `XptWorkbookModel` 内嵌 model 机制是本设计的挂接点；指令扩展走 xdef 源，不手改 `_gen` 生成物。
