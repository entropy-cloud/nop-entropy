package io.nop.xlang.xdef;

/**
 * XDef合并加载的配置选项。
 * 
 * @see XDefMergeLoader
 */
public class XDefMergeOptions {
    private boolean inlineXDefRef = true;
    private boolean inlineEnumOptions = true;
    private boolean removeNsDecls = true;
    private boolean removeXDefChildren = true;
    private boolean collectDefinesToRoot = true;

    public static XDefMergeOptions defaults() {
        return new XDefMergeOptions();
    }

    public static XDefMergeOptions forAi() {
        XDefMergeOptions options = new XDefMergeOptions();
        options.inlineXDefRef = true;
        options.inlineEnumOptions = true;
        options.removeNsDecls = true;
        options.removeXDefChildren = true;
        return options;
    }

    public static XDefMergeOptions forMetaModel() {
        XDefMergeOptions options = new XDefMergeOptions();
        options.inlineXDefRef = false;
        options.inlineEnumOptions = false;
        options.removeNsDecls = false;
        options.removeXDefChildren = false;
        options.collectDefinesToRoot = true;
        return options;
    }

    public boolean isInlineXDefRef() {
        return inlineXDefRef;
    }

    public void setInlineXDefRef(boolean inlineXDefRef) {
        this.inlineXDefRef = inlineXDefRef;
    }

    public boolean isInlineEnumOptions() {
        return inlineEnumOptions;
    }

    public void setInlineEnumOptions(boolean inlineEnumOptions) {
        this.inlineEnumOptions = inlineEnumOptions;
    }

    public boolean isRemoveNsDecls() {
        return removeNsDecls;
    }

    public void setRemoveNsDecls(boolean removeNsDecls) {
        this.removeNsDecls = removeNsDecls;
    }

    public boolean isRemoveXDefChildren() {
        return removeXDefChildren;
    }

    public void setRemoveXDefChildren(boolean removeXDefChildren) {
        this.removeXDefChildren = removeXDefChildren;
    }

    public boolean isCollectDefinesToRoot() {
        return collectDefinesToRoot;
    }

    public void setCollectDefinesToRoot(boolean collectDefinesToRoot) {
        this.collectDefinesToRoot = collectDefinesToRoot;
    }
}
