package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptInputParseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptInputParseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: parser
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parser ;
    
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
     * xml name: parser
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getParser(){
      return _parser;
    }

    
    public void setParser(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._parser = value;
           
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
        
        out.putNotNull("blockBegin",this.getBlockBegin());
        out.putNotNull("blockEnd",this.getBlockEnd());
        out.putNotNull("parser",this.getParser());
    }

    public PromptInputParseModel cloneInstance(){
        PromptInputParseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptInputParseModel instance){
        super.copyTo(instance);
        
        instance.setBlockBegin(this.getBlockBegin());
        instance.setBlockEnd(this.getBlockEnd());
        instance.setParser(this.getParser());
    }

    protected PromptInputParseModel newInstance(){
        return (PromptInputParseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
