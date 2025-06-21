package io.nop.ai.coder.orm;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;
import io.nop.orm.model.OrmModelConstants;

@DataBean
public class AiOrmConfig {
    private String basePackageName;
    private String mavenGroupId;
    private String entityPackageName;
    private String appName;
    private String mavenArtifactId;

    public String getBasePackageName() {
        return basePackageName;
    }

    public void setBasePackageName(String basePackageName) {
        this.basePackageName = basePackageName;
    }

    public String getMavenGroupId() {
        return mavenGroupId;
    }

    public void setMavenGroupId(String mavenGroupId) {
        this.mavenGroupId = mavenGroupId;
    }

    public String getEntityPackageName() {
        return entityPackageName;
    }

    public void setEntityPackageName(String entityPackageName) {
        this.entityPackageName = entityPackageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getMavenArtifactId() {
        return mavenArtifactId;
    }

    public void setMavenArtifactId(String mavenArtifactId) {
        this.mavenArtifactId = mavenArtifactId;
    }

    public static AiOrmConfig fromOrmNode(XNode node) {
        String basePackageName = node.attrText(OrmModelConstants.EXT_BASE_PACKAGE_NAME);
        if (basePackageName == null)
            basePackageName = "app";

        AiOrmConfig config = new AiOrmConfig();
        config.setBasePackageName(basePackageName);
        config.setEntityPackageName(node.attrText(OrmModelConstants.EXT_ENTITY_PACKAGE_NAME));
        config.setAppName(node.attrText(OrmModelConstants.EXT_APP_NAME));
        config.setMavenGroupId(node.attrText(OrmModelConstants.EXT_MAVEN_GROUP_ID));
        config.setMavenArtifactId(node.attrText(OrmModelConstants.EXT_MAVEN_ARTIFACT_ID));

        return config;
    }
}
