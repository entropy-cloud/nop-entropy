package io.nop.converter;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;

public interface IDocumentObject extends ISourceLocationGetter {
    String getFileType();

    default String getFileExt() {
        return StringHelper.lastPart(getFileType(), '.');
    }

    boolean isBinaryOnly();

    Object getModelObject();

    String getText();

    default XNode getNode() {
        return XNodeParser.instance().parseFromText(getLocation(), getText());
    }

    IResource getResource();
}