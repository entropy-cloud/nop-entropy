package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSqlFieldModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * WI25/Q1 裁定：lateness 为时间列注解（Feldera LATENESS 形态子集化）——毫秒，仅 TUMBLE
 * 时间列生效（映射产物 strategy allowedLateness，D9 管线零运行时改动）；非时间列声明
 * 与无 strategy 路径声明构建期 fail-fast
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSqlFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: lateness
     * 
     */
    private java.lang.Long _lateness ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: lateness
     *  
     */
    
    public java.lang.Long getLateness(){
      return _lateness;
    }

    
    public void setLateness(java.lang.Long value){
        checkAllowChange();
        
        this._lateness = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: type
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
        
        out.putNotNull("lateness",this.getLateness());
        out.putNotNull("name",this.getName());
        out.putNotNull("type",this.getType());
    }

    public StreamSqlFieldModel cloneInstance(){
        StreamSqlFieldModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSqlFieldModel instance){
        super.copyTo(instance);
        
        instance.setLateness(this.getLateness());
        instance.setName(this.getName());
        instance.setType(this.getType());
    }

    protected StreamSqlFieldModel newInstance(){
        return (StreamSqlFieldModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
