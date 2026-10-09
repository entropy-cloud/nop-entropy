package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamWindowModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * Window 节点：窗口。WI10：lateData 声明迟到数据侧输出 tag（可选）——
 * 迟到记录（ts+allowedLateness <= wm 的 skipped 记录）经该 tag 通道发往
 * 下游顶点；消费协议 <edge from="<窗口的 aggregate/reduce 宿主节点>"
 * to="<transform>" outputTag="<tag>">，pairing 校验由
 * validateSideOutputDeclarations 前移把关。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamWindowModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: allowedLateness
     * 
     */
    private java.lang.Long _allowedLateness ;
    
    /**
     *  
     * xml name: earlyResultsPeriod
     * 
     */
    private java.lang.String _earlyResultsPeriod ;
    
    /**
     *  
     * xml name: evictorId
     * 
     */
    private java.lang.String _evictorId ;
    
    /**
     *  
     * xml name: lateData
     * 
     */
    private java.lang.String _lateData ;
    
    /**
     *  
     * xml name: strategyRef
     * 
     */
    private java.lang.String _strategyRef ;
    
    /**
     *  
     * xml name: triggerId
     * 
     */
    private java.lang.String _triggerId ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: allowedLateness
     *  
     */
    
    public java.lang.Long getAllowedLateness(){
      return _allowedLateness;
    }

    
    public void setAllowedLateness(java.lang.Long value){
        checkAllowChange();
        
        this._allowedLateness = value;
           
    }

    
    /**
     * 
     * xml name: earlyResultsPeriod
     *  
     */
    
    public java.lang.String getEarlyResultsPeriod(){
      return _earlyResultsPeriod;
    }

    
    public void setEarlyResultsPeriod(java.lang.String value){
        checkAllowChange();
        
        this._earlyResultsPeriod = value;
           
    }

    
    /**
     * 
     * xml name: evictorId
     *  
     */
    
    public java.lang.String getEvictorId(){
      return _evictorId;
    }

    
    public void setEvictorId(java.lang.String value){
        checkAllowChange();
        
        this._evictorId = value;
           
    }

    
    /**
     * 
     * xml name: lateData
     *  
     */
    
    public java.lang.String getLateData(){
      return _lateData;
    }

    
    public void setLateData(java.lang.String value){
        checkAllowChange();
        
        this._lateData = value;
           
    }

    
    /**
     * 
     * xml name: strategyRef
     *  
     */
    
    public java.lang.String getStrategyRef(){
      return _strategyRef;
    }

    
    public void setStrategyRef(java.lang.String value){
        checkAllowChange();
        
        this._strategyRef = value;
           
    }

    
    /**
     * 
     * xml name: triggerId
     *  
     */
    
    public java.lang.String getTriggerId(){
      return _triggerId;
    }

    
    public void setTriggerId(java.lang.String value){
        checkAllowChange();
        
        this._triggerId = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("allowedLateness",this.getAllowedLateness());
        out.putNotNull("earlyResultsPeriod",this.getEarlyResultsPeriod());
        out.putNotNull("evictorId",this.getEvictorId());
        out.putNotNull("lateData",this.getLateData());
        out.putNotNull("strategyRef",this.getStrategyRef());
        out.putNotNull("triggerId",this.getTriggerId());
        out.putNotNull("type",this.getType());
    }

    public StreamWindowModel cloneInstance(){
        StreamWindowModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamWindowModel instance){
        super.copyTo(instance);
        
        instance.setAllowedLateness(this.getAllowedLateness());
        instance.setEarlyResultsPeriod(this.getEarlyResultsPeriod());
        instance.setEvictorId(this.getEvictorId());
        instance.setLateData(this.getLateData());
        instance.setStrategyRef(this.getStrategyRef());
        instance.setTriggerId(this.getTriggerId());
        instance.setType(this.getType());
    }

    protected StreamWindowModel newInstance(){
        return (StreamWindowModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
