package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtCopyNodeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtCopyNodeModel extends io.nop.xlang.xt.model.XtRuleModel {
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
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
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
    }

    
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
        
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("xpath",this.getXpath());
        out.putNotNull("xtType",this.getXtType());
    }

    public XtCopyNodeModel cloneInstance(){
        XtCopyNodeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtCopyNodeModel instance){
        super.copyTo(instance);
        
        instance.setMandatory(this.isMandatory());
        instance.setXpath(this.getXpath());
        instance.setXtType(this.getXtType());
    }

    protected XtCopyNodeModel newInstance(){
        return (XtCopyNodeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
