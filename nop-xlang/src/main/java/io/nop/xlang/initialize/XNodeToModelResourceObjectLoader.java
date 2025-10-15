package io.nop.xlang.initialize;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.xlang.xdsl.DslModelParser;

public class XNodeToModelResourceObjectLoader<T> implements IResourceObjectLoader<T> {
    private final IResourceObjectLoader<XNode> loader;
    private final String xdefPath;
    private final String resolveInDir;

    public XNodeToModelResourceObjectLoader(
            String xdefPath,
            String resolveInDir,
            IResourceObjectLoader<XNode> loader) {
        this.xdefPath = xdefPath;
        this.resolveInDir = resolveInDir;
        this.loader = loader;
    }

    @Override
    public T loadObjectFromPath(String path) {
        XNode xNode = loader.loadObjectFromPath(path);
        return convertXNodeToModel(xNode);
    }

    @Override
    public T parseFromResource(IResource resource) {
        XNode xNode = loader.parseFromResource(resource);
        return convertXNodeToModel(xNode);
    }

    private T convertXNodeToModel(XNode xNode) {
        if (xNode == null) {
            return null;
        }

        return (T) new DslModelParser(xdefPath).resolveInDir(resolveInDir).parseFromNode(xNode);
    }
}
