# C6 资源生命周期与泄漏 — nop-report + nop-format 初审

> 审计单元：CRA WI 5.4 nop-report-format。维度：C6 资源生命周期（流/临时文件/连接的关闭序、异常路径泄漏、OOXML 包资源释放）。
> R1 锚点：资源获取 7 命中 + 宽口径 8 命中（FontManager/ResourceDocumentParser/DefaultResourceHtmlWriter/FileMagic/IChartExporter 等）。
> 盲审纪律同 C1。

## 资源获取点配对表（核心结论）

| 获取点 | 释放路径 | 成功 | 异常 | 取消/超时 | 判定 |
|--------|---------|------|------|----------|------|
| FontManager.loadFromResource InputStream | finally safeClose | ✅ | ✅ | n/a | 安全 |
| FontManager.loadFromFile FileInputStream（TTC 分支） | try-with-resources | ✅ | ✅ | n/a | 安全 |
| OfficePackage.loadFromFile→ZipResourceStore 句柄 | close()；WordTemplate 构造器 loadInMemory 主动释放 | ✅ | ⚠️ 见 C6-004 | n/a | 基本安全 |
| AbstractXlsxParser.doParseResource pkg | finally IoHelper.safeCloseObject | ✅ | ✅ | n/a | 安全 |
| WordTemplateParser.parseFromResource pkg | catch→safeClose；成功后所有权转移 WordTemplate | ✅ | ✅ | n/a | 安全 |
| AbstractOfficeTemplate.generateToStream 临时目录 | finally ResourceHelper.deleteAll | ✅ | ✅ | n/a | 安全 |
| OfficePackage.saveToResource zip out/os | finally 分支 close(out) else close(os)（注释明示异常路径） | ✅ | ✅ | n/a | 安全 |
| ResourceDocumentParser mPDFDocument | parseFromResource finally close() | ✅ | ✅ | n/a | 安全 |
| ResourceDocumentParser/DefaultResourceHtmlWriter BufferedWriter | try-finally close | ✅ | ✅ | n/a | 安全 |
| PdfReportRenderer PDDocument | generateToStream finally safeClose | ✅ | ✅ | n/a | 安全 |
| PdfSheetRenderer PdfPageRenderer（内容流） | renderSheet finally IoHelper.safeClose | ✅ | ✅ | n/a | 安全 |
| RandomAccessFileBinaryDataReader raf | close()；**构造器异常路径不关闭** | ⚠️ | ❌ | n/a | **C6-001 P2** |
| StreamBinaryDataReader/ReaderTextDataReader/BlockCached* close | 幂等（closed 标志） | ✅ | ✅ | n/a | 安全 |
| ExcelReportHelper.downloadXlsx 临时文件 | globalTimer 定时 delete | ✅ | ✅（catch delete） | 重启即漏 | **C6-002 P3** |
| PdfDoc.selectPages 族共享 PDPage | 子文档/父文档关闭序未定义 | ⚠️ | ⚠️ | n/a | **C6-003 P3** |
| IChartExporter.exportToFile FileOutputStream | finally safeCloseObject | ✅ | ✅ | n/a | 安全 |
| NurminenDetectionAlgorithm.removeText PDDocument | finally close | ✅ | ✅ | n/a | 安全 |
| WordTemplate/pkg（loadInMemory 后） | 无 close 调用面；已无外部句柄 | ✅ | n/a | n/a | 安全（登记备注） |

对照组结论：本单元 C6 纪律整体良好（多处以注释明示"异常路径也必须执行"，为既往修复痕迹），异常路径泄漏集中点为 RAF 构造器。

---

