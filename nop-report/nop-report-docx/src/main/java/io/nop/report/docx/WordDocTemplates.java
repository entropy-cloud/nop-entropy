package io.nop.report.docx;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.zip.IZipOutput;
import io.nop.core.resource.zip.ZipOptions;
import io.nop.office.doc.model.NopOfficeDocModelException;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeDocTemplateModel;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficePageBreakModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.ooxml.docx.WordTemplate;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.output.OfficeDocModelWriter;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.ooxml.docx.parse.WordTemplateParser;
import io.nop.report.docx.expand.OfficeDocModelExpander;
import io.nop.xlang.api.XLang;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Unified generation outlet for static and template-carrying Word documents
 * (WI9, plan 2524; decisions U1-U3 in
 * ai-dev/design/word-editor/online-word-model-design.md section 21).
 *
 * <ul>
 * <li>U2: parsing skips are named and carried on the result
 * ({@link WordDocOutput#getParseSkips()}), they never block generation. Forms
 * that need the full xpl tag chain go through
 * {@link #generateViaLegacyTemplate} - a zero-wrap pass-through of the legacy
 * {@code WordTemplateParser} chain. OOXML parts the document model does not
 * carry (customXml, fontTable, ...) are not preserved by this chain: the model
 * is the boundary.</li>
 * <li>U3: static and dynamic input share one entry. Input without template
 * semantics (no template model on any node that expand/bind would consult)
 * skips expansion and binding entirely and is written directly - generation is
 * idempotent for static input because the docx writer output is
 * byte-deterministic (fixed zip entry time; XML serialization and part order
 * are deterministic).</li>
 * <li>PDF output is a separate outlet: {@code io.nop.report.pdf.doc.WordDocPdfTemplates}
 * in the PDF report module (its renderer lives there; keeping the pdfbox chain
 * out of this module is decision U1). It only accepts an already expanded
 * {@link OfficeDocModel} - use {@link #expandToModel} (or
 * {@link WordDocOutput#getModel()}) as the model stage feeding it, because this
 * module must not become a compile dependency of the PDF module (the WI7 edge
 * nop-report-docx -&gt; nop-report-pdf is test scope; adding the reverse compile
 * edge is rejected by the Maven reactor as a cycle).</li>
 * </ul>
 *
 * <p>All methods are stateless and create fresh parser/expander/writer
 * instances per call, so the facade is thread-safe.</p>
 */
public final class WordDocTemplates {

    /**
     * Virtual path of the input resource; only used for error messages and
     * source locations, the bytes never touch the file system.
     */
    private static final String INPUT_DOCX_PATH = "/nop/word-doc-templates/input.docx";

    /**
     * Fixed zip entry modification time (2026-01-01T00:00:00Z), same pattern as
     * the corpus builder determinism contract ({@code DocxCorpusBuilder.FIXED_ZIP_TIME})
     * and the binary stability test ({@code TestExcelTemplateBinaryStability}):
     * without it ZipEntry stamps the current time and repeated generation would
     * not be byte-identical (U3 idempotency).
     */
    private static final long ZIP_ENTRY_TIME = 1767225600000L;

    private WordDocTemplates() {
    }

    // ------------------------------------------------------------------ docx output

    /**
     * Parses a docx package (static or template annotated), expands template
     * semantics (xpt tables, block loops/filters, image bindings) and writes the
     * resulting document back to docx bytes.
     *
     * @param docxBytes the input docx package
     * @param dataScope data variables (e.g. the {@code data} dataset); may be null
     * @return the generated docx bytes with parse skips and expansion counters
     */
    public static WordDocOutput generate(byte[] docxBytes, Map<String, Object> dataScope) {
        requireDocxBytes(docxBytes);
        OfficeDocModelParser parser = new OfficeDocModelParser();
        OfficeDocModel model = parser.parseFromResource(
                new ByteArrayResource(INPUT_DOCX_PATH, docxBytes, 0L));
        List<String> parseSkips = parser.getLastParseSkips();

        Expansion expansion = expandModel(model, dataScope);
        return new WordDocOutput(writeDocx(expansion.model), expansion.model,
                parseSkips, expansion.expandedTableCount, expansion.boundImageCount);
    }

    /**
     * Same pipeline as {@link #generate(byte[], Map)} for an already parsed
     * model. No parse skips exist on this entry.
     */
    public static WordDocOutput generate(OfficeDocModel model, Map<String, Object> dataScope) {
        requireModel(model);
        Expansion expansion = expandModel(model, dataScope);
        return new WordDocOutput(writeDocx(expansion.model), expansion.model,
                List.of(), expansion.expandedTableCount, expansion.boundImageCount);
    }

    // ------------------------------------------------------------------ model stage (PDF feed)

    /**
     * Parses a docx package and expands template semantics, returning the model
     * stage. This is the model feed for the PDF outlet, which accepts an
     * expanded {@link OfficeDocModel} only (decision U1 / dependency reality:
     * the expander lives in this module, the PDF renderer in the PDF module).
     *
     * @param docxBytes the input docx package
     * @param dataScope data variables; may be null
     * @return the expanded model with parse skips and expansion counters
     */
    public static WordDocModelOutput expandToModel(byte[] docxBytes, Map<String, Object> dataScope) {
        requireDocxBytes(docxBytes);
        OfficeDocModelParser parser = new OfficeDocModelParser();
        OfficeDocModel model = parser.parseFromResource(
                new ByteArrayResource(INPUT_DOCX_PATH, docxBytes, 0L));
        Expansion expansion = expandModel(model, dataScope);
        return new WordDocModelOutput(expansion.model, parser.getLastParseSkips(),
                expansion.expandedTableCount, expansion.boundImageCount);
    }

    /**
     * Same as {@link #expandToModel(byte[], Map)} for an already parsed model.
     */
    public static WordDocModelOutput expandToModel(OfficeDocModel model, Map<String, Object> dataScope) {
        requireModel(model);
        Expansion expansion = expandModel(model, dataScope);
        return new WordDocModelOutput(expansion.model, List.of(),
                expansion.expandedTableCount, expansion.boundImageCount);
    }

    // ------------------------------------------------------------------ legacy escape hatch

    /**
     * Generates through the legacy template chain ({@code WordTemplateParser} ->
     * {@code WordTemplate}) - the high-level escape hatch for forms the model
     * chain cannot express (full xpl tag chain, unmodeled OOXML parts). This is
     * a zero-wrap pass-through: no parsing skips, no expansion counters, no
     * determinism contract (the legacy chain stamps zip entry times itself).
     *
     * @param docxBytes   the template docx package
     * @param dataScope   data variables placed into a fresh evaluation scope
     * @param outputFile  target file; missing parent directories are created
     */
    public static void generateViaLegacyTemplate(byte[] docxBytes, Map<String, Object> dataScope,
                                                 Path outputFile) {
        requireDocxBytes(docxBytes);
        if (outputFile == null)
            throw new NopOfficeDocModelException("outputFile must not be null");

        WordTemplate template = new WordTemplateParser().parseFromResource(
                new ByteArrayResource(INPUT_DOCX_PATH, docxBytes, 0L));
        IEvalScope scope = XLang.newEvalScope();
        if (dataScope != null) {
            for (Map.Entry<String, Object> entry : dataScope.entrySet()) {
                scope.setLocalValue(null, entry.getKey(), entry.getValue());
            }
        }
        template.generateToFile(outputFile.toFile(), scope);
    }

    // ------------------------------------------------------------------ template semantics detection (U3)

    /**
     * True when any node of the model tree carries a template model that the
     * expand/bind phases would consult: xpt tables, block/doc/page loops and
     * filters, run value expressions, image bindings. The doc-level template
     * model is exempted for its generation-shell fields (importLibs, dump,
     * beforeGen/afterGen, ...): {@code OfficeDocModelParser} always populates
     * those and neither the expander nor the docx writer consults them, so they
     * are not template semantics.
     */
    static boolean hasTemplateSemantics(OfficeDocModel doc) {
        if (doc == null)
            return false;

        OfficeDocTemplateModel docModel = doc.getModel();
        if (docModel != null && (docModel.getTestExpr() != null
                || docModel.getBeginLoop() != null
                || !StringHelper.isEmpty(docModel.getLoopItemsName())))
            return true;

        List<OfficeDocPageModel> pages = doc.getPages();
        if (pages == null)
            return false;
        for (OfficeDocPageModel page : pages) {
            if (page == null)
                continue;
            if (page.getModel() != null)
                return true;
            if (anyBlockTemplated(page.getBody()) || anyBlockTemplated(page.getHeader())
                    || anyBlockTemplated(page.getFooter()))
                return true;
        }
        return false;
    }

    private static boolean anyBlockTemplated(List<OfficeBlock> blocks) {
        if (blocks == null)
            return false;
        for (OfficeBlock block : blocks) {
            if (block != null && isBlockTemplated(block))
                return true;
        }
        return false;
    }

    private static boolean isBlockTemplated(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) {
            OfficeParagraphModel para = (OfficeParagraphModel) block;
            return para.getModel() != null || anyRunTemplated(para.getRuns());
        }
        if (block instanceof OfficeTitleModel) {
            OfficeTitleModel title = (OfficeTitleModel) block;
            return title.getModel() != null || anyRunTemplated(title.getRuns());
        }
        if (block instanceof OfficeListModel) {
            OfficeListModel list = (OfficeListModel) block;
            if (list.getModel() != null)
                return true;
            if (list.getItems() != null) {
                for (OfficeListItemModel item : list.getItems()) {
                    if (item != null && anyRunTemplated(item.getRuns()))
                        return true;
                }
            }
            return false;
        }
        if (block instanceof OfficeImageModel) {
            return ((OfficeImageModel) block).getModel() != null;
        }
        if (block instanceof OfficeHyperlinkModel) {
            OfficeHyperlinkModel hyperlink = (OfficeHyperlinkModel) block;
            return hyperlink.getModel() != null || anyRunTemplated(hyperlink.getRuns());
        }
        if (block instanceof WordTable) {
            return isTableTemplated((WordTable) block);
        }
        if (block instanceof OfficeSeparatorModel || block instanceof OfficePageBreakModel) {
            return false; // no template model carrier
        }
        // unknown block implementations: conservatively treated as template carrying
        return true;
    }

    private static boolean isTableTemplated(WordTable table) {
        if (table.getModel() != null)
            return true;
        if (table.getCols() != null) {
            for (WordTableColumnConfig col : table.getCols()) {
                if (col != null && col.getModel() != null)
                    return true;
            }
        }
        if (table.getRows() != null) {
            for (WordTableRow row : table.getRows()) {
                if (row == null)
                    continue;
                if (row.getModel() != null)
                    return true;
                if (row.getCells() != null) {
                    for (WordTableCell cell : row.getCells()) {
                        if (cell != null && cell.getModel() != null)
                            return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean anyRunTemplated(List<OfficeRun> runs) {
        if (runs == null)
            return false;
        for (OfficeRun run : runs) {
            if (run instanceof OfficeRunModel) {
                if (((OfficeRunModel) run).getModel() != null)
                    return true;
            } else if (run instanceof OfficeLinkModel) {
                OfficeLinkModel link = (OfficeLinkModel) run;
                if (link.getModel() != null || anyRunTemplated(link.getRuns()))
                    return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ pipeline internals

    private static final class Expansion {
        final OfficeDocModel model;
        final int expandedTableCount;
        final int boundImageCount;

        Expansion(OfficeDocModel model, int expandedTableCount, int boundImageCount) {
            this.model = model;
            this.expandedTableCount = expandedTableCount;
            this.boundImageCount = boundImageCount;
        }
    }

    /**
     * U3: input without template semantics skips expansion and binding entirely
     * (the input model is used as-is); template carrying input goes through a
     * fresh expander whose counters become the result metadata.
     */
    private static Expansion expandModel(OfficeDocModel model, Map<String, Object> dataScope) {
        if (!hasTemplateSemantics(model))
            return new Expansion(model, 0, 0);

        OfficeDocModelExpander expander = new OfficeDocModelExpander();
        OfficeDocModel expanded = expander.expand(model, dataScope);
        return new Expansion(expanded, expander.getLastExpandedTableCount(),
                expander.getLastBoundImageCount());
    }

    /**
     * Writes the model to deterministic docx bytes: {@code OfficeDocModelWriter}
     * builds the package, the package is zipped with the fixed entry time (see
     * {@link #ZIP_ENTRY_TIME}).
     */
    private static byte[] writeDocx(OfficeDocModel model) {
        WordOfficePackage pkg = new OfficeDocModelWriter().buildPackage(model);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOptions options = new ZipOptions();
        options.setEntryTime(ZIP_ENTRY_TIME);
        IZipOutput out = ResourceHelper.getZipTool().newZipOutput(bos, options);
        try {
            pkg.generateToZip(out, XLang.newEvalScope());
        } catch (IOException e) {
            throw new NopOfficeDocModelException("failed to write the generated docx package", e);
        } finally {
            IoHelper.safeClose(out);
        }
        return bos.toByteArray();
    }

    private static void requireDocxBytes(byte[] docxBytes) {
        if (docxBytes == null || docxBytes.length == 0)
            throw new NopOfficeDocModelException(
                    "docxBytes must not be null or empty: a docx package is required");
    }

    private static void requireModel(OfficeDocModel model) {
        if (model == null)
            throw new NopOfficeDocModelException("model must not be null: an OfficeDocModel is required");
    }
}
