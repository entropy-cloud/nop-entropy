package io.nop.pdf.extract.parser;

import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.DummyImageReference;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.pdf.extract.struct.TocItem;
import io.nop.pdf.extract.struct.TocTable;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.IXNodeParser;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;

import java.util.ArrayList;
import java.util.List;

public class ResourceDocumentHtmlParser implements
        IResourceObjectLoader<ResourceDocument> {

    @Override
    public ResourceDocument loadObjectFromPath(String path) {
        return parseFromResource(VirtualFileSystem.instance().getResource(path));
    }

    @Override
    public ResourceDocument parseFromResource(IResource resource) {

        // FileResource file = new FileResource(new File("c:/test.html"));

        ResourceDocument doc = new ResourceDocument();

        List<ResourcePage> pages = new ArrayList<ResourcePage>();

        IXNodeParser parser = XNodeParser.instance();
        parser.defaultEncoding("UTF-8");
        XNode XNode = parser.parseFromResource(resource);
        String titleValue = XNode.childByTag("head")
                .childContentText("title");

        XNode bodyTag = XNode.childByTag("body");

        List<XNode> pageList = bodyTag
                .childrenByAttr("class", "pdf-page");

        for (XNode page : pageList) {
            List<Block> blocks;
            ResourcePage rePage = new ResourcePage();
            String pageNoString = page.attrText("id").substring(1);
            int pageNo = Integer.parseInt(pageNoString);
            int displayPageNo = page.attrInt("data-display-page-no", 0);
            // 得到head
            XNode pageHeadNode = page.childByAttr("class",
                    "pdf-page-header");
            // 判断head不为空
            if (pageHeadNode != null) {
                blocks = parseBlocks(pageHeadNode);
                rePage.setPageHeader(blocks);
            }
            // 得到content
            XNode contentNode = page.childByAttr("class",
                    "pdf-page-content");
            blocks = parseBlocks(contentNode);
            rePage.getSortedBlocks().addAll(blocks);
            // 得到foot
            XNode pageFooterNode = page.childByAttr("class",
                    "pdf-page-footer");
            blocks = parseBlocks(pageFooterNode);
            rePage.setPageFooter(blocks);
            rePage.setDisplayPageNo(displayPageNo);
            rePage.setPageNo(pageNo);

            pages.add(rePage);
        }
        doc.setFileName(titleValue);
        doc.setPages(pages);

        // 添加目录内容
        XNode tocNode = bodyTag.childByAttr("class", "pdf-toc");
        TocTable tocTable = parseToc(tocNode, doc);

        doc.setTocTable(tocTable);
        return doc;
    }

    // 对目录的解析
    private TocTable parseToc(XNode tocNode, ResourceDocument doc) {
        if (tocNode == null)
            return null;
        TocTable tocTable = new TocTable();
        List<TocItem> tocItems = new ArrayList<TocItem>();
        TocItem tocItem;
        for (XNode liNode : tocNode.getChildren()) {
            tocItem = new TocItem();
            XNode aNode = liNode.childByTag("a");
            String hrefValue = aNode.attrText("href");
            int pageIndex = Integer.parseInt(hrefValue.substring(2));
            String title = aNode.contentText();
            tocItem.setTitle(title);

            ResourcePage page = doc.getPages().get(pageIndex);
            tocItem.setPageNo(page.getDisplayPageNo());

            tocItems.add(tocItem);
        }
        tocTable.setItems(tocItems);
        return tocTable;
    }

    // 对page的通用解析
    private List<Block> parseBlocks(XNode node) {
        List<Block> blocks = new ArrayList<>();
        for (XNode child : node.getChildren()) {
            String idParam = child.attrText("id");
            int pageBlockIndex = Integer.parseInt(idParam.substring(idParam
                    .indexOf("-") + 1));
            int pageNo = Integer.parseInt(idParam.substring(1,
                    idParam.indexOf("-")));
            String className = child.attrText("class");
            Block block;
            if ("pdf-line".equals(className)) {
                TextlineBlock textBlock = new TextlineBlock();
                String content = child.text();
                // 设置内容
                textBlock.setContent(content);
                block = textBlock;
            } else if ("pdf-image".equals(className)) {
                ImageBlock image = new ImageBlock();
                DummyImageReference ref = new DummyImageReference();
                String idVal = child.attrText("id");
                int blockIdx = Integer.parseInt(idVal.substring(idVal.lastIndexOf("-") + 1));
//				
//				XNode imgNode = child.childByTag("img");
//				String src = imgNode.attribute("src").stringValue();
//				String imgType = src.substring(src.lastIndexOf(".") + 1);
//				String docUrl = node.getDocUrl();
//				String startUrl = docUrl.substring(0,
//						docUrl.lastIndexOf("/") + 1);
//				
                image.setReference(ref);
                image.setPageNo(pageNo);
                image.setPageBlockIndex(blockIdx);
                block = image;
            } else if ("pdf-table".equals(className)) {
                block = parseTable(child, pageNo);
            } else {
                continue;
            }
            block.setPageBlockIndex(pageBlockIndex);
            block.setPageNo(pageNo);
            blocks.add(block);
        }
        return blocks;
    }

    List<Double> toDoubleList(List<String> list) {
        if (list == null)
            return new ArrayList<Double>(0);
        List<Double> ret = new ArrayList<Double>(list.size());
        for (String s : list) {
            ret.add(Double.parseDouble(s));
        }
        return ret;
    }

    private TableBlock parseTable(XNode child, int pageNo) {
        TableBlock table = new TableBlock();
        List<String> xpts = child.attrCsvList("x-pt");
        List<String> ypts = child.attrCsvList("y-pt");

        // 添加g的内容
        //Map<String, TableCellBlock> cells = new LinkedHashMap<String, TableCellBlock>();
        //table.setCells(cells);
        // 设置行数和列数
        //int rowCount = 0;
        int colCount = 0;
        for (XNode trNode : child.getChildren()) {
            //rowCount++;
            int temp = 0;
            for (XNode tdNode : trNode.getChildren()) {
                int rowIdx = tdNode.attrInt("r", 0);
                int colIdx = tdNode.attrInt("c", 0);
                String content = tdNode.text();
                int rowSpan = tdNode.attrInt("rowspan", 1);
                int colSpan = tdNode.attrInt("colspan", 1);
                if (rowSpan <= 0)
                    rowSpan = 1;
                if (colSpan <= 0)
                    colSpan = 1;

                TableCellBlock cell = new TableCellBlock(rowIdx, colIdx, 2, 2);
                String idVal = tdNode.attrText("id");
                int blockIdx = Integer.parseInt(idVal.substring(idVal
                        .lastIndexOf("-") + 1));
                cell.setPageNo(pageNo);
                cell.setPageBlockIndex(blockIdx);
                cell.setContent(content);
                cell.setRowspan(rowSpan);
                cell.setColspan(colSpan);
                table.addCell(rowIdx, colIdx, cell);
                temp++;
            }
            if (temp > colCount)
                colCount = temp;
        }
        table.setColCount(colCount);
        table.setXpoints(toDoubleList(xpts));
        table.setYpoints(toDoubleList(ypts));
        //table.setRowCount(rowCount);
        //table.setColCount(colCount);
        return table;
    }
}