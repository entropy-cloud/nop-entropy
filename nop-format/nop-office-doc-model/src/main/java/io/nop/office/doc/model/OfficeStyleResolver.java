package io.nop.office.doc.model;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.WordTableStyle;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves named styles collected in {@link OfficeDocModel#getStyles()} against their
 * {@code basedOn} inheritance chain.
 *
 * <p>Merge semantics (design decision, see ai-dev/design/word-editor/online-word-model-design.md):
 * walking from the root ancestor down to the requested style, non-null scalar fields
 * override, nested object fields (font / borders / cellStyle) replace as a whole. The
 * identity fields {@code id}/{@code name} and the meta fields {@code basedOn}/
 * {@code default}/{@code type} are excluded from merging — the resolved snapshot always
 * carries the requested (leaf) style's identity.</p>
 *
 * <p>Fail-fast: unknown style ids, {@code basedOn} cycles and cross-type inheritance
 * throw {@link NopOfficeDocModelException} — never silently skipped.</p>
 *
 * <p>Implementation pattern precedent (parallel implementation, not referenced):
 * WordStylesPart.resolveInheritance in nop-ooxml-docx.</p>
 */
public class OfficeStyleResolver {

    /** Identity and meta fields excluded from basedOn merging. */
    static final Set<String> MERGE_EXCLUDED_PROPS = Set.of("id", "name", "basedOn", "default", "type", "location");

    private final OfficeDocModel doc;

    public OfficeStyleResolver(OfficeDocModel doc) {
        this.doc = Guard.notNull(doc, "doc");
    }

    public WordRunStyle resolveRunStyle(String styleId) {
        return (WordRunStyle) resolve(styleId, WordRunStyle.class);
    }

    public WordParagraphStyle resolveParagraphStyle(String styleId) {
        return (WordParagraphStyle) resolve(styleId, WordParagraphStyle.class);
    }

    public WordCellStyle resolveCellStyle(String styleId) {
        return (WordCellStyle) resolve(styleId, WordCellStyle.class);
    }

    public WordTableStyle resolveTableStyle(String styleId) {
        return (WordTableStyle) resolve(styleId, WordTableStyle.class);
    }

    public WordListStyle resolveListStyle(String styleId) {
        return (WordListStyle) resolve(styleId, WordListStyle.class);
    }

    /**
     * Resolve the inheritance chain for {@code styleId} and merge it into a snapshot.
     *
     * @param expected the concrete style class the id must resolve to
     */
    public OfficeStyle resolve(String styleId, Class<?> expected) {
        Guard.notEmpty(styleId, "styleId");
        Guard.notNull(expected, "expected");
        Set<String> visited = new LinkedHashSet<>();
        OfficeStyle merged = mergeChain(styleId, expected, visited);
        // identity always comes from the requested leaf style
        OfficeStyle leaf = requireStyle(styleId);
        setIdProps(merged, leaf);
        return merged;
    }

    /**
     * Return the default style of the given concrete type, or null when no style is
     * marked default.
     */
    public OfficeStyle defaultStyle(Class<?> expected) {
        Guard.notNull(expected, "expected");
        if (doc.getStyles() == null)
            return null;
        for (OfficeStyle style : doc.getStyles()) {
            if (expected.isInstance(style) && style.isDefault())
                return style;
        }
        return null;
    }

    private OfficeStyle mergeChain(String styleId, Class<?> expected, Set<String> visited) {
        OfficeStyle style = requireStyle(styleId);
        if (!expected.isInstance(style)) {
            throw new NopOfficeDocModelException(
                    "style id '" + styleId + "' resolves to " + style.getClass().getName()
                            + " which is not an instance of " + expected.getName());
        }
        if (!visited.add(styleId)) {
            throw new NopOfficeDocModelException(
                    "basedOn cycle detected while resolving style '" + styleId + "': " + visited);
        }

        String basedOn = style.getBasedOn();
        OfficeStyle base;
        if (StringHelper.isEmpty(basedOn)) {
            base = null;
        } else {
            base = mergeChain(basedOn, expected, visited);
        }

        if (base == null)
            return style;

        // start from a shallow clone of the base, then let the leaf override non-null fields.
        // Primitive fields (int/boolean/...) are never null: a value equal to the freshly
        // constructed default is treated as "unset" and does not override the base.
        OfficeStyle merged = (OfficeStyle) base.cloneInstance();
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(expected);
        Object freshDefaults = ClassHelper.newInstance(expected);
        for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
            if (!propModel.isReadable() || !propModel.isWritable())
                continue;
            String propName = propModel.getName();
            if (MERGE_EXCLUDED_PROPS.contains(propName))
                continue;
            Object leafValue = propModel.getGetter().getProperty(style, propName, null);
            if (leafValue == null)
                continue;
            if (propModel.getRawClass().isPrimitive()) {
                Object freshValue = propModel.getGetter().getProperty(freshDefaults, propName, null);
                if (freshValue != null && freshValue.equals(leafValue))
                    continue; // primitive left at its constructed default => unset
            }
            propModel.getSetter().setProperty(merged, propName, leafValue, null);
        }
        return merged;
    }

    private OfficeStyle requireStyle(String styleId) {
        OfficeStyle style = doc.getStyle(styleId);
        if (style == null)
            throw new NopOfficeDocModelException("style id '" + styleId + "' is not defined in doc styles");
        return style;
    }

    private void setIdProps(OfficeStyle target, OfficeStyle leaf) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(leaf.getClass());
        for (String propName : new String[]{"id", "name", "type"}) {
            IBeanPropertyModel propModel = beanModel.requirePropertyModel(propName);
            // identity always comes from the leaf: overwrite even when the leaf value is null
            // so base identity never leaks into the resolved snapshot
            Object value = propModel.getGetter().getProperty(leaf, propName, null);
            propModel.getSetter().setProperty(target, propName, value, null);
        }
    }
}
