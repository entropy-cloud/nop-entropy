package io.nop.record_mapping.md;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.impl.RecordMappingManagerImpl;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.AbstractDslResourcePersister;

public class MarkdownDslResourceLoader extends AbstractDslResourcePersister {
    private final String mappingName;

    public MarkdownDslResourceLoader(String schemaPath, String resolveInDir, String mappingName) {
        super(schemaPath, resolveInDir);
        this.mappingName = mappingName;
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource) {
        IRecordMappingManager manager = BeanContainer.isInitialized() ?
                BeanContainer.getBeanByType(IRecordMappingManager.class)
                : new RecordMappingManagerImpl();

        MarkdownDocument doc = MarkdownTool.instance().parseFromResource(resource);

        RecordMappingConfig mapping = manager.getRecordMappingConfig(mappingName);

        RecordMappingContext ctx = new RecordMappingContext();
        ctx.setForceUseMap(true);
        ctx.setSkipValidation(true);

        Object model = new MappingBasedMarkdownParser(mapping).map(doc.getRootSection(), ctx);
        return transformBeanToNode(model);
    }

    @Override
    public void saveObjectToResource(IResource resource, Object obj) {
        IRecordMappingManager manager = BeanContainer.isInitialized() ?
                BeanContainer.getBeanByType(IRecordMappingManager.class)
                : new RecordMappingManagerImpl();

        RecordMappingConfig mapping = manager.getRecordMappingConfig(mappingName);

        IEvalScope scope = XLang.newEvalScope();
        new MappingBasedMarkdownGenerator(mapping, obj, scope).generateToResource(resource, scope);
    }
}
