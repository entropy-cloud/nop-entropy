package io.nop.core.resource.component.version;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;

import java.io.Serializable;

@DataBean
public class VersionedName implements Serializable {
    private final String name;
    private final long version;

    public VersionedName(@Name("name") String name, @Name("version") long version) {
        this.name = Guard.notEmpty(name, "name");
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public long getVersion() {
        return version;
    }
}
