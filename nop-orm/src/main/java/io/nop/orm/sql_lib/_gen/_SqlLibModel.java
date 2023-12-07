package io.nop.orm.sql_lib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [3:2:0:0]/nop/schema/orm/sql-lib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _SqlLibModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fragments
     * 
     */
    private KeyedList<io.nop.orm.sql_lib.SqlFragmentModel> _fragments = KeyedList.emptyList();
    
    /**
     *  
     * xml name: sqls
     * 
     */
    private KeyedList<io.nop.orm.sql_lib.SqlItemModel> _sqls = KeyedList.emptyList();
    
    /**
     * 
     * xml name: fragments
     *  
     */
    
    public java.util.List<io.nop.orm.sql_lib.SqlFragmentModel> getFragments(){
      return _fragments;
    }

    
    public void setFragments(java.util.List<io.nop.orm.sql_lib.SqlFragmentModel> value){
        checkAllowChange();
        
        this._fragments = KeyedList.fromList(value, io.nop.orm.sql_lib.SqlFragmentModel::getId);
           
    }

    
    public io.nop.orm.sql_lib.SqlFragmentModel getFragment(String name){
        return this._fragments.getByKey(name);
    }

    public boolean hasFragment(String name){
        return this._fragments.containsKey(name);
    }

    public void addFragment(io.nop.orm.sql_lib.SqlFragmentModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.sql_lib.SqlFragmentModel> list = this.getFragments();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.sql_lib.SqlFragmentModel::getId);
            setFragments(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_fragments(){
        return this._fragments.keySet();
    }

    public boolean hasFragments(){
        return !this._fragments.isEmpty();
    }
    
    /**
     * 
     * xml name: sqls
     *  
     */
    
    public java.util.List<io.nop.orm.sql_lib.SqlItemModel> getSqls(){
      return _sqls;
    }

    
    public void setSqls(java.util.List<io.nop.orm.sql_lib.SqlItemModel> value){
        checkAllowChange();
        
        this._sqls = KeyedList.fromList(value, io.nop.orm.sql_lib.SqlItemModel::getName);
           
    }

    
    public io.nop.orm.sql_lib.SqlItemModel getSql(String name){
        return this._sqls.getByKey(name);
    }

    public boolean hasSql(String name){
        return this._sqls.containsKey(name);
    }

    public void addSql(io.nop.orm.sql_lib.SqlItemModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.sql_lib.SqlItemModel> list = this.getSqls();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.sql_lib.SqlItemModel::getName);
            setSqls(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sqls(){
        return this._sqls.keySet();
    }

    public boolean hasSqls(){
        return !this._sqls.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fragments = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fragments);
            
           this._sqls = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sqls);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("fragments",this.getFragments());
        out.put("sqls",this.getSqls());
    }
}
 // resume CPD analysis - CPD-ON
