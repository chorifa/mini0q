package com.chorifa.mini0q.core.event;

public interface EventTranslator1Arg<T,A> {

    void translateTo(T event, long sequence, A arg0);

}
