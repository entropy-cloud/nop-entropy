package io.nop.autotest.core.execute;

import io.nop.autotest.core.data.AutoTestVars;
import io.nop.core.CoreConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.nop.autotest.core.execute.AutoTestHelper.getVarNamePrefix;
import static io.nop.autotest.core.execute.AutoTestHelper.isVarCol;

/**
 * 收集实体模式上哪些字段被标记为var。收集过程会按照relation传播var。
 */
public class TagVarCollector {
    // 从tableName映射到具有var标签的列
    private Map<String, Set<ColInfo>> varCols = new HashMap<>();

    static class ColInfo {
        String code;
        String varNamePrefix;

        public ColInfo(String colCode, String varNamePrefix) {
            this.code = colCode;
            this.varNamePrefix = varNamePrefix;
        }

        public int hashCode() {
            return code.hashCode();
        }

        public boolean equals(Object o) {
            if (o == this)
                return true;

            if (!(o instanceof ColInfo))
                return false;

            ColInfo other = (ColInfo) o;
            return code.equals(other.code);
        }
    }

    public Map<String, Object> replaceVars(String tableName, Map<String, Object> row) {
        Set<ColInfo> tableVarCols = varCols.get(tableName);
        if (tableVarCols == null)
            return row;

        AutoTestVars.VarsMap varsMap = AutoTestVars.getVarsMap();
        if (varsMap == null)
            return row;

        for (ColInfo col : tableVarCols) {
            if (col.varNamePrefix.equals("*")) {
                row.put(col.code, "*");
                continue;
            }

            String varName = varsMap.getNameByValue(col.varNamePrefix, row.get(col.code));
            if (varName != null) {
                String value = CoreConstants.BINDER_VAR_PREFIX + varName;
                row.put(col.code, value);
            }
        }
        return row;
    }

    public void add(IEntityModel entityModel) {
        Set<ColInfo> cols = varCols.computeIfAbsent(entityModel.getTableName(), k -> new HashSet<>());

        // 外键字段如果引用的是var变量，则也会被替换为对应变量名
        addRefVars(entityModel, cols);

        for (IColumnModel col : entityModel.getColumns()) {
            if (shouldIgnore(col)) {
                cols.add(new ColInfo(col.getCode(), "*"));
            } else if (isVarCol(col)) {
                cols.add(new ColInfo(col.getCode(), getVarNamePrefix(col)));
            }
        }
    }

    boolean shouldIgnore(IColumnModel col) {
        IEntityModel entityModel = col.getOwnerEntityModel();
        int propId = col.getPropId();
        return propId == entityModel.getUpdateTimePropId() || propId == entityModel.getCreateTimePropId();
    }

    private void addRefVars(IEntityModel entityModel, Set<ColInfo> cols) {
        for (IEntityRelationModel rel : entityModel.getRelations()) {
            if (rel.isToManyRelation())
                continue;
            if (rel.isOneToOne() && rel.isReverseDepends())
                continue;

            for (IEntityJoinConditionModel join : rel.getJoin()) {
                if (join.getLeftPropModel() instanceof IColumnModel) {
                    IColumnModel col = (IColumnModel) join.getLeftPropModel();
                    if (join.getRightPropModel() instanceof IColumnModel) {
                        IColumnModel refCol = (IColumnModel) join.getRightPropModel();
                        if (shouldIgnore(refCol)) {
                            cols.add(new ColInfo(col.getCode(), "*"));
                        } else if (isVarCol(refCol)) {
                            cols.add(new ColInfo(col.getCode(), getVarNamePrefix(refCol)));
                        }
                    }
                }
            }
        }
    }
}