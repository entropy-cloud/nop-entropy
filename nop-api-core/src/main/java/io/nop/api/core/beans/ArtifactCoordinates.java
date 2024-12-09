package io.nop.api.core.beans;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;

@DataBean
public final class ArtifactCoordinates {
    private final String groupId;
    private final String artifactId;
    private final String version;

    private final String text;

    public ArtifactCoordinates(@Name("groupId") String groupId,
                               @Name("artifactId") String artifactId,
                               @Name("version") String version) {
        this.groupId = Guard.notEmpty(groupId, "groupId");
        this.artifactId = Guard.notEmpty(artifactId, "artifactId");
        this.version = Guard.notEmpty(version, "version");

        this.text = toString(groupId, artifactId, version);
    }

    public int hashCode() {
        return text.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof ArtifactCoordinates) {
            return text.equals(((ArtifactCoordinates) obj).text);
        }
        return false;
    }

    public static String toString(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    @StaticFactoryMethod
    public static ArtifactCoordinates parse(String text) {
        int pos = text.indexOf(':');
        if (pos < 0 || pos == text.length() - 1)
            throw new IllegalArgumentException("invalid artifact coordinates: " + text);
        int pos2 = text.indexOf(':', pos + 1);
        if (pos2 < 0 || pos2 == text.length() - 1)
            throw new IllegalArgumentException("invalid artifact coordinates: " + text);

        return new ArtifactCoordinates(text.substring(0, pos), text.substring(pos + 1, pos2), text.substring(pos2 + 1));
    }

    public String toString() {
        return text;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}