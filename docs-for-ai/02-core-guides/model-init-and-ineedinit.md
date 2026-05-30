# 模型初始化与 INeedInit

本页回答一个高频问题：

**什么时候应该实现 `INeedInit`，框架什么时候会自动调用它，什么时候必须手动调用它。**

## 默认结论

1. `INeedInit` 用于**把解析后的原始 DSL/模型数据整理成运行期需要的派生结构**。
2. 如果模型是通过标准 XDSL 解析链加载，`DslModelParser` 会在反序列化后自动调用 `init()`。
3. 如果模型是你在 Java 中手工 new、手工 merge、手工拼装出来的，通常需要你自己调用 `init()`。
4. `init()` 里应该优先做派生字段填充、索引构建、父子引用回填、校验前预处理；不要做外部副作用。
5. `INeedInit` 适合“模型装载后的最后一步整理”，不适合替代普通 getter、service 初始化或带外部资源依赖的启动流程。

## 接口定义

`INeedInit` 很简单：

```java
public interface INeedInit {
    void init();
}
```

语义重点不在接口本身，而在**调用时机**。

## 自动调用时机

标准 XDSL 加载链中，`DslModelParser` 在把 XML/XDSL 节点转换成 Java 对象后，会自动调用 `init()`：

```java
if (!disableInit && model instanceof INeedInit)
    ((INeedInit) model).init();
```

这意味着：

1. 通过标准模型加载器读取 DSL 文件时，通常**不需要**额外手动调用一次 `init()`。
2. 如果解析链显式设置了 `disableInit(true)`，自动初始化会被关闭。

## 什么时候要实现 INeedInit

最适合的场景是：模型刚被加载后，需要把“声明式结构”转成“运行期可直接访问的结构”。

常见模式：

1. 建索引 / 建映射
2. 回填父对象、上下文对象、包名、模块引用
3. 规范化类名、类型名、消息名等派生值
4. 递归初始化子节点
5. 在完整模型可见后做一致性校验

## 仓库里的典型用法

### 1. 回填上下文并递归初始化子模型

`ApiModel -> ApiServiceModel -> ApiMethodModel` 这条链用 `init()` 回填父级上下文，让子节点能推导包名、消息名等派生信息。

适用场景：

1. 子节点需要访问父模型信息
2. XML 本身不想重复存储这些派生字段

### 2. 构建运行期索引

`StateMachineModel` 在 `init()` 中构建 `stateValue -> StateModel`、`fullStateId -> StateModel` 映射，并完成重复值校验。

适用场景：

1. 运行期需要高频查表
2. 原始列表结构不足以支撑高效访问

### 3. 构建变量表或派生 schema

`RuleModel` 在 `init()` 中整理输入/输出变量映射，并准备决策树/矩阵后续执行所需的索引。

适用场景：

1. DSL 中是列表，运行期要按名字快速访问
2. 派生结构依赖整个模型装载完成后才能建立

### 4. 递归初始化嵌套节点

`BizModel` 会在 `init()` 中继续初始化 state machine、action 等子模型；`UiFormModel` 会继续初始化 cell。

适用场景：

1. 根模型是统一入口
2. 子节点各自也有派生逻辑

## 什么时候必须手动调用

下面这些场景不要假设框架一定会自动调用：

1. 你在 Java 里 `new` 出模型对象并手工填字段
2. 你把多个模型 merge 到一起后，得到一个新的组合模型
3. 你把模型从数据库、Excel、PDM、元数据发现结果等非标准 DSL 入口转成 Java 对象
4. 你显式关闭了 `DslModelParser.disableInit(true)`

仓库里的典型例子：

1. `OrmModelLoader` 在 merge 完模型后显式 `model.init()`，然后再 `freeze(true)`
2. 多个 parser / converter 在返回结果前会手动 `model.init()`

经验规则：

**只要模型经历过“手工组装/合并/二次转换”，就重新检查是否需要补一次 `init()`。**

## init() 里应该做什么

推荐做：

1. 建立 transient 索引、缓存、映射
2. 回填 parent/container/apiModel 这类上下文引用
3. 对用户输入的短名、相对名做规范化
4. 递归调用子模型 `init()`
5. 做依赖完整模型视图的结构校验

