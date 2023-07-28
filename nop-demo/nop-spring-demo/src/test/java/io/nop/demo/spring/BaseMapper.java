package io.nop.demo.spring;

public interface BaseMapper<T> {
    String insertEntity(T entity);
}
