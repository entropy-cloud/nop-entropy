package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayMatchModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayMatchModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: httpMethod
     * 
     */
    private java.util.Set<java.lang.String> _httpMethod ;
    
    /**
     *  
     * xml name: path
     * 路径匹配规则（按优先级排序）：
     * 1. 精确匹配: /api/users
     * 2. 路径变量: /api/{type} 匹配单段，变量值通过path获取
     * 3. 后缀匹配: /files/*.json 仅最后一段支持
     * 4. 前缀+变量: /pages/page_{id}.json 仅最后一段支持
     * 5. 通配所有: /api/** 或 /api/{*path} 匹配所有剩余路径
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _when ;
    
    /**
     * 
     * xml name: httpMethod
     *  
     */
    
    public java.util.Set<java.lang.String> getHttpMethod(){
      return _httpMethod;
    }

    
    public void setHttpMethod(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._httpMethod = value;
           
    }

    
    /**
     * 
     * xml name: path
     *  路径匹配规则（按优先级排序）：
     * 1. 精确匹配: /api/users
     * 2. 路径变量: /api/{type} 匹配单段，变量值通过path获取
     * 3. 后缀匹配: /files/*.json 仅最后一段支持
     * 4. 前缀+变量: /pages/page_{id}.json 仅最后一段支持
     * 5. 通配所有: /api/** 或 /api/{*path} 匹配所有剩余路径
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
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._when = value;
           
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
        
        out.putNotNull("httpMethod",this.getHttpMethod());
        out.putNotNull("path",this.getPath());
        out.putNotNull("when",this.getWhen());
    }

    public GatewayMatchModel cloneInstance(){
        GatewayMatchModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayMatchModel instance){
        super.copyTo(instance);
        
        instance.setHttpMethod(this.getHttpMethod());
        instance.setPath(this.getPath());
        instance.setWhen(this.getWhen());
    }

    protected GatewayMatchModel newInstance(){
        return (GatewayMatchModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
