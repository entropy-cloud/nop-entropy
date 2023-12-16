//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlOrderBy;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlOrderBy extends EqlASTNode {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> items;
    

    public _SqlOrderBy(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> getItems(){
        return items;
    }

    public void setItems(java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.items = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> makeItems(){
        java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> list = getItems();
        if(list == null){
            list = new java.util.ArrayList<>();
            setItems(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("items",getItems());
       
    }


    public SqlOrderBy newInstance(){
      return new SqlOrderBy();
    }

    @Override
    public SqlOrderBy deepClone(){
       SqlOrderBy ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(items != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> copy_items = new java.util.ArrayList<>(items.size());
                          for(io.nop.orm.eql.ast.SqlOrderByItem item: items){
                              copy_items.add(item.deepClone());
                          }
                          ret.setItems(copy_items);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(items != null){
               for(io.nop.orm.eql.ast.SqlOrderByItem child: items){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(items != null){
               for(io.nop.orm.eql.ast.SqlOrderByItem child: items){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.items != null){
               int index = this.items.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> list = this.replaceInList(this.items,index,newChild);
                   this.setItems(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.items != null){
               int index = this.items.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlOrderByItem> list = this.removeInList(this.items,index);
                   this.setItems(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlOrderBy other = (SqlOrderBy)node;
    
            if(isListEquivalent(this.items,other.getItems())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlOrderBy;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(items != null){
                      
                              if(!items.isEmpty())
                                json.put("items", items);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                items = io.nop.api.core.util.FreezeHelper.freezeList(items,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
