package io.nop.web.page;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectLoaderFactory;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xdsl.AbstractDslResourcePersister;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xui.model.UiFormModel;
import io.nop.xui.model.UiGridModel;

import java.util.List;
import java.util.Map;

public class ViewModelLoaderFactory implements IResourceObjectLoaderFactory<Object> {
    @Override
    public IResourceObjectLoader<Object> newResourceObjectLoader(ComponentModelConfig config, Map<String, Object> attributes) {
        return new ViewModelLoader(config.getXdefPath(), config.getResolveInDir());
    }

    public static class ViewModelLoader extends AbstractDslResourcePersister {
        public ViewModelLoader(String schemaPath, String resolveInDir) {
            super(schemaPath, resolveInDir);
        }

        @Override
        public Object loadObjectFromResource(IResource resource) {
            Object comp = super.loadObjectFromResource(resource);

            String objMetaPath = (String) BeanTool.instance().getProperty(comp, "objMeta");


            ResourceComponentManager.instance().ignoreDepends(() -> {
                IObjMeta objMeta = objMetaPath == null ? null : SchemaLoader.loadXMeta(objMetaPath);
                List<UiFormModel> forms = (List<UiFormModel>) BeanTool.instance().getProperty(comp, "forms");
                if (forms != null) {
                    for (UiFormModel form : forms) {
                        form.validate(getObjMeta(form.getObjMeta(), objMeta));
                    }
                }

                List<UiGridModel> grids = (List<UiGridModel>) BeanTool.instance().getProperty(comp, "grids");
                if (grids != null) {
                    for (UiGridModel grid : grids) {
                        grid.validate(getObjMeta(grid.getObjMeta(), objMeta));
                    }
                }
                return null;
            });
            return comp;
        }

        IObjMeta getObjMeta(String path, IObjMeta defaultObjMeta) {
            if (!StringHelper.isEmpty(path))
                return SchemaLoader.loadXMeta(path);
            return defaultObjMeta;
        }

        @Override
        public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
            return loadXmlFile(resource, phase);
        }

        @Override
        public void saveObjectToResource(IResource resource, Object obj) {
            XNode node = transformBeanToNode(obj);
            node.saveToResource(resource, null);
        }
    }
}
