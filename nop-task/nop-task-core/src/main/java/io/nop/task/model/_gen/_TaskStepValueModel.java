package io.nop.task.model._gen;

import io.nop.core.lang.json.IJsonHandler;


/**
 * generate from [18:10:0:0]/nop/schema/flow/task.xdef <p>
 */
public abstract class _TaskStepValueModel extends io.nop.core.resource.component.AbstractComponentModel {

    /**
     * xml name: displayName
     */
    private java.lang.String _displayName;

    /**
     * xml name: name
     */
    private java.lang.String _name;

    /**
     * xml name: value
     */
    private io.nop.core.lang.eval.IEvalAction _value;

    /**
     * xml name: displayName
     */

    public java.lang.String getDisplayName() {
        return _displayName;
    }


    public void setDisplayName(java.lang.String value) {
        checkAllowChange();

        this._displayName = value;

    }


    /**
     * xml name: name
     */

    public java.lang.String getName() {
        return _name;
    }


    public void setName(java.lang.String value) {
        checkAllowChange();

        this._name = value;

    }


    /**
     * xml name: value
     */

    public io.nop.core.lang.eval.IEvalAction getValue() {
        return _value;
    }


    public void setValue(io.nop.core.lang.eval.IEvalAction value) {
        checkAllowChange();

        this._value = value;

    }


    public void freeze(boolean cascade) {
        if (frozen()) return;
        super.freeze(cascade);

        if (cascade) {

        }
    }

    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);

        out.put("displayName", this.getDisplayName());
        out.put("name", this.getName());
        out.put("value", this.getValue());
    }
}
