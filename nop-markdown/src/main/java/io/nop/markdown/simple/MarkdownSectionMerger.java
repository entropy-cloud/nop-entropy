package io.nop.markdown.simple;

import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.stream.Collectors;

public class MarkdownSectionMerger {
    static MarkdownSectionMerger INSTANCE = new MarkdownSectionMerger();

    public static MarkdownSectionMerger instance() {
        return INSTANCE;
    }

    public void merge(MarkdownSection sectionA, MarkdownSection sectionB) {
        if (!StringHelper.isEmpty(sectionB.getSectionNo())) {
            sectionA.setSectionNo(sectionB.getSectionNo());
        }

        if (!StringHelper.isEmpty(sectionB.getTitle())) {
            sectionA.setTitle(sectionB.getTitle());
        }

        if (!StringHelper.isEmpty(sectionB.getText())) {
            sectionA.setText(sectionB.getText());
        }

        if (sectionB.getTpl() != null)
            sectionA.setTpl(sectionB.getTpl());

        List<MarkdownSection> children = sectionB.getChildren();
        if (children != null) {
            if (!sectionA.hasChild()) {
                sectionA.setChildren(children.stream().map(MarkdownSection::cloneInstance).collect(Collectors.toList()));
                return;
            }

            boolean hasSectionNo = false;

            for (MarkdownSection child : children) {
                String title = child.getTitle();
                if (!StringHelper.isEmpty(title)) {
                    MarkdownSection childA = sectionA.findChildByTitle(title);
                    if (childA != null) {
                        merge(childA, child);
                        continue;
                    }
                }

                if (!StringHelper.isEmpty(child.getSectionNo())) {
                    hasSectionNo = true;
                    MarkdownSection childA = sectionA.findSectionBySectionNo(child.getSectionNo());
                    if (childA != null) {
                        merge(childA, child);
                        continue;
                    }
                }
                sectionA.addChild(child.cloneInstance());
            }

            if (hasSectionNo)
                sectionA.getChildren().sort(MarkdownSection::compareWithSectionNo);
        }
    }
}
