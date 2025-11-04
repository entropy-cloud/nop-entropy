package io.nop.xlang.ast._gen;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTVisitor;


public abstract class _FunctionDeclaration extends io.nop.xlang.ast.DecoratedDeclaration {

    private io.nop.xlang.ast.Identifier name;

    private java.util.List<io.nop.xlang.ast.ParameterDeclaration> params;

    private io.nop.xlang.ast.NamedTypeNode returnType;

    private boolean resultOptional;

    private io.nop.xlang.ast.Expression body;

    private int modifiers;

    private boolean varArgs;


    public _FunctionDeclaration() {
    }


    public io.nop.xlang.ast.Identifier getName() {
        return name;
    }

    public void setName(io.nop.xlang.ast.Identifier value) {
        checkAllowChange();
        this.name = value;
    }

    public java.util.List<io.nop.xlang.ast.ParameterDeclaration> getParams() {
        return params;
    }

    public void setParams(java.util.List<io.nop.xlang.ast.ParameterDeclaration> value) {
        checkAllowChange();
        this.params = value;
    }

    public io.nop.xlang.ast.NamedTypeNode getReturnType() {
        return returnType;
    }

    public void setReturnType(io.nop.xlang.ast.NamedTypeNode value) {
        checkAllowChange();
        this.returnType = value;
    }

    public boolean getResultOptional() {
        return resultOptional;
    }

    public void setResultOptional(boolean value) {
        checkAllowChange();
        this.resultOptional = value;
    }

    public io.nop.xlang.ast.Expression getBody() {
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value) {
        checkAllowChange();
        this.body = value;
    }

    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int value) {
        checkAllowChange();
        this.modifiers = value;
    }

    public boolean getVarArgs() {
        return varArgs;
    }

    public void setVarArgs(boolean value) {
        checkAllowChange();
        this.varArgs = value;
    }

    public FunctionDeclaration newInstance() {
        return new FunctionDeclaration();
    }

    public FunctionDeclaration cloneInstance() {
        FunctionDeclaration ret = newInstance();
        ret.setLocation(getLocation());
        ret.setLeadingComment(getLeadingComment());
        ret.setTrailingComment(getTrailingComment());

        ret.setName(name);
        ret.setParams(params);
        ret.setReturnType(returnType);
        ret.setResultOptional(resultOptional);
        ret.setBody(body);
        ret.setModifiers(modifiers);
        ret.setVarArgs(varArgs);
        return ret;
    }

    @Override
    public <T> T optimizeBy(XLangASTOptimizer optimizer) {
        return (T) optimizer.optimizeFunctionDeclaration((FunctionDeclaration) this);
    }

    @Override
    public FunctionDeclaration deepClone() {
        FunctionDeclaration ret = newInstance();
        ret.setLocation(getLocation());
        ret.setLeadingComment(getLeadingComment());
        ret.setTrailingComment(getTrailingComment());

        if (name != null) {

            ret.setName(name.deepClone());

        }

        if (params != null) {

            java.util.List<io.nop.xlang.ast.ParameterDeclaration> copy_params = new java.util.ArrayList<>(params.size());
            for (io.nop.xlang.ast.ParameterDeclaration item : params) {
                copy_params.add(item.deepClone());
            }
            ret.setParams(copy_params);

        }

        if (returnType != null) {

            ret.setReturnType(returnType.deepClone());

        }

        ret.setResultOptional(resultOptional);

        if (body != null) {

            ret.setBody(body.deepClone());

        }

        ret.setModifiers(modifiers);

        ret.setVarArgs(varArgs);

        return ret;
    }

    @Override
    protected void accept0(XLangASTVisitor visitor) {
        if (visitor.visitFunctionDeclaration((FunctionDeclaration) this)) {

            this.acceptChild(visitor, name);
            this.acceptChildren(visitor, params);
            this.acceptChild(visitor, returnType);
            this.acceptChild(visitor, body);
        }
        visitor.endVisitFunctionDeclaration((FunctionDeclaration) this);
    }

    @Override
    public XLangASTKind getASTKind() {
        return XLangASTKind.FunctionDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {

        json.put("name", name);

        json.put("params", params);

        json.put("returnType", returnType);

        json.put("resultOptional", resultOptional);

        json.put("body", body);

        json.put("modifiers", modifiers);

        json.put("varArgs", varArgs);

    }

    @Override
    public void freeze(boolean cascade) {
        super.freeze(cascade);

        if (name != null)
            name.freeze(cascade);
        params = io.nop.api.core.util.FreezeHelper.freezeList(params, cascade);
        if (returnType != null)
            returnType.freeze(cascade);
        if (body != null)
            body.freeze(cascade);
    }

}