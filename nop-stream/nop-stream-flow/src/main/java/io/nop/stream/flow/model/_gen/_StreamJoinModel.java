package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamJoinModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * Join 节点：等值 join 声明（WI8d）。joinRef 引用 <joins> 注册表 entry；
 * 上游恰两条边（left/right）。仅声明与构造期校验，运行时由 WI13 buildJoin 承接。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamJoinModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: joinRef
     * 
     */
    private java.lang.String _joinRef ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: joinRef
     *  
     */
    
    public java.lang.String getJoinRef(){
      return _joinRef;
    }

    
    public void setJoinRef(java.lang.String value){
        checkAllowChange();
        
        this._joinRef = value;
           
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
        
        out.putNotNull("joinRef",this.getJoinRef());
        out.putNotNull("type",this.getType());
    }

    public StreamJoinModel cloneInstance(){
        StreamJoinModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamJoinModel instance){
        super.copyTo(instance);
        
        instance.setJoinRef(this.getJoinRef());
        instance.setType(this.getType());
    }

    protected StreamJoinModel newInstance(){
        return (StreamJoinModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
