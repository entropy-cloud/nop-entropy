package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.MarkdownConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.markdown.MarkdownErrors.ARG_TITLE;
import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_MISSING_BLOCK;

public class MarkdownDocument implements IComponentModel {
    private SourceLocation location;
    private MarkdownBlock block;
    private String text;
    private Set<String> allTitles;

    public void reset() {
        allTitles = null;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Set<String> getAllTitles() {
        if (allTitles == null && block != null)
            allTitles = block.getAllTitles();
        return allTitles;
    }

    public boolean containsTitle(String title) {
        Set<String> allTitles = getAllTitles();
        if (allTitles == null)
            return false;
        return allTitles.contains(title);
    }

    public MarkdownBlock findBlockByTitle(String title) {
        if (block == null)
            return null;
        return block.findByTitle(title);
    }

    public MarkdownBlock getBlock() {
        return block;
    }

    public void setBlock(MarkdownBlock block) {
        this.block = Guard.notNull(block, "block");
    }

    public void forEachBlock(Consumer<MarkdownBlock> action) {
        this.block.forEachBlock(action);
    }

    public List<String> getAllFullTitles() {
        List<String> ret = new ArrayList<>();
        forEachBlock(block -> {
            if (block.getTitle() != null) {
                ret.add(block.getFullTitle());
            }
        });
        return ret;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        block.buildText(sb);
        return sb.toString();
    }

    /**
     * 检查markdown文档的结构与模板中定义的结构一致，包含所有结构部分
     */
    public void checkBlockInTemplate(MarkdownDocument tpl) {
        tpl.forEachBlock(block -> {
            if (block.getTitle() != null
                    && !block.containsText(MarkdownConstants.MARK_OPTIONAL)
                    && !block.containsText(MarkdownConstants.MARK_DYNAMIC)
            ) {
                if (findBlockByTitle(block.getTitle()) == null) {
                    throw new NopException(ERR_MARKDOWN_MISSING_BLOCK)
                            .param(ARG_TITLE, block.getTitle());
                }
            }
        });
    }

    public void removeBlockNotInTemplate(MarkdownDocument tpl) {
        block.removeBlock(blk -> {
            if (blk.getTitle() == null)
                return true;
            return tpl.findBlockByTitle(blk.getTitle()) == null;
        }, true);
    }
}