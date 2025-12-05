package io.nop.graphql.core.schema.introspection;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.graphql.core.ast.GraphQLDirectiveLocation;

import java.util.List;

@GraphQLObject
@DataBean
public class __Directive {

    private String name;

    private String description;

    private Boolean isRepeatable;

    private List<GraphQLDirectiveLocation> locations;

    private List<__InputValue> args;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsRepeatable() {
        return isRepeatable;
    }

    public void setIsRepeatable(Boolean repeatable) {
        isRepeatable = repeatable;
    }

    public List<GraphQLDirectiveLocation> getLocations() {
        return locations;
    }

    public void setLocations(List<GraphQLDirectiveLocation> locations) {
        this.locations = locations;
    }

    public List<__InputValue> getArgs() {
        return args;
    }

    public void setArgs(List<__InputValue> args) {
        this.args = args;
    }
}