package com.chorifa.mini0q.core.event;

public interface EventPublisher<E> {

    /* no arg  */
    void publishEvent(EventTranslator<E> translator);

    boolean tryPublishEvent(EventTranslator<E> translator);

    void publishEvents(EventTranslator<E>[] translators);

    boolean tryPublishEvents(EventTranslator<E>[] translators);

    /* one arg */
    <A> void publishEvent(EventTranslator1Arg<E,A> translator, A arg);

    <A> boolean tryPublishEvent(EventTranslator1Arg<E,A> translator, A arg);

    <A> void publishEvents(EventTranslator1Arg<E,A> translator, A[] args);

    <A> boolean tryPublishEvents(EventTranslator1Arg<E,A> translator, A[] args);

    /* two arg */
    <A,B> void publishEvent(EventTranslator2Arg<E,A,B> translator, A arg0, B arg1);

    <A,B> boolean tryPublishEvent(EventTranslator2Arg<E,A,B> translator, A arg0, B arg1);

    <A,B> void publishEvents(EventTranslator2Arg<E,A,B> translator, A[] arg0, B[] arg1);

    <A,B> boolean tryPublishEvents(EventTranslator2Arg<E,A,B> translator, A[] arg0, B[] arg1);

    /* n arg */
    void publishEvent(EventTranslatorNArg<E> translator, Object... args);

    boolean tryPublishEvent(EventTranslatorNArg<E> translator, Object... args);

    void publishEvents(EventTranslatorNArg<E> translator, Object[]... args);

    boolean tryPublishEvents(EventTranslatorNArg<E> translator, Object[]... args);

}
