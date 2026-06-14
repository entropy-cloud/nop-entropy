package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.PathRuleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PathRuleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: access
     * 
     */
    private java.lang.String _access ;
    
    /**
     *  
     * xml name: pattern
     * 
     */
    private java.lang.String _pattern ;
    
    /**
     * 
     * xml name: access
     *  
     */
    
    public java.lang.String getAccess(){
      return _access;
    }

    
    public void setAccess(java.lang.String value){
        checkAllowChange();
        
        this._access = value;
           
    }

    
    /**
     * 
     * xml name: pattern
     *  
     */
    
    public java.lang.String getPattern(){
      return _pattern;
    }

    
    public void setPattern(java.lang.String value){
        checkAllowChange();
        
        this._pattern = value;
           
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
        
        out.putNotNull("access",this.getAccess());
        out.putNotNull("pattern",this.getPattern());
    }

    public PathRuleModel cloneInstance(){
        PathRuleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PathRuleModel instance){
        super.copyTo(instance);
        
        instance.setAccess(this.getAccess());
        instance.setPattern(this.getPattern());
    }

    protected PathRuleModel newInstance(){
        return (PathRuleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
