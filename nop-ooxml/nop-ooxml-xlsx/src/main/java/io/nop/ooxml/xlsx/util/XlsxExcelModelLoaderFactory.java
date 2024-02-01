package io.nop.ooxml.xlsx.util;

import io.nop.core.resource.IResourceObjectLoader;
import io.nop.ooxml.xlsx.imp.XlsxObjectLoader;
import io.nop.xlang.xdsl.IExcelModelLoaderFactory;

public class XlsxExcelModelLoaderFactory implements IExcelModelLoaderFactory {
    @Override
    public IResourceObjectLoader<?> newExcelModelLoader(String impPath) {
        return new XlsxObjectLoader(impPath);
    }
}
