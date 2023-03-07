//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlCaseExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlCaseExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> caseWhens;
    
    protected io.nop.orm.eql.ast.SqlExpr elseExpr;
    
    protected io.nop.orm.eql.ast.SqlExpr test;
    

    public _SqlCaseExpr(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> getCaseWhens(){
        return caseWhens;
    }

    public void setCaseWhens(java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.caseWhens = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> makeCaseWhens(){
        java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> list = getCaseWhens();
        if(list == null){
            list = new java.util.ArrayList<>();
            setCaseWhens(list);
        }
        return list;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getElseExpr(){
        return elseExpr;
    }

    public void setElseExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.elseExpr = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getTest(){
        return test;
    }

    public void setTest(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.test = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlCaseExpr newInstance(){
      return new SqlCaseExpr();
    }

    @Override
    public SqlCaseExpr deepClone(){
       SqlCaseExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(test != null){
                  
                          ret.setTest(test.deepClone());
                      
                }
            
                if(caseWhens != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> copy_caseWhens = new java.util.ArrayList<>(caseWhens.size());
                          for(io.nop.orm.eql.ast.SqlCaseWhenItem item: caseWhens){
                              copy_caseWhens.add(item.deepClone());
                          }
                          ret.setCaseWhens(copy_caseWhens);
                      
                }
            
                if(elseExpr != null){
                  
                          ret.setElseExpr(elseExpr.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(test != null)
                processor.accept(test);
        
            if(caseWhens != null){
               for(io.nop.orm.eql.ast.SqlCaseWhenItem child: caseWhens){
                    processor.accept(child);
                }
            }
            if(elseExpr != null)
                processor.accept(elseExpr);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(test != null && processor.apply(test) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(caseWhens != null){
               for(io.nop.orm.eql.ast.SqlCaseWhenItem child: caseWhens){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(elseExpr != null && processor.apply(elseExpr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.test == oldChild){
               this.setTest((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.caseWhens != null){
               int index = this.caseWhens.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> list = this.replaceInList(this.caseWhens,index,newChild);
                   this.setCaseWhens(list);
                   return true;
               }
            }
            if(this.elseExpr == oldChild){
               this.setElseExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.test == child){
                this.setTest(null);
                return true;
            }
        
            if(this.caseWhens != null){
               int index = this.caseWhens.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlCaseWhenItem> list = this.removeInList(this.caseWhens,index);
                   this.setCaseWhens(list);
                   return true;
               }
            }
            if(this.elseExpr == child){
                this.setElseExpr(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlCaseExpr other = (SqlCaseExpr)node;
    
            if(!isNodeEquivalent(this.test,other.getTest())){
               return false;
            }
        
            if(isListEquivalent(this.caseWhens,other.getCaseWhens())){
               return false;
            }
            if(!isNodeEquivalent(this.elseExpr,other.getElseExpr())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlCaseExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(test != null){
                      
                              json.put("test", test);
                          
                    }
                
                    if(caseWhens != null){
                      
                              if(!caseWhens.isEmpty())
                                json.put("caseWhens", caseWhens);
                          
                    }
                
                    if(elseExpr != null){
                      
                              json.put("elseExpr", elseExpr);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(test != null)
                    test.freeze(cascade);
                caseWhens = io.nop.api.core.util.FreezeHelper.freezeList(caseWhens,cascade);         
                if(elseExpr != null)
                    elseExpr.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
