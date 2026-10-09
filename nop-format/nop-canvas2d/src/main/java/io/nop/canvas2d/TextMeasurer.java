package io.nop.canvas2d;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本度量器：(text, font 串) 维度 LRU 缓存（plan 2360 D7——key 为原始串恒等，不做规范化）、
 * 串→解析字体快路径（zrender 每 brushText 都 setFont）、getLineHeight = width('国')
 * （zrender 宽字符/行高规则，dist:5843 实证）。
 * 已知限制：字体环境变化不失效缓存（服务端字体集静态）。
 */
public class TextMeasurer {
    private static final char WIDE_CHAR = '\u56fd';
    private static final int DEFAULT_CACHE_SIZE = 4096;

    private final CanvasFontMapper mapper;
    private final Map<String, ResolvedFont> fontCache;
    private final Map<String, ITextMetrics> metricsCache;
    private final Graphics2D probeGraphics;
    private long fontCacheHits;
    private long fontCacheMisses;
    private long metricsCacheHits;
    private long metricsCacheMisses;

    public TextMeasurer() {
        this(new CanvasFontMapper(), DEFAULT_CACHE_SIZE);
    }

    public TextMeasurer(CanvasFontMapper mapper, int cacheSize) {
        this.mapper = mapper;
        this.fontCache = newLruCache(cacheSize);
        this.metricsCache = newLruCache(cacheSize);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        this.probeGraphics = probe.createGraphics();
    }

    private static <K, V> Map<K, V> newLruCache(int maxSize) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * font 串 → 已解析字体（解析 + 映射 + 可用性校验，走缓存快路径）。
     */
    public ResolvedFont resolveFont(String cssFont) {
        synchronized (fontCache) {
            ResolvedFont resolved = fontCache.get(cssFont);
            if (resolved != null) {
                fontCacheHits++;
                return resolved;
            }
        }
        CssFont parsed = CssFontParser.parse(cssFont);
        Font font = mapper.resolve(parsed.families(), parsed.style() | parsed.weight(), parsed.sizePx());
        float lineHeight = parsed.lineHeight() > 0
                ? parsed.lineHeight()
                : (float) measureUncached(String.valueOf(WIDE_CHAR), font).getWidth();
        ResolvedFont resolved = new ResolvedFont(cssFont, font, lineHeight);
        synchronized (fontCache) {
            fontCacheMisses++;
            fontCache.put(cssFont, resolved);
        }
        return resolved;
    }

    /**
     * 文本宽度度量（key = 原始 font 串 + text，D7）。null 文本按浏览器语义强转 "null"。
     */
    public ITextMetrics measure(String text, String cssFont) {
        String normalized = text == null ? "null" : text;
        String key = cssFont + '\u0000' + normalized;
        synchronized (metricsCache) {
            ITextMetrics cached = metricsCache.get(key);
            if (cached != null) {
                metricsCacheHits++;
                return cached;
            }
        }
        ResolvedFont resolved = resolveFont(cssFont);
        ITextMetrics metrics = measureUncached(normalized, resolved.font());
        synchronized (metricsCache) {
            metricsCacheMisses++;
            metricsCache.put(key, metrics);
        }
        return metrics;
    }

    private ITextMetrics measureUncached(String text, Font font) {
        probeGraphics.setFont(font);
        FontMetrics fm = probeGraphics.getFontMetrics();
        return new TextMetrics(fm.stringWidth(text), fm.getAscent(), fm.getDescent());
    }

    /**
     * zrender 宽字符/行高规则：lineHeight = width('国')（dist:5843）。
     */
    public double getLineHeight(String cssFont) {
        return measure(String.valueOf(WIDE_CHAR), cssFont).getWidth();
    }

    public long getFontCacheHits() {
        return fontCacheHits;
    }

    public long getFontCacheMisses() {
        return fontCacheMisses;
    }

    public long getMetricsCacheHits() {
        return metricsCacheHits;
    }

    public long getMetricsCacheMisses() {
        return metricsCacheMisses;
    }

    /**
     * 已解析字体：解析产物 + 派生行高（显式 line-height 优先，否则 width('国')）。
     */
    public record ResolvedFont(String cssFont, Font font, float lineHeight) {
    }
}
