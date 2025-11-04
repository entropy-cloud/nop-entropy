package io.nop.xlang.xt.model._gen;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtEachModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 对于xpath选中的一组节点，每一个都应用body段的规则
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtEachModel extends io.nop.xlang.xt.model.XtRuleGroupModel {
    
    /**
     *  
     * xml name: xpath
     * 
     */
    private io.nop.core.lang.xml.IXSelector<io.nop.core.lang.xml.XNode> _xpath ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _xtType ;
    
    /**
     * 
     * xml name: xpath
     *  
     */
    
    public io.nop.core.lang.xml.IXSelector<io.nop.core.lang.xml.XNode> getXpath(){
      return _xpath;
    }

    
    public void setXpath(io.nop.core.lang.xml.IXSelector<io.nop.core.lang.xml.XNode> value){
        checkAllowChange();
        
        this._xpath = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getXtType(){
      return _xtType;
    }

    
    public void setXtType(java.lang.String value){
        checkAllowChange();
        
        this._xtType = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("xpath",this.getXpath());
        out.putNotNull("xtType",this.getXtType());
    }

    public XtEachModel cloneInstance(){
        XtEachModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtEachModel instance){
        super.copyTo(instance);
        
        instance.setXpath(this.getXpath());
        instance.setXtType(this.getXtType());
    }

    protected XtEachModel newInstance(){
        return (XtEachModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