### [C6-001] RandomAccessFileBinaryDataReader 构造器：打开后 length() 抛异常导致文件句柄泄漏

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/RandomAccessFileBinaryDataReader.java:53-60`
- **证据片段**:
  ```java
  public RandomAccessFileBinaryDataReader(File file) {
      try {
          raf = new RandomAccessFile(file, "r");   // 句柄已打开
          size = raf.length();                     // 此处抛 IOException →
      } catch (Exception e) {
          throw NopException.adapt(e);             // raf 永不关闭
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 记录文件解析入口按文件构建 reader；文件在打开与 length() 之间被删除/截断（网络盘、容器挂载卷常见）、或 fd 耗尽时段并发打开多文件时，length() 可抛 IOException。
- **触发时序**: T1 构造 reader：`new RandomAccessFile(file)` 成功（fd 已占）→ `raf.length()` 因文件被并发删除/IO 错误抛 IOException → catch 包装 NopException 抛出 → raf 无 finally 兜底 → fd 泄漏。重复触发（重试解析同一批文件）逐次泄漏 fd，长期运行进程触达 `too many open files`。
- **现状**: 类内 close() 正常（幂等性略欠——重复 close 抛 IOException 而非静默，但调用面单一），唯构造异常路径不对称，属 F8"资源清理不对称"的标准形态。
- **风险**: 错误/重试路径 fd 泄漏（P1 判级要素"错误路径资源泄漏"成立；因触发前提是 length() 异常这一低概率事件，综合定 P2）。
- **建议**:
  ```java
  RandomAccessFile tmp = new RandomAccessFile(file, "r");
  try { this.size = tmp.length(); } catch (IOException e) { try { tmp.close(); } catch (IOException ignore) {} throw NopException.adapt(e); }
  this.raf = tmp;   // 或改用非 final 字段
  ```
  同时 close() 增加幂等标志（对齐 StreamBinaryDataReader.closed 惯例）。
- **失效面枚举**: reader 族构造器逐一核对：StreamBinaryDataReader（构造仅保存引用，不打开资源，安全）；ByteBufferBinaryDataReader（包装缓冲，无外部句柄）；ByteBufBinaryDataReader（引用计数由 netty 侧管理，释放点在 PacketCodecHandler——平台核心，不在本单元重审）；BlockCached*（包装底层，安全）；ReaderTextDataReader（同 Stream 模式）；Sub*（包装，无所有权）；**RandomAccessFileBinaryDataReader（违规）**。
- **信心水平**: 确定
- **误报排除**: 非低频路径豁免——解析入口可由外部输入触发；非"close 重复调用安全"豁免（那是对 close 而言，本条是构造路径）。
- **复核状态**: 已复核（保留 P2；证据行号实际 :54-61 微偏不影响实质，构造泄漏与生产可达性 ModelBasedResourceRecordIO.java:89 核实；修正一处备注：JDK RandomAccessFile.close() 自带 closed 守卫、幂等，"重复 close 抛 IOException"说法不成立且不影响判定，见「维度复核结论」表）

---

### [C6-002] downloadXlsx 临时文件依赖一次性定时器删除：重启泄漏 + 删除失败静默

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/util/ExcelReportHelper.java:116-134`
- **证据片段**:
  ```java
  public static WebContentBean downloadXlsx(String fileName, String impModelPath, Object bean, int waitMinutes) {
      IResource resource = ResourceHelper.getTempResource("download");
      try {
          ExcelReportHelper.saveXlsxObject(impModelPath, resource, bean);
          WebContentBean content = new WebContentBean("application/octet-stream", resource.toFile(), fileName);
          GlobalExecutors.globalTimer().schedule(() -> { resource.delete(); return null; },
              waitMinutes, TimeUnit.MINUTES);      // 一次性定时删除，返回值被忽略
          return content;
      } catch (Exception e) { resource.delete(); throw NopException.adapt(e); }
  }
  ```
- **严重程度**: P3
- **并发场景**: 每次下载请求创建一个临时文件 + 一个全局定时器任务；并发下载 N 个文件 = N 个 pending 定时任务 + N 个临时文件。
- **触发时序**: （a）T0 下载成功调度 delete；T0+ε 进程重启/部署 → 定时任务丢失 → 临时文件永久残留（temp 目录只增不减，F4 形态）；（b）`resource.delete()` 在 Windows/被占用（客户端仍在下载大文件且 waitMinutes 过短）时返回 false → 静默失败，无日志无重试；（c）定时任务本身无清理钩子注册（无 shutdown 钩子兜底 drain）。
- **现状**: 异常路径处理正确（catch 内 delete）；缺口在成功路径的"定时器单点"。
- **风险**: 长时运行服务 temp 目录单调增长（磁盘占用），重启周期短的服务尤其明显；属慢性病而非主路径缺陷。
- **建议**: 复用平台已有模式——AbstractOfficeTemplate 的"请求结束即删"不适用（需存活到下载完成），建议：启动时清理 `download` 前缀残留（对照 ResourceHelper.getTempResource 的目录约定加清扫钩子）+ 定时删除失败时 DEBUG 日志登记。
- **失效面枚举**: 临时文件产生点全清单：AbstractOfficeTemplate.generateToStream（finally deleteAll，安全）；WordTemplateRenderer？无；downloadXlsx（本条）；SimpleHtmlSplitter（显式传入 targetDir，调用方责任）；DocxHelper.extractImagesToDir（显式 dir，调用方责任）；FontManager（无临时文件）。定时器使用点全清单：GlobalExecutors.globalTimer 在本单元唯一调用点即本处（grep 核对）。
- **信心水平**: 确定（机制确定；泄漏量为运维可观察的慢性问题）
- **误报排除**: 非正常成功路径即泄漏（P0 要素不满足——文件最终会被删除或重启前一直可用），按长时运行慢性增长计 P3。
- **复核状态**: 已复核（保留 P3；证据 :116-134 逐行核实，全库 grep 确认无任何启动期临时目录清扫钩子（cleanTemp/TempCleaner 零命中），"重启即漏"成立，见「维度复核结论」表）

---

### [C6-003] PdfDoc.selectPages/selectPageRange/selectPage 新旧 PDDocument 共享 PDPage：关闭序未定义

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/core/PdfDoc.java:104-142,144-157`
- **证据片段**:
  ```java
  public PdfDoc selectPages(List<Integer> pages) {
      PDDocument ret = new PDDocument();
      for (int pageNum : pages) {
          PDPage page = this.doc.getPage(pageNum - 1);
          ret.addPage(page);          // 同一 PDPage 实例挂入第二棵页树（COS 层共享）
      }
      return new PdfDoc(ret);         // 与 this.doc 生命周期耦合但无任何声明
  }
  ```
- **严重程度**: P3
- **并发场景**: 单线程使用即可触发（生命周期别名问题，非数据竞争）；splitIntoPages 内每页 selectPage 后先 save 再 close 子文档——恰为安全顺序（save 在 close 前）。
- **触发时序**: T1 `PdfDoc sub = doc.selectPageRange(1,10)` → 使用 sub 后 `sub.close()`（PDPage COS 对象与父共享，PDFBox close 只关自身 COSDocument，共享对象内容仍存活）→ 若此后继续 `doc.save(...)`：父文档保存依赖的页对象流缓存可能已被子文档 close 部分失效（PDFBox 对共享 COSStream 的 state 跨文档不复位）→ 保存损坏或 IOException。反向序（父先关再用子）同理。当前调用面 splitIntoPages 顺序正确，无实际触发点——**理论风险，未具体化到现网路径，定 P3**。
- **现状**: 代码注释自认"PDFBox 3.x 中 getPage 可能返回不可变页面对象，需根据实际情况处理"——已意识到别名风险但未建立关闭序契约；javadoc 无"子文档不得先于父文档 save"的声明。
- **风险**: 上层（文档拆分/裁剪服务）按任意顺序使用父子 PdfDoc 时产生难复现的保存损坏。
- **建议**: selectPages 改用 PDFBox `Splitter`/`PDPageTree` 导入语义（importPage 复制内容流，NurminenDetectionAlgorithm.removeText:127-138 已有正确的 importPage 先例可对照），或 javadoc 强制声明"子文档必须先 save 后 close，父文档存活期内使用"。
- **失效面枚举**: 同族方法全清单：selectPages/selectPageRange/selectPage（共享 PDPage，违规倾向）；splitIntoPages（内部使用 selectPage，顺序安全，但把风险封装在内部而无声明）；removeText（Nurminen，importPage+新内容流，正确对照组）。
- **信心水平**: 很可能（别名关系确定；损坏后果依赖 PDFBox 内部状态，未逐一验证）
- **误报排除**: 非并发数据竞争误报——报的是资源（文档状态）所有权不对称；非已声明边界（无任何声明）。
- **复核状态**: 已复核（保留 P3；共享 PDPage 别名与注释自认 :111-112/:127-128、splitIntoPages save 先于 close :152/:154 均核实；全 repo grep 确认 selectPages/selectPageRange/selectPage 生产代码零外部调用方，理论风险定级恰当，见「维度复核结论」表）

---

### [C6-004] OfficePackage 包句柄依赖显式 close：解析器侧纪律完备，模板侧依赖 loadInMemory 单点

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/OfficePackage.java:70-97,153-157`；`WordTemplate.java:53-56`；`AbstractXlsxParser.java:38-54`
- **证据片段**:
  ```java
  public void loadFromFile(File file) {
      close();                                   // 重入前先关旧 store —— 好的纪律
      ...
      store = ZipResourceStore.build(file, "/", "/");   // 打开 zip 文件句柄
      ...
  }
  // WordTemplate 构造器：
  // 将模板中所有内容都读入到内存中，从而不再持有外部zip文件的引用，可以被安全的缓存并重复使用。
  pkg.loadInMemory();
  ```
- **严重程度**: P3（登记为守备面确认 + 隐患点）
- **并发场景**: OOXML 包（docx/xlsx/pptx）解析/模板缓存路径；模板被缓存复用时句柄释放完全依赖构造器内 loadInMemory 这一行。
- **触发时序**: 若未来任何模板构建路径漏调 loadInMemory（或新增的 OfficePackage 使用方不 close），ZipResourceStore 持有的 zip 文件句柄即随缓存模板长期驻留 → Windows 上锁文件 + fd 驻留。当前全部调用面（WordTemplateParser/AbstractXlsxParser/OfficeDocModelParser/DocxHelper/DocxToMarkdownConverter/PptxToMarkdownConverter/WordXmlHelper）均 try-finally 或所有权转移，**无现网触发点**。
- **现状**: 关闭纪律良好（多为既往修复痕迹，如 saveToResource 的"out.close 负责收尾 Deflater 等本地资源，异常路径也必须执行"注释）；登记为脆弱单点。
- **风险**: 回归风险登记——新增包使用方漏 close 时无 fail-fast 检测（无 finalize/cleaner 兜底，亦无泄漏检测钩子，C10 关联）。
- **建议**: OfficePackage 加 `isResourceStoreOpen()` 断言钩子供测试使用；或在 AbstractOfficeTemplate/模板缓存层加"缓存登记时 store 必须为 null"的 Guard。
- **失效面枚举**: 资源 store 生命周期 owner 清单：OfficePackage.resourceStore（本条）；WordOfficePackage/ExcelOfficePackage（继承，同修）；ZipResourceStore.close（平台核心 nop-core，不重审）；InMemoryResourceStore（无外部句柄，安全）。
- **信心水平**: 确定（现状安全；风险为回归型）
- **误报排除**: 不按缺陷报（现网无泄漏），按 C10/C7 关联的守备缺口登记 P3。
- **复核状态**: 已复核（保留 P3；OfficePackage.java:70-83/:123-134/:154-157 与 WordTemplate 构造器 loadInMemory 逐行核实，抽查全部列名调用面 close 纪律（DocxHelper:15-16、Docx/PptxToMarkdownConverter、WordTemplateParser:85）属实，见「维度复核结论」表）

---

## 维度复核结论

> 复核人：CRA WI 5.4 C6 维度独立复核子代理。方法：逐条重读 6 条发现（C6-001~004 + C6-R2-001/R2-002）引用的源码文件与行号核实证据与资源所有权/close 配对语义，并对 R2 两条生命周期裁定做消费方代码执行序验证（全 repo grep 调用面 + XLang 编译链核读）。复核日期：2026-10-22。本节为追加内容，不修改既有初审/round2 结论。

| 编号 | 判定 | 理由摘要（file:line 复核证据） | 现状标注 |
|------|------|------------------------------|---------|
| C6-001 | 保留 P2 | 证据属实：RandomAccessFileBinaryDataReader.java:54-61，`raf = new RandomAccessFile(file,"r")`（:56）成功后 `raf.length()`（:57）抛异常则 catch 直接 rethrow（:58-59），final 字段无 finally 兜底，fd 泄漏成立；生产可达性核实 ModelBasedResourceRecordIO.createBinaryInput:89（resource.toFile()!=null 即走 RAF 分支）。唯一修正：初审现状备注"重复 close 抛 IOException"不成立——JDK RandomAccessFile.close() 自带 closed 守卫、幂等（本地 JDK 源码核实），不影响主判定 | 未修复（构造器现状未变） |
| C6-002 | 保留 P3 | 证据属实：ExcelReportHelper.java:116-134，一次性 globalTimer schedule 删除（:124-127）、返回值被忽略、catch 内 delete（:131）；补充核实：全库 grep cleanTemp/TempCleaner/启动清扫钩子零命中，"重启即漏 + 删除失败静默"成立 | 未修复（现状未变） |
| C6-003 | 保留 P3 | 证据属实：PdfDoc.java:104-142，三个 select* 方法将父文档 PDPage 原实例 addPage 进新 PDDocument（:113-114/:129-130/:139），:111-112/:127-128 注释自认 PDFBox 3.x 别名风险；splitIntoPages save 在 close 前（:152/:154）顺序正确。补充核实：全 repo grep 确认 selectPages/selectPageRange/selectPage 生产代码零外部调用方（仅 splitIntoPages 内部用 selectPage）——无实际触发点，理论风险定级 P3 恰当 | 未修复（无关闭序契约声明，现状未变） |
| C6-004 | 保留 P3 | 证据属实：OfficePackage.java:70-83（loadFromFile 先 close 后开 ZipResourceStore :71/:79）、loadInMemory :123-134（safeClose+置 null）、close :154-157；WordTemplate 构造器 loadInMemory 单点属实。抽查全部列名调用面 close 纪律逐一核实：DocxHelper:15-16、DocxToMarkdownConverter:45-46、PptxToMarkdownConverter:59-60+491-492（两处）、WordTemplateParser:85（catch→safeClose→rethrow）、AbstractXlsxParser finally safeCloseObject——现网无泄漏，守备面确认成立 | 未修复（回归守备缺口登记，现状安全） |
| C6-R2-001 | 保留 P3 | 结构事实属实：ModelBasedPacketCodec.decodeFromBuf:166-179 顶层 `new ByteBufBinaryDataReader(buf)`（:174）从不 close（全库 grep 顶层 reader close 调用为 0，唯一 decodeFromBuf 生产调用方 PacketCodecHandler:94）；ByteBufBinaryDataReader.close()（实际 :135-140）执行 ReferenceCountUtil.release；PacketCodecHandler.java:91-92 `readSlice`（非 retainedSlice）+ 注释"不使用 retainedSlice，也就不需要 release"证明 pipeline 持有帧 buf 释放责任——"close 即双重释放、不 close 则契约悬空"的不对称成立；触发为条件性：**当前调用序不触发**（无任何路径 close 顶层 reader），属所有权隐式契约回归地雷，P3 口径恰当 | 未修复（所有权声明缺失，现状安全） |
| C6-R2-002 | 驳回 | 结构事实属实（OfficeDocModelParser.java:67-100，:77 逃逸进编译作用域、:98-100 finally close），但 use-after-close 在任何实际调用序中均不触发：(a) XLang 编译把 `ofcPkg` 解析为 SCOPE_VAR_REF → ScopeIdentifierExecutable，仅持变量名、invoke 时从运行期 scope 按名解析（LexicalScopeAnalysis.java:782-798 → BuildExecutableProcessor.java:470-472 → ScopeIdentifierExecutable.java:17,35），编译期 setLocalValue 是编译期供养（类型/注册），不捕获 pkg 实例；(b) OfficeDocModel 唯一生产消费链 OfficeDocModelWriter→OfficeDocXmlBuilder 从不求值 valueExpr/templateExpr/testExpr（OfficeDocXmlBuilder.java:174-176 仅物化字面 run.getT()）；(c) 实际执行表达式的路径（WordTemplate.generateToDir:73、ExcelTemplate.java:69）均在求值前向渲染 scope 注入新鲜 pkg/副本。初审信心水平"有趣的猜测"经执行序验证不成立；残留观察（OfficeDocModelWriter 渲染作用域未提供 ofcPkg，若未来求值表达式将得 null 而非 closed pkg）登记为 watch-only 备注，不构成 C6 生命周期缺陷 | 驳回（watch-only 备注：writer 求值面未提供 ofcPkg 的隐式契约缺口） |

### 批次覆盖核验

对照源码根清单逐组核实 C6 两份文档的核查痕迹（grep 计数 + 逐条核对行所引文件所属模块）：

- **批A excel**：有痕迹（AbstractXlsxParser 配对表行；SimpleHtmlSplitter——nop-excel/renderer，C6-002 失效面枚举；ExcelReportHelper）。
- **批B ooxml**：有痕迹（OfficePackage/WordTemplate/WordTemplateParser/DocxHelper/WordXmlHelper/OfficeDocModelParser——nop-ooxml-common/docx；AbstractXlsxParser——nop-ooxml-xlsx；Docx/PptxToMarkdownConverter——nop-ooxml-markdown）。注意 nop-ooxml-pptx 子模块本体（PptOfficePackage/SlidePart/PptxTableParser 等）零提及。
- **批C pdf+record+record-netty**：pdf、record 均有痕迹（PdfReportRenderer/PdfSheetRenderer/PdfDoc/ResourceDocumentParser/NurminenDetectionAlgorithm/ExtractPageDrawer/ExtractStripper；RandomAccessFileBinaryDataReader + reader 族构造器枚举 + ModelBasedPacketCodec/ByteBufBinaryDataReader）；**nop-record-netty 模块本体（io.nop.netty.ext.*，9 文件：ModelBasedTcpServer/Client、TransferServerProxy、ProxyHandler、PacketStateMachineHandler 等）零提及**——R2-001 的 ByteBufBinaryDataReader 物理位于 nop-record 的 io.nop.record.netty 包，非该源码根，与 C1/C5 复核登记口径一致。
- **批D report+mermaid+svg+chart-export+converter+tablesaw+markdown-ext+office-\***：report 有痕迹（C6-002 + PdfReportRenderer 两文档）；**chart-export 有真实痕迹（IChartExporter 配对表行 + JFreeChartStyleAdapter:635 Graphics2D dispose 核对，两文件均属 nop-chart-export 模块本体）——与 C5 不同，chart-export 不构成 C6 缺口**。

覆盖缺口登记（本轮不补审，仅登记，供 C10 汇总裁定）：

1. 与 C1/C5 复核登记缺口四维重合的源码根在 C6 同样零痕迹：**nop-tablesaw、nop-office-model、nop-office-doc-model**（OfficeDocModel 等模型类被 R2-002 引用讨论，但模块本体源文件未审，口径同 C1/C5"模块本体"）、**nop-record-netty**。
2. C5 维度额外缺口 **nop-mermaid、nop-svg** 在 C6 同样零痕迹。
3. C6 维度额外缺口（C1/C5 有痕迹而 C6 两文档零提及）：**nop-converter**（XmlDocumentObjectBuilder/ResourceDocumentObject/ExcelDocHelper 等）、**nop-markdown-ext**（MarkdownNormalizer/Math* 等）、**nop-ooxml-pptx**（PptOfficePackage/SlidePart 等）。

---

## 最终保留项

供 C10 与合并裁定消费。最终保留 5 项（P2×1、P3×4），驳回 1 项（C6-R2-002）：

| 编号 | 严重度 | 一句话结论 |
|------|--------|-----------|
| C6-001 | P2 | RandomAccessFileBinaryDataReader 构造器在 raf.length() 异常路径泄漏已打开的文件句柄（RandomAccessFileBinaryDataReader.java:54-61，生产可达 ModelBasedResourceRecordIO.java:89），错误/重试路径逐次泄漏 fd，长期运行触达 too many open files |
| C6-002 | P3 | downloadXlsx 临时文件依赖一次性 globalTimer 定时删除且返回值被忽略（ExcelReportHelper.java:116-134），进程重启即永久残留、删除失败静默，长时运行 temp 目录单调增长 |
| C6-003 | P3 | PdfDoc.selectPages 族新旧 PDDocument 共享 PDPage 且无任何关闭序契约（PdfDoc.java:104-142），当前唯一调用序安全（splitIntoPages save 先于 close），理论风险登记 |
| C6-004 | P3 | OfficePackage zip 句柄释放依赖 WordTemplate 构造器 loadInMemory 单点且无泄漏检测钩子（OfficePackage.java:70-97,123-134,153-157），现网调用面纪律完备，登记为回归守备缺口 |
| C6-R2-001 | P3 | ModelBasedPacketCodec 顶层 ByteBufBinaryDataReader 所有权不对称（ModelBasedPacketCodec.java:166-179；ByteBufBinaryDataReader.java:135-140；PacketCodecHandler.java:91-92）：close 即双重释放、不 close 则契约悬空，当前安全纯靠隐式约定，回归地雷 |

watch-only（不计入保留项）：C6-R2-002 驳回后残留——OfficeDocModelWriter 渲染作用域未提供 ofcPkg，若未来该求值面落地需补变量注入契约。
