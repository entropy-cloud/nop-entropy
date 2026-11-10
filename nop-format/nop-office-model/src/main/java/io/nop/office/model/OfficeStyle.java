package io.nop.office.model;

/**
 * Marker interface for named style models collected in the doc-level {@code styles}
 * container: {@link WordRunStyle}, {@link WordParagraphStyle}, {@link WordCellStyle},
 * {@link WordTableStyle} and {@link WordListStyle}. Dispatched by the doc.xdef styles
 * wrapper node via bean-sub-type-prop.
 */
public interface OfficeStyle {
    /**
     * Key accessor used by the KeyedList produced for the doc.xdef styles wrapper node
     * (xdef:key-attr="id").
     */
    String getId();

    /**
     * Id of the base style this style inherits from, or null when the style is a root.
     */
    String getBasedOn();

    /**
     * Whether this style is the default style of its concrete kind (at most one per kind).
     */
    boolean isDefault();

    /**
     * Shallow copy per the platform model convention (scalars copied, child containers
     * shared by reference).
     */
    OfficeStyle cloneInstance();
}
