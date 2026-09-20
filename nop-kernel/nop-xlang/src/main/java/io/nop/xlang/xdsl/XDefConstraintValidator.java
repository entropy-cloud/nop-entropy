/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefCheckScope;
import io.nop.xlang.xdef.impl.XDefAbstractCheck;
import io.nop.xlang.xdef.impl.XDefCheckMutex;
import io.nop.xlang.xdef.impl.XDefCheckRef;
import io.nop.xlang.xdef.impl.XDefCheckRequire;
import io.nop.xlang.xdef.impl.XDefCheckUnique;
import io.nop.xlang.xpath.XPathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_ATTR_VALUE;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_NODE_A;
import static io.nop.xlang.XLangErrors.ARG_NODE_B;
import static io.nop.xlang.XLangErrors.ARG_RULE_ID;
import static io.nop.xlang.XLangErrors.ERR_XDEF_CHECK_NOT_IMPLEMENTED;
import static io.nop.xlang.XLangErrors.ERR_XDSL_CHECK_MUTEX_VIOLATION;
import static io.nop.xlang.XLangErrors.ERR_XDSL_CHECK_REQUIRE_VIOLATION;
import static io.nop.xlang.XLangErrors.ERR_XDSL_CHECK_RULE_NO_KEY_PROP;
import static io.nop.xlang.XLangErrors.ERR_XDSL_CHECK_UNIQUE_VIOLATION;

/**
 * xdef文档级声明式约束规则的执行器，在{@link XDslValidator#validate}尾部对合并清理后的最终树执行。
 * 只读断言、不修改树、可重复执行（幂等）。select编译走{@link XPathHelper}静态缓存，无约束声明时零开销直通。
 */
public class XDefConstraintValidator {

    /**
     * condition表达式中绑定的当前节点变量名
     */
    public static final String SCOPE_VAR_NODE = "node";

    public void validate(XNode rootNode, IXDefinition xdef) {
        List<XDefCheckUnique> uniques = xdef.getXdefCheckUniques();
        List<XDefCheckMutex> mutexes = xdef.getXdefCheckMutexs();
        List<XDefCheckRequire> requires = xdef.getXdefCheckRequires();
        List<XDefCheckRef> refs = xdef.getXdefCheckRefs();

        if (uniques.isEmpty() && mutexes.isEmpty() && requires.isEmpty() && refs.isEmpty())
            return;

        if (!uniques.isEmpty())
            checkUniques(rootNode, xdef, uniques);

        if (!mutexes.isEmpty())
            checkMutexes(rootNode, mutexes);

        if (!requires.isEmpty())
            checkRequires(rootNode, requires);

        // check-ref的执行属P1规划。解析期成功填充（供工具链机读），实例校验期显式fail-loud
        if (!refs.isEmpty()) {
            XDefCheckRef ref = refs.get(0);
            throw new NopException(ERR_XDEF_CHECK_NOT_IMPLEMENTED).param(ARG_RULE_ID, ref.getId())
                    .param(ARG_ATTR_VALUE, "xdef:check-ref").loc(ref.getLocation());
        }
    }

    private void checkUniques(XNode rootNode, IXDefinition xdef, List<XDefCheckUnique> checks) {
        for (XDefCheckUnique check : checks) {
            List<XNode> nodes = selectNodes(rootNode, check);
            if (check.getScope() == XDefCheckScope.siblings) {
                // 按父节点分桶，桶内查重
                Map<XNode, Map<String, XNode>> buckets = new IdentityHashMap<>();
                for (XNode node : nodes) {
                    XNode parent = node.getParent();
                    Map<String, XNode> bucket = buckets.computeIfAbsent(parent, k -> new HashMap<>());
                    checkUniqueInBucket(node, bucket, check, xdef);
                }
            } else {
                // 缺省document：全部选中节点构成单桶
                Map<String, XNode> bucket = new HashMap<>();
                for (XNode node : nodes) {
                    checkUniqueInBucket(node, bucket, check, xdef);
                }
            }
        }
    }

    private void checkUniqueInBucket(XNode node, Map<String, XNode> bucket, XDefCheckUnique check, IXDefinition xdef) {
        String propName = resolveUniqueProp(node, check, xdef);
        String keyValue = node.attrText(propName);
        // 空key跳过，与XDslValidator.checkUniqueAttr的既有行为一致
        if (StringHelper.isEmpty(keyValue))
            return;

        XNode oldNode = bucket.put(keyValue, node);
        if (oldNode != null) {
            throw newViolation(ERR_XDSL_CHECK_UNIQUE_VIOLATION, check, node).param(ARG_ATTR_NAME, propName)
                    .param(ARG_ATTR_VALUE, keyValue).param(ARG_NODE_A, oldNode).param(ARG_NODE_B, node);
        }
    }

