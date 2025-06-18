package io.nop.ai.coder.meta;

import io.nop.ai.coder.orm.DictGenHelper;
import io.nop.api.core.beans.DictBean;
import io.nop.commons.text.CodeBuilder;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.dict.DictProvider;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;

public class MetaToJava {
    private IServiceContext svcCtx;
    private boolean useDictCode = true;

    public MetaToJava(IServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public MetaToJava() {
        this(new ServiceContextImpl());
    }

    public MetaToJava useDictCode(boolean useDictCode) {
        this.useDictCode = useDictCode;
        return this;
    }

    public String generate(IObjSchema objMeta) {
        return generate(objMeta, objMeta.getName());
    }

    public String generateAllDefined(IObjMeta objMeta) {
        CodeBuilder cb = new CodeBuilder();
        generateAllDefined(objMeta, cb);
        return cb.toString();
    }

    public void generateAllDefined(IObjMeta objMeta, CodeBuilder cb) {
        for (IObjSchema defined : objMeta.getDefinedObjSchemas()) {
            generate(defined, defined.getName(), cb);
        }
    }

    public String generate(IObjSchema objMeta, String objName) {
        CodeBuilder cb = new CodeBuilder();
        generate(objMeta, objName, cb);
        return cb.toString();
    }

    public void generate(IObjSchema objMeta, String objName, CodeBuilder cb) {
        if (objMeta.getDisplayName() != null)
            cb.line("// {0}", objMeta.getDisplayName());

        cb.line("class {0} '{'", objName);
        for (IObjPropMeta propMeta : objMeta.getProps()) {
            cb.incIndent();
            cb.printIndent();
            if (propMeta.isMandatory()) {
                cb.append("@Nonnull ");
            }
            IGenericType type = propMeta.getType();
            if (type == null)
                type = PredefinedGenericTypes.ANY_TYPE;
            cb.append(StringHelper.simplifyJavaType(type.toString()));
            cb.append(' ');
            cb.append(propMeta.getName());
            cb.append(';');
            cb.append("// ").append(propMeta.getDisplayName());
            appendDict(propMeta, cb);
            cb.decIndent();
            cb.line();
        }
        cb.line("'}'");
        cb.line();
    }

    void appendDict(IObjPropMeta propMeta, CodeBuilder cb) {
        ISchema schema = propMeta.getSchema();
        if (schema == null)
            return;

        String dictName = schema.getDict();
        if (dictName == null)
            return;

        DictBean dictBean = DictProvider.instance().getDict(null, dictName, svcCtx.getCache(), svcCtx);
        DictGenHelper.generateOptions(dictBean, dictName, useDictCode, cb);
    }
}
