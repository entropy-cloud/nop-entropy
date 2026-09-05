/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.font;

import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.report.pdf.ReportPdfConfigs;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FontManager {
    static final Logger LOG = LoggerFactory.getLogger(FontManager.class);

    static final FontManager _instance = new FontManager();

    /**
     * CJK字形探测字符：判断一个字体能否作为中文回退字体
     */
    static final String CJK_PROBE_CHAR = "中";

    private final Map<String, PDFont> systemFonts = new CaseInsensitiveMap<>();
    private final Map<String, String> fontNameAliases = new HashMap<>();

    /**
     * 系统字体目录中发现的字体文件：规范化文件名（去扩展名、小写、去空格）-> 文件
     */
    private volatile Map<String, File> discoveredFontFiles;

    /**
     * 已解析的CJK回退字体文件（JVM级缓存，避免每个文档重复探测）
     */
    private volatile File fallbackFontFile;

    /**
     * 每个字体实例无法编码的码点缓存（PDFont实例与PDDocument绑定，实例消亡后缓存条目随之失效）
     */
    private final Map<PDFont, Set<Integer>> unencodableChars = Collections.synchronizedMap(new HashMap<>());

    private PDFont defaultFont;
    private IResource defaultFontResource;
    private boolean inited;

    public static FontManager instance() {
        return _instance;
    }

    private void registerSystemFonts() {
        for (Standard14Fonts.FontName fontName : Standard14Fonts.FontName.values()) {
            systemFonts.put(fontName.getName(), new PDType1Font(fontName));
        }

        systemFonts.put("Times New Roman", new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN));
        systemFonts.put("Helvetica-BoldItalic", new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE));
        systemFonts.put("Courier-BoldItalic", new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE));
        systemFonts.put("Helvetica-Italic", new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
        systemFonts.put("Courier-Italic", new PDType1Font(Standard14Fonts.FontName.COURIER_OBLIQUE));
    }

    public FontManager() {

    }

    protected synchronized void init() {
        if (inited)
            return;

        try {
            registerSystemFonts();

            IResource resource = getFontResource("default", false, false);
            if (resource != null && resource.exists()) {
                defaultFontResource = resource;
            }

            // Try to use Helvetica as default font in PDFBox
            defaultFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        } catch (Exception e) {
            LOG.error("nop.pdf.create-base-font-fail", e);
        }
        inited = true;
    }

    public PDFont getDefaultFont() {
        init();
        if (defaultFont == null) {
            // init过程中创建失败时兜底重试，避免返回null字体
            try {
                defaultFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            } catch (Exception e) {
                LOG.error("nop.pdf.create-base-font-fail", e);
            }
        }
        return defaultFont;
    }

    public boolean hasDefaultFontResource() {
        init();
        return defaultFontResource != null;
    }

    public void addFontAlias(String alias, String fontName) {
        fontNameAliases.put(alias, fontName);
    }

    public PDFont getFont(String fontName, boolean bold, boolean italic, PDDocument doc) {
        init();

        // 1. 获取基础字体名称（处理可能为null的情况）
        if (fontName == null)
            fontName = "Helvetica";

        PDFont font = getSystemFont(fontName, bold, italic);
        if (font != null) {
            return font;
        }

        // 4. 尝试加载字体
        font = loadFont(fontName, bold, italic, doc);
        if (font != null) {
            return font;
        }
        // 6. 回退到默认字体
        LOG.warn("nop.pdf.font-not-found:fontName={}, using default font", fontName);
        return getDefaultFont();
    }

    public PDFont getSystemFont(String fontName, boolean bold, boolean italic) {
        String fullName = getFontFullName(fontName, bold, italic);
        PDFont font = systemFonts.get(fullName);
        if (font == null)
            font = systemFonts.get(fontName);
        return font;
    }

    public String getFontFullName(String fontName, boolean bold, boolean italic) {
        String fullName = fontName.replace(' ', '-');
        if (bold && italic) {
            fullName += "-BoldItalic";
        } else if (bold) {
            fullName += "-Bold";
        } else if (italic) {
            fullName += "-Italic";
        }
        return fullName;
    }

    private PDFont loadFont(String fontName, boolean bold, boolean italic, PDDocument doc) {

        IResource fontResource = getFontResource(fontName, bold, italic);
        if (fontResource == null)
            fontResource = defaultFontResource;

        if (fontResource != null) {
            PDFont font = loadFromResource(fontResource, doc);
            if (font != null)
                return font;
        }

        // VFS中没有对应字体文件时，从配置目录与系统字体目录中发现
        File file = findDiscoveredFont(getFontFullName(fontName, bold, italic));
        if (file == null)
            file = findDiscoveredFont(fontName);
        if (file != null) {
            PDFont font = loadFromFile(file, doc);
            if (font != null)
                return font;
        }

        return null;
    }

    /**
     * 加载默认字体资源（/fonts/default.ttf）。VFS提供默认字体资源时优先于base-14的Helvetica
     */
    public PDFont loadDefaultFont(PDDocument doc) {
        init();
        if (defaultFontResource != null) {
            PDFont font = loadFromResource(defaultFontResource, doc);
            if (font != null)
                return font;
        }
        return getDefaultFont();
    }

    /**
     * 解析CJK回退字体：配置的回退字体名 -> /fonts/default.ttf -> 字体目录中第一个可编码
     * 中文探测字符的字体（文件级JVM缓存）。找不到时返回null，由调用方抛出带指引的错误
     */
    public PDFont loadFallbackFont(PDDocument doc) {
        init();

        File cached = fallbackFontFile;
        if (cached != null) {
            PDFont font = loadFromFile(cached, doc);
            if (font != null)
                return font;
        }

        PDFont font = resolveFallbackFont(doc);
        return font;
    }

    private synchronized PDFont resolveFallbackFont(PDDocument doc) {
        File cached = fallbackFontFile;
        if (cached != null) {
            PDFont font = loadFromFile(cached, doc);
            if (font != null)
                return font;
        }

        String fallbackName = ReportPdfConfigs.CFG_PDF_FALLBACK_FONT.get();
        if (fallbackName != null)
            fallbackName = fallbackName.trim();
        if (StringHelper.isEmpty(fallbackName))
            fallbackName = null;
        if (StringHelper.isEmpty(fallbackName))
            fallbackName = null;
        if (fallbackName != null) {
            IResource resource = getFontResource(fallbackName, false, false);
            if (resource != null) {
                PDFont font = loadFromResource(resource, doc);
                if (font != null && canEncode(font, CJK_PROBE_CHAR)) {
                    return font;
                }
            }
            File file = findDiscoveredFont(fallbackName);
            if (file != null) {
                PDFont font = loadFromFile(file, doc);
                if (font != null && canEncode(font, CJK_PROBE_CHAR)) {
                    fallbackFontFile = file;
                    return font;
                }
            }
        }

        if (defaultFontResource != null) {
            PDFont font = loadFromResource(defaultFontResource, doc);
            if (font != null && canEncode(font, CJK_PROBE_CHAR))
                return font;
        }

        for (File file : cjkPrioritySortedFonts()) {
            PDFont font = loadFromFile(file, doc);
            if (font != null && canEncode(font, CJK_PROBE_CHAR)) {
                LOG.info("nop.pdf.use-fallback-font:file={}", file.getAbsolutePath());
                fallbackFontFile = file;
                return font;
            }
        }
        return null;
    }

    /**
     * 常见CJK字体名优先排序，避免为探测字形而加载无关字体
     */
    List<File> cjkPrioritySortedFonts() {
        List<File> files = new ArrayList<>(discoveredFontFiles().values());
        files.sort((a, b) -> {
            boolean aCjk = isCjkFontFileName(a.getName());
            boolean bCjk = isCjkFontFileName(b.getName());
            if (aCjk != bCjk)
                return aCjk ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return files;
    }

    static boolean isCjkFontFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("simsun") || lower.contains("simhei") || lower.contains("pingfang")
                || lower.contains("songti") || lower.contains("heiti") || lower.contains("kaiti")
                || lower.contains("fangsong") || lower.contains("msyh") || lower.contains("yahei")
                || lower.contains("wqy") || lower.contains("sourcehan") || lower.contains("notosanscjk")
                || lower.contains("notoserifcjk") || lower.contains("arial unicode") || lower.contains("hiragino")
                || lower.contains("sthei") || lower.contains("stsong") || lower.contains("stfang");
    }

    /**
     * 判断字体能否编码文本中的全部字符（结果按字体+码点缓存）
     */
    public boolean canEncode(PDFont font, String text) {
        if (font == null || StringHelper.isEmpty(text))
            return true;

        Set<Integer> bad = unencodableChars.get(font);
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            if (bad != null && bad.contains(codePoint))
                return false;
            try {
                font.encode(new String(Character.toChars(codePoint)));
            } catch (Exception e) {
                markUnencodable(font, codePoint);
                return false;
            }
        }
        return true;
    }

    private void markUnencodable(PDFont font, int codePoint) {
        synchronized (unencodableChars) {
            if (unencodableChars.size() > 200)
                unencodableChars.clear();
            unencodableChars.computeIfAbsent(font, k -> ConcurrentHashMap.newKeySet()).add(codePoint);
        }
    }

    private PDFont loadFromResource(IResource resource, PDDocument doc) {
        InputStream is = resource.getInputStream();
        if (is == null)
            return null;
        try {
            return PDType0Font.load(doc, is);
        } catch (Exception e) {
            LOG.error("nop.pdf.load-font-fail:resource={}", resource.getPath(), e);
            return null;
        } finally {
            IoHelper.safeClose(is);
        }
    }

    private PDFont loadFromFile(File file, PDDocument doc) {
        try {
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                // TTC集合取第一个字体，读入内存后释放文件句柄
                byte[] bytes;
                org.apache.fontbox.ttf.TrueTypeCollection collection = new org.apache.fontbox.ttf.TrueTypeCollection(file);
                try {
                    org.apache.fontbox.ttf.TrueTypeFont[] first = new org.apache.fontbox.ttf.TrueTypeFont[1];
                    collection.processAllFonts(ttf -> {
                        if (first[0] == null)
                            first[0] = ttf;
                    });
                    if (first[0] == null)
                        return null;
                    bytes = IOUtils.toByteArray(first[0].getOriginalData());
                } finally {
                    collection.close();
                }
                return PDType0Font.load(doc, new ByteArrayInputStream(bytes));
            }

            InputStream is = new FileInputStream(file);
            try {
                return PDType0Font.load(doc, is);
            } finally {
                IoHelper.safeClose(is);
            }
        } catch (Exception e) {
            LOG.error("nop.pdf.load-font-fail:file={}", file.getAbsolutePath(), e);
            return null;
        }
    }

    public File findDiscoveredFont(String fontName) {
        if (StringHelper.isEmpty(fontName))
            return null;
        Map<String, File> fonts = discoveredFontFiles();
        File file = fonts.get(normalizeFontKey(fontName));
        if (file != null)
            return file;
        // 去除bold/italic后缀再尝试一次
        int pos = fontName.lastIndexOf('-');
        if (pos > 0)
            return fonts.get(normalizeFontKey(fontName.substring(0, pos)));
        return null;
    }

    public Map<String, File> discoveredFontFiles() {
        Map<String, File> ret = discoveredFontFiles;
        if (ret == null) {
            synchronized (this) {
                ret = discoveredFontFiles;
                if (ret == null) {
                    ret = scanFontDirs();
                    discoveredFontFiles = ret;
                }
            }
        }
        return ret;
    }

    static String normalizeFontKey(String name) {
        return name.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }

    Map<String, File> scanFontDirs() {
        Map<String, File> ret = new CaseInsensitiveMap<>();
        for (File dir : fontDirs()) {
            scanDir(dir, ret, 0);
        }
        LOG.debug("nop.pdf.font-dirs-scanned:count={}", ret.size());
        return ret;
    }

    private void scanDir(File dir, Map<String, File> ret, int depth) {
        if (dir == null || depth > 4)
            return;
        File[] files = dir.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file, ret, depth + 1);
            } else if (isFontFileName(file.getName())) {
                ret.putIfAbsent(normalizeFontKey(removeExt(file.getName())), file);
            }
        }
    }

    static boolean isFontFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc");
    }

    static String removeExt(String name) {
        int pos = name.lastIndexOf('.');
        return pos > 0 ? name.substring(0, pos) : name;
    }

    static List<File> fontDirs() {
        List<File> ret = new ArrayList<>();
        String configured = ReportPdfConfigs.CFG_PDF_FONT_DIRS.get();
        if (configured != null)
            configured = configured.trim();
        if (StringHelper.isEmpty(configured))
            configured = null;
        if (StringHelper.isEmpty(configured))
            configured = null;
        if (configured != null) {
            for (String dir : configured.split("[,;]")) {
                addDir(ret, dir);
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String windir = System.getenv("WINDIR");
            if (windir != null)
                addDir(ret, windir + "/Fonts");
            addDir(ret, "C:/Windows/Fonts");
        } else if (os.contains("mac") || os.contains("darwin")) {
            addDir(ret, "/System/Library/Fonts");
            addDir(ret, "/System/Library/Fonts/Supplemental");
            addDir(ret, "/Library/Fonts");
        } else {
            addDir(ret, "/usr/share/fonts");
            addDir(ret, "/usr/local/share/fonts");
            addDir(ret, System.getProperty("user.home") + "/.fonts");
        }
        return ret;
    }

    private static void addDir(List<File> ret, String path) {
        if (StringHelper.isEmpty(path))
            return;
        File dir = new File(path.trim());
        if (dir.isDirectory() && !ret.contains(dir))
            ret.add(dir);
    }

    // 辅助方法
    protected IResource getFontResource(String fontName, boolean bold, boolean italic) {
        String fullName = getFontFullName(fontName, bold, italic);
        IResource resource = VirtualFileSystem.instance().getResource("/fonts/" + fullName + ".ttf");
        if (!resource.exists()) {
            resource = VirtualFileSystem.instance().getResource("/fonts/" + fontName + ".ttf");
            if (!resource.exists()) {
                resource = VirtualFileSystem.instance().getResource("/fonts/" + fullName + ".otf");
                if (!resource.exists()) {
                    resource = VirtualFileSystem.instance().getResource("/fonts/" + fontName + ".otf");
                    if (!resource.exists())
                        return null;
                }
            }
        }
        return resource;
    }
}
