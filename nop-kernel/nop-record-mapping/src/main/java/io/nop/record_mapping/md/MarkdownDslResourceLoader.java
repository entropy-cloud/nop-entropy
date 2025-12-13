package io.nop.record_mapping.md;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.RecordMappingManager;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.AbstractDslResourcePersister;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.IDslNodeTextSerializer;

public class MarkdownDslResourceLoader extends AbstractDslResourcePersister implements IDslNodeTextSerializer {
    private final String mappingName;
    private final String reverseMappingName;

    public MarkdownDslResourceLoader(String schemaPath, String resolveInDir, String mappingName) {
        super(schemaPath, resolveInDir);
        this.mappingName = mappingName;
        this.reverseMappingName = StringHelper.reverseMappingName(mappingName);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
        IRecordMappingManager manager = RecordMappingManager.instance();

        MarkdownDocument doc = MarkdownTool.instance().parseFromResource(resource);

        RecordMappingConfig mapping = manager.getRecordMappingConfig(mappingName);

        RecordMappingContext ctx = new RecordMappingContext();
        ctx.setForceUseMap(true);
        ctx.setSkipValidation(true);

        Object model = new MappingBasedMarkdownParser(mapping).map(doc.getRootSection(), ctx);
        XNode node = transformBeanToNode(model);
        return DslNodeLoader.INSTANCE.processDslNode(node, schemaPath, phase);
    }

    @Override
    public String serializeDslNodeToText(String fileType, XNode node) {
        IXDefinition xdef = loadXDef();
        Object bean = DslModelHelper.dslNodeToJson(xdef, node);

        IRecordMappingManager manager = RecordMappingManager.instance();

        RecordMappingConfig mapping = manager.getRecordMappingConfig(reverseMappingName);

        IEvalScope scope = XLang.newEvalScope();
        return new MappingBasedMarkdownGenerator(mapping, bean, scope).generateText(scope);
    }

    @Override
    public void saveObjectToResource(IResource resource, Object obj) {
        IRecordMappingManager manager = RecordMappingManager.instance();

        RecordMappingConfig mapping = manager.getRecordMappingConfig(reverseMappingName);

        IEvalScope scope = XLang.newEvalScope();
        new MappingBasedMarkdownGenerator(mapping, obj, scope).generateToResource(resource, scope);
    }
}
