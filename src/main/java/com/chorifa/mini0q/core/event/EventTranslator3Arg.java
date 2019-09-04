package com.chorifa.mini0q.core.event;

public interface EventTranslator3Arg<T,A,B,C> {

    void translateTo(T event, long sequence, A arg0, B arg1, C arg2);

}
