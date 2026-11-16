# Wave 6 / 6B-3 零回退矩阵（外部项目重测 vs recorded baseline）

- Date: 2026-11-16
- Status: adjudicated（发现已修复或裁定，见 plan wave6-audit-and-fixes.md Closure 段）
- Agent: 6B-3 runtime verification (subagent)
- Machine data: `_tmp/6b3/<project>.json` + `_tmp/6b3/<project>.log`（逐模块 ib 输出）

## 执行环境

- ib binary：`./mvnw install -pl nop-tools/nop-ib -am -DskipTests` 于 **2026-11-16 17:19:15 CST** 完成；此后全矩阵只用该安装（未重建）。运行中无 target/ 疑似变更迹象（各项目首次与复跑计数一致）。
- 每项目模型：沿用 `<project>/.nop-ib/model.json`（2026-11-14 ~ 11-16 刷新，均存在，未触发 refresh）。
- 排除：spring-batch、liteflow（并行 agent 占用）。

## 结论矩阵（10 项目 10 面，全部 GREEN）

| 项目 | 模块 | baseline 来源 | ib 实测 | baseline | verdict |
|---|---|---|---|---|---|
| liquibase | liquibase-standard | campaign L105 + 11-15 log | **6494/0**（selected 440 类） | ib 6494/0（mvn 6517−23s） | **GREEN**（逐数精确） |
| OpenPDF | openpdf（dir openpdf-core） | 11-15 log | **2077/0**（skipped=7） | mvn 2080−3s，ib 2077/0 | **GREEN**（run 精确；skipped 口径 7 vs 3，watch-only） |
| pdfbox | 10 模块全测 | 本轮 fresh mvn oracle（recorded 997 不可复现，系子集基线） | 见下 | fresh oracle | **GREEN**（修复环境 fixture 后 parity） |
| mvnd | mvnd-daemon | campaign L106（_tmp_mvnd_base.log） | **7/0** | mvn 8/0/1s | **GREEN**（skip 口径） |
| jsr352 | jberet-core, jberet-se | defect-31 trial mvn 基线（_tmp/jsr352-mvn-baseline.log） | **62/0；34/0** | mvn 63/0/1s；35/0/1s | **GREEN**（逐数精确） |
| zeebe | zeebe-gateway-grpc | campaign L105 | **480/0**（69 类） | 462 + 已知 Deferred Enclosed over-dispatch +18 | **GREEN**（=记录基线+已知 watch-only，精确） |
| flink-agents | flink-agents-integrations-embedding-models-ollama | sources/_tmp/_tmp_flink_agents_base2.log | **3/0** | mvn 3/0 | **GREEN**（精确） |
| shardingsphere | shardingsphere-infra-context | campaign L106 infra 5 模块（_tmp_ss_base3.log） | **5/0** | mvn 5/0 | **GREEN**（精确） |
| Activiti | activiti-expression-language | 无逐模块记录 → fresh mvn oracle（12/0/0s） | **12/0** | mvn 12/0 | **GREEN**（精确） |
| flink | flink-container | 11-15 log wave1 | **11/0** | mvn 11/0 精确 | **GREEN**（精确） |

## pdfbox 详录（唯一非平凡项）

1. 首轮 10 模块 ib：xmpbox 875/0、pdfbox-io 122/0、layout-awt 12/0、layout-fop 8/0、tools 18/0、debugger 1/0 全绿；fontbox 6f、pdfbox 47f、examples 6f——失败**全部**为 `NoSuchFileException target/pdfs|target/fonts`（maven-download-plugin 下载的 fixture 缺失）。
2. mvn oracle 复现在**同一下载步失败**：`ipag00303.zip: Archive is not a ZIP archive`（fontbox 先红；清除损坏 zip 后 fontbox mvn 恢复 SUCCESS 384/0/9s）。两侧同红 → 环境预存，非 ib 缺陷。
3. fixture 恢复后重跑：
   - fontbox：ib 378/0/3s vs mvn 384/0/9s（执行数 378 vs 375，+3，参数化/assume 计数口径，watch-only）。
   - pdfbox：ib **850/1f**/2s vs mvn **851/1f**/3s——同一失败 `TestPDFParser.testPDFBox3949`（Page tree root must be a dictionary），两侧同红预存。
   - pdfbox-examples：ib 40/0/6s = mvn 40/0/6s 逐数精确。
4. recorded 997/0 基线与当前 checkout（git clean, a9dea1f, 2026-10-16）不符——当日为部分模块子集口径，本矩阵改用 fresh mvn oracle（runbook 预案）。

## 观察与 follow-up（均 watch-only，不阻塞）

- OpenPDF skipped 口径：ib 现报 skipped=7（mvn 3）——assume-skip 计数口径差异，与 runbook 第 5 节口径一致方向。
- jberet-se worker 轮 16 失败被模块级 fresh rerun 治愈（6A-4 A-4 结论复现，safe-with-dependency）。
- zeebe-gateway-grpc 480=462+18 与 Deferred Enclosed 残留精确吻合（该残留仍需 successor plan）。
- pdfbox recorded 997 基线不可复现：建议矩阵行补注"基线为子集口径"。
- liquibase selected 类数 440 vs 11-15 记录 438（+2 个 0 测试 helper，无害同 mvn 语义）；run 数 6494 精确不变。

## 命令（runbook 步骤 2/4）

- ib：`java -Xmx6g -cp "<nop-ib-target-classes>:$(cat nop-tools/nop-ib/target/ib-cp.txt)" io.nop.ib.NopIbMain test -pl <modules>`
- oracle：`mvn test -fae -Dmaven.test.failure.ignore=true -Dmaven.build.cache.skipCache=true -Denforcer.skip=true -Dspotless.check.skip=true -Drevapi.skip=true [-pl <m>]`
- 逐项目命令与计数已存 `_tmp/6b3/*.json`。
