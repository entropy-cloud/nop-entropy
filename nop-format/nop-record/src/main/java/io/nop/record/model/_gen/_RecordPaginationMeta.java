package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordPaginationMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-file.xdef <p>
 * 分页生成，每页包含指定条目数，并且有可能会具有页头和页尾
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordPaginationMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: aggregates
     * 
     */
    private KeyedList<io.nop.record.model.RecordAggregateFieldMeta> _aggregates = KeyedList.emptyList();
    
    /**
     *  
     * xml name: groupByExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _groupByExpr ;
    
    /**
     *  
     * xml name: pageFooter
     * 
     */
    private io.nop.record.model.RecordObjectMeta _pageFooter ;
    
    /**
     *  
     * xml name: pageHeader
     * 
     */
    private io.nop.record.model.RecordObjectMeta _pageHeader ;
    
    /**
     *  
     * xml name: pageSize
     * 分页条目数
     */
    private int _pageSize  = 0;
    
    /**
     * 
     * xml name: aggregates
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordAggregateFieldMeta> getAggregates(){
      return _aggregates;
    }

    
    public void setAggregates(java.util.List<io.nop.record.model.RecordAggregateFieldMeta> value){
        checkAllowChange();
        
        this._aggregates = KeyedList.fromList(value, io.nop.record.model.RecordAggregateFieldMeta::getName);
           
    }

    
    public io.nop.record.model.RecordAggregateFieldMeta getAggregate(String name){
        return this._aggregates.getByKey(name);
    }

    public boolean hasAggregate(String name){
        return this._aggregates.containsKey(name);
    }

    public void addAggregate(io.nop.record.model.RecordAggregateFieldMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordAggregateFieldMeta> list = this.getAggregates();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordAggregateFieldMeta::getName);
            setAggregates(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_aggregates(){
        return this._aggregates.keySet();
    }

    public boolean hasAggregates(){
        return !this._aggregates.isEmpty();
    }
    
    /**
     * 
     * xml name: groupByExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getGroupByExpr(){
      return _groupByExpr;
    }

    
    public void setGroupByExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._groupByExpr = value;
           
    }

    
    /**
     * 
     * xml name: pageFooter
     *  
     */
    
    public io.nop.record.model.RecordObjectMeta getPageFooter(){
      return _pageFooter;
    }

    
    public void setPageFooter(io.nop.record.model.RecordObjectMeta value){
        checkAllowChange();
        
        this._pageFooter = value;
           
    }

    
    /**
     * 
     * xml name: pageHeader
     *  
     */
    
    public io.nop.record.model.RecordObjectMeta getPageHeader(){
      return _pageHeader;
    }

    
    public void setPageHeader(io.nop.record.model.RecordObjectMeta value){
        checkAllowChange();
        
        this._pageHeader = value;
           
    }

    
    /**
     * 
     * xml name: pageSize
     *  分页条目数
     */
    
    public int getPageSize(){
      return _pageSize;
    }

    
    public void setPageSize(int value){
        checkAllowChange();
        
        this._pageSize = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._aggregates = io.nop.api.core.util.FreezeHelper.deepFreeze(this._aggregates);
            
           this._pageFooter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pageFooter);
            
           this._pageHeader = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pageHeader);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("aggregates",this.getAggregates());
        out.putNotNull("groupByExpr",this.getGroupByExpr());
        out.putNotNull("pageFooter",this.getPageFooter());
        out.putNotNull("pageHeader",this.getPageHeader());
        out.putNotNull("pageSize",this.getPageSize());
    }

    public RecordPaginationMeta cloneInstance(){
        RecordPaginationMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordPaginationMeta instance){
        super.copyTo(instance);
        
        instance.setAggregates(this.getAggregates());
        instance.setGroupByExpr(this.getGroupByExpr());
        instance.setPageFooter(this.getPageFooter());
        instance.setPageHeader(this.getPageHeader());
        instance.setPageSize(this.getPageSize());
    }

    protected RecordPaginationMeta newInstance(){
        return (RecordPaginationMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
