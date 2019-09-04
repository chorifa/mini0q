package com.chorifa.mini0q.core.event;

public interface EventTranslator2Arg<T,A,B> {

    void translateTo(T event, long sequence, A arg0, B arg1);

}
