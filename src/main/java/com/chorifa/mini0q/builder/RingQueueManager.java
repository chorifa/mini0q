package com.chorifa.mini0q.builder;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.RingQueue;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.core.event.*;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import com.chorifa.mini0q.core.consumer.BatchEventProcessor;
import com.chorifa.mini0q.core.consumer.ConcurrentProcessorPool;
import com.chorifa.mini0q.core.consumer.EventHandler;
import com.chorifa.mini0q.core.provider.ProducerType;
import com.chorifa.mini0q.utils.Assert;
import com.chorifa.mini0q.utils.CoreException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

// only consumer need start
// consumer need specify types and set depends
public class RingQueueManager<T> implements EventPublisher<T> {
    private final RingQueue<T> ringQueue;
    private final Executor executor;
    private Collection<BatchEventProcessor<? super T>> batchProcessors = new ArrayList<>();
    private Collection<ConcurrentProcessorPool<? super T>> concurrentPool = new ArrayList<>();
    private final AtomicBoolean startLock = new AtomicBoolean(false);

    private RingQueueManager(final RingQueue<T> ringQueue, final Executor executor){
        this.ringQueue = ringQueue;
        this.executor = executor;
    }

    /* ******************************* static create builder method ******************************* */

    public static <T> RingQueueManager<T> createBuilder(
        final EventFactory<T> eventFactory,
        int ringQueueSize,
        final ThreadFactory threadFactory,
        final ProducerType producerType,
        final WaitStrategy waitStrategy){
        return new RingQueueManager<T>(RingQueue.create(producerType,eventFactory,ringQueueSize,waitStrategy),
                new DefaultExecutor(threadFactory));
    }

    public static <T> RingQueueManager<T> createBuilder(final EventFactory<T> eventFactory,
                                                        int ringQueueSize, final ThreadFactory threadFactory){
        return new RingQueueManager<>(RingQueue.createMultiProducer(eventFactory, ringQueueSize),
                new DefaultExecutor(threadFactory));
    }

    public static <T> RingQueueManager<T> createBuilder(final EventFactory<T> eventFactory, int ringQueueSize){
        return new RingQueueManager<>(RingQueue.createMultiProducer(eventFactory, ringQueueSize), new DefaultExecutor());
    }

    public static <T> RingQueueManager<T> createBuilder(final EventFactory<T> eventFactory, int ringQueueSize,
                                                        ProducerType producerType){
        return new RingQueueManager<>(RingQueue.create(producerType, eventFactory, ringQueueSize), new DefaultExecutor());
    }

    public static <T> RingQueueManager<T> createBuilder(final EventFactory<T> eventFactory, int ringQueueSize, WaitStrategy waitStrategy){
        return new RingQueueManager<>(RingQueue.createMultiProducer(eventFactory,ringQueueSize,waitStrategy), new DefaultExecutor());
    }

    public void stop(){
        for(BatchEventProcessor<? super T> processor : batchProcessors)
            processor.stop();
        for(ConcurrentProcessorPool<? super T> pool : concurrentPool)
            pool.stop();
    }

    /* ******************************* chain builder method ******************************* */

    @SafeVarargs
    public final ChainBuilder<T> handleEventIndependentWith(final EventHandler<? super T>... handlers){
        Assert.notEmpty(handlers);
        checkNotStart();

        final AtomicLong[] processorSequences = new AtomicLong[handlers.length];
        final SequenceBarrier barrier = ringQueue.newBarrier(); // non barrier

        for(int i = 0; i < handlers.length; i++){
            final BatchEventProcessor<? super T> batchEventProcessor = new BatchEventProcessor<>(ringQueue,barrier,handlers[i]);
            batchProcessors.add(batchEventProcessor);
            processorSequences[i] = batchEventProcessor.getSequence();
        }

        ringQueue.addGatingSequences(processorSequences);
        return new ChainBuilder<>(this,processorSequences, null);
    }

    @SafeVarargs
    public final ChainBuilder<T> handleEventInPoolWith(final EventHandler<? super T>... handlers){
        Assert.notEmpty(handlers);
        checkNotStart();

        final SequenceBarrier barrier = ringQueue.newBarrier(); // non barrier
        final ConcurrentProcessorPool<T> pool = new ConcurrentProcessorPool<>(ringQueue,barrier,handlers);

        concurrentPool.add(pool);

        //refresh gating
        final AtomicLong[] sequencesInPool = pool.getSequencesInPool();
        ringQueue.addGatingSequences(sequencesInPool);

        return new ChainBuilder<>(this, sequencesInPool, null);
    }

    public final ChainBuilder<T> handleEventInPoolWith(final EventHandler<? super T> handler, int num){
        Assert.notNull(handler);
        checkNotStart();

        @SuppressWarnings("unchecked")
        EventHandler<? super T>[] handlers = new EventHandler[num];
        Arrays.fill(handlers, handler);

        return handleEventInPoolWith(handlers);
    }

