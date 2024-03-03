package io.nop.search.api;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class SearchRequest {
    private String topic;

    private String query;

    private int limit;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
