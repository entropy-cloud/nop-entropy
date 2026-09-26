# nop-code 代码索引 — 启动与查询手册（实测版）

nop-code 是 nop-entropy 仓库内的 AI 代码索引服务（`nop-code/` 模块）：符号查找、类 Outline、引用/依赖图、关键节点、变更分析。**只读分析，不改代码**。实现源码：`nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`；设计文档 `nop-code/design/ai-code-index-graphql-design.md` **与实现有出入（实现更丰富），以本文为准**——本文所有操作名与签名均经真实服务验证（2026-09-26）。

Phase 2（确定性结构提取）的主力工具。**索引写入失败时排查一次即转降级（§4），不要空转**；本仓库 2026-09-26 曾发生的列截断缺陷已由 plan 362 修复（见 §5），但降级路径仍是语言不支持时的主路径。

## 1. 启动与认证（实测路径）

```bash
# 首次：构建（仓库根）
./mvnw install -pl nop-code/nop-code-app -am -DskipTests

# 启动：必须关掉页面模型校验，否则 AMIS 页面校验报错会挡死启动
java -Dnop.web.validate-page-model=false \
  -jar nop-code/nop-code-app/target/quarkus-app/quarkus-run.jar

# 轮询等待就绪（登录端点返回 200 即就绪；约 30-60 秒）
until [ "$(curl -s -o /dev/null -w '%{http_code}' -m 3 -X POST \
  http://localhost:8081/r/LoginApi__login -H 'Content-Type: application/json' \
  -d '{"principalId":"nop","principalSecret":"123","loginType":1}')" = "200" ]; do sleep 5; done
```

认证事实（实测）：

- 端口 **8081**（`nop-code-app/src/main/resources/application.yaml`），H2 内存库（重启即空，索引要重建）。
- **所有业务查询都要认证**：无 token 直接 401 `nop.err.auth.not-authorized`。
- 默认账户 `nop` / **`123`**（application.yaml 注释写"密码 nop-test"是错的，实测不匹配）。
- Token 30 分钟过期；长任务批间见到 401 就重新 `--login`。
- 默认用户即可通过 Mutation 的 `@Auth(roles="admin")` 门禁（实测）。

## 2. 查询助手脚本

本 skill 附带零依赖助手 `scripts/nop-code-graphql.mjs`（Node 18+）。**token 必须落盘传递**（agent 的 shell 环境变量跨 Bash 调用不持久，`export` 无效）：

```bash
# 登录并把 token 写入文件
node scripts/nop-code-graphql.mjs --login nop 123 --save-token _tmp/nop-code-token.txt

# GraphQL：stdin 传 query，--token 显式传
echo 'query { NopCodeIndex__findList { id name rootPath } }' \
  | node scripts/nop-code-graphql.mjs --token "$(cat _tmp/nop-code-token.txt)"

# 文件 + 变量 / REST 形态
node scripts/nop-code-graphql.mjs --token $T --query-file q.graphql --vars '{"indexId":"x"}'
node scripts/nop-code-graphql.mjs --token $T --rest /r/NopCodeIndex__findList --data '{}'
```

环境变量 `NOP_CODE_ENDPOINT`（默认 `http://localhost:8081/graphql`）、`NOP_CODE_TOKEN` 仍可用，但勿依赖它跨调用存活。

**排错速查**：`没有定义操作:X` = 该操作在 schema 中不存在（换模板或降级，勿盲试变体）；`对象[X]的属性[Y]没有定义参数[Z]` = 签名与模板不符（删掉该参数重试）；401 = 重登；Map 类参数用**内联对象字面量** `{k: "v"}`，不是 JSON 字符串。

## 3. 实测可用的操作面

### 3.0 建索引（Mutation，三步）

设计文档的 `NopCodeIndex__create` **不存在**。实测路径：

```graphql
# ① 建主记录（内联对象；status 是字典项不要传；id 由服务端生成——读回返回值！）
mutation { NopCodeIndex__save(data: {name: "nop-jq", rootPath: "/abs/path/to/module", language: "java"}) { id name } }

# ② 全量索引（indexId 用 ①返回的 id）
mutation { NopCodeIndex__triggerFullIndex(indexId: "<id>", projectPath: "/abs/path/to/module") }

# ③ 查状态与统计
query { NopCodeIndex__getIncrementalStatus(indexId: "<id>") { mode fileCount completed errorMessage } }
query { NopCodeIndex__getStats(indexId: "<id>") { fileCount symbolCount } }
```

其余索引 Mutation：`indexDirectory(indexId, directoryPath, filePattern?)`（**无 recursive 参数**，设计文档有误）、`indexFile(indexId, filePath, sourceCode)`、`triggerIncrementalIndex(indexId, projectPath, manifestPath)`、`deleteIndex(indexId)`。

### 3.1 结构与重要性分析（wiki Phase 2 的主力，均实测存在）

