package io.nop.pdf.extract.struct;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class ResourcePage {

    /**
     * 按照pdf结构解析出来的顺序号，为index+1
     */
    private int pageNo;

    /**
     * 页面上打印出来的页面顺序号
     */
    private int displayPageNo;

    private List<Block> pageHeader;
    private List<Block> pageFooter;

    private String allTextContent;

    private Rectangle2D pageViewBounding = null;

    private List<TextBlock> phraseBlocks = new ArrayList<TextBlock>();
    private List<TextBlock> charBlocks = new ArrayList<TextBlock>();
    private List<ShapeBlock> shapeBlocks = new ArrayList<ShapeBlock>();

    /**
     * 所有解析出的纯文本行
     */
    private List<TextlineBlock> textlines = new ArrayList<TextlineBlock>();

    /**
     * 所有解析出的图片
     */
    private List<ImageBlock> imageBlocks = new ArrayList<ImageBlock>();

    /**
     * 所有解析出的表格
     */
    private List<TableBlock> tables = new ArrayList<TableBlock>();

    private List<Block> sortedBlocks;

    public void simplify() {
        phraseBlocks = Collections.emptyList();
        charBlocks = Collections.emptyList();

        // 保留shapeBlock

        for (TableBlock table : tables) {
            table.simplify();
        }

        // 删除所有不需要使用的小图片
        Iterator<ImageBlock> it = imageBlocks.iterator();
        while (it.hasNext()) {
            ImageBlock img = it.next();
            if (img.getReference() == null) {
                it.remove();
            }
        }
    }

    public int getDisplayPageNo() {
        return displayPageNo;
    }

    public void setDisplayPageNo(int displayPageNo) {
        this.displayPageNo = displayPageNo;
    }

    public List<Block> getPageHeader() {
        return pageHeader;
    }

    public void setPageHeader(List<Block> pageHeader) {
        this.pageHeader = pageHeader;
    }

    public List<Block> getPageFooter() {
        return pageFooter;
    }

    public void setPageFooter(List<Block> pageFooter) {
        this.pageFooter = pageFooter;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public String getAllTextContent() {

        return allTextContent;
    }

    public void setAllTextContent(String content) {

        this.allTextContent = content;
    }

    public Rectangle2D getPageViewBounding() {
        return pageViewBounding;
    }

    public void setPageViewBounding(Rectangle2D pageViewBounding) {
        this.pageViewBounding = pageViewBounding;
    }

    public List<TextlineBlock> getTextlines() {
        return textlines;
    }

    public void addTextline(TextlineBlock textline) {

        this.textlines.add(textline);
    }

    public List<TextBlock> getPhraseBlocks() {
        return phraseBlocks;
    }

    public void addPhraseBlock(TextBlock block) {

        this.phraseBlocks.add(block);
    }

    public List<TextBlock> getCharBlocks() {
        return charBlocks;
    }

    public void addCharBlocks(TextBlock block) {

        this.charBlocks.add(block);
    }

    public List<ShapeBlock> getShapeBlocks() {
        return shapeBlocks;
    }

    public void setShapeBlocks(List<ShapeBlock> blocks) {
        this.shapeBlocks = blocks;
    }

    public void addShapeBlock(ShapeBlock block) {

        this.shapeBlocks.add(block);
    }

    public List<ImageBlock> getImageBlocks() {
        return this.imageBlocks;
    }

    public void addImageBlock(ImageBlock imageBlock) {

        this.imageBlocks.add(imageBlock);
    }

    public List<TableBlock> getTables() {

        return this.tables;
    }

    public void addTable(TableBlock table) {

        this.tables.add(table);
    }

    public List<Block> getSortedBlocks() {
        if (sortedBlocks == null)
            this.sortedBlocks = _getSortedBlocks();
        return sortedBlocks;
    }

    public void resetAllBlockIndex() {
        int index = 1;
        for (Block block : this.getSortedBlocks()) {
            block.setPageBlockIndex(index);
            block.setPageNo(pageNo);
            index++;
            if (block instanceof TableBlock) {
                index = ((TableBlock) block).resetCellBlockIndex(index);
            }
        }
    }

    /**
     * @return
     */
    List<Block> _getSortedBlocks() {

        // TODO: 影响性能，需要缓存

        List<TextlineBlock> textlines = this.getTextlines();
        List<TableBlock> tables = this.getTables();
        List<ImageBlock> images = this.getImageBlocks();

        List<Block> blocks = new ArrayList<Block>();
        blocks.addAll(textlines);
        blocks.addAll(tables);
        blocks.addAll(images);

        Collections.sort(blocks, new Comparator<Block>() {

            @Override
            public int compare(Block block1, Block block2) {

                Rectangle2D rect1 = block1.getViewBounding();
                Rectangle2D rect2 = block2.getViewBounding();

                if (rect1.getMinY() < rect2.getMinY())
                    return -1;
                if (rect1.getMinY() > rect2.getMinY())
                    return 1;

                return 0;
            }
        });

        return blocks;
    }

    public Block findBlockByIndex(int index) {

        for (Block block : this.getSortedBlocks()) {
            if (block.getPageBlockIndex() == index)
                return block;
            if (block instanceof TableBlock) {
                TableBlock table = (TableBlock) block;
                if (table.containsCellBlock(index)) {
                    return table.getCellBlockByIndex(index);
                }
            }
        }
        return null;
    }

    /**
     * 查找页内第一个表格
     *
     * @return
     */
    public TableBlock findFirstTable() {

        List<Block> blocks = this.getSortedBlocks();
        for (Block block : blocks) {

            if (block == null) continue;

            if (block instanceof TableBlock) {

                return (TableBlock) block;
            }
        }

        return null;
    }

    /**
     * 查找页内最后一个表格
     *
     * @return
     */
    public TableBlock findLastTable() {

        List<Block> blocks = this.getSortedBlocks();
        for (int i = blocks.size() - 1; i >= 0; i--) {

            Block block = blocks.get(i);

            if (block instanceof TableBlock) {

                return (TableBlock) block;
            }
        }

        return null;
    }

//    /**
//     * 查找业内指定文本串之后的第一个表格, 表格至少具有minCol列
//     *
//     * @param matcher
//     * @param minCol  表格至少要具有指定的列数, 这样用于过滤掉一些不必要的表格. minCol<=0时不按列数过滤
//     * @return
//     */
//    public TableBlock findFirstTableAfterText(ITextMatcher matcher, Predicate<TableBlock> filter) {
//        TextMatchState state = new TextMatchState();
//
//        boolean textFound = false;
//
//        List<Block> blocks = this.getSortedBlocks();
//        for (Block block : blocks) {
//
//            if (block == null) continue;
//
//            if (block instanceof TextlineBlock) {
//
//                if (textFound) continue;
//
//                TextInput input = ((TextlineBlock) block).getNormalizedInput();
//                if (input == null) continue;
//
//                if (matcher.match(input, state)) {
//                    textFound = true;
//                }
//            } else if (block instanceof TableBlock) {
//
//                if (textFound && block != null) {
//                    TableBlock table = (TableBlock) block;
//                    if (filter == null || filter.test(table))
//                        return table;
//                }
//            }
//        }
//
//        return null;
//    }

    /**
     * 查找页内指定文本串之后的第一个表格
     *
     * @param textPattern  文本，可以为正则表达式，注意括号的写法
     * @param matchExactly 为true表示不使用正则，textPattern当作普通文本，进行精确的相等匹配
     * @return
     */
    public TableBlock findFirstTableAfterText(String textPattern, boolean matchExactly) {

        boolean textFound = false;

        List<Block> blocks = this.getSortedBlocks();
        for (Block block : blocks) {

            if (block == null) continue;

            if (block instanceof TextlineBlock) {

                if (textFound) continue;

                String text = ((TextlineBlock) block).getContent();
                if (text == null) continue;

                text = text.trim();

                boolean matched = matchExactly ? text.equals(textPattern) : Pattern.matches(textPattern, text);
                if (matched) {
                    textFound = true;
                }
            } else if (block instanceof TableBlock) {

                if (textFound && block != null) {
                    return (TableBlock) block;
                }
            }
        }

        return null;
    }
}
