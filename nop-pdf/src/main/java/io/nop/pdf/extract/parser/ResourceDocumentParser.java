package io.nop.pdf.extract.parser;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.pdf.extract.IResourceDocumentParser;
import io.nop.pdf.extract.IResourceDocumentProcessor;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.cmp.LineBasedTextBlockComparator;
import io.nop.pdf.extract.dashline.DashlineDetector;
import io.nop.pdf.extract.dashline.DefaultDashLineDetector;
import io.nop.pdf.extract.processor.DefaultPageFooterProcessor;
import io.nop.pdf.extract.processor.DefaultPageHeaderProcessor;
import io.nop.pdf.extract.processor.DefaultTocTableProcessor;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.ShapeBlock;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import io.nop.pdf.extract.struct.TextBlock;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.pdf.extract.table.DefaultTableDetector;
import io.nop.pdf.extract.table.TableDetector;
import io.nop.pdf.tabula.QuickSort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ResourceDocumentParser implements IResourceDocumentParser {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceDocumentParser.class);

    private boolean exportPageToImage = false;

    //private int minImageArea = 64;

    private IResource mPDFFile = null;
    private PDDocument mPDFDocument = null;
    private int mStartPageNo = -1;
    private int mEndPageNo = -1;

    private DashlineDetector mDashlineDetector;

    private TableDetector mTableDetector;

    private ExtractStripper mResourceStripper;

    /**
     * 整个pdf文档的文本内容
     */
    private String mAllTextContent;

    /**
     * 整个pdf文档的文本内容(按行存储)
     */
    private List<String> mAllTextLines = new ArrayList<String>();

    /**
     * 所有解析出来的页面
     */
    private Map<Integer, ResourcePage> mAllPages = new HashMap<Integer, ResourcePage>();

    private ResourceParseConfig config;

    private List<IResourceDocumentProcessor> processors = new ArrayList<IResourceDocumentProcessor>();

    public ResourceDocumentParser(ResourceParseConfig config) {

        this.config = config;

        try {
            ExtractStripper stripper = new ExtractStripper(new ThisCallback(), config);
            this.setResourceStripper(stripper);
        } catch (IOException e) {
        }

        this.setTableDetector(new DefaultTableDetector());

        DashlineDetector dashTetector = new DefaultDashLineDetector();

        this.setDashlineDetector(dashTetector);

        this.addPostProcessor(new DefaultPageFooterProcessor());
        this.addPostProcessor(new DefaultPageHeaderProcessor());
        this.addPostProcessor(new DefaultTocTableProcessor());
    }

    public void addPostProcessor(IResourceDocumentProcessor processor) {
        this.processors.add(processor);
    }

    public DashlineDetector getDashlineDetector() {
        return mDashlineDetector;
    }

    public void setDashlineDetector(DashlineDetector dashlineDetector) {
        this.mDashlineDetector = dashlineDetector;
    }

    public TableDetector getTableDetector() {
        return mTableDetector;
    }

    public void setTableDetector(TableDetector tableDetector) {
        this.mTableDetector = tableDetector;
    }

    public ExtractStripper getResourceStripper() {
        return mResourceStripper;
    }

    public void setResourceStripper(ExtractStripper resourceStripper) {

        this.mResourceStripper = resourceStripper;
    }

    @Override
    public ResourceDocument parseFromResource(IResource resource) {

        LOG.info("pdf.parse_resource:{}", resource.getPath());

        this.mPDFFile = resource;
        try {
            open();

            int startPageNo = config.getStartPageNo();
            int endPageNo = config.getEndPageNo();

            if (startPageNo <= 0) {
                startPageNo = 1;
            }

            if (endPageNo <= 0) {
                endPageNo = this.mPDFDocument.getNumberOfPages();
            }

            this.extract(startPageNo, endPageNo);

            ResourceDocument doc = new ResourceDocument();
            doc.setFileName(resource.getName());
            doc.setPages(this.getExtractedPages());

            for (IResourceDocumentProcessor processor : this.processors) {
                processor.process(doc, config);
            }

            return doc;
        } catch (Exception e) {
            throw NopException.wrap(e);
        } finally {
            close();
        }
    }

    /**
     * 打开
     *
     * @throws IOException
     * @throws InvalidPasswordException
     */
    public void open() throws IOException, InvalidPasswordException {

        MemoryUsageSetting settings = null;
        if (this.config.isMemoryRestrictEnabled()) {
            settings = MemoryUsageSetting.setupMixed(this.config.getMemoryRestrictSize());
        }

        File file = mPDFFile.toFile();
        if (file != null) {
            if (this.config.isMemoryRestrictEnabled()) {
                mPDFDocument = Loader.loadPDF(file);// file, settings );
            } else mPDFDocument = Loader.loadPDF(file);
        } else {
            InputStream is = mPDFFile.getInputStream();
            try {
                if (this.config.isMemoryRestrictEnabled()) {
                    mPDFDocument = Loader.loadPDF(IoHelper.readBytes(is)); //is, settings);
                }
                mPDFDocument = Loader.loadPDF(IoHelper.readBytes(is));
            } finally {
                IoHelper.safeClose(is);
            }
        }
    }

    /**
     * 关闭，释放资源
     */
    public void close() {

        if (this.mPDFDocument != null) {

            try {
                this.mPDFDocument.close();
            } catch (IOException e) {
            }
        }
        this.mPDFDocument = null;
    }

    /**
     * @param pageNo
     * @return
     */
    public ResourcePage getPageByNo(int pageNo) {

        ResourcePage page = mAllPages.get(pageNo);
        return page;
    }

    /**
     * 返回所有的页
     *
     * @return
     */
    public List<ResourcePage> getExtractedPages() {

        List<ResourcePage> pages = new ArrayList<ResourcePage>();

        for (int i = mStartPageNo; i <= mEndPageNo; i++) {

            ResourcePage page = this.getPageByNo(i);

            pages.add(page);
        }

        return pages;
    }

    /**
     * 解析所有的页
     *
     * @throws IOException
     */
    public void extract() throws IOException {

        int count = mPDFDocument.getNumberOfPages();

        this.extract(1, count);
    }

    /**
     * 解析指定页码范围内的页
     *
     * @param startPageNo
     * @param endPageNo
     * @throws IOException
     */
    public void extract(int startPageNo, int endPageNo) throws IOException {

        this.mAllTextContent = null;
        this.mAllTextLines.clear();
        this.mAllPages.clear();

        this.mStartPageNo = startPageNo;
        this.mEndPageNo = endPageNo;

        if (config.isIncludeRawText()) {
            tryParseAllText();
        }

        tryParsePages();
    }

    /**
     * 导出所有解析的内容到指定目录
     *
     * @param dir
     * @throws IOException
     */
    public void exportTo(String dir, boolean skipSmallImage) throws IOException {

        File thisFile = mPDFFile.toFile();
        String baseName = thisFile.getName();

        this.exportAllTextToFile(new File(dir, baseName + ".txt").getPath());
        this.exportAllTextlinesToFile(new File(dir, baseName + ".lines.txt").getPath());
        this.exportAllPageToHtml(baseName, new File(dir, baseName + ".html").getPath(), skipSmallImage);

        File imagesDir = new File(dir, baseName + ".resources");
        imagesDir.mkdirs();
        this.exportAllImages(imagesDir.getPath(), skipSmallImage);
    }

    private void exportAllTextToFile(String file) throws IOException {

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
        try {
            writer.write(mAllTextContent);
        } finally {
            writer.close();
        }
    }

    private void exportAllTextlinesToFile(String file) throws IOException {

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
        try {
            for (int i = mStartPageNo; i <= mEndPageNo; i++) {

                ResourcePage page = this.getPageByNo(i);
                if (page == null) continue;

                List<TextlineBlock> textlines = page.getTextlines();
                if (textlines == null) continue;

                for (int j = 0; j < textlines.size(); j++) {
                    writer.write(textlines.get(j).getContent());
                    writer.write("\r\n");
                }
            }
        } finally {
            writer.close();
        }
    }

    /**
     * 导出所有的页到一个html
     *
     * @param skipSmallImage 是否忽略很小的图片
     * @throws IOException
     */
    private void exportAllPageToHtml(String baseName, String htmlFile, boolean skipSmallImage) throws IOException {

        // DefaultResourceHtmlWriter writer = new DefaultResourceHtmlWriter( baseName, htmlFile );
        // writer.writeDoc( this.getExtractedPages() );

    }

    /**
     * 导出所有的图片
     *
     * @param dir
     * @param skipSmallImage 是否忽略很小的图片
     * @throws IOException
     */
    private void exportAllImages(String dir, boolean skipSmallImage) throws IOException {

        for (int i = mStartPageNo; i <= mEndPageNo; i++) {

            ResourcePage page = this.getPageByNo(i);
            if (page == null) continue;

            List<ImageBlock> images = page.getImageBlocks();
            if (images == null) continue;

            for (int j = 0; j < images.size(); j++) {

                ImageBlock block = images.get(j);
                BufferedImage bi = block.getReference().getBufferedImage();

                if (skipSmallImage) {
                    int area = bi.getWidth() * bi.getHeight();
                    if (area < config.getMinImageArea()) continue;
                }

                long id = block.getPageBlockIndex();
                String fileName = "page-" + i + "-" + id + ".jpg";
                ImageIO.write(bi, "jpg", new File(dir, fileName));
            }

        }
    }

    private ResourcePage getOrCreatePage(int pageNo) {

        ResourcePage page = mAllPages.get(pageNo);
        if (page == null) {

            page = new ResourcePage();
            page.setPageNo(pageNo);
            mAllPages.put(pageNo, page);
        }

        return page;
    }

    private void tryParseAllText() throws IOException {

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(mStartPageNo);
        stripper.setEndPage(mEndPageNo);

        mAllTextContent = stripper.getText(mPDFDocument);

        String[] splits = mAllTextContent.split("\r\n");
        mAllTextLines = new ArrayList<String>();
        for (int i = 0; i < splits.length; i++) {

            String line = splits[i];
            mAllTextLines.add(line);
        }
    }

    /**
     * 解析所有页
     *
     * @throws IOException
     */
    private void tryParsePages() throws IOException {

        for (int i = mStartPageNo; i <= mEndPageNo; i++) {

            tryParsePage(i);
        }
    }

    /**
     * 解析指定页的所有资源
     *
     * @param pageNo
     * @throws IOException
     */
    private void tryParsePage(int pageNo) throws IOException {

        LOG.info("parsing page {}", pageNo);

        ResourcePage page = this.getOrCreatePage(pageNo);

        //按简单方式统一处理一遍文本
        if (config.isIncludeRawText()) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            textStripper.setStartPage(pageNo);
            textStripper.setEndPage(pageNo);
            String content = textStripper.getText(mPDFDocument);
            page.setAllTextContent(content);
        }

        if (this.config.isExportPageImage()) {
            //逐字符处理
            String pageImageFile = mPDFFile + "-" + pageNo + ".png";
            this.mResourceStripper.stripPage(mPDFDocument, pageNo, exportPageToImage ? pageImageFile : null);
        } else {
            this.mResourceStripper.stripPage(mPDFDocument, pageNo, null);
        }

        //重建表格
        rebuildTablesInPage(page);

        //重建页眉，页脚

        //重建普通文本行
        rebuildTextLinesInPage(page);

        page.simplify();

        page.resetAllBlockIndex();
    }


    private void rebuildTablesInPage(ResourcePage page) {

        List<ShapeBlock> shapes = new ArrayList<ShapeBlock>();
        shapes.addAll(page.getShapeBlocks());

        List<ImageBlock> images = page.getImageBlocks();
        List<ShapeBlock> dashlines = this.mDashlineDetector.process(images);
        shapes.addAll(dashlines);

        List<TableBlock> tables = mTableDetector.process(shapes);
        for (int i = 0; i < tables.size(); i++) {

            TableBlock table = tables.get(i);
            if (table.getRowCount() < 1 || table.getColCount() < 1) continue;

            rebuildTable(table, page.getCharBlocks());
            page.addTable(table);
        }
    }

    /**
     * 重建指定页内的文本行
     *
     * @param page
     */
    private void rebuildTextLinesInPage(ResourcePage page) {

        List<TableBlock> tableBlocks = page.getTables();
        List<TextBlock> charBlocks = page.getCharBlocks();

        //过滤出普通的文本字符(不在表格内)
        List<TextBlock> list = new ArrayList<TextBlock>();
        for (int i = 0; i < charBlocks.size(); i++) {

            TextBlock charBlock = charBlocks.get(i);

            Rectangle2D charRect = charBlock.getViewBounding();

            boolean intersectWithTable = false;
            for (int j = 0; j < tableBlocks.size(); j++) {

                Rectangle2D tableRect = tableBlocks.get(j).getViewBounding();

                if (charRect.intersects(tableRect)) {
                    intersectWithTable = true;
                    break;
                }
            }

            if (!intersectWithTable) {
                list.add(charBlock);
            }
        }

        if (list.size() < 1) return;

        //排序：从上到下，从左到右
        // JDK1.7的排序要求comparator必须满足传递性, 这里使用PDFBox提供的安全版本
        QuickSort.sort(list, new LineBasedTextBlockComparator());

        //合并y坐标相近的字符为行
        TextBlock block0 = list.get(0);
        Rectangle2D rect0 = block0.getViewBounding();
        double lineStartY = rect0.getMinY();
        double lineStartX = rect0.getMinX();
        double lineWidth = rect0.getWidth();
        double lineHeight = rect0.getHeight();

        StringBuilder lastLineBuilder = new StringBuilder();
        FontSizeCounter lastFontCounter = new FontSizeCounter();
        lastLineBuilder.append(block0.getText());
        lastFontCounter.onFontSize(block0.getViewFontSize());

        for (int i = 1; i < list.size(); i++) {

            TextBlock charBlock = list.get(i);

            double x = charBlock.getViewBounding().getMinX();
            double y = charBlock.getViewBounding().getMinY();

            if (y - lineStartY < lineHeight * 0.5) {
                lastLineBuilder.append(charBlock.getText());
                lastFontCounter.onFontSize(charBlock.getViewFontSize());
                lineWidth = charBlock.getViewBounding().getMaxX() - lineStartX;
                continue;
            }

            //当前行结束了
            TextlineBlock textLine = new TextlineBlock();
            textLine.setContent(lastLineBuilder.toString());
            textLine.setFontSize(lastFontCounter.getFontSize());
            textLine.setViewBounding(new Rectangle2D.Double(lineStartX, lineStartY, lineWidth, lineHeight));
            page.addTextline(textLine);

            //新起一行
            lineStartY = y;
            lineStartX = x;
            lineHeight = charBlock.getViewBounding().getHeight();
            lastLineBuilder = new StringBuilder();
            lastFontCounter = new FontSizeCounter();
            lastLineBuilder.append(charBlock.getText());
            lastFontCounter.onFontSize(charBlock.getViewFontSize());
        }

        String content = StringHelper.strip(lastLineBuilder.toString());
        if (content != null) {
            //结束最后一行
            TextlineBlock textLine = new TextlineBlock();
            textLine.setContent(content);
            textLine.setFontSize(lastFontCounter.getFontSize());
            textLine.setViewBounding(new Rectangle2D.Double(lineStartX, lineStartY, lineWidth, lineHeight));
            page.addTextline(textLine);
        }
    }

    private boolean isSameLine(Block a, Block b) {
        double y1 = a.getViewBounding().getMinY();
        double y2 = b.getViewBounding().getMinY();

        double lineHeight = Math.max(a.getViewBounding().getHeight(), b.getViewBounding().getHeight());

        if (Math.abs(y2 - y1) < lineHeight * 0.5) {
            return true;
        }
        return false;
    }

    /**
     * 重建表格文本内容
     *
     * @param table
     * @param charBlocks
     */
    private void rebuildTable(TableBlock table, List<TextBlock> charBlocks) {

        for (int p = 0; p < table.getRowCount(); p++) {

            for (int q = 0; q < table.getColCount(); q++) {

                //  String key = "" + p + "," + q;

                // if( table.getCells().containsKey( key ) ) {

                TableCellBlock cell = table.getCell(p, q);
                if (cell == null)
                    continue;

                String text = this.rebuildTextInRect(cell.getViewBounding(), charBlocks);
                cell.content = text;
                //}
            }
        }
    }

    /**
     * 重建制定区域内的文本内容
     *
     * @param rect
     * @param charBlocks
     * @return
     */
    public String rebuildTextInRect(Rectangle2D rect, List<TextBlock> charBlocks) {

        List<TextBlock> list = new ArrayList<TextBlock>();

        for (int i = 0; i < charBlocks.size(); i++) {

            TextBlock cblk = charBlocks.get(i);

            if (rect.intersects(cblk.getViewBounding())) {

                list.add(cblk);
            }
        }

        //TODO: 外部可以选择Comparator
        //Collections.sort( list, new TLBased1TextBlockComparator() );
        // 为避免comparator不支持传递律而报错
        QuickSort.sort(list, new LineBasedTextBlockComparator());

        TextBlock prevBlock = null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            TextBlock block = list.get(i);
            // 保留单元格内文本的行结构
            if (prevBlock != null) {
                if (!isSameLine(prevBlock, block)) {
                    sb.append("\r\n");
                }
            }
            sb.append(block.getText());

            prevBlock = block;
        }

        String text = StringHelper.strip(sb.toString());
        if (text == null)
            text = "";
        return text;
    }

    private class ThisCallback implements IExtractStripperCallback {

        int pageBlockIndex;

        @Override
        public void onPageBegin(int pageIndex, float pageWidth, float pageHeight, float viewScale) {
            LOG.debug("page index={} begin", pageIndex);

            int pageNo = pageIndex + 1;
            ResourcePage page = getOrCreatePage(pageNo);

            Rectangle2D rect = new Rectangle2D.Double(0, 0, pageWidth * viewScale, pageHeight * viewScale);
            page.setPageViewBounding(rect);

            pageBlockIndex = 0;
        }

        @Override
        public void onPageEnd(int pageIndex) {
            LOG.debug("page index={} end", pageIndex);
        }

        @Override
        public void onTextBlockBegin(int pageIndex, TextBlock textBlock) {

            textBlock.setPageBlockIndex(pageBlockIndex++);
        }

        @Override
        public void onCharBlock(int pageIndex, TextBlock charBlock) {

            charBlock.setPageBlockIndex(pageBlockIndex++);

            ResourcePage page = getOrCreatePage(pageIndex + 1);

            if (LOG.isTraceEnabled()) {

                Rectangle2D rect = charBlock.getViewBounding();
                LOG.trace("page {} char {} at( {},{} - {},{} ), char={} fontsize={}",
                        charBlock.getPageNo(), charBlock.getPageBlockIndex(),
                        rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight(),
                        charBlock.getText(),
                        charBlock.getViewFontSize()
                );
            }

            if (charBlock.getViewBounding().getMinX() >= charBlock.getViewBounding().getMaxX()) {
                LOG.warn("char block invalid: {} ", charBlock);
                return;
            }

            if (charBlock.getViewBounding().getMinY() >= charBlock.getViewBounding().getMaxY()) {
                LOG.warn("char block invalid: {} ", charBlock);
                return;
            }

            page.addCharBlocks(charBlock);
        }

        @Override
        public void onTextBlockEnd(int pageIndex, TextBlock textBlock) {

            //ResourcePage page = getOrCreatePage( pageIndex + 1 );

            // page.addPhraseBlock( textBlock );
        }

        @Override
        public void onFillShape(int pageIndex, ShapeBlock shapeBlock) {

            shapeBlock.setPageBlockIndex(pageBlockIndex++);

            ResourcePage page = getOrCreatePage(pageIndex + 1);

            page.addShapeBlock(shapeBlock);
        }

        @Override
        public void onDrawImage(int pageIndex, ImageBlock imageBlock) {

            imageBlock.setPageBlockIndex(pageBlockIndex++);

            int pageNo = pageIndex + 1;
            ResourcePage page = getOrCreatePage(pageNo);

            imageBlock.setPageNo(pageNo);
            page.addImageBlock(imageBlock);

            if (LOG.isTraceEnabled()) {

                Rectangle2D rect = imageBlock.getViewBounding();

                LOG.trace("page {} image {} at( {},{} - {},{} )",
                        imageBlock.getPageNo(),
                        imageBlock.getPageBlockIndex(),
                        rect.getX(), rect.getY(),
                        rect.getWidth(), rect.getHeight()
                );
            }
        }
    }

    ;

}