    /**
     * prop缺省回退：选中节点def的unique-attr优先，其次父def的key-attr在该子def上声明的同名属性
     * （key-attr是父节点作用域的声明）。两者都缺时属于规则配置错误，显式报错。
     */
    private String resolveUniqueProp(XNode node, XDefCheckUnique check, IXDefinition xdef) {
        String prop = check.getProp();
        if (!StringHelper.isEmpty(prop))
            return prop;

        IXDefNode defNode = locateDefNode(xdef, node);
        if (defNode != null) {
            String uniqueAttr = defNode.getXdefUniqueAttr();
            if (!StringHelper.isEmpty(uniqueAttr))
                return uniqueAttr;

            IXDefNode parentDef = locateDefNode(xdef, node.getParent());
            if (parentDef != null) {
                String keyAttr = parentDef.getXdefKeyAttr();
                if (!StringHelper.isEmpty(keyAttr)) {
                    IXDefAttribute attrDef = defNode.getAttribute(keyAttr);
                    if (attrDef != null)
                        return keyAttr;
                }
            }
        }

        throw new NopException(ERR_XDSL_CHECK_RULE_NO_KEY_PROP).param(ARG_RULE_ID, check.getId())
                .param(ARG_NODE, node).source(node);
    }

    private void checkMutexes(XNode rootNode, List<XDefCheckMutex> checks) {
        for (XDefCheckMutex check : checks) {
            for (XNode node : selectNodes(rootNode, check)) {
                List<String> props = check.getProps();
                if (props == null || props.isEmpty())
                    continue;

                List<String> present = new ArrayList<>();
                for (String prop : props) {
                    if (!StringHelper.isEmpty(node.attrText(prop)))
                        present.add(prop);
                }

                if (present.size() > 1 || (Boolean.TRUE.equals(check.getAtLeastOne()) && present.isEmpty())) {
                    throw newViolation(ERR_XDSL_CHECK_MUTEX_VIOLATION, check, node)
                            .param(ARG_ATTR_VALUE, StringHelper.join(present, ","));
                }
            }
        }
    }

    private void checkRequires(XNode rootNode, List<XDefCheckRequire> checks) {
        IEvalScope scope = XLang.newEvalScope();
        for (XDefCheckRequire check : checks) {
            for (XNode node : selectNodes(rootNode, check)) {
                if (check.getCondition() != null) {
                    scope.setLocalValue(null, SCOPE_VAR_NODE, node);
                    Object condition = check.getCondition().invoke(scope);
                    if (!Boolean.TRUE.equals(condition))
                        continue;
                }

                if (check.getRequiredProps() != null) {
                    for (String prop : check.getRequiredProps()) {
                        if (StringHelper.isEmpty(node.attrText(prop)))
                            throw newViolation(ERR_XDSL_CHECK_REQUIRE_VIOLATION, check, node)
                                    .param(ARG_ATTR_NAME, prop);
                    }
                }
                if (check.getForbiddenProps() != null) {
                    for (String prop : check.getForbiddenProps()) {
                        if (!StringHelper.isEmpty(node.attrText(prop)))
                            throw newViolation(ERR_XDSL_CHECK_REQUIRE_VIOLATION, check, node)
                                    .param(ARG_ATTR_NAME, prop);
                    }
                }
            }
        }
    }

    private List<XNode> selectNodes(XNode rootNode, XDefAbstractCheck check) {
        IXSelector<XNode> selector = XPathHelper.parseXSelector(check.getSelect());
        Collection<?> result = rootNode.selectMany(selector);
        List<XNode> nodes = new ArrayList<>(result.size());
        for (Object item : result) {
            if (item instanceof XNode)
                nodes.add((XNode) item);
        }
        return nodes;
    }

    /**
     * 从选中节点沿父链上行收集tag路径，再从xdef根def逐层getChild下探（未命中自动回落unknown-tag）。
     * tags包含根tag自身，下探从根def之下的第一层开始（即跳过tags最后一个元素）。
     */
    private IXDefNode locateDefNode(IXDefinition xdef, XNode node) {
        if (node == null)
            return null;

        List<String> tags = new ArrayList<>();
        for (XNode n = node; n != null; n = n.getParent()) {
            tags.add(n.getTagName());
        }

        IXDefNode defNode = xdef.getRootNode();
        for (int i = tags.size() - 2; i >= 0; i--) {
            if (defNode == null)
                return null;
            defNode = defNode.getChild(tags.get(i));
        }
        return defNode;
    }

    /**
     * 声明了errorCode时构造动态错误码（status取-1，与既有ERR_XDSL_*系一致），message声明覆盖默认文案
     */
    private NopException newViolation(ErrorCode defaultCode, XDefAbstractCheck check, XNode node) {
        ErrorCode errorCode = defaultCode;
        if (!StringHelper.isEmpty(check.getErrorCode())) {
            String message = StringHelper.isEmpty(check.getMessage()) ? defaultCode.getDescription()
                    : check.getMessage();
            errorCode = new ErrorCode(defaultCode.getStatus(), check.getErrorCode(), message);
        }
        return new NopException(errorCode).param(ARG_RULE_ID, check.getId()).source(node);
    }
}
