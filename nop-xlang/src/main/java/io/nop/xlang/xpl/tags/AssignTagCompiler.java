package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.utils.XplParseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_OTHER_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_ASSIGN_BODY_ONLY_ALLOW_FIELD;
import static io.nop.xlang.XLangErrors.ERR_XPL_ASSIGN_DUPLICATE_FIELD;
import static io.nop.xlang.XLangErrors.ERR_XPL_ASSIGN_NO_FIELDS;
import static io.nop.xlang.xpl.XplConstants.FIELD_NAME;
import static io.nop.xlang.xpl.XplConstants.NAME_NAME;
import static io.nop.xlang.xpl.XplConstants.OBJ_NAME;
import static io.nop.xlang.xpl.XplConstants.VALUE_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrIdentifier;
import static java.util.Arrays.asList;

public class AssignTagCompiler implements IXplTagCompiler {
    public static final AssignTagCompiler INSTANCE = new AssignTagCompiler();

    static final List<String> ATTR_NAMES = asList(OBJ_NAME);
    static final List<String> FIELD_ATTR_NAMES = asList(NAME_NAME, VALUE_NAME);
    private static final Logger log = LoggerFactory.getLogger(AssignTagCompiler.class);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        XplParseHelper.checkArgNames(node, ATTR_NAMES);
        Expression objExpr = XplParseHelper.parseAttrExpr(node, OBJ_NAME, cp, scope);
        if (!node.hasChild())
            throw new NopEvalException(ERR_XPL_ASSIGN_NO_FIELDS).param(ARG_NODE, node);

        List<Expression> ret = new ArrayList<>();
        String varName = scope.generateVarName(XLangConstants.TEMP_VAR_PREFIX);
        Identifier var = Identifier.valueOf(node.attrLoc(OBJ_NAME), varName);
        Expression notNulValue = ChainExpression.valueOf(node.getLocation(), objExpr, false);
        ret.add(XLangASTBuilder.varDecl(var.getLocation(), var, notNulValue));

        Map<String, XNode> fields = new HashMap<>();
        for (XNode child : node.getChildren()) {
            XplParseHelper.checkArgNames(child, FIELD_ATTR_NAMES);
            if (!child.getTagName().equals(FIELD_NAME))
                throw new NopEvalException(ERR_XPL_ASSIGN_BODY_ONLY_ALLOW_FIELD).param(ARG_NODE, child);

            Identifier name = requireAttrIdentifier(child, NAME_NAME, cp, scope);

            if (fields.putIfAbsent(name.getName(), child) != null)
                throw new NopEvalException(ERR_XPL_ASSIGN_DUPLICATE_FIELD).param(ARG_NODE, child).param(ARG_OTHER_NODE, fields.get(name.getName()));

            Expression valueExpr = XplParseHelper.parseAttrTemplateExpr(child, VALUE_NAME, cp, scope);

            MemberExpression member = MemberExpression.valueOf(child.getLocation(), var.deepClone(), name, false);
            AssignmentExpression assignment = AssignmentExpression.valueOf(child.getLocation(), member, XLangOperator.ASSIGN, valueExpr);
            ret.add(assignment);
        }

        return SequenceExpression.valueOf(node.getLocation(), ret);
    }
}

