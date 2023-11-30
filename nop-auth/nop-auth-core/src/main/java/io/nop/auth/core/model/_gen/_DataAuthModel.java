package io.nop.auth.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [6:2:0:0]/nop/schema/data-auth.xdef <p>
 * 用于描述系统内置的数据权限规则
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _DataAuthModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: objs
     * 
     */
    private KeyedList<io.nop.auth.core.model.ObjDataAuthModel> _objs = KeyedList.emptyList();
    
    /**
     * 
     * xml name: objs
     *  
     */
    
    public java.util.List<io.nop.auth.core.model.ObjDataAuthModel> getObjs(){
      return _objs;
    }

    
    public void setObjs(java.util.List<io.nop.auth.core.model.ObjDataAuthModel> value){
        checkAllowChange();
        
        this._objs = KeyedList.fromList(value, io.nop.auth.core.model.ObjDataAuthModel::getName);
           
    }

    
    public io.nop.auth.core.model.ObjDataAuthModel getObj(String name){
        return this._objs.getByKey(name);
    }

    public boolean hasObj(String name){
        return this._objs.containsKey(name);
    }

    public void addObj(io.nop.auth.core.model.ObjDataAuthModel item) {
        checkAllowChange();
        java.util.List<io.nop.auth.core.model.ObjDataAuthModel> list = this.getObjs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.auth.core.model.ObjDataAuthModel::getName);
            setObjs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_objs(){
        return this._objs.keySet();
    }

    public boolean hasObjs(){
        return !this._objs.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._objs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._objs);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("objs",this.getObjs());
    }
}
 // resume CPD analysis - CPD-ON
