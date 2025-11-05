package io.nop.pdf.extract.processor;

import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.api.core.exceptions.NopScriptError;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockSimilarityAnalyzer {

    static class Counter {
        int totalCount;
        int maxContinuousCount;

        int continuousCount;

        public String toString() {
            return "{total=" + totalCount + ",maxContinuous="
                    + maxContinuousCount + "}";
        }
    }

    Map<String, Counter> counters = new HashMap<String, Counter>();

    Map<String, Counter> prevBlocks = new HashMap<String, Counter>();

    Map<String, Counter> curBlocks = new HashMap<String, Counter>();

    boolean allowTableInHeader = false;

    public void setAllowTableInHeader(boolean allowTableInHeader) {
        this.allowTableInHeader = allowTableInHeader;
    }

    void addText(String text) {
        if (text == null || text.length() <= 0)
            return;

        Counter prevCount = prevBlocks.get(text);
        if (prevCount != null) {
            prevCount.continuousCount++;
            prevCount.totalCount++;
            prevCount.maxContinuousCount = Math.max(prevCount.continuousCount,
                    prevCount.maxContinuousCount);

            curBlocks.put(text, prevCount);
            return;
        }

        Counter counter = counters.get(text);
        if (counter == null) {
            counter = new Counter();
            counters.put(text, counter);
        }
        counter.totalCount++;
        counter.continuousCount = 1;
        counter.maxContinuousCount = Math.max(counter.maxContinuousCount, 1);
        curBlocks.put(text, counter);
    }

    void movePage() {
        Map<String, Counter> temp = this.prevBlocks;
        this.prevBlocks = curBlocks;
        this.curBlocks = temp;
        this.curBlocks.clear();
    }

    private String normalizeText(String str) {
        return new StringProcessor(str).removeWhitespace().normalizeDigit()
                .replaceInteger("$").toString();
    }

    private String encodeImageSize(Rectangle2D rect) {
        int w = ((int) Math.floor(rect.getWidth() * 10000)) / 100;
        int h = ((int) Math.floor(rect.getHeight() * 10000)) / 100;

        return String.format("#%d,%d#", w, h);
    }

    public String getContent(Block block) {
        if (block instanceof TextlineBlock) {
            TextlineBlock textBlock = (TextlineBlock) block;
            return textBlock.getContent();
        } else if (block instanceof ImageBlock) {
            ImageBlock imgBlock = (ImageBlock) block;
            if (imgBlock.getReference() == null)
                return null;
            String text = encodeImageSize(imgBlock.getViewBounding());
            return text;
        } else if (block instanceof TableBlock) {
            return getTableText((TableBlock) block);
        } else {
            return null;
        }
    }

    private String getBlockText(Block block) {
        if (block instanceof TextlineBlock) {
            TextlineBlock textBlock = (TextlineBlock) block;
            String text = normalizeText(textBlock.getContent());
            return text;
        } else if (block instanceof ImageBlock) {
            ImageBlock imgBlock = (ImageBlock) block;
            if (imgBlock.getReference() == null)
                return null;
            String text = encodeImageSize(imgBlock.getViewBounding());
            return text;
        } else if (block instanceof TableBlock) {
            return getTableText((TableBlock) block);
        } else {
            return null;
        }
    }

    String getTableText(TableBlock table) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            for (int j = 0, m = table.getColCount(); j < m; j++) {
                TableCellBlock cell = table.getCell(i, j);
                sb.append('|');
                if (cell != null)
                    sb.append(cell.getContent());
            }
            sb.append("%");
        }
        return sb.toString();
    }

    public void addPageBlockTexts(List<String> blocks) {
        if (blocks == null || blocks.isEmpty())
            return;

        this.movePage();
        String prevText = "";
        String text = null;
        for (String block : blocks) {
            text = block;
            if (text == null)
                continue;

            addText(text);
            // 考虑到有可能出现自动折行,导致一行变成两行
            if (prevText.length() > 0) {
                addText(prevText + text);
            }
            prevText = text;
        }
    }

    Counter getCounter(String text) {
        return counters.get(text);
    }

    /**
     * 至少重复7次或者连续重复3次以上
     *
     * @return
     */
    boolean isRepeatMany(Counter counter) {
        return counter.totalCount >= 7 || counter.maxContinuousCount > 3;
    }

    int getRepeatMatch(String text, String prevText) {
        Counter counter = getCounter(text);
        if (counter != null && this.isRepeatMany(counter))
            return 1;

        if (!prevText.isEmpty()) {
            counter = getCounter(prevText + text);
            if (counter != null && this.isRepeatMany(counter))
                return 2;
        }

        return -1;
    }

    /**
     * 找到满足重复匹配条件的block对应的index.
     *
     * @param blocks
     * @param forHeader
     * @return
     */
    public int findMatch(List<String> blocks, boolean forHeader) {
        if (blocks == null)
            return -1;

        int[] bMatch = new int[blocks.size()];

        String prevText = "";
        int prevIndex = -1;
        String text = null;
        for (int i = 0, n = blocks.size(); i < n; i++) {
            text = blocks.get(i);
            if (text == null || text.length() <= 0)
                continue;

            bMatch[i] = getRepeatMatch(text, prevText);
            if (bMatch[i] == 2 && prevIndex >= 0 && bMatch[prevIndex] <= 0) {
                bMatch[prevIndex] = 3;
            }
            prevText = text;
            prevIndex = i;
        }

        // 返回的整个区间不能有不匹配的情况
        if (forHeader) {
            for (int i = 0, n = bMatch.length; i < n; i++) {
                if (bMatch[i] < 0)
                    return i - 1;
            }
            return blocks.size() - 1;
        } else {
            for (int i = bMatch.length - 1; i >= 0; i--) {
                if (bMatch[i] < 0) {
                    if (i == bMatch.length)
                        return -1;
                    return i + 1;
                }
            }
            return 0;
        }
    }

    List<String> getHeaderBlockTexts(ResourcePage page, int maxBlockSize) {
        List<Block> blocks = page.getSortedBlocks();
        if (blocks == null)
            return null;

        Rectangle2D viewBounding = page.getPageViewBounding();

        List<String> ret = new ArrayList<String>(maxBlockSize);
        for (int i = 0, n = Math.min(blocks.size(), maxBlockSize); i < n; i++) {
            Block block = blocks.get(i);
            Rectangle2D rect = block.getViewBounding();

            // 排除页面下半部分
            if (viewBounding != null && !viewBounding.isEmpty()) {

                if ((rect.getMinY() + rect.getHeight() - viewBounding.getMinY())
                        / viewBounding.getHeight() > 0.20) {
                    break;
                }
            }

            if (block instanceof TableBlock) {
                TableBlock table = (TableBlock) block;
                if (table.getRowCount() > 1 || table.getColCount() > 1)
                    break;
            }
            ret.add(this.getBlockText(block));
        }
        return ret;
    }

    public List<String> getFooterBlockTexts(ResourcePage page, int maxBlockSize) {
        List<Block> blocks = page.getSortedBlocks();
        if (blocks == null)
            return null;

        Rectangle2D viewBounding = page.getPageViewBounding();

        List<String> ret = new ArrayList<String>(maxBlockSize);
        for (int i = Math.max(0, blocks.size() - maxBlockSize), n = blocks
                .size(); i < n; i++) {
            Block block = blocks.get(i);
            Rectangle2D rect = block.getViewBounding();

            // 排除页面下半部分. 这里假定block已经按照minY排序
            if (viewBounding != null && !viewBounding.isEmpty()) {

                if ((rect.getMinY() - viewBounding.getMinY())
                        / viewBounding.getHeight() < 0.8) {
                    if (!ret.isEmpty())
                        throw new NopScriptError("pdf.err_invalid_block_order")
                                .param("block", block);
                    continue;
                }
            }
            ret.add(this.getBlockText(block));
        }

        return ret;
    }

    public List<List<String>> prepareForHeader(ResourceDocument doc,
                                               int maxBlockSize) {
        List<List<String>> ret = new ArrayList<List<String>>();

        for (ResourcePage page : doc.getPages()) {
            List<String> blocks = getHeaderBlockTexts(page, maxBlockSize);
            ret.add(blocks);

            if (blocks == null)
                continue;
            this.addPageBlockTexts(blocks);
        }

        return ret;
    }

    public List<List<String>> prepareForFooter(ResourceDocument doc,
                                               int maxBlockSize) {
        List<List<String>> ret = new ArrayList<List<String>>();

        for (ResourcePage page : doc.getPages()) {
            List<String> blocks = getFooterBlockTexts(page, maxBlockSize);
            ret.add(blocks);

            if (blocks == null)
                continue;
            this.addPageBlockTexts(blocks);
        }

        return ret;
    }
}