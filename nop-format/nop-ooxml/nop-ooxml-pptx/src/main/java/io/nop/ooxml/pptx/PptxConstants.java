/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.pptx;

public interface PptxConstants {

    // ======== 文件路径常量 ========
    
    String PATH_PPT_PRESENTATION = "ppt/presentation.xml";
    String PATH_PPT_RELS = "ppt/_rels/presentation.xml.rels";
    String PATH_PPT_SLIDES_DIR = "ppt/slides/";
    String PATH_PPT_SLIDE_LAYOUTS_DIR = "ppt/slideLayouts/";
    String PATH_PPT_SLIDE_MASTERS_DIR = "ppt/slideMasters/";
    String PATH_PPT_THEME_DIR = "ppt/theme/";
    String PATH_PPT_MEDIA_DIR = "ppt/media/";
    String PATH_PPT_COMMENTS_DIR = "ppt/comments/";

    // ======== 具体文件路径常量 ========
    
    String PATH_PPT_SLIDE_TEMPLATE = "ppt/slides/slide%d.xml";
    String PATH_PPT_SLIDE_LAYOUT_TEMPLATE = "ppt/slideLayouts/slideLayout%d.xml";
    String PATH_PPT_SLIDE_MASTER_TEMPLATE = "ppt/slideMasters/slideMaster%d.xml";
    String PATH_PPT_THEME_TEMPLATE = "ppt/theme/theme%d.xml";
    String PATH_PPT_COMMENTS_TEMPLATE = "ppt/comments/comments%d.xml";
    String PATH_PPT_COMMENT_AUTHORS = "ppt/comments/commentAuthors.xml";

    // ======== 关系文件路径常量 ========
    
    String PATH_PPT_SLIDES_RELS_TEMPLATE = "ppt/slides/_rels/slide%d.xml.rels";
    String PATH_PPT_SLIDE_LAYOUTS_RELS_TEMPLATE = "ppt/slideLayouts/_rels/slideLayout%d.xml.rels";
    String PATH_PPT_SLIDE_MASTERS_RELS_TEMPLATE = "ppt/slideMasters/_rels/slideMaster%d.xml.rels";

    // ======== 内容类型常量 ========
    
    String CONTENT_TYPE_PRESENTATION = "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
    String CONTENT_TYPE_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml";
    String CONTENT_TYPE_SLIDE_LAYOUT = "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml";
    String CONTENT_TYPE_SLIDE_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml";
    String CONTENT_TYPE_THEME = "application/vnd.openxmlformats-officedocument.theme+xml";
    String CONTENT_TYPE_COMMENTS = "application/vnd.openxmlformats-officedocument.presentationml.comments+xml";
    String CONTENT_TYPE_COMMENT_AUTHORS = "application/vnd.openxmlformats-officedocument.presentationml.commentAuthors+xml";
    String CONTENT_TYPE_HANDOUT_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.handoutMaster+xml";
    String CONTENT_TYPE_NOTES_MASTER = "application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml";
    String CONTENT_TYPE_NOTES_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml";
    String CONTENT_TYPE_PRESENTATION_PROPS = "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml";
    String CONTENT_TYPE_VIEW_PROPS = "application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml";
    String CONTENT_TYPE_TABLE_STYLES = "application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml";

    // ======== 关系类型常量 ========
    
    String REL_TYPE_PRESENTATION = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    String REL_TYPE_SLIDE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide";
    String REL_TYPE_SLIDE_LAYOUT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout";
    String REL_TYPE_SLIDE_MASTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster";
    String REL_TYPE_THEME = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme";
    String REL_TYPE_COMMENTS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments";
    String REL_TYPE_COMMENT_AUTHORS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/commentAuthors";
    String REL_TYPE_HANDOUT_MASTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/handoutMaster";
    String REL_TYPE_NOTES_MASTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster";
    String REL_TYPE_NOTES_SLIDE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide";
    String REL_TYPE_PRESENTATION_PROPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps";
    String REL_TYPE_VIEW_PROPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps";
    String REL_TYPE_TABLE_STYLES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles";
    String REL_TYPE_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    String REL_TYPE_HYPERLINK = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink";

    // ======== 部件名称常量 ========
    
    String PART_NAME_PRESENTATION = "/ppt/presentation.xml";
    String PART_NAME_SLIDE = "/ppt/slides/slide%d.xml";
    String PART_NAME_SLIDE_LAYOUT = "/ppt/slideLayouts/slideLayout%d.xml";
    String PART_NAME_SLIDE_MASTER = "/ppt/slideMasters/slideMaster%d.xml";
    String PART_NAME_THEME = "/ppt/theme/theme%d.xml";
    String PART_NAME_COMMENTS = "/ppt/comments/comments%d.xml";
    String PART_NAME_COMMENT_AUTHORS = "/ppt/comments/commentAuthors.xml";

    // ======== XML命名空间常量 ========
    
    String NS_PRESENTATION_ML = "http://schemas.openxmlformats.org/presentationml/2006/main";
    String NS_DRAWING_ML = "http://schemas.openxmlformats.org/drawingml/2006/main";
    String NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    String NS_PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";

    // ======== XML前缀常量 ========
    
    String PREFIX_PRESENTATION = "p";
    String PREFIX_DRAWING = "a";
    String PREFIX_RELATIONSHIPS = "r";

    // ======== 默认文件扩展名 ========
    
    String FILE_EXT_PPTX = "pptx";
    String FILE_EXT_PPTM = "pptm";
    String FILE_EXT_POTX = "potx";
    String FILE_EXT_POTM = "potm";
    String FILE_EXT_PPSX = "ppsx";
    String FILE_EXT_PPSM = "ppsm";

    // ======== 幻灯片尺寸常量 ========
    
    int DEFAULT_SLIDE_WIDTH = 9144000; // EMU单位 (10英寸)
    int DEFAULT_SLIDE_HEIGHT = 6858000; // EMU单位 (7.5英寸)
    
    // ======== 其他常量 ========
    
    String DEFAULT_THEME_NAME = "theme1.xml";
    String DEFAULT_SLIDE_LAYOUT_NAME = "slideLayout1.xml";
    String DEFAULT_SLIDE_MASTER_NAME = "slideMaster1.xml";
    
    // 幻灯片类型
    String SLIDE_LAYOUT_TITLE = "title";
    String SLIDE_LAYOUT_TITLE_CONTENT = "titleContent";
    String SLIDE_LAYOUT_CONTENT = "content";
    String SLIDE_LAYOUT_BLANK = "blank";
    
    // 形状类型
    String SHAPE_TYPE_TEXT_BOX = "textBox";
    String SHAPE_TYPE_TITLE = "title";
    String SHAPE_TYPE_CONTENT = "content";
    String SHAPE_TYPE_PLACEHOLDER = "placeholder";
}