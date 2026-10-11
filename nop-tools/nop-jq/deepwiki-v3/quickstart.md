# 快速上手

> 源基线（本文断言的核查范围）：
>
> - ../pom.xml（85 行，模块依赖与 JMH 编译配置）
> - ../../pom.xml（nop-kernel 聚合 POM，模块注册行）
> - ../src/main/java/io/nop/jq/jq/JqEngine.java（33 行）
> - ../src/main/java/io/nop/jq/jq/IJsonQuery.java（41 行）
> - ../src/main/java/io/nop/jq/jsonpath/NopJsonPath.java（121 行）
> - ../src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java（228 行）
> - ../src/test/java/io/nop/jq/jq/TestJqOfficial.java（250 行）
> - ../src/test/java/io/nop/jq/jq/JqOfficialCase.java（139 行）
> - ../src/test/resources/io/nop/jq/jq/jq-official.test（2117 行）
> - ../../../nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java（68 行）
> - ../../nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java（仅取 parse/parseMap/stringify 签名）

本页给最短路径：一条命令构建、一套测试基线、一段可直接抄的 Java API。引擎定位与术语见 [总览](./overview.md) 与 [术语表](./glossary.md)。

## 1. 构建

模块坐标 `io.github.entropy-cloud:nop-jq`：groupId/version 继承自父 POM（nop-kernel/pom.xml:8-10），artifactId 在模块 POM 声明（nop-jq/pom.xml:13）；在 nop-kernel 聚合中注册为 `<module>nop-jq</module>`（nop-kernel/pom.xml:474），并在其 `dependencyManagement` 中托管版本（nop-kernel/pom.xml:119-123）。

```bash
./mvnw test -pl nop-kernel/nop-jq          # 前提：nop-core/nop-commons/nop-xlang 已 install 到本地仓库
./mvnw test -pl nop-kernel/nop-jq -am      # 连带构建上游依赖模块，适合干净环境
```

编译依赖只有三个内部模块：nop-core、nop-commons、nop-xlang（nop-jq/pom.xml:24-37）；测试依赖 junit-jupiter、JMH 1.33、jayway json-path（nop-jq/pom.xml:39-63）。JMH 注解处理器在编译期生成基准代码（nop-jq/pom.xml:68-80）。JMH 基准**不随 `mvn test` 运行**——4 个基准类都只有 `main` 方法入口（JqEngineBenchmark.java:117、JsonPathBenchmark.java:149、JqToolBenchmark.java:19、SimpleBenchmark.java:16），需单独启动。

## 2. 测试

### 2.1 官方套件 TestJqOfficial（语义基线）

`src/test/resources/io/nop/jq/jq/jq-official.test` 是 vendor jq 1.7.1 release `tests/jq.test` 的原文，共 2117 行，首行即格式约定 `# Tests are groups of three lines: program, input, expected output`（jq-official.test:1-2）。原文件 447 块 = 421 ok + 12 模块依赖块 + 9 %%FAIL + 5 %%FAIL IGNORE；本模块实际运行动态用例 **430 = 421 ok + 9 %%FAIL**，其余被解析层跳过——模块依赖块（`import`/`include`/`modulemeta` 开头，需 jq 的 `-L` 模块机制）整块丢弃（JqOfficialCase.java:69-72,121-124），`%%FAIL IGNORE` 同官方 runner 一样跳过（JqOfficialCase.java:73-87）。

测试类用 `@TestFactory` 把每块用例包装成 `DynamicTest`，用例名 `ok[n]`/`fail[n]`（TestJqOfficial.java:36-54）。断言与官方 runner 对齐：数值宽松比较（`105 == 105.0`，BigDecimal 语义，TestJqOfficial.java:225-233）、多输出按有序列表比较（TestJqOfficial.java:92-107）、`# Runtime error:` 注释表示"先产出这些输出再报错"（JqOfficialCase.java:97-102）、%%FAIL 块的程序必须抛错否则判失败（TestJqOfficial.java:110-121）。

固化结论（plan 01 closure 实测，本页未重跑）：**TestJqOfficial 430/430 通过；模块 652 测试全绿；下游 nop-ai-toolkit 242 测试通过**（ai-dev/logs/2026/09-25.md:29；用例分解见同文件 :22,43）。

### 2.2 全部 JUnit 测试类

`mvn test` 实际执行 **13 个测试类**（按包分组，括号内为 @Test 注解数）：

- `io.nop.jq`（jq 抽象层）：TestJsonValue(10)、TestNopJsonAccessor(10)、TestJPathBridge(7，JPath 墓碑 SPI 桥接回归)
- `io.nop.jq.jq`（jq 求值线）：TestJqOfficial(1 个 @TestFactory，展开 430 动态用例)、TestJqExecution(52)、TestJqComprehensive(40)、TestJqAiAgentUseCases(32)
- `io.nop.jq.jsonpath`（JSON Path 线）：TestJsonPathEngine(22)、TestJsonPathComprehensive(40)、TestJsonPathApi(10)、TestNopJsonPath(6)、JSONPointTest_0(5)、JSONPointTest_1(1)

两点边界：`DeepScanTest` 有测试方法体但缺 `@Test` 注解，JUnit 平台不执行它（DeepScanTest.java:15-16）；deepwiki 计划基线（commit a3403c8935）时测试类为 12 个，TestJPathBridge 随 plan 361（JPath SPI 桥接，commit 00793c6958）新增，故以仓库现状计 13。JPath 桥接的行为见 [modules/jsonpath.md](./modules/jsonpath.md)。

## 3. Java API 最小示例

