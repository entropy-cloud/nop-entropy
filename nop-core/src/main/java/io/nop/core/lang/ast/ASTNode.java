/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.ast;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ProcessResult;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.mutable.MutableBoolean;
import io.nop.commons.mutable.MutableValue;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.nop.core.CoreErrors.ARG_AST_NODE;
import static io.nop.core.CoreErrors.ARG_MAX_ITEMS;
import static io.nop.core.CoreErrors.ARG_MIN_ITEMS;
import static io.nop.core.CoreErrors.ARG_OLD_PARENT_NODE;
import static io.nop.core.CoreErrors.ARG_PARENT_NODE;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_LANG_AST_IS_READ_ONLY;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_NOT_ALLOW_MULTIPLE_PARENT;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_PROP_NOT_ALLOW_EMPTY;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_PROP_NO_ENOUGH_ITEMS;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_PROP_TOO_MANY_ITEMS;

/**
 * 通用的AST语法树结构支持。树节点具有父节点，并可以遍历所有子节点。
 */
public abstract class ASTNode<N extends ASTNode<N>> implements IASTNode, IFreezable {
    static final Logger LOG = LoggerFactory.getLogger(ASTNode.class);

    private static final long serialVersionUID = 7921116749502255465L;

    private SourceLocation location;
    // private N parent;

    private String leadingComment;
    private String trailingComment;
    private boolean frozen;

    private N parent;

    @Override
    public boolean frozen() {
        return frozen;
    }

    public void freeze(boolean cascade) {
        this.frozen = true;
    }

    public String toString() {
        return getASTType() + "@" + this.getLocation();
    }

    protected void checkAllowChange() {
        if (frozen)
            throw new NopException(ERR_LANG_AST_IS_READ_ONLY).loc(location).param(ARG_AST_NODE, this);
    }

    public abstract String getASTType();

    public String getDisplayString() {
        return toString();
    }

    public N getASTParent() {
        return parent;
    }

    public N getASTParentParent() {
        if (parent == null)
            return null;
        return parent.getASTParent();
    }

    public void setASTParent(N parent) {
        checkAllowChange();
        if (this.parent != parent && parent != null && this.parent != null) {
            throw new NopEvalException(ERR_LANG_AST_NODE_NOT_ALLOW_MULTIPLE_PARENT).param(ARG_AST_NODE, this)
                    .param(ARG_PARENT_NODE, parent).param(ARG_OLD_PARENT_NODE, this.parent);
        }
        this.parent = parent;
    }

    /**
     * 判断两个节点是否等价。等价的节点要求具有同样的类型和等价的节点属性
     */
    public abstract boolean isEquivalentTo(N node);

    protected boolean isValueEquivalent(Object v1, Object v2) {
        if (v1 == v2)
            return true;
        if (v1 == null)
            return StringHelper.isEmptyObject(v2);
        if (v2 == null)
            return StringHelper.isEmptyObject(v1);
        return v1.equals(v2);
    }

    protected boolean isNodeEquivalent(N nodeA, N nodeB) {
        if (nodeA == nodeB)
            return true;
        if (nodeA == null || nodeB == null)
            return false;
        return nodeA.isEquivalentTo(nodeB);
    }

    protected <V extends IASTNode> boolean isListEquivalent(List<V> list, List<V> other) {
        if (list == other)
            return true;

        int countA = list == null ? 0 : list.size();
        int countB = other == null ? 0 : other.size();
        if (countA != countB)
            return false;

        if (countA > 0) {
            for (int i = 0; i < countA; i++) {
                N a = (N) list.get(i);
                N b = (N) other.get(i);
                if (!a.isEquivalentTo(b))
                    return false;
            }
        }

        return true;
    }

    abstract public N deepClone();

    protected void copyExtFieldsTo(ASTNode node) {

    }

    /**
     * 与父节点解除关联，返回当前节点
     */
    public N detach() {
        if (parent != null)
            parent.removeChild((N) this);
        setASTParent(null);

        return (N) this;
    }

    /**
     * 遍历所有子节点, 返回为stop时不再继续遍历
     *
     * @param processor 处理函数
     */
    public abstract ProcessResult processChild(Function<N, ProcessResult> processor);

    public N nextSibling() {
        return nextSibling(null);
    }

    public N nextSibling(Predicate<N> filter) {
        MutableBoolean b = new MutableBoolean();
        MutableValue<N> ret = new MutableValue<>();
        processChild(child -> {
            if (b.get()) {
                if (filter == null || filter.test(child)) {
                    ret.setValue(child);
                    return ProcessResult.STOP;
                } else {
                    return ProcessResult.CONTINUE;
                }
            }

            if (child == this) {
                b.set(true);
            }
            return ProcessResult.CONTINUE;
        });
        return ret.getValue();
    }

