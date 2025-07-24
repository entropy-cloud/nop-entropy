package io.nop.image.utils;

import io.nop.commons.util.StringHelper;

import java.util.HashMap;
import java.util.Map;

public class ImageTypeMap {
    public static final ImageTypeMap INSTANCE = new ImageTypeMap();

    private final Map<String, String> fileExtToMimeType = new HashMap<>();
    private final Map<String, String> mimeTypeToFormatName = new HashMap<>();
    private final Map<String, String> formatNameToMimeType = new HashMap<>();

    public ImageTypeMap() {
        // 常见扩展名 <-> MIME type <-> formatName
        registerAll("jpg", "image/jpeg", "jpg");
        registerAll("jpeg", "image/jpeg", "jpg");
        registerAll("png", "image/png", "png");
        registerAll("gif", "image/gif", "gif");
        registerAll("bmp", "image/bmp", "bmp");
        registerAll("wbmp", "image/vnd.wap.wbmp", "wbmp");
        registerAll("webp", "image/webp", "webp");
        registerAll("tif", "image/tiff", "tiff");
        registerAll("tiff", "image/tiff", "tiff");
        registerAll("svg", "image/svg+xml", "svg");
        registerAll("ico", "image/x-icon", "ico");

        registerAll("emf", "image/emf", "emf");

        registerAll("pcx", "image/x-pcx", "pcx");     // Paintbrush
        registerAll("psd", "image/vnd.adobe.photoshop", "psd"); // Photoshop格式
        registerAll("heic", "image/heic", "heic");   // 高效图像
        registerAll("heif", "image/heif", "heif");
        registerAll("jfif", "image/jfif", "jfif");   // JPEG File Interchange Format
        registerAll("dds", "image/vnd.ms-dds", "dds"); // DirectDraw Surface，游戏开发常见
        registerAll("exr", "image/aces", "exr");     // OpenEXR，高动态范围图像
        registerAll("ras", "image/x-cmu-raster", "ras");
        registerAll("ppm", "image/x-portable-pixmap", "ppm"); // PPM, PGM, PBM 系列
        registerAll("pgm", "image/x-portable-graymap", "pgm");
        registerAll("pbm", "image/x-portable-bitmap", "pbm");
        registerAll("xbm", "image/x-xbitmap", "xbm");
        registerAll("xpm", "image/x-xpixmap", "xpm");
        registerAll("svgz", "image/svg+xml", "svgz"); // 压缩SVG
        registerAll("cur", "image/x-icon", "cur");    // 鼠标指针，和ico类似
        registerAll("thm", "image/jpeg", "thm");      // Thumbnail
    }

    private void registerAll(String ext, String mimeType, String formatName) {
        fileExtToMimeType.put(ext.toLowerCase(), mimeType);
        mimeTypeToFormatName.put(mimeType.toLowerCase(), formatName.toLowerCase());
        formatNameToMimeType.put(formatName.toLowerCase(), mimeType);
    }

    public void addContentTypeMapping(String fileExt, String mimeType) {
        fileExtToMimeType.put(fileExt, mimeType);
    }

    public void addMapping(String ext, String mimeType, String formatName) {
        registerAll(ext, mimeType, formatName);
    }

    // 根据文件扩展名获取 MIME type
    public String getMimeTypeByExtension(String ext) {
        if (ext == null) return null;
        return fileExtToMimeType.get(ext.toLowerCase());
    }

    // 根据 fileName 获取 MIME type
    public String getMimeTypeByFileName(String fileName) {
        if (fileName == null) return null;
        String ext = StringHelper.fileExt(fileName);
        return getMimeTypeByExtension(ext);
    }

    // 根据 MIME type 获取 formatName（给ImageIO用）
    public String getFormatNameByMimeType(String mimeType) {
        if (mimeType == null) return null;
        return mimeTypeToFormatName.get(mimeType.toLowerCase());
    }

    // 根据 formatName 获取 MIME type
    public String getMimeTypeByFormatName(String formatName) {
        if (formatName == null) return null;
        return formatNameToMimeType.get(formatName.toLowerCase());
    }

    // 根据 fileName 推断 formatName
    public String getFormatNameByFileName(String fileName) {
        String mime = getMimeTypeByFileName(fileName);
        return getFormatNameByMimeType(mime);
    }
}