两条查询线共用一个入口习惯：先 `JsonTool` 解析 JSON，再交给查询引擎。jq 线入口 `JqEngine.compile(String): IJsonQuery` 带进程级缓存（容量 1000，JqEngine.java:15-24），编译走 lexer→parser→AST→`JqDirectQuery`（JqEngine.java:26-32）；执行接口 `apply(Object): List<Object>` 返回全部输出流，`applyOne(Object): Object` 只取第一个（IJsonQuery.java:14,19）。JSON Path 线入口 `NopJsonPath` 的方法名与签名对齐 fastjson 的 `JSONPath` 以便迁移（NopJsonPath.java:11-14）。

```java
import io.nop.core.lang.json.JsonTool;
import io.nop.jq.jq.IJsonQuery;
import io.nop.jq.jq.JqEngine;
import io.nop.jq.jsonpath.NopJsonPath;

public class NopJqDemo {
    public static void main(String[] args) {
        Object root = JsonTool.parseMap(
                "{\"items\":[{\"price\":5},{\"price\":20}]}"); // Map<String,Object>

        // jq 线：编译一次（进缓存），可对多个 root 复用
        IJsonQuery query = JqEngine.compile(".items[].price");
        System.out.println(query.apply(root));    // [5, 20] —— 全部输出，有序
        System.out.println(query.applyOne(root)); // 5       —— 仅第一个输出

        // JSON Path 线：fastjson 风格的读/写门面
        Object prices = NopJsonPath.eval(root, "$.items[*].price"); // [5, 20]
        boolean ok = NopJsonPath.set(root, "$.items[0].price", 8);  // true
        boolean miss = NopJsonPath.set(root, "$.missing.x", 1);     // false：父容器不存在，不创建中间容器
        System.out.println(JsonTool.stringify(root)); // {"items":[{"price":8},{"price":20}]}
    }
}
```

签名逐条对源码核对：`JsonTool.parseMap(String): Map<String,Object>`（JsonTool.java:62）、`JsonTool.parse(String): Object`（JsonTool.java:48）、`JsonTool.stringify(Object): String`（JsonTool.java:80）；`NopJsonPath.eval(Object,String): Object`（NopJsonPath.java:45-47）、`set(Object,String,Object): boolean`——父容器缺失返回 false 且不创建中间容器（NopJsonPath.java:76-78，NopCompiledJsonPath.java:72-82）。set/remove 的完整写语义见 [modules/jsonpath.md](./modules/jsonpath.md)。

### 编译失败与运行时错误的形态

- jq 线运行期错误抛 `io.nop.jq.jq.runtime.JqRuntimeException`，`errorMessage()` 即 jq 风格错误消息（JqToolExecutor.java:61-63）。
- JSON Path 线走平台错误码：`NopJqException` + `NOP_JQ-001..008`（编译失败为 `ERR_JQ_COMPILE_ERROR`/NOP_JQ-002，携带 `ARG_EXPR`，NopJsonPath.java:33-38；全表见 NopJqErrors.java:15-36）。分工与选择见 [topics/error-model.md](./topics/error-model.md)。

### 生产参照：唯一的活跃消费者

nop-ai-toolkit 的 `JqToolExecutor` 把 jq 暴露为 AI 工具 `jq`（JqToolExecutor.java:19），其执行序列就是生产环境的最小用法：`JsonTool.parse(data)` → `JqEngine.compile(expression)` → `query.apply(root)` → 逐输出 `JsonTool.stringify` 拼接（JqToolExecutor.java:43-58）。查询全链路的内部机制见 [flows/query-pipeline.md](./flows/query-pipeline.md)；读者路径规划见 [阅读指南](./reading-guide.md)。

## Sources

- [nop-kernel/nop-jq/pom.xml:7-13,24-63,68-80](/nop-kernel/nop-jq/pom.xml#L7-L13)
- [nop-kernel/pom.xml:8-10,119-123,474](/nop-kernel/pom.xml#L8-L10)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java:14-33](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqEngine.java#L14-L33)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/IJsonQuery.java:14,19,24](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/IJsonQuery.java#L14)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopJsonPath.java:11-14,20-22,33-38,45-47,76-78](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopJsonPath.java#L11-L14)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java:72-82](/nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopCompiledJsonPath.java#L72-L82)
- [nop-kernel/nop-jq/src/main/java/io/nop/jq/NopJqErrors.java:15-36](/nop-kernel/nop-jq/src/main/java/io/nop/jq/NopJqErrors.java#L15-L36)
- [nop-kernel/nop-jq/src/test/java/io/nop/jq/jq/TestJqOfficial.java:30,36-54,110-121,225-233](/nop-kernel/nop-jq/src/test/java/io/nop/jq/jq/TestJqOfficial.java#L30)
- [nop-kernel/nop-jq/src/test/java/io/nop/jq/jq/JqOfficialCase.java:69-87,97-102,121-124](/nop-kernel/nop-jq/src/test/java/io/nop/jq/jq/JqOfficialCase.java#L69-L87)
- [nop-kernel/nop-jq/src/test/resources/io/nop/jq/jq/jq-official.test:1-2](/nop-kernel/nop-jq/src/test/resources/io/nop/jq/jq/jq-official.test#L1-L2)
- [nop-kernel/nop-jq/src/test/java/io/nop/jq/jsonpath/DeepScanTest.java:15-16](/nop-kernel/nop-jq/src/test/java/io/nop/jq/jsonpath/DeepScanTest.java#L15-L16)
- [nop-kernel/nop-jq/src/test/java/io/nop/jq/benchmark/JqEngineBenchmark.java:117](/nop-kernel/nop-jq/src/test/java/io/nop/jq/benchmark/JqEngineBenchmark.java#L117)
- [nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java:48,62,80](/nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java#L48)
- [nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java:19,43-63](/nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java#L19)
- [ai-dev/logs/2026/09-25.md:22,29,43](/ai-dev/logs/2026/09-25.md#L22)
