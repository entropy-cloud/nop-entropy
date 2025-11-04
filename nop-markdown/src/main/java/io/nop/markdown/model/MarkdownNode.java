package io.nop.markdown.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_ALLOW_CHANGE;

public abstract class MarkdownNode implements ISourceLocationGetter, ISourceLocationSetter {
    protected static MarkdownTextOptions DEFAULT_OPTIONS = new MarkdownTextOptions();
    protected static MarkdownTextOptions NOT_INCLUDE_TAGS = new MarkdownTextOptions().includeTags(false);

    private SourceLocation location;
    private int startPos;
    private int endPos;

    protected boolean frozen;

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public int getStartPos() {
        return startPos;
    }

    public void setStartPos(int startPos) {
        this.startPos = startPos;
    }

    public int getEndPos() {
        return endPos;
    }

    public void setEndPos(int endPos) {
        this.endPos = endPos;
    }


    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        this.frozen = true;
    }

    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_COMPONENT_NOT_ALLOW_CHANGE);
    }

    public final String toText() {
        StringBuilder sb = new StringBuilder();
        buildText(sb, DEFAULT_OPTIONS);
        return sb.toString();
    }

    protected abstract void buildText(StringBuilder sb, MarkdownTextOptions options);
}
