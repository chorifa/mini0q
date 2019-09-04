package com.chorifa.mini0q.core.consumer;

public interface EventHandler<T> {

    void onEvent(T event, long sequence) throws Exception;

}