    ChainBuilder<T> createBatchProcessors(final AtomicLong[] sequencesToTrack, final EventHandler<? super T>[] handlers){
        Assert.notEmpty(handlers);
        Assert.notEmpty(sequencesToTrack);
        checkNotStart();

        final AtomicLong[] processorSequences = new AtomicLong[handlers.length];
        final SequenceBarrier barrier = ringQueue.newBarrier(sequencesToTrack);

        for(int i = 0; i < handlers.length; i++){
            final BatchEventProcessor<? super T> batchEventProcessor = new BatchEventProcessor<>(ringQueue,barrier,handlers[i]);
            batchProcessors.add(batchEventProcessor);
            processorSequences[i] = batchEventProcessor.getSequence();
        }

        for(AtomicLong sequence : sequencesToTrack) // remove sequences in previous layer
            ringQueue.removeGatingSequence(sequence);
        ringQueue.addGatingSequences(processorSequences);
        return new ChainBuilder<>(this, processorSequences, sequencesToTrack);
    }

    ChainBuilder<T> createPoolProcessors(final AtomicLong[] sequencesToTrack, final EventHandler<? super T>[] handlers) {
        Assert.notEmpty(handlers);
        Assert.notEmpty(sequencesToTrack);
        checkNotStart();

        final SequenceBarrier barrier = ringQueue.newBarrier(sequencesToTrack);
        final ConcurrentProcessorPool<T> pool = new ConcurrentProcessorPool<>(ringQueue,barrier,handlers);

        concurrentPool.add(pool);

        //refresh gating
        final AtomicLong[] sequencesInPool = pool.getSequencesInPool();
        for(AtomicLong sequence : sequencesToTrack)
            ringQueue.removeGatingSequence(sequence);
        ringQueue.addGatingSequences(sequencesInPool);

        return new ChainBuilder<>(this, sequencesInPool, sequencesToTrack);
    }

    /* ************************************* start method ************************************* */
    public RingQueue<T> get(){
        return ringQueue;
    }

    RingQueue<T> startAndGet(){
        start();
        return ringQueue;
    }

    public void start(){
        checkNotStart();
        for(BatchEventProcessor<? super T> processor : batchProcessors)
            executor.execute(processor);
        for(ConcurrentProcessorPool<? super T> pool : concurrentPool)
            pool.start(executor);
    }

    private void checkNotStart(){
        if(startLock.get())
            throw new CoreException("RingQueueBuilder: cannot modify consumer chain after start.");
    }

    /* ************************************* publish method for producer ************************************* */

    @Override
    public void publishEvent(EventTranslator<T> translator) {
        this.ringQueue.publishEvent(translator);
    }

    @Override
    public boolean tryPublishEvent(EventTranslator<T> translator) {
        return this.ringQueue.tryPublishEvent(translator);
    }

    @Override
    public void publishEvents(EventTranslator<T>[] translators) {
        this.ringQueue.publishEvents(translators);
    }

    @Override
    public boolean tryPublishEvents(EventTranslator<T>[] translators) {
        return this.ringQueue.tryPublishEvents(translators);
    }

    @Override
    public <A> void publishEvent(EventTranslator1Arg<T, A> translator, A arg) {
        this.ringQueue.publishEvent(translator, arg);
    }

    @Override
    public <A> boolean tryPublishEvent(EventTranslator1Arg<T, A> translator, A arg) {
        return this.ringQueue.tryPublishEvent(translator, arg);
    }

    @Override
    public <A> void publishEvents(EventTranslator1Arg<T, A> translator, A[] args) {
        this.ringQueue.publishEvents(translator, args);
    }

    @Override
    public <A> boolean tryPublishEvents(EventTranslator1Arg<T, A> translator, A[] args) {
        return this.ringQueue.tryPublishEvents(translator, args);
    }

    @Override
    public <A, B> void publishEvent(EventTranslator2Arg<T, A, B> translator, A arg0, B arg1) {
        this.ringQueue.publishEvent(translator, arg0, arg1);
    }

    @Override
    public <A, B> boolean tryPublishEvent(EventTranslator2Arg<T, A, B> translator, A arg0, B arg1) {
        return this.ringQueue.tryPublishEvent(translator, arg0, arg1);
    }

    @Override
    public <A, B> void publishEvents(EventTranslator2Arg<T, A, B> translator, A[] arg0, B[] arg1) {
        this.ringQueue.publishEvents(translator, arg0, arg1);
    }

    @Override
    public <A, B> boolean tryPublishEvents(EventTranslator2Arg<T, A, B> translator, A[] arg0, B[] arg1) {
        return this.ringQueue.tryPublishEvents(translator, arg0, arg1);
    }

    @Override
    public void publishEvent(EventTranslatorNArg<T> translator, Object... args) {
        this.ringQueue.publishEvent(translator, args);
    }

    @Override
    public boolean tryPublishEvent(EventTranslatorNArg<T> translator, Object... args) {
        return this.ringQueue.tryPublishEvent(translator, args);
    }

    @Override
    public void publishEvents(EventTranslatorNArg<T> translator, Object[]... args) {
        this.ringQueue.publishEvents(translator, args);
    }

    @Override
    public boolean tryPublishEvents(EventTranslatorNArg<T> translator, Object[]... args) {
        return this.ringQueue.tryPublishEvents(translator, args);
    }

}
