package io.nop.xlang.xdsl;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResourceObjectLoader;

public interface IExcelModelLoaderFactory {
    IResourceObjectLoader<?> newExcelModelLoader(String impPath);
}
