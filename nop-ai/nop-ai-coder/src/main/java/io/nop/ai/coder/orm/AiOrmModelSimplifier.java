package io.nop.ai.coder.orm;

import io.nop.ai.coder.AiCoderConstants;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.XDslCleaner;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.coder.AiCoderConstants.SCHEMA_AI_ORM;

/**
 * 简化ORM模型，去除一般情况下不会使用的信息便于AI小模型识别
 */
public class AiOrmModelSimplifier {
    public XNode simplify(XNode node) {
        XNode entities = node.childByTag("entities");
        if (entities != null) {
            Map<String, XNode> entityMap = new HashMap<>();
            for (XNode entity : entities.getChildren()) {
                collectEntityMap(entity, entityMap);
            }

            for (XNode entity : entities.getChildren()) {
                simplifyEntity(entity, entityMap);
            }
        }
        new XDslCleaner().cleanForXDef(SCHEMA_AI_ORM, node);
        return node;
    }

    void collectEntityMap(XNode node, Map<String, XNode> entityMap) {
        String name = node.attrText("name");
        name = StringHelper.simpleClassName(name);
        node.setAttr("name", name);
        entityMap.put(name, node);
    }

    void simplifyEntity(XNode entity, Map<String, XNode> entityMap) {
        XNode columns = entity.childByTag("columns");
        if (columns == null)
            return;

        XNode relations = entity.childByTag("relations");
        if (relations != null) {
            for (XNode relation : relations.getChildren()) {
                Pair<String, String> joinOn = getJoinOn(relation);
                if (joinOn == null)
                    continue;

                String refEntityName = StringHelper.simpleClassName(relation.attrText("refEntityName"));
                String refPropNme = relation.attrText("refPropName");
                String refDisplayName = relation.attrText("refDisplayName");

                if (refEntityName == null)
                    continue;

                if (relation.getTagName().equals("to-one")) {
                    // to-one
                    XNode col = columns.childByAttr("name", joinOn.getLeft());
                    if (col != null) {
                        col.setAttr(AiCoderConstants.ATTR_ORM_REF_TABLE, refEntityName);
                        col.setAttr(AiCoderConstants.ATTR_ORM_REF_PROP, refPropNme);
                        col.setAttr(AiCoderConstants.ATTR_ORM_REF_PROP_DISPLAY_NAME, refDisplayName);
                    }
                } else {
                    // to-many
                    XNode refEntity = entityMap.get(refEntityName);
                    if (refEntity != null) {
                        XNode refCol = refEntity.childByAttr("name", joinOn.getRight());
                        if (refCol != null) {
                            refCol.setAttr(AiCoderConstants.ATTR_ORM_REF_TABLE, entity.attrText("name"));
                            refCol.setAttr(AiCoderConstants.ATTR_ORM_REF_PROP, relation.attrText("name"));
                            refCol.setAttr(AiCoderConstants.ATTR_ORM_REF_PROP_DISPLAY_NAME, relation.attrText("displayName"));
                        }
                    }
                }
            }
        }
    }

    Pair<String, String> getJoinOn(XNode relation) {
        XNode join = relation.childByTag("join");
        if (join == null || join.getChildCount() > 1)
            return null;

        XNode on = join.childByTag("on");
        if (on == null)
            return null;

        String leftProp = on.attrText("leftProp");
        String rightProp = on.attrText("rightProp");

        if (leftProp == null || rightProp == null)
            return null;

        return Pair.of(leftProp, rightProp);
    }
}
