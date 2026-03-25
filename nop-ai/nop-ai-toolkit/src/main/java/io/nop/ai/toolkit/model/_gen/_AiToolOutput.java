package io.nop.ai.toolkit.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/call-tools-response.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AiToolOutput extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: fromLine
     * 
     */
    private java.lang.Integer _fromLine ;
    
    /**
     *  
     * xml name: path
     * 如果output很多，不能通过响应结果返回，则可以保存到文件中，此后可以通过read-file工具读取
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: toLine
     * 
     */
    private java.lang.Integer _toLine ;
    
    /**
     *  
     * xml name: totalLines
     * 
     */
    private java.lang.Integer _totalLines ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getBody(){
      return _body;
    }

    
    public void setBody(java.lang.String value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: fromLine
     *  
     */
    
    public java.lang.Integer getFromLine(){
      return _fromLine;
    }

    
    public void setFromLine(java.lang.Integer value){
        checkAllowChange();
        
        this._fromLine = value;
           
    }

    
    /**
     * 
     * xml name: path
     *  如果output很多，不能通过响应结果返回，则可以保存到文件中，此后可以通过read-file工具读取
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
    }

    
    /**
     * 
     * xml name: toLine
     *  
     */
    
    public java.lang.Integer getToLine(){
      return _toLine;
    }

    
    public void setToLine(java.lang.Integer value){
        checkAllowChange();
        
        this._toLine = value;
           
    }

    
    /**
     * 
     * xml name: totalLines
     *  
     */
    
    public java.lang.Integer getTotalLines(){
      return _totalLines;
    }

    
    public void setTotalLines(java.lang.Integer value){
        checkAllowChange();
        
        this._totalLines = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("fromLine",this.getFromLine());
        out.putNotNull("path",this.getPath());
        out.putNotNull("toLine",this.getToLine());
        out.putNotNull("totalLines",this.getTotalLines());
    }

    public AiToolOutput cloneInstance(){
        AiToolOutput instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AiToolOutput instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setFromLine(this.getFromLine());
        instance.setPath(this.getPath());
        instance.setToLine(this.getToLine());
        instance.setTotalLines(this.getTotalLines());
    }

    protected AiToolOutput newInstance(){
        return (AiToolOutput) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
