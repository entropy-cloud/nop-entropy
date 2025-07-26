package io.nop.markdown.simple;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

@DataBean
public class MarkdownSectionHeader {
    private int level;
    private String sectionNo;
    private String linkUrl;
    private String title;

    public MarkdownSectionHeader cloneInstance() {
        MarkdownSectionHeader ret = new MarkdownSectionHeader();
        ret.level = level;
        ret.sectionNo = sectionNo;
        ret.linkUrl = linkUrl;
        ret.title = title;
        return ret;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        buildText(sb, level, sectionNo, title, linkUrl);
        return sb.toString();
    }

    public static void buildText(StringBuilder sb, int level, String sectionNo, String title, String linkUrl) {
        if (level > 0) {
            sb.append(StringHelper.repeat("#", level));
            sb.append(' ');
        }

        if (linkUrl != null) {
            sb.append('[');
            if (sectionNo != null)
                sb.append(sectionNo).append(' ');
            sb.append(title);
            sb.append(']');
            sb.append('(');
            sb.append(linkUrl);
            sb.append(')');
        } else {
            if (sectionNo != null)
                sb.append(sectionNo).append(' ');
            sb.append(title);
        }
    }

    public String getSectionNo() {
        return sectionNo;
    }

    public void setSectionNo(String sectionNo) {
        this.sectionNo = StringHelper.strip(sectionNo);
        if (this.sectionNo != null && this.sectionNo.endsWith("."))
            this.sectionNo = this.sectionNo.substring(0, this.sectionNo.length() - 1);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }
}
