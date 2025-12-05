package io.nop.graphql.core.schema.introspection;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

import java.util.Collections;
import java.util.List;

@GraphQLObject
@DataBean
public class __Schema {
    private List<__Type> types = Collections.emptyList();

    private __Type queryType;

    private __Type mutationType;

    private __Type subscriptionType;

    private List<__Directive> directives = Collections.emptyList();

    public List<__Type> getTypes() {
        return types;
    }

    public void setTypes(List<__Type> types) {
        this.types = types;
    }

    public __Type getQueryType() {
        return queryType;
    }

    public void setQueryType(__Type queryType) {
        this.queryType = queryType;
    }

    public __Type getMutationType() {
        return mutationType;
    }

    public void setMutationType(__Type mutationType) {
        this.mutationType = mutationType;
    }

    public __Type getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(__Type subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public List<__Directive> getDirectives() {
        return directives;
    }

    public void setDirectives(List<__Directive> directives) {
        this.directives = directives;
    }
}