    public N prevSibling() {
        return prevSibling(null);
    }

    public N prevSibling(Predicate<N> filter) {
        MutableValue<N> ret = new MutableValue<>();
        processChild(child -> {
            if (child == this) {
                return ProcessResult.STOP;
            }
            if (filter == null || filter.test(child)) {
                ret.setValue(child);
            }
            return ProcessResult.CONTINUE;
        });
        return ret.getValue();
    }

    public abstract void forEachChild(Consumer<N> consumer);

    public abstract boolean removeChild(N child);

    /**
     * 将oldChild节点替换为新的子节点newChild
     *
     * @param oldChild 待替换的子节点
     * @param newChild 新节点应该不是其他节点的子节点，它的parent应该为null
     * @return true表示已替换。false表示没有找到对应的oldChild，替换失败
     */
    public boolean replaceChild(N oldChild, N newChild) {
        return false;
    }

    protected <V> List<V> replaceInList(List<V> list, int index, N newChild) {
        list.set(index, (V) newChild);
        return list;
    }

    protected <V> List<V> removeInList(List<V> list, int index) {
        list.remove(index);
        return list;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        checkAllowChange();
        this.location = location;
    }

    public String getLeadingComment() {
        return leadingComment;
    }

    public void setLeadingComment(String leadingComment) {
        checkAllowChange();
        this.leadingComment = leadingComment;
    }

    public String getTrailingComment() {
        return trailingComment;
    }

    public void setTrailingComment(String trailingComment) {
        checkAllowChange();
        this.trailingComment = trailingComment;
    }

    @Override
    public final void serializeToJson(IJsonHandler out) {
        out.beginObject(null);
        out.key("$type").value(null, getASTType());
        if (location != null)
            out.key("$loc").value(null, location.toString());
        if (leadingComment != null)
            out.key("leadingComment").value(null, leadingComment);
        if (trailingComment != null)
            out.key("trailingComment").value(null, trailingComment);
        serializeFields(out);
        out.endObject();
    }

    protected abstract void serializeFields(IJsonHandler out);

    /**
     * 确定是否需要增加括号来明确算符的结合顺序。当内部算符的结合优先级低于外部算符时需要使用括号。
     *
     * @param lp 左侧算符优先级。数值越小优先级越高
     * @param rp 右侧算符优先级
     * @return 是否需要增加括号
     */
    public boolean requireParentheses(int lp, int rp) {
        // f (lprec < local_lprec
        // && local_rprec >= rprec) {
        // return false;
        // }
        return false;
    }

    /**
     * 设置完AST节点属性后首先调用normalize函数，用于将节点属性规范化为标准形式，减少后续处理时需要识别的场景。 比如对于 {a}这种json属性的简化表达方式被规范化为 {"a":a}
     */
    public void normalize() {

    }

    /**
     * 创建AST节点并调用完属性设置方法之后调用此函数来验证语法树节点初始化正确，例如检查成员变量不为null等。
     */
    public void validate() {

    }

    public void dump(String title) {
        LOG.info("{}={}", title, JsonTool.stringify(this, null, "  "));
    }

    protected void checkMandatory(String propName, Object value) {
        if (StringHelper.isEmptyObject(value))
            throw new NopException(ERR_LANG_AST_NODE_PROP_NOT_ALLOW_EMPTY).loc(getLocation())
                    .param(ARG_AST_NODE, getASTType()).param(ARG_PROP_NAME, propName);
    }

    protected void checkMinItems(String propName, Collection<?> coll, int minItems) {
        if (coll == null || coll.size() <= minItems)
            throw new NopException(ERR_LANG_AST_NODE_PROP_NO_ENOUGH_ITEMS).loc(getLocation())
                    .param(ARG_AST_NODE, getASTType()).param(ARG_PROP_NAME, propName).param(ARG_MIN_ITEMS, minItems);
    }

    protected void checkMaxItems(String propName, Collection<?> coll, int maxItems) {
        if (coll == null || coll.size() > maxItems)
            throw new NopException(ERR_LANG_AST_NODE_PROP_TOO_MANY_ITEMS).loc(getLocation())
                    .param(ARG_AST_NODE, getASTType()).param(ARG_PROP_NAME, propName).param(ARG_MAX_ITEMS, maxItems);
    }
}