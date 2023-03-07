package io.nop.task.model._gen;

import io.nop.core.lang.json.IJsonHandler;


/**
 * generate from [138:22:0:0]/nop/schema/flow/task.xdef <p>
 * 可能用于全局跳转，因此不使用嵌套步骤定义
 */
public abstract class _TaskChooseStepCaseModel extends io.nop.core.resource.component.AbstractComponentModel {

    /**
     * xml name: to
     */
    private java.lang.String _to;

    /**
     * xml name: when
     */
    private java.lang.String _when;

    /**
     * xml name: to
     */

    public java.lang.String getTo() {
        return _to;
    }


    public void setTo(java.lang.String value) {
        checkAllowChange();

        this._to = value;

    }


    /**
     * xml name: when
     */

    public java.lang.String getWhen() {
        return _when;
    }


    public void setWhen(java.lang.String value) {
        checkAllowChange();

        this._when = value;

    }


    public void freeze(boolean cascade) {
        if (frozen()) return;
        super.freeze(cascade);

        if (cascade) {

        }
    }

    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);

        out.put("to", this.getTo());
        out.put("when", this.getWhen());
    }
}
