package com.chorifa.mini0q.core.consumer;

public interface ExceptionHandler<T> {

    void handleEventException(Throwable ex, long sequence, T event);

}
