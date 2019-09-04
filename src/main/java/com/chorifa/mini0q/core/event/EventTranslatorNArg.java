package com.chorifa.mini0q.core.event;

public interface EventTranslatorNArg<T> {

    void translateTo(T event, long sequence, Object... args);

}
