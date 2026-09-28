# 2026-09-28 nop-code 全量索引巨事务内 DB 读回级联缺陷修复

## Problem

- 用 nop-code 对 `nop-kernel/nop-xlang`（997 个 .java）执行 `indexDirectory` 全量建索引，索引阶段无进展：主线程 CPU 100% 数十分钟无输出，等效挂死（预估小时级）。
- 越过性能问题后接连暴露：索引完成但查询 `getFiles()` 直接抛 `scan-string-not-end`（JSON 残串解析失败）；类型层级查询（super/sub 方向）对跨包继承完全失明。

## Diagnostic Method

- 第一轮怀疑 autotest 基座：jstack 抓到 main 卡在 `AutoTestOrmHook.postSave → AutoTestVars.addVar` 的同名变量改名 `while` 循环（O(N²)）且逐值打 INFO 日志；移除 hook 后 15 万级实体 INSERT 秒级完成 → 排除持久化写入本身。
- 第二轮 jstack 定位到主线程 600s 全部耗在 `persistInSession → resolveQualifiedNamesToIds` 的**第一批**查询（OFFSET=0），排除 OFFSET 翻页问题；读回循环发生在与写入相同的未提交巨事务内，H2 MVStore 版本链在大事务下的读路径退化是根因（session 缓存为 HashMap、非瓶颈，已排除）。
- JSON 残串：错误位置恰为 8192 字节（`jsonImport` 列 precision），`fitColumn` 对 JSON 值做 `substring` 硬截断产生非法 JSON。
- 层级失明：分析器存的是源码书写形式（简单名），跨包继承占位名 `XLangExprParser` 无法经 `getByQualifiedName` 解析 → `getById` 也 miss；且查询端 `collectRelevantInheritances` 用 qn 列表匹配 `superTypeId`，而解析后的行存的是 ID，两个方向都断。

## Root Cause

- 全量索引在单个巨事务内"写入十万级实体行 + 分页读回再改写"，任何读回都在 H2 未提交版本链上付出数量级代价（`CodeIndexService.persistInSession` 流程设计缺陷）。
- `fitColumn` 对 JSON 列（`jsonImport`/`jsonContent`）按字符硬截断，破坏 JSON 合法性（写读契约断裂）。
- 继承/注解占位名只按全限定名解析，未利用文件的 imports/同包上下文（`JavaFileAnalyzer` 存 `getNameAsString()` 简单名）。
- 层级 BFS 查询的 `superTypeId` 匹配形态（qn vs ID）与写入形态不一致。

## Fix

全部收敛在 `nop-code-service`：

- `CodeIndexService.persistInSession`：全局符号表在分析期即完整，改为**写入即解析**——`saveFileResultInSession` 新增 `SymbolTable resolveTable` 参数，实体构建时经 `resolveSymbolId`（全限定名 → 显式 import → 同包 → 通配 import → java.lang）就地落 ID；删除全量路径的 `resolveQualifiedNamesToIds` 读回。增量路径（单文件小事务）保持原读回逻辑。
- `synthesizeAndPersistHeuristicEdges`：`buildInheritanceIndexFromResult` / `collectExistingEdgeKeys` 改为从内存分析结果构建；启发式边的 fileId 用 `generateFileId`（与落库规则一致的确定性生成）替代逐符号 PK 读。
- `fitColumn` 对 `[`/`{` 开头的值改走 `fitJsonToPrecision`：先截短超长字符串值（保键名/条目），仍超宽再丢尾部条目，保证落库始终是合法 JSON。
- `CodeGraphService.collectRelevantInheritances`：sub 方向遍历同时按 qn 与对应符号 ID 匹配 `superTypeId`，兼容遗留未解析行。

## Tests

- 新增 `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestIndexNopXlangModule.java`：对 nop-xlang 真实模块建索引 + 7 组断言（覆盖数=磁盘数、符号查找、类型层级 super/sub、TESTED_BY 引用行级校验、文件依赖正反向、调用层级、GraphQL 全栈 stats）。
- `TestDeletePathIntegrity` 前置断言由 `=1` 放宽为 `>=1`：写入即解析后索引自身的同包继承行会以真实 ID 并存（删除后清零的强断言不变）。
- `TestColumnTruncationProtection` 无需改动即通过：JSON 保键截值策略保留了 `{"k"` 语义。
- 全量回归：`./mvnw test -pl nop-code/nop-code-service` → 222 tests, 0 failures, 0 errors（13 skipped 为原有）。

## Affected Files

- `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java`
- `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestIndexNopXlangModule.java`（新增）
- `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestDeletePathIntegrity.java`

## Notes For Future Refactors

- 巨事务内禁止对索引表做任何读回/分页扫描；需要"先写后改写"的信息一律从 `ProjectAnalysisResult` 内存对象取，或移出事务。
- `fitColumn` 的 JSON 分支语义（合法 JSON 进、合法 JSON 出）不能回退为字符截断。
- 已知能力边界（本次验证确认、未修）：TYPE_REFERENCE/READ/WRITE 等类型引用 usage 未提取（`findReferencedBy` 对类型只反映继承/注解边）；CONSTRUCTOR 调用边仅覆盖同文件内类型；`ProjectAnalyzer` 的 filePattern 仅按扩展名匹配，`target/` 下构建产物副本会被重复索引。改进属功能项（roadmap M3/M5 范畴），非性能修复。
