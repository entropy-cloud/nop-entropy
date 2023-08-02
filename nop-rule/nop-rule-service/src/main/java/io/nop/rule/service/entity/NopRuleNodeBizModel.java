
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Description;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.query.FilterBeanFormatter;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.entity.NopRuleNode;
import io.nop.rule.dao.model.DaoRuleModelLoader;
import io.nop.xlang.xdsl.XDslParseHelper;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ObjMetaHelper;

import javax.inject.Inject;

@BizModel("NopRuleNode")
public class NopRuleNodeBizModel extends CrudBizModel<NopRuleNode> {

    @Inject
    DaoRuleModelLoader ruleModelLoader;

    public NopRuleNodeBizModel() {
        setEntityName(NopRuleNode.class.getName());
    }

    @Description("将规则节点的判断条件格式化后显示")
    @BizLoader
    public String getPredicateDisplayText(@ContextSource NopRuleNode node, IServiceContext context) {
        NopRuleDefinition rule = node.getRuleDefinition();
        ISchema inputSchema = (ISchema) context.getCache().computeIfAbsent("rule-input-schema:" + rule.getRuleId(), k -> {
            return ruleModelLoader.buildRuleInputSchema(rule);
        });

        XNode predicateNode = XDslParseHelper.parseXJson(null, node.getPredicate(), null);
        String text = new FilterBeanFormatter(name -> formatFilterName(inputSchema, name)).format(predicateNode);
        return text;
    }

    private String formatFilterName(ISchema schema, String name) {
        String displayName = ObjMetaHelper.getDisplayName(schema, name);
        if (displayName == null)
            return name;
        return name + '[' + displayName + ']';
    }
}
