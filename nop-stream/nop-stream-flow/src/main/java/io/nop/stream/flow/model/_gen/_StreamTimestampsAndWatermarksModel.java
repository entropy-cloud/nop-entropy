package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamTimestampsAndWatermarksModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * TimestampsAndWatermarks 节点：事件时间分配
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamTimestampsAndWatermarksModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: timestampAssigner
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _timestampAssigner ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: watermarkGenerator
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _watermarkGenerator ;
    
    /**
     *  
     * xml name: watermarkInterval
     * 
     */
    private long _watermarkInterval  = 200;
    
    /**
     *  
     * xml name: watermarkStrategyBean
     * 
     */
    private java.lang.String _watermarkStrategyBean ;
    
    /**
     * 
     * xml name: timestampAssigner
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getTimestampAssigner(){
      return _timestampAssigner;
    }

    
    public void setTimestampAssigner(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._timestampAssigner = value;
           
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

    
    /**
     * 
     * xml name: watermarkGenerator
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWatermarkGenerator(){
      return _watermarkGenerator;
    }

    
    public void setWatermarkGenerator(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._watermarkGenerator = value;
           
    }

    
    /**
     * 
     * xml name: watermarkInterval
     *  
     */
    
    public long getWatermarkInterval(){
      return _watermarkInterval;
    }

    
    public void setWatermarkInterval(long value){
        checkAllowChange();
        
        this._watermarkInterval = value;
           
    }

    
    /**
     * 
     * xml name: watermarkStrategyBean
     *  
     */
    
    public java.lang.String getWatermarkStrategyBean(){
      return _watermarkStrategyBean;
    }

    
    public void setWatermarkStrategyBean(java.lang.String value){
        checkAllowChange();
        
        this._watermarkStrategyBean = value;
           
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
        
        out.putNotNull("timestampAssigner",this.getTimestampAssigner());
        out.putNotNull("type",this.getType());
        out.putNotNull("watermarkGenerator",this.getWatermarkGenerator());
        out.putNotNull("watermarkInterval",this.getWatermarkInterval());
        out.putNotNull("watermarkStrategyBean",this.getWatermarkStrategyBean());
    }

    public StreamTimestampsAndWatermarksModel cloneInstance(){
        StreamTimestampsAndWatermarksModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamTimestampsAndWatermarksModel instance){
        super.copyTo(instance);
        
        instance.setTimestampAssigner(this.getTimestampAssigner());
        instance.setType(this.getType());
        instance.setWatermarkGenerator(this.getWatermarkGenerator());
        instance.setWatermarkInterval(this.getWatermarkInterval());
        instance.setWatermarkStrategyBean(this.getWatermarkStrategyBean());
    }

    protected StreamTimestampsAndWatermarksModel newInstance(){
        return (StreamTimestampsAndWatermarksModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
