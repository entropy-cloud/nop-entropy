# 维度 01：依赖图与模块边界

## 审计范围

nop-stream 全部 9 个子模块的 pom.xml、依赖树、代码级 import 验证。

## 依赖图

```
nop-stream (parent POM, packaging=pom)
│
├── nop-stream-api            [placeholder, 无源码, 无依赖]
│
├── nop-stream-core           [核心引擎]
│   ├── nop-commons           (compile)
│   └── nop-core              (compile)
│
├── nop-stream-runtime        [运行时]
│   ├── nop-stream-core       (compile)
│   ├── nop-dao               (provided)
│   ├── nop-message-core      (test)
│   ├── HikariCP              (test)
│   └── h2                    (test)
│
├── nop-stream-cep            [复杂事件处理]
│   └── nop-stream-core       (compile)
│
├── nop-stream-connector      [连接器]
│   ├── nop-stream-core       (compile)
│   ├── nop-batch-core        (optional)
│   ├── nop-message-core      (optional)
│   └── nop-message-debezium  (optional)
│
├── nop-stream-checkpoint     [placeholder]
├── nop-stream-flow           [placeholder]
├── nop-stream-flink          [placeholder]
│
└── nop-stream-fraud-example  [欺诈检测示例]
    └── nop-stream-cep        (compile, version=${project.version})
```

## 第 1 轮（初审）

### [维度01-01] nop-stream-fraud-example 使用 `${project.version}` 与同组模块不一致

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:18-24`
- **证据片段**:
```xml
    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-stream-cep</artifactId>
            <version>${project.version}</version>
        </dependency>
```
- **严重程度**: P3
- **现状**: nop-stream-fraud-example 是 nop-stream 组内唯一显式指定 `<version>${project.version}</version>` 的模块。同组的 nop-stream-runtime、nop-stream-cep、nop-stream-connector 依赖内部模块时不指定版本，由 nop-bom 统一管理。
- **风险**: 功能上无影响。但违反组内一致性约定，后续开发者可能误以为 fraud-example 有特殊版本管理需求。
- **建议**: 删除 `<version>${project.version}</version>` 行，与同组模块保持一致。
- **信心水平**: 确定
- **误报排除**: 不是"传递依赖声明"误报，而是版本声明风格不一致。
- **复核状态**: 未复核

### [维度01-02] nop-stream-connector 将 nop-message-core 声明为 optional 但主代码未使用

- **文件**: `nop-stream/nop-stream-connector/pom.xml:26-30`
- **证据片段**:
```xml
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-message-core</artifactId>
            <optional>true</optional>
        </dependency>
```
- **严重程度**: P3
- **现状**: nop-stream-connector 的主代码中无任何文件 import `io.nop.message.core.*`。nop-message-core 仅被测试代码 `TestMessageAdapters.java` 引用。当前声明为 optional（compile scope），应改为 test scope。
- **风险**: 实际影响极小——仅多了一个不必要的编译期 classpath 条目。
- **建议**: 将 scope 改为 test，与实际使用方式一致。
- **信心水平**: 很可能
- **误报排除**: 发现的是 scope 定位不准确，不是"声明传递依赖"误报。
- **复核状态**: 未复核

### [维度01-03] nop-bom 未管理 nop-stream-runtime、nop-stream-connector 等模块的版本

- **文件**: `nop-bom/pom.xml:1106-1134`
- **证据片段**:
```xml
            <dependency>
                <groupId>io.github.entropy-cloud</groupId>
                <artifactId>nop-stream-api</artifactId>
                <version>${nop-entropy.version}</version>
            </dependency>
            <dependency>
                <groupId>io.github.entropy-cloud</groupId>
                <artifactId>nop-stream-core</artifactId>
                <version>${nop-entropy.version}</version>
            </dependency>
            <!-- 遗漏: nop-stream-runtime, nop-stream-connector, nop-stream-flink, nop-stream-fraud-example -->
```
- **严重程度**: P3
- **现状**: nop-bom 管理了 5 个 nop-stream 模块（api、core、flow、cep、checkpoint），但遗漏了 runtime、connector、flink、fraud-example。全仓库扫描确认当前无任何外部模块依赖这些遗漏的 artifact。
- **风险**: 若未来有外部模块需要依赖这些模块，将必须自行指定版本号。属于潜在的未来风险。
- **建议**: 在 nop-bom 中补充 nop-stream-runtime 和 nop-stream-connector 的版本管理条目。
- **信心水平**: 确定
- **误报排除**: 全仓库扫描确认无外部消费者，因此严重程度定为 P3。
- **复核状态**: 未复核

## 合规清单

| 检查项 | 结果 |
|--------|------|
| nop-stream-core 依赖方向 | 合规。仅依赖 nop-commons 和 nop-core |
| nop-stream-runtime 对 nop-dao 的 provided 依赖 | 合规。仅 JdbcCheckpointStorage 和 JdbcClusterRegistry 使用 |
| nop-stream-cep 依赖方向 | 合规。仅依赖 nop-stream-core |
| nop-stream-connector optional 依赖（batch-core、debezium） | 合规 |
| 循环依赖 | 无 |
| SPI 文件引用 | 合规 |
| 占位符模块 | 合规（注释明确说明意图） |
