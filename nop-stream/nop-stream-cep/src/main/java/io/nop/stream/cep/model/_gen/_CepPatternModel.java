package io.nop.stream.cep.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.cep.model.CepPatternModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [10:2:0:0]/nop/schema/stream/pattern.xdef <p>
 * 1. 所有模式序列必须以.begin()开始；
 * 2. 模式序列不能以.notFollowedBy()结束；
 * 3. “not”类型的模式不能被optional所修饰；
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CepPatternModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterMatchSkipStrategy
     * 
     */
    private io.nop.stream.cep.model.AfterMatchSkipStrategyKind _afterMatchSkipStrategy ;
    
    /**
     *  
     * xml name: afterMatchSkipTo
     * 当afterMatchSkipStrategy 为SKIP_TO_FIRST或者SKIP_TO_LAST时需要设置此参数，对应子pattern的名字
     */
    private java.lang.String _afterMatchSkipTo ;
    
    /**
     *  
     * xml name: gapWithin
     * 
     */
    private java.time.Duration _gapWithin ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.stream.cep.model.CepPatternPartModel> _parts = KeyedList.emptyList();
    
    /**
     *  
     * xml name: start
     * 
     */
    private java.lang.String _start ;
    
    /**
     *  
     * xml name: within
     * 一个模式序列只能有一个时间限制。如果限制了多个时间在不同的单个模式上，会使用最小的那个时间限制。
     */
    private java.time.Duration _within ;
    
    /**
     * 
     * xml name: afterMatchSkipStrategy
     *  
     */
    
    public io.nop.stream.cep.model.AfterMatchSkipStrategyKind getAfterMatchSkipStrategy(){
      return _afterMatchSkipStrategy;
    }

    
    public void setAfterMatchSkipStrategy(io.nop.stream.cep.model.AfterMatchSkipStrategyKind value){
        checkAllowChange();
        
        this._afterMatchSkipStrategy = value;
           
    }

    
    /**
     * 
     * xml name: afterMatchSkipTo
     *  当afterMatchSkipStrategy 为SKIP_TO_FIRST或者SKIP_TO_LAST时需要设置此参数，对应子pattern的名字
     */
    
    public java.lang.String getAfterMatchSkipTo(){
      return _afterMatchSkipTo;
    }

    
    public void setAfterMatchSkipTo(java.lang.String value){
        checkAllowChange();
        
        this._afterMatchSkipTo = value;
           
    }

    
    /**
     * 
     * xml name: gapWithin
     *  
     */
    
    public java.time.Duration getGapWithin(){
      return _gapWithin;
    }

    
    public void setGapWithin(java.time.Duration value){
        checkAllowChange();
        
        this._gapWithin = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.stream.cep.model.CepPatternPartModel> getParts(){
      return _parts;
    }

    
    public void setParts(java.util.List<io.nop.stream.cep.model.CepPatternPartModel> value){
        checkAllowChange();
        
        this._parts = KeyedList.fromList(value, io.nop.stream.cep.model.CepPatternPartModel::getName);
           
    }

    
    public io.nop.stream.cep.model.CepPatternPartModel getPart(String name){
        return this._parts.getByKey(name);
    }

    public boolean hasPart(String name){
        return this._parts.containsKey(name);
    }

    public void addPart(io.nop.stream.cep.model.CepPatternPartModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.cep.model.CepPatternPartModel> list = this.getParts();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.cep.model.CepPatternPartModel::getName);
            setParts(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_parts(){
        return this._parts.keySet();
    }

    public boolean hasParts(){
        return !this._parts.isEmpty();
    }
    
    /**
     * 
     * xml name: start
     *  
     */
    
    public java.lang.String getStart(){
      return _start;
    }

    
    public void setStart(java.lang.String value){
        checkAllowChange();
        
        this._start = value;
           
    }

    
    /**
     * 
     * xml name: within
     *  一个模式序列只能有一个时间限制。如果限制了多个时间在不同的单个模式上，会使用最小的那个时间限制。
     */
    
    public java.time.Duration getWithin(){
      return _within;
    }

    
    public void setWithin(java.time.Duration value){
        checkAllowChange();
        
        this._within = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._parts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._parts);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterMatchSkipStrategy",this.getAfterMatchSkipStrategy());
        out.putNotNull("afterMatchSkipTo",this.getAfterMatchSkipTo());
        out.putNotNull("gapWithin",this.getGapWithin());
        out.putNotNull("parts",this.getParts());
        out.putNotNull("start",this.getStart());
        out.putNotNull("within",this.getWithin());
    }

    public CepPatternModel cloneInstance(){
        CepPatternModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CepPatternModel instance){
        super.copyTo(instance);
        
        instance.setAfterMatchSkipStrategy(this.getAfterMatchSkipStrategy());
        instance.setAfterMatchSkipTo(this.getAfterMatchSkipTo());
        instance.setGapWithin(this.getGapWithin());
        instance.setParts(this.getParts());
        instance.setStart(this.getStart());
        instance.setWithin(this.getWithin());
    }

    protected CepPatternModel newInstance(){
        return (CepPatternModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
