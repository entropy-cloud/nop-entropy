package io.nop.pdf.outline;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WI14：outline.yaml 输出（字段序与 null 语义对齐 card-kb 契约样本）：
 * title/depth/page/(page-end)/stats{entries,max-depth,unresolved-pages}；
 * page 为 null 时键保留值 null；page-end 键仅 page!=null 时写入（R1-m2）。
 */
public final class PdfOutlineYamlWriter {

    private PdfOutlineYamlWriter() {
    }

    /**
     * @param target      目标文档标识（契约样本 target 字段，null=空串）
     * @param source      原始 PDF 路径（契约样本 source 字段，null=空串）
     * @param totalPages  文档总页数
     */
    public static String toYaml(PdfOutline outline, String target, String source, int totalPages) {
        List<Object> entries = new ArrayList<>(outline.getEntries().size());
        for (PdfOutlineEntry e : outline.getEntries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", e.getTitle());
            m.put("depth", e.getDepth());
            m.put("page", e.getPage());
            if (e.getPage() != null)
                m.put("page-end", e.getPageEnd());
            entries.add(m);
        }

        PdfOutlineStats s = outline.getStats();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("entries", s.getEntries());
        stats.put("max-depth", s.getMaxDepth());
        stats.put("unresolved-pages", s.getUnresolvedPages());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("target", target == null ? "" : target);
        root.put("source", source == null ? "" : source);
        root.put("outline-source", "pdf-outline");
        root.put("total-pages", totalPages);
        root.put("entries", entries);
        root.put("stats", stats);

        return new Yaml().dump(root);
    }
}
