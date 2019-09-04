package com.chorifa.mini0q.core.event;

public interface EventTranslator<T> {

    void translateTo(T event, long sequence);

}
