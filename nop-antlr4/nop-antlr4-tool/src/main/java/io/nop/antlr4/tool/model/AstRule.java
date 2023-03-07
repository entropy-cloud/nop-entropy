/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr4.tool.model;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * 语法节点规则，一般对应于一个AST语法节点类。当isList为true时对应一个语法节点列表。 例如 parameterList :: e=param (',' e=param)*; 解析得到的为列表对象
 */
public class AstRule extends GrammarElement {
    /**
     * 语法规则名或者分支的label名
     */
    private String ruleName;

    /**
     * 抽象语法树节点类名。当语法规则对应于单个AST节点时，ruleName应该对应于astNodeName或者格式为astNodeName+'_'+其他区分标识。
     */
    private String astNodeName;

    /**
     * 仅起到分组作用的规则，它的每个alternative都是一个分支规则引用
     */
    private boolean group;

    private boolean childHasAltLabel;

    /**
     * 节点规则可能解析得到一个语法树节点。属性名为altLabel名规范化后的结果
     */
    private Map<String, GrammarElement> properties = new LinkedHashMap<>();

    /**
     * 节点规则可能解析得到一个列表，列表中每个元素的类型必须是一致的。
     */
    private RuleRef element;

    private String elementAstNodeName;

    /**
     * 本身不对应于抽象语法树节点，仅作为辅助规则存在。实际解析结果由innerRule决定
     */
    private RuleRef innerRule;

    /**
     * 所有的分支都是终端节点
     */
    private boolean singleTerminal;

    /**
     * 当语法规则由单个TerminalNode构成时，对应的AST节点对象只有唯一的属性。在g4文件中通过 astProp这个选项来指定属性名。例如
     *
     * <pre>
     * literal
     * options{ astProp=value; }
     * : StringLiteral
     *  | NumberLiteral;
     * </pre>
     * <p>
     * 解析得到的Literal节点具有唯一属性value，value的值对应于StringLiteral或者NumberLiteral
     */
    private String astProp;

    private String astPropType;

    private Map<String, String> astAssigns = Collections.emptyMap();

    public String toString() {
        return "AstRule[ruleName=" + ruleName + ",altLabel=" + getAltLabel() + "]";
    }

    public GrammarElementKind getKind() {
        return GrammarElementKind.RULE;
    }

    public boolean isChildHasAltLabel() {
        return childHasAltLabel;
    }

    public void setChildHasAltLabel(boolean childHasAltLabel) {
        this.childHasAltLabel = childHasAltLabel;
    }

    public boolean isList() {
        return element != null;
    }

    public boolean isReturnList() {
        if (isList())
            return true;
        if (innerRule != null)
            return innerRule.isReturnList();
        return false;
    }

    public Map<String, String> getAstAssigns() {
        return astAssigns;
    }

    public void setAstAssigns(Map<String, String> astAssigns) {
        this.astAssigns = astAssigns;
    }

    /**
     * 不属于ast语法树的节点，例如标记statement结束的eos__
     */
    public boolean notInAst() {
        return ruleName.endsWith("__");
    }

    /**
     * visit函数的返回值对应的astNodeName
     */
    public String getReturnAstNodeName() {
        // 如果本身返回ast节点，则优先使用innerRule的返回类型
        if (astNodeName != null) {
            if (innerRule != null)
                return innerRule.getAstNodeName();
        }
        return astNodeName;
    }

    public RuleRef getInnerRule() {
        return innerRule;
    }

    public void setInnerRule(RuleRef innerRule) {
        this.innerRule = innerRule;
    }

    public boolean hasProperty() {
        return !properties.isEmpty();
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean group) {
        this.group = group;
    }

    public String getParseFuncName() {
        if (getReturnAstNodeName() != null)
            return "visit" + StringHelper.capitalize(getAltLabelOrRuleName());
        return "build" + StringHelper.capitalize(getAltLabelOrRuleName());
    }

    public String getParseFuncDecl() {
        return getReturnType() + " " + getParseFuncName() + "(" + getContextName() + " ctx)";
    }

    public String getReturnType() {
        if (super.getReturnType() != null)
            return super.getReturnType();

        if (innerRule != null)
            return innerRule.getReturnType();
        return null;
    }

    public XNode toNode() {
        XNode node = super.toNode();
        node.setAttr("ruleName", ruleName);
        if (astNodeName != null)
            node.setAttr("astNodeName", astNodeName);
        if (astProp != null)
            node.setAttr("astProp", astProp);

        if (singleTerminal)
            node.setAttr("singleTerminal", true);

        if (element != null) {
            node.setAttr("element", element.getRuleName());
        }

        if (innerRule != null) {
            node.setAttr("innerRule", innerRule.getRuleName());
        }

        if (!properties.isEmpty())
            node.setAttr("props", StringHelper.join(new TreeSet<>(properties.keySet()), ","));

        return node;
    }

    public String getAstProp() {
        return astProp;
    }

    public void setAstProp(String astProp) {
        this.astProp = astProp;
    }

    public String getContextName() {
        return StringHelper.capitalize(getAltLabelOrRuleName()) + "Context";
    }

    public RuleRef getElement() {
        return element;
    }

    public void setElement(RuleRef element) {
        element.setParentRule(this);
        this.element = element;
    }

    public String getAstNodeName() {
        return astNodeName;
    }

    public void setAstNodeName(String astNodeName) {
        this.astNodeName = astNodeName;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getAltLabelOrRuleName() {
        if (getAltLabel() != null)
            return getAltLabel();
        return getRuleName();
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Map<String, GrammarElement> getProperties() {
        return properties;
    }

    public GrammarElement getProperty(String name) {
        return properties.get(name);
    }

    public void setProperties(Map<String, GrammarElement> properties) {
        this.properties = properties;
    }

    public String getAstPropType() {
        return astPropType;
    }

    public void setAstPropType(String astPropType) {
        this.astPropType = astPropType;
    }

    public String getAstPropParseFuncName() {
        if (astProp == null)
            return null;
        return getAstNodeName() + "_" + astProp;
    }

    public String getAstPropParseFuncDecl() {
        if (astProp == null)
            return null;
        return getAstPropType() + ' ' + getAstNodeName() + "_" + astProp + "(ParseTree node)";
    }

    public void addProperty(String propName, GrammarElement node) {
        node.setParentRule(this);
        Guard.notEmpty(node.getPropName(), "propName");
        Guard.notEmpty(node.getPropLabel(), "propLabel");
        this.properties.put(propName, node);
    }

    public boolean isSingleTerminal() {
        return singleTerminal;
    }

    public void setSingleTerminal(boolean singleTerminal) {
        this.singleTerminal = singleTerminal;
    }

    public void setElementAstNodeName(String elementAstNodeName) {
        this.elementAstNodeName = elementAstNodeName;
    }

    public String getElementAstNodeName() {
        if (elementAstNodeName != null)
            return elementAstNodeName;
        if (element == null)
            return null;
        return element.getReturnAstNodeName();
    }
}