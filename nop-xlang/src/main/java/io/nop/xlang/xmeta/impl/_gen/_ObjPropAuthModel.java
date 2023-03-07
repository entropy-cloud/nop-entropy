package io.nop.xlang.xmeta.impl._gen;

import io.nop.core.lang.json.IJsonHandler;

/**
 * generate from [49:10:0:0]/nop/schema/schema/obj-schema.xdef
 * <p>
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _ObjPropAuthModel extends io.nop.core.resource.component.AbstractComponentModel {

    /**
     * xml name: permissions
     */
    private java.util.Set<java.lang.String> _permissions;

    /**
     * xml name: roles
     */
    private java.util.Set<java.lang.String> _roles;

    /**
     * xml name: permissions
     */

    public java.util.Set<java.lang.String> getPermissions() {
        return _permissions;
    }

    public void setPermissions(java.util.Set<java.lang.String> value) {
        checkAllowChange();

        this._permissions = value;

    }

    /**
     * xml name: roles
     */

    public java.util.Set<java.lang.String> getRoles() {
        return _roles;
    }

    public void setRoles(java.util.Set<java.lang.String> value) {
        checkAllowChange();

        this._roles = value;

    }

    public void freeze(boolean cascade) {
        if (frozen())
            return;
        super.freeze(cascade);

        if (cascade) {

        }
    }

    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);

        out.put("permissions", this.getPermissions());
        out.put("roles", this.getRoles());
    }
}
