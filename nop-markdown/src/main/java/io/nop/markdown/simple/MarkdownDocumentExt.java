package io.nop.markdown.simple;

import io.nop.markdown.utils.MarkdownTool;

import java.util.HashMap;
import java.util.Map;

public class MarkdownDocumentExt {
    private Map<String, MarkdownSection> sectionMap;

    public Map<String, MarkdownSection> getSectionMap() {
        return sectionMap;
    }

    public void setSectionMap(Map<String, MarkdownSection> sectionMap) {
        this.sectionMap = sectionMap;
    }

    public void addSection(String sectionNo, MarkdownSection section) {
        if (sectionMap == null)
            sectionMap = new HashMap<>();
        sectionMap.put(sectionNo, section);
    }

    public void matchTplForSection(String tplPath, boolean throwError) {
        MarkdownDocument tpl = MarkdownTool.loadMarkdownTpl(tplPath);
        if (this.sectionMap != null) {
            for (MarkdownSection section : sectionMap.values()) {
                section.matchTpl(tpl.getRootSection(), throwError);
            }
        }
    }


    public void mergeToDocument(MarkdownDocument doc) {
        MarkdownSection rootSection = doc.getRootSection();
        if (sectionMap != null && rootSection != null) {
            rootSection.forEachSection(section -> {
                String sectionNo = section.getSectionNo();
                if (sectionNo != null && sectionMap.containsKey(sectionNo)) {
                    MarkdownSection ext = sectionMap.get(sectionNo);
                    if (ext != null) {
                        ext.resetLevel(section.getLevel());
                        section.addChildren(ext.getChildren());
                    }
                }
            });
        }
    }
}
