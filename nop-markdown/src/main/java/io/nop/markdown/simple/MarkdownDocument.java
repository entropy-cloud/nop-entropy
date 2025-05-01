package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.MarkdownConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.markdown.MarkdownErrors.ARG_TITLE;
import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_BLOCK_NOT_DEFINED_IN_TPL;
import static io.nop.markdown.MarkdownErrors.ERR_MARKDOWN_MISSING_BLOCK;

public class MarkdownDocument implements IComponentModel {
    private SourceLocation location;
    private MarkdownBlock block;
    private Set<String> allTitles;

    public void reset() {
        allTitles = null;
    }

    public MarkdownDocument cloneInstance() {
        MarkdownDocument ret = new MarkdownDocument();
        ret.setLocation(location);
        if (block != null)
            ret.setBlock(block.cloneInstance());
        return ret;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
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
    public boolean matchTpl(MarkdownDocument tpl, boolean throwError) {
        if (this.block == null || !this.block.hasChild()) {
            throw new NopException(ERR_MARKDOWN_MISSING_BLOCK)
                    .param(ARG_TITLE, block.getTitle());
        }

        return matchBlockChildren(this.getBlock().getChildren(), tpl.getBlock(), throwError);
    }

    private boolean matchBlockChildren(List<MarkdownBlock> blocks, MarkdownBlock tplBlock, boolean throwError) {
        boolean match = true;

        Map<String, MarkdownBlock> byTitle = new HashMap<>();
        if (tplBlock.hasChild()) {
            for (MarkdownBlock child : tplBlock.getChildren()) {
                byTitle.put(child.getTitle(), child);
            }
        }

        if (blocks != null) {
            for (MarkdownBlock block : blocks) {
                MarkdownBlock tpl = byTitle.remove(block.getTitle());
                if (tpl != null) {
                    block.setTpl(tpl);
                } else {
                    tpl = tplBlock.findChildByMark(MarkdownConstants.MARK_DYNAMIC);
                    if (tpl != null) {
                        block.setTpl(tpl);
                    } else {
                        if (throwError)
                            throw new NopException(ERR_MARKDOWN_BLOCK_NOT_DEFINED_IN_TPL)
                                    .param(ARG_TITLE, block.getTitle());
                    }
                }

                match = match && matchBlockChildren(block.getChildren(), tpl, throwError);
            }
        }

        for (MarkdownBlock tpl : byTitle.values()) {
            if (!tpl.containsText(MarkdownConstants.MARK_OPTIONAL) && !tpl.containsText(MarkdownConstants.MARK_DYNAMIC)) {
                throw new NopException(ERR_MARKDOWN_MISSING_BLOCK)
                        .param(ARG_TITLE, tpl.getTitle());
            }
        }

        return match;
    }

    public boolean removeBlockNoTpl() {
        return block.removeBlock(blk -> {
            if (block == blk)
                return false;
            return blk.getTpl() == null;
        }, true);
    }

    public MarkdownDocument selectBlockByTplMark(String mark) {
        MarkdownDocument ret = cloneInstance();
        if (ret.getBlock() != null)
            ret.getBlock().removeBlock(blk -> !blk.hasChild() && !blk.tplContainsText(mark), true);
        return ret;
    }
}