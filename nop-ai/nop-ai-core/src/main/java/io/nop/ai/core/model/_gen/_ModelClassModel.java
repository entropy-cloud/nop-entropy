package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.ModelClassModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/model-class.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ModelClassModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  模型类候选集（声明顺序 = 默认策略的声明序）
     * xml name: candidates
     * 
     */
    private java.util.List<io.nop.ai.core.model.ModelClassCandidateModel> _candidates = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: members
     * 
     */
    private java.util.Set<java.lang.String> _members ;
    
    /**
     * 模型类候选集（声明顺序 = 默认策略的声明序）
     * xml name: candidates
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.ModelClassCandidateModel> getCandidates(){
      return _candidates;
    }

    
    public void setCandidates(java.util.List<io.nop.ai.core.model.ModelClassCandidateModel> value){
        checkAllowChange();
        
        this._candidates = value;
           
    }

    
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
     * xml name: members
     *  
     */
    
    public java.util.Set<java.lang.String> getMembers(){
      return _members;
    }

    
    public void setMembers(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._members = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._candidates = io.nop.api.core.util.FreezeHelper.deepFreeze(this._candidates);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("candidates",this.getCandidates());
        out.putNotNull("id",this.getId());
        out.putNotNull("members",this.getMembers());
    }

    public ModelClassModel cloneInstance(){
        ModelClassModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ModelClassModel instance){
        super.copyTo(instance);
        
        instance.setCandidates(this.getCandidates());
        instance.setId(this.getId());
        instance.setMembers(this.getMembers());
    }

    protected ModelClassModel newInstance(){
        return (ModelClassModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
