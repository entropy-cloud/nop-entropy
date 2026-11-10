package io.nop.office.doc.model;

/**
 * Marker interface for inline run models inside a paragraph: text ({@link OfficeRunModel}),
 * {@link OfficeTabModel}, {@link OfficeBreakModel} and {@link OfficeLinkModel} (which
 * recursively nests runs). Dispatched by the doc.xdef runs wrapper node via
 * bean-sub-type-prop.
 */
public interface OfficeRun {
    /**
     * Key accessor used by the KeyedList produced for the doc.xdef runs wrapper node
     * (xdef:key-attr="id").
     */
    String getId();
}
