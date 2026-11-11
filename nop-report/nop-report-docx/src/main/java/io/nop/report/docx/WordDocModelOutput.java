package io.nop.report.docx;

import io.nop.office.doc.model.OfficeDocModel;

import java.util.List;

/**
 * Result of the model stage of the unified Word outlet ({@link WordDocTemplates}):
 * the expanded document model plus the observability metadata gathered on the
 * way (WI9, plan 2524).
 *
 * <p>The model can be handed to the PDF outlet of the PDF report module, which
 * only accepts an already expanded model (see {@link WordDocTemplates} for the
 * dependency reason).</p>
 */
public class WordDocModelOutput {
    private final OfficeDocModel model;
    private final List<String> parseSkips;
    private final int expandedTableCount;
    private final int boundImageCount;

    public WordDocModelOutput(OfficeDocModel model, List<String> parseSkips,
                              int expandedTableCount, int boundImageCount) {
        this.model = model;
        this.parseSkips = parseSkips == null ? List.of() : List.copyOf(parseSkips);
        this.expandedTableCount = expandedTableCount;
        this.boundImageCount = boundImageCount;
    }

    /**
     * The model the output was (or would be) generated from. For input carrying
     * template semantics this is the expanded copy; for static input it is the
     * input model itself (nothing was expanded, nothing was copied).
     */
    public OfficeDocModel getModel() {
        return model;
    }

    /**
     * Named skips recorded while parsing the input docx: forms the model chain
     * cannot express structurally. Empty when the input was already a model or
     * nothing was skipped. A non-empty list does not block generation - callers
     * that need those forms use the legacy template escape hatch.
     */
    public List<String> getParseSkips() {
        return parseSkips;
    }

    /** Number of xpt tables expanded; 0 for static input. */
    public int getExpandedTableCount() {
        return expandedTableCount;
    }

    /** Number of image placeholders replaced by a chart/code binding; 0 for static input. */
    public int getBoundImageCount() {
        return boundImageCount;
    }
}
