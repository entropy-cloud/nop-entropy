package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [81:10:0:0]/nop/schema/xui/form.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _UiFormRuleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: message
     * 
     */
    private java.lang.String _message ;
    
    /**
     *  
     * xml name: name
     * 验证失败时需要高亮的表单项
     */
    private java.util.Set<java.lang.String> _name ;
    
    /**
     *  
     * xml name: rule
     * 
     */
    private java.lang.String _rule ;
    
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
     * xml name: message
     *  
     */
    
    public java.lang.String getMessage(){
      return _message;
    }

    
    public void setMessage(java.lang.String value){
        checkAllowChange();
        
        this._message = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  验证失败时需要高亮的表单项
     */
    
    public java.util.Set<java.lang.String> getName(){
      return _name;
    }

    
    public void setName(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: rule
     *  
     */
    
    public java.lang.String getRule(){
      return _rule;
    }

    
    public void setRule(java.lang.String value){
        checkAllowChange();
        
        this._rule = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("id",this.getId());
        out.put("message",this.getMessage());
        out.put("name",this.getName());
        out.put("rule",this.getRule());
    }
}
 // resume CPD analysis - CPD-ON
