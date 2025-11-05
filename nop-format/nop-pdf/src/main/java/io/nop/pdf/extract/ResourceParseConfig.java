package io.nop.pdf.extract;

import io.nop.pdf.extract.parser.MemoryImageHandler;
import io.nop.api.core.config.AppConfig;

import java.io.File;

public class ResourceParseConfig {
    /**
     * 是否解析图片
     */
    boolean includeImage = true;

    /**
     * 是否解析文本
     */
    boolean includeText = true;

    /**
     * 是否解析表格
     */
    boolean includeTable = true;

    /**
     * 是否解析PDFBox内置的文本解析器得到的文本对象
     */
    boolean includeRawText = AppConfig.var("pdf.default_parse_include_raw_text", false);

    /**
     * 是否将每一页内容导出为一个图片
     */
    boolean exportPageImage = false;

    boolean drawText = false;

    /**
     * 所有小于该面积的图片不保存在最终的解析结果中
     **/
    int minImageArea = 64;

    int minExportImageArea = 64;

    float imageScale = 4;

    /**
     * 从哪一页开始解析, 从1开始计算
     */
    int startPageNo = -1;

    /**
     * 解析到哪一页结束，包含此页
     */
    int endPageNo = -1;

    /**
     * 解析时图片等资源文件保存路径
     */
    File workDir;

    String tocStartMark;
    String tocEndMark;

    /**
     * 是否在解析图片时对图片进行灰度化处理，以便节省内存
     */
    private boolean grayImageEnabled = true;

    /**
     * 灰度化像素数阈值，当grayImageEnabled为true,且图片像素数（宽高之积）超过此值时，进行灰度化处理
     * 灰度图内存占用 = w * h * 1
     */
    private int grayImagePixels = 1024 * 1024;

    /**
     * 是否在解析图片时对图片进行缩小处理，以便节省内存
     */
    private boolean resizeImageEnabled = true;

    /**
     * 压缩处理像素数阈值，当resizeImageEnabled为true,且图片像素数（宽高之积）超过此值时，进行缩小处理
     * <p>
     * 图片内存占用 = w * h * 4
     */
    private int resizeImagePixels = 1536 * 1536;

    /**
     * 是否启用内存限制
     */
    private boolean memoryRestrictEnabled = true;

    /**
     * 限制内存使用量(字节数)
     */
    private long memoryRestrictSize = 256 * 1024 * 1024L;

    IResourceImageHandler imageHandler = MemoryImageHandler.INSTANCE;

    public boolean isExportPageImage() {
        return exportPageImage;
    }

    public void setExportPageImage(boolean exportPageImage) {
        this.exportPageImage = exportPageImage;
    }

    public int getMinImageArea() {
        return minImageArea;
    }

    public void setMinImageArea(int minImageArea) {
        this.minImageArea = minImageArea;
    }

    public int getMinExportImageArea() {
        return minExportImageArea;
    }

    public void setMinExportImageArea(int minExportImageArea) {
        this.minExportImageArea = minExportImageArea;
    }

    public float getImageScale() {
        return imageScale;
    }

    public void setImageScale(float imageScale) {
        this.imageScale = imageScale;
    }

    public IResourceImageHandler getImageHandler() {
        return imageHandler;
    }

    public void setImageHandler(IResourceImageHandler imageHandler) {
        this.imageHandler = imageHandler;
    }

    public File getWorkDir() {
        return workDir;
    }

    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }

    public boolean isIncludeImage() {
        return includeImage;
    }

    public void setIncludeImage(boolean includeImage) {
        this.includeImage = includeImage;
    }

    public boolean isIncludeText() {
        return includeText;
    }

    public void setIncludeText(boolean includeText) {
        this.includeText = includeText;
    }

    public boolean isIncludeTable() {
        return includeTable;
    }

    public void setIncludeTable(boolean includeTable) {
        this.includeTable = includeTable;
    }

    public boolean isIncludeRawText() {
        return includeRawText;
    }

    public void setIncludeRawText(boolean includeRawText) {
        this.includeRawText = includeRawText;
    }

    public int getStartPageNo() {
        return startPageNo;
    }

    public void setStartPageNo(int startPage) {
        this.startPageNo = startPage;
    }

    public int getEndPageNo() {
        return endPageNo;
    }

    public void setEndPageNo(int endPage) {
        this.endPageNo = endPage;
    }

    public boolean isDrawText() {
        return drawText;
    }

    public void setDrawText(boolean drawText) {
        this.drawText = drawText;
    }

    public String getTocStartMark() {
        return tocStartMark;
    }

    public void setTocStartMark(String tocStartMark) {
        this.tocStartMark = tocStartMark;
    }

    public String getTocEndMark() {
        return tocEndMark;
    }

    public void setTocEndMark(String tocEndMark) {
        this.tocEndMark = tocEndMark;
    }

    public boolean isGrayImageEnabled() {
        return grayImageEnabled;
    }

    public void setGrayImageEnabled(boolean grayImageEnabled) {
        this.grayImageEnabled = grayImageEnabled;
    }

    public int getGrayImagePixels() {
        return grayImagePixels;
    }

    public void setGrayImagePixels(int grayImagePixels) {
        this.grayImagePixels = grayImagePixels;
    }

    public boolean isResizeImageEnabled() {
        return resizeImageEnabled;
    }

    public void setResizeImageEnabled(boolean resizeImageEnabled) {
        this.resizeImageEnabled = resizeImageEnabled;
    }

    public int getResizeImagePixels() {
        return resizeImagePixels;
    }

    public void setResizeImagePixels(int resizeImagePixels) {
        this.resizeImagePixels = resizeImagePixels;
    }

    public boolean isMemoryRestrictEnabled() {
        return memoryRestrictEnabled;
    }

    public void setMemoryRestrictEnabled(boolean memoryRestrictEnabled) {
        this.memoryRestrictEnabled = memoryRestrictEnabled;
    }

    public long getMemoryRestrictSize() {
        return memoryRestrictSize;
    }

    public void setMemoryRestrictSize(long memoryRestrictSize) {
        this.memoryRestrictSize = memoryRestrictSize;
    }


}