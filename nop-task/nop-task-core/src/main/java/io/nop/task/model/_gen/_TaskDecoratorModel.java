package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [47:10:0:0]/nop/schema/task/task.xdef <p>
 * 对taskStep进行增强，返回新的step
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskDecoratorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: order
     * 按照从小到大的顺序排序。order更小的decorator会先被应用
     */
    private int _order ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: bean
     *  
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: order
     *  按照从小到大的顺序排序。order更小的decorator会先被应用
     */
    
    public int getOrder(){
      return _order;
    }

    
    public void setOrder(int value){
        checkAllowChange();
        
        this._order = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.putNotNull("bean",this.getBean());
        out.putNotNull("id",this.getId());
        out.putNotNull("order",this.getOrder());
        out.putNotNull("source",this.getSource());
    }

    public TaskDecoratorModel cloneInstance(){
        TaskDecoratorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskDecoratorModel instance){
        super.copyTo(instance);
        
        instance.setBean(this.getBean());
        instance.setId(this.getId());
        instance.setOrder(this.getOrder());
        instance.setSource(this.getSource());
    }

    protected TaskDecoratorModel newInstance(){
        return (TaskDecoratorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