不推荐做：

1. 访问数据库、HTTP、文件系统等外部副作用
2. 启动线程、注册全局单例、发送消息
3. 把 `init()` 写成必须调用多次才稳定的流程
4. 在 getter 里再偷偷补同一批初始化逻辑，导致职责重复

## 设计建议

### 1. 优先让 init() 幂等或近似幂等

因为有些模型会被手动再次调用 `init()`，如果重复调用会把列表重复追加、索引污染，后果很难排查。

### 2. 派生结果优先放 transient 字段

`INeedInit` 常用于把 source model 整理成 runtime model。像父引用、索引 map、缓存对象这类值，一般不应回写成持久 source 数据。

### 3. 让根模型负责级联 init

如果存在清晰的根节点，优先由根节点统一调用子节点 `init()`，避免调用方必须知道完整初始化顺序。

### 4. 需要冻结时，先 init 再 freeze

如果模型后续会被冻结，通常应先完成 `init()` 生成派生结构，再进入不可变阶段。

## 常见误用

### 1. 在 init() 里做外部副作用

反例：

1. 访问数据库补字段
2. 发 HTTP 请求拉远端配置
3. 注册全局 listener / 定时器

问题：

1. 模型加载会变慢且不可预测
2. 同一个模型在测试、codegen、运行期三个场景行为不一致
3. 重复 `init()` 时容易出现副作用叠加

### 2. 忘记在手工 merge 后重新 init

这是最常见的真实 bug 来源之一。

典型症状：

1. 列表字段已经 merge 进去了，但按名字查不到
2. 子模型里依赖父包名/模块名的派生 getter 返回空值
3. 运行期报“未解析”“未注册”“找不到映射”，但源数据表面完整

### 3. 把 init() 当成 getter 的替代品

如果某个值只是一个轻量、纯函数式派生值，优先保持为普通 getter。

`INeedInit` 更适合：

1. 需要遍历整棵模型树
2. 需要建立索引
3. 需要一次性回填上下文

不适合：

1. `getSimpleClassName()` 这类随用随算即可的简单派生

### 4. 重复追加集合或重复注册索引

如果 `init()` 可能被再次调用，要避免：

1. 每次都 `add()` 到同一个缓存列表
2. 每次都往 map 里写入重复派生对象
3. 每次都重复连接父子关系而不先清理旧状态

## 排障信号

遇到下面这些现象时，优先检查 `INeedInit` 是否缺失、没被调用，或调用时机不对：

1. source XML 明明有数据，但运行期派生字段为空
2. merge 后模型打印出来正确，但运行期索引查找失败
3. 子模型按父上下文推导包名/类名时结果为空或错误
4. 某模型通过标准 loader 正常，通过手工构造路径异常
5. 调试时发现“第一次加载正常，手工改造后的组合模型异常”

建议排查顺序：

1. 先确认模型类是否实现了 `INeedInit`
2. 再确认当前入口是否走 `DslModelParser` 自动初始化
3. 如果是手工组装/merge，确认是否显式补了 `init()`
4. 如果模型后续被冻结，确认顺序是否为 `init()` 在前、`freeze()` 在后

## 这次 API XML 迁移里的使用方式

这次 `api.xml` source baseline 迁移中，`ApiModel` / `ApiServiceModel` / `ApiMethodModel` 使用 `INeedInit` 取代了原先依赖 `api.imp.xml` 的一部分派生计算。

这样做的原因是：

1. `api.xml` 走的是 `schemaPath` 语义，不再天然带 `impPath`
2. 派生逻辑本质上属于“模型加载完成后的整理”，适合回收到 Java `init()`
3. 逻辑回收到模型类后，更容易被测试覆盖和局部复用

## 什么时候优先想到本页

1. 你在模型类里看到 `implements INeedInit`
2. 你发现某个 DSL 模型需要在装载后补派生字段
3. 你把模型从多个来源 merge 后，发现运行期字段不完整
4. 你想替换一部分 `imp.xml`/loader 派生逻辑为 Java helper

## 相关文档

- `./xdef-and-xdsl.md`
- `./model-first-development.md`
- `./xlang-and-xpl-basics.md`
- `../04-reference/source-anchors.md`