| 操作 | 用途 |
|------|------|
| `NopCodeIndex__getStats(indexId)` | 文件/符号规模 |
| `NopCodeIndex__getCriticalNodes(indexId, topN?)` | 关键节点（≈ PageRank/中心度）→ reading-guide 排序、页面取舍 |
| `NopCodeIndex__getDeps/getReverseDeps(indexId, filePath, depth?, limit?)` | 依赖/反向依赖 → 模块地图、fan-in |
| `NopCodeIndex__getDepGraph(indexId, includeExternal?)` | 全图 → 架构页 |
| `NopCodeIndex__findCycles(indexId, minSize?)` | 环检测 → 架构风险 |
| `NopCodeIndex__detectCommunities(indexId)` / `getKnowledgeGaps(indexId)` | 社区划分 / 覆盖盲区 |
| `NopCodeIndex__getImpactAnalysis(indexId, symbolId, depth?)` / `findDependentFiles(indexId, filePath)` | 影响面 → 交叉引用 |
| `NopCodeIndex__exportGraph(indexId, format, communityView?)` | 导出图（graphviz/dot 等，按 format） |

### 3.2 符号/类型/文件查询

设计文档中的 `NopCodeSymbol__*`、`NopCodeType__*`、`NopCodeFile__*` 查询**大部分存在**（实测 `NopCodeIndex__get`、`NopCodeFile__findPage` 均在；签名可能与文档有出入）。判断标准：报"没有定义操作"才是不存在；报参数/数据错误说明操作在。模板（按需选用，遇到参数报错就删参重试）：

```graphql
query { NopCodeType__findByQualifiedName(qualifiedName: "io.nop.jq.jq.JqEngine") { id name } }
query { NopCodeType__batchGetOutlines(qualifiedNames: ["a.B", "c.D"]) { className superClassName methods { name signature line } } }
query { NopCodeSymbol__findByAnnotation(annotationName: "BizModel", limit: 50) { name qualifiedName filePath } }
query { NopCodeFile__get(filePath: "a/b/C.java", indexId: "<id>") { packageName imports outline { types { name kind line endLine } } } }
```

⚠️ 本引擎 **`__schema`/`__type` 内省不可用**（报"AST语法节点不允许存在多个父节点"）——不要依赖内省，用上面的"报错即探测"法。

### 3.3 增量与变更（update mode 可选加速）

```graphql
mutation { NopCodeIndex__triggerIncrementalIndex(indexId: "<id>", projectPath: "...", manifestPath: "...") }
query { NopCodeIndex__analyzeChanges(indexId: "<id>", baselineCommitish: "<old-commit>", targetCommitish: "<new-commit>") }
query { NopCodeIndex__getAffectedFlows(indexId: "<id>", changedFilePaths: ["a.java"]) }
```

## 4. 降级路径（服务不可用 / 索引写入失败 / 语言不支持）

降级是**常态路径**（本仓库实测：索引写入因 NopCodeCall 列截断失败后全程降级，wiki 照常产出并通过自检）。fan-in 实测口径：

```bash
# 类名被模块内其他文件引用的文件数（文件级精度，非符号级——如实记入覆盖缺口）
for f in $(find src/main/java -name "*.java"); do
  cls=$(basename $f .java)
  cnt=$(grep -rl "\b$cls\b" src/main/java --include="*.java" | grep -v "$f" | wc -l | tr -d ' ')
  echo "$cnt $cls"
done | sort -rn

# import 边（先看一眼真实 import 形态再定 pattern；同包/通配符 import 不会被计入）
grep -rh "^import com.example" src/main/java --include="*.java" | sort | uniq -c | sort -rn
```

| Phase 2 能力 | 降级实现 |
|--------------|---------|
| 模块地图 | 顶层目录 + 构建文件（pom.xml `<modules>`、package.json workspaces） |
| 关键类型结构 | `grep -n "class \|interface \|def \|function "` 提签名行 |
| 注解/模式发现 | `grep -rn "@Component\|@Service\|@BizModel" --include="*.java"` |
| import 图 | 见上方实测 recipe；注意通配符 import 需单独统计 |
| fan-in 排序 | 见上方实测 recipe |
| 超长文件骨架 | `grep -n "^\s*\(public\|private\|protected\).*(" file` 取签名行号 |
| 模块职责不明 | 派 Explore 子代理读 manifest 与入口文件，只要结论 |

## 5. 已知问题（2026-09-26 实测）

1. **~~索引写入列截断~~ 已修复（plan 362，2026-09-26）**：`CodeIndexService` 现在在持久化边界按 ORM 元数据的有效列宽截断分析器产出的自由文本字段（context/metadata/callType/signature 等），超长内容被截断（debug 日志记录）而非使索引事务回滚。实测 `triggerFullIndex(nop-jq)` 成功：fileCount=154、symbolCount=9555。注意截断语义：超长字段值**头部保留、尾部丢弃**，JSON 类字段截断后可能不再是合法 JSON。
2. **`__schema` 内省报错**（见 §3.2）。
3. **设计文档漂移**：`ai-code-index-graphql-design.md` 的 `NopCodeIndex__create`/`recursive` 参数等与实现不符，且未记载 §3.1 的富查询面。修改 nop-code API 时必须同步本文件。
