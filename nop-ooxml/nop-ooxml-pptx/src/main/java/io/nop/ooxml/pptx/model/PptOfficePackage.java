/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.pptx.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.common.constants.ContentTypes;
import io.nop.ooxml.common.impl.ResourceOfficePackagePart;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.pptx.PptxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.nop.ooxml.common.OfficeErrors.ARG_FILE_EXT;
import static io.nop.ooxml.common.OfficeErrors.ARG_PATH;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_UNSUPPORTED_CONTENT_TYPE;

public class PptOfficePackage extends OfficePackage {
    @Override
    public PptOfficePackage copy() {
        PptOfficePackage pkg = new PptOfficePackage();
        copyTo(pkg);
        return pkg;
    }

    public void saveImagesToDir(File dir) {
        forEachImage(resource -> {
            resource.saveToFile(new File(dir, resource.getName()));
        });
    }

    public void forEachImage(Consumer<IResource> action) {
        this.getFiles("ppt/media/image").forEach(part -> {
            action.accept(part.getResource());
        });
    }

//    public CommentsPart getComments() {
//        String path = "ppt/comments/comments1.xml";
//        IOfficePackagePart file = getFile(path);
//        if (file == null)
//            return null;
//
//        if (file instanceof CommentsPart)
//            return (CommentsPart) file;
//
//        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
//        CommentsPart part = new CommentsPart(file.getPath(), res.loadXml());
//        addFile(part);
//        return part;
//    }
//
//    public CommentsAuthorsPart getCommentsAuthors() {
//        String path = "ppt/comments/commentAuthors.xml";
//        IOfficePackagePart file = getFile(path);
//        if (file == null)
//            return null;
//
//        if (file instanceof CommentsAuthorsPart)
//            return (CommentsAuthorsPart) file;
//
//        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
//        CommentsAuthorsPart part = new CommentsAuthorsPart(file.getPath(), res.loadXml());
//        addFile(part);
//        return part;
//    }

    public OfficeRelationship addImage(IResource resource) {
        return addImage(StringHelper.fileExt(resource.getPath()), resource);
    }

    public OfficeRelationship addImage(String fileExt, IResource resource) {
        ContentTypesPart contentTypes = getContentTypes();
        String contentType = ContentTypes.getContentTypeFromFileExtension(fileExt);
        if (contentType == null)
            throw new NopException(ERR_OOXML_UNSUPPORTED_CONTENT_TYPE).param(ARG_PATH, resource.getPath())
                    .param(ARG_FILE_EXT, fileExt);

        contentTypes.addDefaultContentType(fileExt.toLowerCase(), contentType);

        String target = addNewFile("ppt/media/image1." + fileExt, resource);
        OfficeRelsPart part = getRels(PptxConstants.PATH_PPT_RELS);
        if (target.startsWith("ppt/"))
            target = target.substring("ppt/".length());
        return part.addImage(target);
    }

    public XNode getPresentationXml() {
        return getFile(PptxConstants.PATH_PPT_PRESENTATION).buildXml(null);
    }

    public List<XNode> getSlidesXml() {
        List<XNode> slides = new ArrayList<>();
        // 获取所有幻灯片文件
        this.getFiles("ppt/slides/slide").forEach(part -> {
            slides.add(part.buildXml(null));
        });
        return slides;
    }

    public XNode getSlideXml(int slideIndex) {
        String path = "ppt/slides/slide" + (slideIndex + 1) + ".xml";
        IOfficePackagePart file = getFile(path);
        if (file == null)
            return null;
        return file.buildXml(null);
    }

    public SlidePart getSlide(int slideIndex) {
        String path = "ppt/slides/slide" + (slideIndex + 1) + ".xml";
        IOfficePackagePart file = getFile(path);
        if (file == null)
            return null;

        if (file instanceof SlidePart)
            return (SlidePart) file;

        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
        SlidePart part = new SlidePart(file.getPath(), res.loadXml());
        addFile(part);
        return part;
    }

    public int getSlideCount() {
        return this.getFiles("ppt/slides/slide").size();
    }

    public void forEachSlide(Consumer<SlidePart> action) {
        int slideCount = getSlideCount();
        for (int i = 0; i < slideCount; i++) {
            SlidePart slide = getSlide(i);
            if (slide != null) {
                action.accept(slide);
            }
        }
    }

//    public SlideLayoutPart getSlideLayout(int layoutIndex) {
//        String path = "ppt/slideLayouts/slideLayout" + (layoutIndex + 1) + ".xml";
//        IOfficePackagePart file = getFile(path);
//        if (file == null)
//            return null;
//
//        if (file instanceof SlideLayoutPart)
//            return (SlideLayoutPart) file;
//
//        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
//        SlideLayoutPart part = new SlideLayoutPart(file.getPath(), res.loadXml());
//        addFile(part);
//        return part;
//    }
//
//    public SlideMasterPart getSlideMaster(int masterIndex) {
//        String path = "ppt/slideMasters/slideMaster" + (masterIndex + 1) + ".xml";
//        IOfficePackagePart file = getFile(path);
//        if (file == null)
//            return null;
//
//        if (file instanceof SlideMasterPart)
//            return (SlideMasterPart) file;
//
//        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
//        SlideMasterPart part = new SlideMasterPart(file.getPath(), res.loadXml());
//        addFile(part);
//        return part;
//    }

    public void removeCommentsFile() {
        this.removeFile("ppt/comments/comments1.xml");
        this.removeFile("ppt/comments/commentAuthors.xml");

        ContentTypesPart part = getContentTypes();
        //part.removeContentType(PART_NAME_COMMENTS);
        //part.removeContentType(PART_NAME_COMMENTS_AUTHORS);
    }

    /**
     * 添加新的幻灯片
     */
    public SlidePart addSlide() {
        int slideCount = getSlideCount();
        int newSlideIndex = slideCount + 1;
        String path = "ppt/slides/slide" + newSlideIndex + ".xml";

        // 创建空的幻灯片XML
        XNode slideXml = createEmptySlideXml();
        SlidePart slidePart = new SlidePart(path, slideXml);
        addFile(slidePart);

        // 更新presentation.xml中的幻灯片引用
        updatePresentationSlideRefs();

        return slidePart;
    }

    private XNode createEmptySlideXml() {
        // 创建基本的幻灯片XML结构
        XNode slide = XNode.make("p:sld");
        slide.setAttr("xmlns:p", "http://schemas.openxmlformats.org/presentationml/2006/main");
        slide.setAttr("xmlns:a", "http://schemas.openxmlformats.org/drawingml/2006/main");
        // 添加基本的幻灯片内容结构
        return slide;
    }

    private void updatePresentationSlideRefs() {
        // 更新presentation.xml中的幻灯片列表
        // 这里需要根据实际的PowerPoint文档结构来实现
    }
}