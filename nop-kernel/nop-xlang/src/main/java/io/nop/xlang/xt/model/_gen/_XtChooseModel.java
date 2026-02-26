package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtChooseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtChooseModel extends io.nop.xlang.xt.model.XtRuleModel {
    
    /**
     *  
     * xml name: otherwise
     * 
     */
    private io.nop.xlang.xt.model.XtRuleGroupModel _otherwise ;
    
    /**
     *  
     * xml name: when
     * 
     */
    private KeyedList<io.nop.xlang.xt.model.XtChooseWhenModel> _whens = KeyedList.emptyList();
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _xtType ;
    
    /**
     * 
     * xml name: otherwise
     *  
     */
    
    public io.nop.xlang.xt.model.XtRuleGroupModel getOtherwise(){
      return _otherwise;
    }

    
    public void setOtherwise(io.nop.xlang.xt.model.XtRuleGroupModel value){
        checkAllowChange();
        
        this._otherwise = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public java.util.List<io.nop.xlang.xt.model.XtChooseWhenModel> getWhens(){
      return _whens;
    }

    
    public void setWhens(java.util.List<io.nop.xlang.xt.model.XtChooseWhenModel> value){
        checkAllowChange();
        
        this._whens = KeyedList.fromList(value, io.nop.xlang.xt.model.XtChooseWhenModel::getId);
           
    }

    
    public io.nop.xlang.xt.model.XtChooseWhenModel getWhen(String name){
        return this._whens.getByKey(name);
    }

    public boolean hasWhen(String name){
        return this._whens.containsKey(name);
    }

    public void addWhen(io.nop.xlang.xt.model.XtChooseWhenModel item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xt.model.XtChooseWhenModel> list = this.getWhens();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xt.model.XtChooseWhenModel::getId);
            setWhens(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_whens(){
        return this._whens.keySet();
    }

    public boolean hasWhens(){
        return !this._whens.isEmpty();
    }
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getXtType(){
      return _xtType;
    }

    
    public void setXtType(java.lang.String value){
        checkAllowChange();
        
        this._xtType = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._otherwise = io.nop.api.core.util.FreezeHelper.deepFreeze(this._otherwise);
            
           this._whens = io.nop.api.core.util.FreezeHelper.deepFreeze(this._whens);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("otherwise",this.getOtherwise());
        out.putNotNull("whens",this.getWhens());
        out.putNotNull("xtType",this.getXtType());
    }

    public XtChooseModel cloneInstance(){
        XtChooseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtChooseModel instance){
        super.copyTo(instance);
        
        instance.setOtherwise(this.getOtherwise());
        instance.setWhens(this.getWhens());
        instance.setXtType(this.getXtType());
    }

    protected XtChooseModel newInstance(){
        return (XtChooseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
