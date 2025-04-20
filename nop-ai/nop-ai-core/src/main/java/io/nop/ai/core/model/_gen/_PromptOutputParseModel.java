package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptOutputParseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 没有指定format的情况下才会使用parseFromResponse配置
 * 如果指定了source，则执行代码来解析变量。如果没有指定source，但是指定了blockBegin和blockEnd，则从响应消息中截取相关信息。
 * 如果以上配置都没有，但是配置了contains，则只要响应消息中包含此字符串，就设置为true。
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
     * xml name: includeBlockBegin
     * 如果为true，则将blockBegin包含在解析结果中
     */
    private boolean _includeBlockBegin  = false;
    
    /**
     *  
     * xml name: includeBlockEnd
     * 
     */
    private boolean _includeBlockEnd  = false;
    
    /**
     *  
     * xml name: parser
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parser ;
    
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
     * xml name: includeBlockBegin
     *  如果为true，则将blockBegin包含在解析结果中
     */
    
    public boolean isIncludeBlockBegin(){
      return _includeBlockBegin;
    }

    
    public void setIncludeBlockBegin(boolean value){
        checkAllowChange();
        
        this._includeBlockBegin = value;
           
    }

    
    /**
     * 
     * xml name: includeBlockEnd
     *  
     */
    
    public boolean isIncludeBlockEnd(){
      return _includeBlockEnd;
    }

    
    public void setIncludeBlockEnd(boolean value){
        checkAllowChange();
        
        this._includeBlockEnd = value;
           
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
        
        out.putNotNull("beginBlockOptional",this.isBeginBlockOptional());
        out.putNotNull("blockBegin",this.getBlockBegin());
        out.putNotNull("blockEnd",this.getBlockEnd());
        out.putNotNull("contains",this.getContains());
        out.putNotNull("includeBlockBegin",this.isIncludeBlockBegin());
        out.putNotNull("includeBlockEnd",this.isIncludeBlockEnd());
        out.putNotNull("parser",this.getParser());
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
        instance.setIncludeBlockBegin(this.isIncludeBlockBegin());
        instance.setIncludeBlockEnd(this.isIncludeBlockEnd());
        instance.setParser(this.getParser());
    }

    protected PromptOutputParseModel newInstance(){
        return (PromptOutputParseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
