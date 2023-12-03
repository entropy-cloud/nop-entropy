package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [141:10:0:0]/nop/schema/orm/entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _OrmToOneReferenceModel extends io.nop.orm.model.OrmReferenceModel {
    
    /**
     *  
     * xml name: constraint
     * 外键约束名
     */
    private java.lang.String _constraint ;
    
    /**
     *  
     * xml name: ignoreDepends
     * 在计算表的拓扑依赖顺序时，是否忽略对此关联表的依赖。出现循环依赖时需要进行标注。
     */
    private boolean _ignoreDepends  = false;
    
    /**
     *  
     * xml name: ref-set
     * 一对多的外键关联中父表对象可以存在集合属性来反向引用子表。集合对象可以定义keyProp，并支持排序条件。
     */
    private io.nop.orm.model.OrmRefSetModel _refSet ;
    
    /**
     *  
     * xml name: reverseDepends
     * 一般情况下to-one应该是子表指向父表。而在一对一关联时，reverseDepends=true表示是从父表指向子表。
     */
    private boolean _reverseDepends  = false;
    
    /**
     * 
     * xml name: constraint
     *  外键约束名
     */
    
    public java.lang.String getConstraint(){
      return _constraint;
    }

    
    public void setConstraint(java.lang.String value){
        checkAllowChange();
        
        this._constraint = value;
           
    }

    
    /**
     * 
     * xml name: ignoreDepends
     *  在计算表的拓扑依赖顺序时，是否忽略对此关联表的依赖。出现循环依赖时需要进行标注。
     */
    
    public boolean isIgnoreDepends(){
      return _ignoreDepends;
    }

    
    public void setIgnoreDepends(boolean value){
        checkAllowChange();
        
        this._ignoreDepends = value;
           
    }

    
    /**
     * 
     * xml name: ref-set
     *  一对多的外键关联中父表对象可以存在集合属性来反向引用子表。集合对象可以定义keyProp，并支持排序条件。
     */
    
    public io.nop.orm.model.OrmRefSetModel getRefSet(){
      return _refSet;
    }

    
    public void setRefSet(io.nop.orm.model.OrmRefSetModel value){
        checkAllowChange();
        
        this._refSet = value;
           
    }

    
    /**
     * 
     * xml name: reverseDepends
     *  一般情况下to-one应该是子表指向父表。而在一对一关联时，reverseDepends=true表示是从父表指向子表。
     */
    
    public boolean isReverseDepends(){
      return _reverseDepends;
    }

    
    public void setReverseDepends(boolean value){
        checkAllowChange();
        
        this._reverseDepends = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._refSet = io.nop.api.core.util.FreezeHelper.deepFreeze(this._refSet);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("constraint",this.getConstraint());
        out.put("ignoreDepends",this.isIgnoreDepends());
        out.put("refSet",this.getRefSet());
        out.put("reverseDepends",this.isReverseDepends());
    }
}
 // resume CPD analysis - CPD-ON
