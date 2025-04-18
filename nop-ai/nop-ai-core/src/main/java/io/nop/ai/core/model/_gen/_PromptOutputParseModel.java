package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptOutputParseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptOutputParseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: beginBlockOptional
     * 
     */
    private boolean _beginBlockOptional  = false;
    
    /**
     *  
     * xml name: blockBegin
     * 
     */
    private java.lang.String _blockBegin ;
    
    /**
     *  
     * xml name: blockEnd
     * 
     */
    private java.lang.String _blockEnd ;
    
    /**
     *  
     * xml name: contains
     * 
     */
    private java.lang.String _contains ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _source ;
    
    /**
     * 
     * xml name: beginBlockOptional
     *  
     */
    
    public boolean isBeginBlockOptional(){
      return _beginBlockOptional;
    }

    
    public void setBeginBlockOptional(boolean value){
        checkAllowChange();
        
        this._beginBlockOptional = value;
           
    }

    
    /**
     * 
     * xml name: blockBegin
     *  
     */
    
    public java.lang.String getBlockBegin(){
      return _blockBegin;
    }

    
    public void setBlockBegin(java.lang.String value){
        checkAllowChange();
        
        this._blockBegin = value;
           
    }

    
    /**
     * 
     * xml name: blockEnd
     *  
     */
    
    public java.lang.String getBlockEnd(){
      return _blockEnd;
    }

    
    public void setBlockEnd(java.lang.String value){
        checkAllowChange();
        
        this._blockEnd = value;
           
    }

    
    /**
     * 
     * xml name: contains
     *  
     */
    
    public java.lang.String getContains(){
      return _contains;
    }

    
    public void setContains(java.lang.String value){
        checkAllowChange();
        
        this._contains = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.putNotNull("beginBlockOptional",this.isBeginBlockOptional());
        out.putNotNull("blockBegin",this.getBlockBegin());
        out.putNotNull("blockEnd",this.getBlockEnd());
        out.putNotNull("contains",this.getContains());
        out.putNotNull("source",this.getSource());
    }

    public PromptOutputParseModel cloneInstance(){
        PromptOutputParseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptOutputParseModel instance){
        super.copyTo(instance);
        
        instance.setBeginBlockOptional(this.isBeginBlockOptional());
        instance.setBlockBegin(this.getBlockBegin());
        instance.setBlockEnd(this.getBlockEnd());
        instance.setContains(this.getContains());
        instance.setSource(this.getSource());
    }

    protected PromptOutputParseModel newInstance(){
        return (PromptOutputParseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
