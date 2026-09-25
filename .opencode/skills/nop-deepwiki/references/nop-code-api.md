# nop-code 代码索引 — 启动与查询手册

nop-code 是 nop-entropy 仓库内的 AI 代码索引服务（`nop-code/` 模块）：符号查找、类 Outline、引用查找、调用关系、继承关系、文件结构。**只读分析，不改代码**。设计文档：`nop-code/design/ai-code-index-graphql-design.md`。

Phase 2（确定性结构提取）的主力工具。**启动失败或语言不支持时直接走 Grep/Read 降级路径（§4），不要阻塞在环境问题上。**

## 1. 启动服务

```bash
# 首次：构建（在 nop-entropy 仓库根）
./mvnw install -pl nop-code/nop-code-app -am -DskipTests

# 运行（二选一）
java -jar nop-code/nop-code-app/target/quarkus-app/quarkus-run.jar   # 已构建产物
./mvnw quarkus:dev -pl nop-code/nop-code-app                          # 开发模式
```

- 端口 **8081**（`nop-code-app/src/main/resources/application.yaml`），H2 内存库（重启即空）。
- GraphQL 入口：`http://localhost:8081/graphql`；REST 入口：`http://localhost:8081/r/<Query或Mutation名>`。
- 需要认证时先登录（应用空表时自动创建默认账户 `nop` / `nop-test`）：

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/r/LoginApi__login \
  -H 'Content-Type: application/json' \
  -d '{"principalId":"nop","principalSecret":"nop-test","loginType":1}' \
  | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).accessToken))')
```

## 2. 查询助手脚本

本 skill 附带零依赖助手 `scripts/nop-code-graphql.mjs`（Node 18+）：

```bash
# 一键登录并记住 token（写入并读取环境变量 NOP_CODE_TOKEN）
node scripts/nop-code-graphql.mjs --login nop nop-test

# GraphQL 查询：从 stdin 读 query
echo 'query { NopCodeIndex__findList { id name rootPath status } }' \
  | node scripts/nop-code-graphql.mjs

# 从文件读 query + 变量
node scripts/nop-code-graphql.mjs --query-file q.graphql --vars '{"indexId":"main"}'

# REST 方式（GET 语义的查询也可走 POST /r/）
node scripts/nop-code-graphql.mjs --rest /r/NopCodeIndex__findList --data '{}'
```

环境变量：`NOP_CODE_ENDPOINT`（默认 `http://localhost:8081/graphql`）、`NOP_CODE_TOKEN`。

## 3. 按 Phase 2 需求组织的查询模板

### 3.0 建索引（Mutation）

```graphql
mutation {
  NopCodeIndex__create(name: "deepwiki", rootPath: "/abs/path/to/repo", language: "java") { id }
}
# 返回 id 即 indexId；Java 用 filePattern "**/*.java"
mutation {
  NopCodeIndex__indexDirectory(
    directoryPath: "/abs/path/to/repo"
    indexId: "<indexId>" recursive: true filePattern: "**/*.java")
}
```

### 3.1 模块地图（包结构 → 模块划分候选）

```graphql
query { NopCodeIndex__get(id: "<indexId>") {
  name symbolCount fileCount status packages
} }
query { NopCodeFile__findPage(pathPattern: "**/io/nop/biz/**", indexId: "<indexId>", limit: 100) {
  totalCount items { filePath packageName lineCount }
} }
```

### 3.2 关键类型结构（批量 Outline）

```graphql
query { NopCodeType__batchGetOutlines(
  qualifiedNames: ["io.nop.biz.crud.CrudBizModel", "io.nop.api.core.annotations.biz.BizModel"]
  indexId: "<indexId>") {
  className packageName superClassName interfaceNames
  methods { name signature returnType documentation line }
  fields { name type documentation }
} }
```

### 3.3 注解驱动的架构支柱发现

```graphql
query { NopCodeSymbol__findByAnnotation(
  annotationName: "BizModel" indexId: "<indexId>" limit: 50) {
  name qualifiedName filePath location { line endLine }
} }
```

按目标技术栈替换注解名：Spring 系 `Component/Service/Controller`、Nop 系 `BizModel`、JAX-RS `Path` 等。多跑几组注解拼出架构支柱清单。

### 3.4 调用链与继承层级（请求流/扩展点确认）

```graphql
query { NopCodeSymbol__findByQualifiedName(
  qualifiedName: "io.nop.biz.crud.CrudBizModel.save" indexId: "<indexId>") {
  ... on NopCodeMethod {
    callers(maxDepth: 2, limit: 20) { caller { name qualifiedName
      declaringClass { name qualifiedName } } location { filePath line } }
    callees(maxDepth: 1, limit: 20) { method { name qualifiedName } }
  } } }
query { NopCodeTypeHierarchy__get(typeId: "<typeId>", direction: BOTH, maxDepth: 3) {
  root { symbol { name qualifiedName }
    subTypes { symbol { name qualifiedName } subTypes { symbol { name } } } } } }
```

### 3.5 引用密度（fan-in → 重要性排序）

```graphql
query { NopCodeSymbol__findByQualifiedName(
  qualifiedName: "<候选核心类全限定名>" indexId: "<indexId>") {
  usageCount
  usages(limit: 50) { kind location { filePath line } }
} }
```

对模块划分候选逐一取 `usageCount`，降序即 fan-in 排序 → reading-guide 顺序与页面取舍依据。

### 3.6 文件级探查（单文件结构）

```graphql
query { NopCodeFile__get(filePath: "a/b/C.java", indexId: "<indexId>") {
  packageName imports lineCount
  outline { types { name kind signature line endLine } }
} }
```

超长文件用此接口拿骨架（`outline.types[].line/endLine`），再对需要的区段 `sourceCodeRange(startLine:, endLine:)` 精读——**替代头部截断阅读**。

### 3.7 增量刷新（update mode）

```graphql
mutation { NopCodeIndex__incrementalUpdate(indexId: "<indexId>") }
```

## 4. 降级路径（服务不可用 / 语言不支持）

| Phase 2 能力 | 降级实现 |
|--------------|---------|
| 模块地图 | 顶层目录 + 构建文件（pom.xml 的 `<modules>`、package.json workspaces） |
| 关键类型结构 | `grep -n "class\|interface\|def \|function "` 提签名行 |
| 注解/模式发现 | `grep -rn "@Component\|@Service\|@BizModel" --include="*.java"` |
| import 图 | `grep -h "^import " 各文件` 聚合建边 |
| fan-in 排序 | 统计类名在全仓库出现次数（`grep -rl` 计数） |
| 超长文件骨架 | `grep -n "^\s*\(public\|private\|protected\).*(" file` 取方法签名行号 |

降级时在 PLAN.md"覆盖缺口"如实记录哪些能力走了近似路径。
