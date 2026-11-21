package io.nop.pdf.census;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WI17：逐页图片普查（PDResources XObject 遍历，跨页重复判重，面积阈值 keep/skip）
 * + registry YAML 输出（对齐 image-registry schema：seq/file/page/width/height/size-kb）
 * + 文档机械化统计。
 */
public final class ImageCensus {

    private static final Logger LOG = LoggerFactory.getLogger(ImageCensus.class);

    public static final class Options {
        private File outputDir;
        private int minAreaPx = 1;

        public File getOutputDir() { return outputDir; }
        public Options setOutputDir(File v) { this.outputDir = v; return this; }
        public int getMinAreaPx() { return minAreaPx; }
        public Options setMinAreaPx(int v) { this.minAreaPx = v; return this; }
    }

    public static final class Result {
        private final List<ImageCensusEntry> entries;
        private final DocumentStats docStats;

        public Result(List<ImageCensusEntry> entries, DocumentStats docStats) {
            this.entries = entries;
            this.docStats = docStats;
        }

        public List<ImageCensusEntry> getEntries() { return entries; }
        public DocumentStats getDocStats() { return docStats; }
    }

    private ImageCensus() {
    }

    public static Result census(PDDocument doc, Options opts) throws IOException {
        List<ImageCensusEntry> entries = new ArrayList<>();
        Map<String, String> hashToFirst = new HashMap<>();
        int seq = 0;

        int nPages = doc.getNumberOfPages();
        for (int p = 1; p <= nPages; p++) {
            PDPage page = doc.getPage(p - 1);
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (COSName name : resources.getXObjectNames()) {
                try {
                    var xobj = resources.getXObject(name);
                    if (!(xobj instanceof PDImageXObject img)) continue;

                    int w = img.getWidth();
                    int h = img.getHeight();
                    int area = w * h;
                    boolean repeated = false;
                    String hash = contentHash(img);
                    if (hashToFirst.containsKey(hash)) {
                        repeated = true;
                    } else {
                        hashToFirst.put(hash, null);
                    }

                    seq++;
                    boolean keep = area >= opts.getMinAreaPx();
                    String fileName = keep ? "img-" + seq + "-p" + p + getFileExt(img.getSuffix()) : null;

                    if (keep && opts.getOutputDir() != null && !repeated) {
                        File outFile = new File(opts.getOutputDir(), fileName);
                        try (var is = img.createInputStream()) {
                            Files.copy(is, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                    long sizeKb = img.getStream().getLength() / 1024;
                    entries.add(new ImageCensusEntry(seq, fileName, p, w, h, sizeKb, area, keep, repeated));
                } catch (IOException e) {
                    // 无法解析的 XObject：跳过但记录 warn（plan 2586：不再静默吞异常）
                    LOG.warn("skip unresolvable image XObject on page {}: {}", p, String.valueOf(e), e);
                }
            }
        }

        return new Result(entries, buildDocStats(doc, entries));
    }

    /**
     * 流式 digest（plan 2586：不再整流 toByteArray，防大图 OOM）。
     */
    private static String contentHash(PDImageXObject img) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream in = img.createInputStream()) {
                DigestInputStream din = new DigestInputStream(in, md);
                byte[] buf = new byte[8192];
                while (din.read(buf) != -1) {
                    // drain
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.identityHashCode(img));
        }
    }

    private static String getFileExt(String suffix) {
        if ("jpeg".equalsIgnoreCase(suffix) || "jpg".equalsIgnoreCase(suffix)) return ".jpg";
        if ("png".equalsIgnoreCase(suffix)) return ".png";
        return "." + (suffix != null ? suffix.toLowerCase() : "bin");
    }

    public static DocumentStats buildDocStats(PDDocument doc, List<ImageCensusEntry> entries) {
        DocumentStats stats = new DocumentStats();
        stats.setPages(doc.getNumberOfPages());
        stats.setImageCount((int) entries.stream().filter(ImageCensusEntry::isKeep).count());
        return stats;
    }

    /**
     * registry YAML 输出（字段对齐 image-registry 契约：seq/file/page/width/height/size-kb）
     */
    public static String toRegistryYaml(List<ImageCensusEntry> entries) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ImageCensusEntry e : entries) {
            if (!e.isKeep()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", e.getSeq());
            m.put("file", e.getFile());
            m.put("page", e.getPage());
            m.put("width", e.getWidth());
            m.put("height", e.getHeight());
            m.put("size-kb", e.getSizeKb());
            items.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("images", items);
        return new Yaml().dump(root);
    }
}
