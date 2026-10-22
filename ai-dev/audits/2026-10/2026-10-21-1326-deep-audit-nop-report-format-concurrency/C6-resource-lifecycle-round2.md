# C6 深挖第 2 轮 — nop-report + nop-format

> 方法：不与初审 C6-001~004 重复。深挖 netty 读取器所有权、docx 解析器作用域逃逸、图形资源 dispose 面、包解析器 close 面。

### [C6-R2-001] ModelBasedPacketCodec 顶层 ByteBufBinaryDataReader 所有权不对称：close 即双重释放，不 close 则契约悬空

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/ModelBasedPacketCodec.java:166-177`；`nop-format/nop-record/src/main/java/io/nop/record/netty/ByteBufBinaryDataReader.java:128-135`
- **证据片段**:
  ```java
  // ModelBasedPacketCodec.decodeFromBuf
  public Object decodeFromBuf(ByteBuf buf, Class<?> targetType) {
      buf.skipBytes(lengthFieldEndOffset);
      Object record = newObject(targetType);
      ...
      try {
          deserializer.readObject(new ByteBufBinaryDataReader(buf), typeMeta, record, context);
          // 顶层 reader 从不 close——依赖 netty pipeline 对帧 buf 的统一 release
      } catch (Exception e) { throw NopException.adapt(e); }
      return record;
  }
  // ByteBufBinaryDataReader.close()
  public void close() { if (bb != null) { ReferenceCountUtil.release(bb); bb = null; } }
  ```
- **严重程度**: P3
- **并发场景**: netty 解码线程；帧缓冲 `buf` 的所有权在 pipeline（PacketCodecHandler 统一 release，平台核心职责）与解码代码（reader 包装）之间未显式划分。
- **触发时序**: 现状安全仅因"顶层 reader 从不 close"。任何未来改动（如按 IDataReaderBase extends Closeable 契约加 try-with-resources、或异常清理路径调用 close）→ `ReferenceCountUtil.release(buf)` 与 pipeline 的 release 叠加 → IllegalReferenceCountException（下游双释放）。反向风险：若 pipeline 侧改为"解码器接管所有权"，现状不 close 则每次解码泄漏一个引用计数。
- **现状**: 所有权约定完全隐式（代码注释只在 subInput 的 retain 侧声明"子视图独立所有权"，顶层侧无任何声明）。
- **风险**: 所有权隐式约定的回归地雷；属 F8"资源清理不对称"的静态形态（当前无泄漏，未来易引入）。
- **建议**: decodeFromBuf 内 `try (IBinaryDataReader reader = new ByteBufBinaryDataReader(buf.slice()))`——slice+retain 后 reader 完全拥有引用，close 语义自洽，与 pipeline release 解耦；或 javadoc 显式声明"顶层 reader 不得 close"。
- **失效面枚举**: ByteBufBinaryDataReader 的构造/所有权点全清单：ModelBasedPacketCodec.decodeFromBuf（顶层，本条）；subInput（retain+close 对称，安全）；detach/duplicate（duplicate 共享引用计数——调用方 close 时释放共享计数，正确性依赖 duplicate 契约，与 C1-R2-001 契约缺失同根）；byte[] 构造器（Unpooled.wrappedBuffer 自有，安全）。
- **信心水平**: 确定（结构事实）；触发为条件性
- **误报排除**: 当前无泄漏不升级；按所有权声明缺失/回归地雷口径 P3 登记。
- **复核状态**: 已复核（保留 P3；证据行号实际 close() :135-140 微偏不影响实质；PacketCodecHandler.java:91-92 readSlice+注释、decodeFromBuf:174 顶层零 close 调用均核实，触发为条件性——当前调用序不触发，见 C6-resource-lifecycle.md「维度复核结论」表）

---

### [C6-R2-002] OfficeDocModelParser：pkg 以 VAR_OFC_PKG 逃逸进编译作用域，finally close 先于模型表达式消费

- **文件**: `nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/parse/OfficeDocModelParser.java:64-99,264-324`
- **证据片段**:
  ```java
  public OfficeDocModel parseFromResource(IResource resource) {
      WordOfficePackage pkg = new WordOfficePackage();
      try {
          pkg.loadFromResource(resource);
          XNode docNode = pkg.getWordXml();
          ...
          cp.getScope().setLocalValue(null, DocxConstants.VAR_OFC_PKG, pkg);  // 逃逸进作用域
          ...
          model.setValueExpr(compileValueAction(loc, ..., cp));   // 编译进模型的表达式
          ...
      } finally {
          pkg.close();                                            // 资源 store 置空
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 单线程解析即可触发（生命周期别名）；docx 模板的 valueExpr/templateExpr 被编译并存入 OfficeDocModel（model.setValueExpr/setTemplateExpr），若表达式体引用 `ofcPkg` 变量（XLang 闭包可捕获编译作用域），渲染期求值将触达已 close 的包。
- **触发时序**: 模板 docx 头部声明 `<gen:config>` 且 body 内表达式使用 `ofcPkg` → parseFromResource 编译通过（此时 pkg 存活）→ finally pkg.close()（resourceStore=null）→ 上层稍后调用 `valueExpr.invoke(scope)` → 表达式内对 pkg 惰性操作（如 getPart/loadXml 走 ZipResourceStore）→ NPE 或静默空结果。
- **现状**: 是否实际触发取决于 (a) XLang 编译是否捕获作用域局部变量（vs 仅运行期解析）——未验证；(b) 模板是否实际使用 VAR_OFC_PKG。**理论风险，未具体化**，按口径 P3 + 有趣的猜测。
- **风险**: 惰性资源访问在 close 后触发；诊断困难（NPE 指向 XLang 内部栈）。
- **建议**: 模型保存表达式前对引用 VAR_OFC_PKG 的表达式做"渲染期不可用"守卫（编译期检查变量引用），或 OfficeDocModel 持有 pkg 所有权并生命周期对齐（模型 close 时关包）。
- **失效面枚举**: VAR_OFC_PKG 的作用域注入点全清单：OfficeDocModelParser.parseFromResource（本条）；WordTemplateParser.parseFromResource（WordTemplate 路径，pkg 所有权转移给模板+loadInMemory，生命周期对齐，安全对照）；generateToDir 的 copy 注入（渲染期，副本存活于渲染过程，安全）。
- **信心水平**: 有趣的猜测
- **误报排除**: 与 C6-004（包句柄纪律）不同层：那条是 store 未释放，本条是释放后仍被引用（use-after-close）；未合并计数。
- **复核状态**: 已复核（驳回；use-after-close 机制经消费方代码验证不成立：XLang SCOPE_VAR_REF→ScopeIdentifierExecutable 仅按名运行期解析、OfficeDocModel 唯一消费链从不求值表达式、实际执行表达式的路径均渲染期注入新鲜 pkg 副本，详见 C6-resource-lifecycle.md「维度复核结论」表 C6-R2-002 行）

---

## 继续判定（第 2 轮新增核查面，无新发现项）

| 核查面 | 结论 |
|--------|------|
| ExtractPageDrawer / ExtractStripper / JFreeChartStyleAdapter 的 Graphics2D dispose | 全部 createGraphics→dispose 成对（ExtractPageDrawer:213,238 / ExtractStripper:146 / JFreeChartStyleAdapter:635）——安全 |
| ResourceDocumentParser.writer 族 + DefaultResourceHtmlWriter + NurminenDetectionAlgorithm out | try-finally close 全覆盖（含 exportAllTextToFile/exportAllTextlinesToFile/writeDoc）——安全 |
| docx/pptx/xlsx/markdown 转换器 pkg close 面（DocxHelper/WordXmlHelper/DocxToMarkdownConverter/PptxToMarkdownConverter×2 处/AbstractXlsxParser/OfficeDocModelParser） | 逐一核对均 try-finally safeCloseObject/close（grep + 逐文件核读）——纪律完备 |
| AbstractOfficeTemplate.generateToStream 临时目录 | finally ResourceHelper.deleteAll；normalizeTempDirLastModified 用 try(Stream<Path>)——安全 |
| WordTemplateParser 异常路径 | catch(Exception)→safeClose(pkg)→rethrow；成功路径所有权转移 WordTemplate（loadInMemory 后无外部句柄）——安全 |
| WordXmlHelper.loadDocxXml 返回已解析 XNode 后关包 | XNode 为已物化树，不依赖 store——安全 |
| PdfReportRenderer.generateToStream 半个成功（F2 面） | 渲染中途异常时 os 已有部分字节 + PDDocument finally 关闭；半成品输出语义由 HTTP 层兜底——登记为边界，不立项（与全平台模板输出一致） |
| PdfDoc.splitIntoPages 子文档 close 序 | save 在 close 前（L150-155），顺序正确（对照 C6-003 的契约缺失）——安全 |
