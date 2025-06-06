package io.nop.markdown.simple;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.Map;

@DataBean
public class MarkdownTitle {
    private int level;
    private String sectionNo;
    private String text;
    private Map<String, String> meta;

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getNormalizedTitle() {
        if (sectionNo == null)
            return text;
        return sectionNo + " " + text;
    }

    public String getSectionNo() {
        return sectionNo;
    }

    public void setSectionNo(String sectionNo) {
        this.sectionNo = StringHelper.strip(sectionNo);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }
}
