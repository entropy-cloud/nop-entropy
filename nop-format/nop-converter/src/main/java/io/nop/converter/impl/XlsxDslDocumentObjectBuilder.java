package io.nop.converter.impl;

import io.nop.api.core.util.Guard;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.imp.ImportExcelParser;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.xlang.xdsl.DslModelHelper;

public class XlsxDslDocumentObjectBuilder implements IDocumentObjectBuilder {

    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new XlsxDslDocumentObject(fileType, resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        throw new UnsupportedOperationException("DslDocumentObject does not support buildFromText");
    }

    @Override
    public String getXdefPath(String fileType) {
        return getXdefPathFromFileType(fileType);
    }

    static String getXdefPathFromFileType(String fileType) {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(fileType);
        if(config.getXdefPath() != null)
            return config.getXdefPath();

        ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(fileType);
        Guard.notNull(loaderConfig, "loaderConfig");

        Guard.notEmpty(loaderConfig.getImpPath(), "impPath");

        ImportModel importModel = (ImportModel) ResourceComponentManager.instance()
                .loadComponentModel(loaderConfig.getImpPath());
        return importModel.getXdef();
    }

    public static class XlsxDslDocumentObject extends ResourceDocumentObject {
        public XlsxDslDocumentObject(String fileType, IResource resource) {
            super(fileType, resource);
        }

        @Override
        public Object getModelObject(DocumentConvertOptions options) {
            String fileType = getFileType();
            ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(fileType);
            ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(fileType);
            Guard.notNull(loaderConfig, "loaderConfig");

            if(loaderConfig.getImpPath() != null) {
                Guard.notEmpty(loaderConfig.getImpPath(), "impPath");

                ImportModel importModel = (ImportModel) ResourceComponentManager.instance()
                        .loadComponentModel(loaderConfig.getImpPath());
                ImportExcelParser parser = new ImportExcelParser(importModel);
                parser.setReturnDynamicObject(true);
                ExcelWorkbook wk = ExcelDocHelper.loadExcel(this);
                return parser.parseFromWorkbook(wk);
            }else{
                return loaderConfig.getLoader().loadObjectFromResource(getResource());
            }
        }

        @Override
        public String getXdefPath() {
            String fileType = getFileType();
            return getXdefPathFromFileType(fileType);
        }

        @Override
        public String getText(DocumentConvertOptions options) {
            return getNode(options).xml();
        }

        @Override
        public XNode getNode(DocumentConvertOptions options) {
            Object obj = getModelObject(options);
            XNode node = DslModelHelper.dslModelToXNode(getXdefPath(obj), obj, true);
            return node;
        }

        String getXdefPath(Object obj) {
            String xdefPath = DslModelHelper.getXdefPath(obj, null);
            if (xdefPath != null)
                return xdefPath;

            return getXdefPath();
        }
    }
}