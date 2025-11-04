package io.nop.xlang.xt.model._gen;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtApplyMappingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 应用指定mapping
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtApplyMappingModel extends io.nop.xlang.xt.model.XtRuleGroupModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: xpath
     * 选择目标节点，只会匹配第一个满足要求的节点。如果要对多个节点进行处理，需要使用xt:each
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
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
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
     *  选择目标节点，只会匹配第一个满足要求的节点。如果要对多个节点进行处理，需要使用xt:each
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
        
        out.putNotNull("id",this.getId());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("xpath",this.getXpath());
        out.putNotNull("xtType",this.getXtType());
    }

    public XtApplyMappingModel cloneInstance(){
        XtApplyMappingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtApplyMappingModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setMandatory(this.isMandatory());
        instance.setXpath(this.getXpath());
        instance.setXtType(this.getXtType());
    }

    protected XtApplyMappingModel newInstance(){
        return (XtApplyMappingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
