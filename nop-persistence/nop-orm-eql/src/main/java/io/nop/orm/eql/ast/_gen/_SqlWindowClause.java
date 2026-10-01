//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlWindowClause;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlWindowClause extends EqlASTNode {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> items;
    

    public _SqlWindowClause(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> getItems(){
        return items;
    }

    public void setItems(java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.items = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> makeItems(){
        java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> list = getItems();
        if(list == null){
            list = new java.util.ArrayList<>();
            setItems(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlWindowClause newInstance(){
      return new SqlWindowClause();
    }

    @Override
    public SqlWindowClause deepClone(){
       SqlWindowClause ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(items != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> copy_items = new java.util.ArrayList<>(items.size());
                          for(io.nop.orm.eql.ast.SqlWindowDecl item: items){
                              copy_items.add(item.deepClone());
                          }
                          ret.setItems(copy_items);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(items != null){
               for(io.nop.orm.eql.ast.SqlWindowDecl child: items){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(items != null){
               for(io.nop.orm.eql.ast.SqlWindowDecl child: items){
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
                   java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> list = this.replaceInList(this.items,index,newChild);
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
                   java.util.List<io.nop.orm.eql.ast.SqlWindowDecl> list = this.removeInList(this.items,index);
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
    SqlWindowClause other = (SqlWindowClause)node;
    
            if(isListEquivalent(this.items,other.getItems())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlWindowClause;
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
