package io.nop.converter.impl;

import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentConverterManager;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;

import java.util.Set;

public class ConvertModelBuilder {
    public XNode buildConvertNode(IDocumentConverterManager manager) {
        XNode convert = XNode.make("convert");
        XNode builders = convert.makeChild("builders");
        for (String fileType : manager.getDocumentFileTypes()) {
            builders.appendChild(buildBuilderNode(fileType, manager));
        }

        XNode converters = convert.makeChild("converters");
        for (String fromType : manager.getFromFileTypes()) {
            Set<String> toTypes = manager.getToFileTypes(fromType, false);
            for (String toType : toTypes) {
                converters.appendChild(buildConverterNode(fromType, toType, manager));
            }
        }

        return convert;
    }

    XNode buildBuilderNode(String fileType, IDocumentConverterManager manager) {
        IDocumentObjectBuilder builder = manager.requireDocumentObjectBuilder(fileType);
        XNode builderNode = XNode.make("builder");
        builderNode.setAttr("fileType", fileType);
        builderNode.setAttr("class", builder.getClass().getName());
        return builderNode;
    }

    XNode buildConverterNode(String fromType, String toType, IDocumentConverterManager manager) {
        IDocumentConverter converter = manager.requireConverter(fromType, toType, false);
        XNode converterNode = XNode.make("converter");
        converterNode.setAttr("from", fromType);
        converterNode.setAttr("to", toType);
        converterNode.setAttr("class", converter.getClass().getName());
        return converterNode;
    }
}
