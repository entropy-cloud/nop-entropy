package io.nop.stream.cep.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.cep.model.CepPatternGroupModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/pattern.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CepPatternGroupModel extends io.nop.stream.cep.model.CepPatternPartModel {
    
    /**
     *  
     * xml name: afterMatchSkipStrategy
     * 
     */
    private io.nop.stream.cep.model.AfterMatchSkipStrategyKind _afterMatchSkipStrategy ;
    
    /**
     *  
     * xml name: afterMatchSkipTo
     * 
     */
    private java.lang.String _afterMatchSkipTo ;
    
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
     *  
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
        out.putNotNull("parts",this.getParts());
        out.putNotNull("start",this.getStart());
    }

    public CepPatternGroupModel cloneInstance(){
        CepPatternGroupModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CepPatternGroupModel instance){
        super.copyTo(instance);
        
        instance.setAfterMatchSkipStrategy(this.getAfterMatchSkipStrategy());
        instance.setAfterMatchSkipTo(this.getAfterMatchSkipTo());
        instance.setParts(this.getParts());
        instance.setStart(this.getStart());
    }

    protected CepPatternGroupModel newInstance(){
        return (CepPatternGroupModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
