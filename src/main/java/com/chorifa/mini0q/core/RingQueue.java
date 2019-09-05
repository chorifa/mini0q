package com.chorifa.mini0q.core;

import com.chorifa.mini0q.core.event.*;
import com.chorifa.mini0q.core.producer.MultiProducerSequencer;
import com.chorifa.mini0q.core.producer.ProducerType;
import com.chorifa.mini0q.core.producer.Sequencer;
import com.chorifa.mini0q.core.producer.SingleProducerSequencer;
import com.chorifa.mini0q.core.wait.BlockingWaitStrategy;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import com.chorifa.mini0q.utils.Assert;
import com.chorifa.mini0q.utils.CoreException;
//import com.chorifa.mini0q.utils.Util;
//import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

abstract class RingQueuePad{
    protected long p1, p2, p3, p4, p5, p6, p7;
}

abstract class RingQueueField<E> extends RingQueuePad{

    private static final int BUFFER_PADDING = 128/4;
//    private static final long REF_ARRAY_BASE;
//    private static final int REF_ELEMENT_SHIFT;
//    private static final Unsafe UNSAFE = Util.getUnsafe();

    private static final VarHandle BUFFER_HANDLE;

    static {
        try {
            BUFFER_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
        }catch (IllegalArgumentException e){
            throw new CoreException("RingQueue: cannot get VarHandle for Object[].",e);
        }
    }

//    static{
//        final int scale = UNSAFE.arrayIndexScale(Object[].class);
//        if(scale == 4){
//            REF_ELEMENT_SHIFT = 2;
//        } else if(scale == 8){
//            REF_ELEMENT_SHIFT = 3;
//        } else throw new CoreException("RingBuffer: illegal pointer size");
//        BUFFER_PADDING = 128/scale;
//        REF_ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class) +128;
//    }

    private final long mask;
    private final Object[] buffer;
    protected final int bufferSize;
    protected final Sequencer sequencer;

    RingQueueField(EventFactory<E> eventFactory, Sequencer sequencer){
        Assert.notNull(eventFactory);
        this.sequencer = sequencer;
        this.bufferSize = sequencer.getBufferSize();
        mask = bufferSize -1;
        buffer = new Object[bufferSize + (BUFFER_PADDING <<1)];
        for(int i = 0; i < bufferSize; i++)
            buffer[BUFFER_PADDING +i] = eventFactory.newInstance();
    }

    @SuppressWarnings("unchecked")
    protected E elementAt(long sequence){
        // really need volatile?
        return (E) BUFFER_HANDLE.get(buffer, ((int)(sequence & mask))+BUFFER_PADDING);
        //return (E) UNSAFE.getObjectVolatile(buffer, REF_ARRAY_BASE+((sequence & mask) << REF_ELEMENT_SHIFT));
    }

}

public final class RingQueue<E> extends RingQueueField<E> implements EventSequencer<E>, Cursored, EventPublisher<E>, Sequencer {

    protected long p1, p2, p3, p4, p5, p6, p7;

    public RingQueue(EventFactory<E> eventFactory, Sequencer sequencer) {
        super(eventFactory, sequencer);
    }

    /* *************************************** static create methods *************************************** */

    public static <E> RingQueue<E> createMultiProducer(EventFactory<E> factory, int bufferSize, WaitStrategy waitStrategy){
        MultiProducerSequencer sequencer = new MultiProducerSequencer(bufferSize, waitStrategy);
        return new RingQueue<>(factory, sequencer);
    }

    public static <E> RingQueue<E> createSingleProducer(EventFactory<E> factory, int bufferSize, WaitStrategy waitStrategy){
        SingleProducerSequencer sequencer = new SingleProducerSequencer(bufferSize, waitStrategy);
        return new RingQueue<>(factory, sequencer);
    }

    public static <E> RingQueue<E> createMultiProducer(EventFactory<E> factory, int bufferSize){
        return createMultiProducer(factory,bufferSize, new BlockingWaitStrategy());
    }

    public static <E> RingQueue<E> createSingleProducer(EventFactory<E> factory, int bufferSize){
        return createSingleProducer(factory,bufferSize, new BlockingWaitStrategy());
    }

    public static <E> RingQueue<E> create(ProducerType type, EventFactory<E> factory, int bufferSize, WaitStrategy waitStrategy){
        switch (type){
            case MULTI: return createMultiProducer(factory,bufferSize,waitStrategy);
            case SINGLE: return createSingleProducer(factory, bufferSize, waitStrategy);
            default: throw new CoreException("RingQueue: illegal ProducerType "+type);
        }
    }

    public static <E> RingQueue<E> create(ProducerType type, EventFactory<E> factory, int bufferSize){
        return create(type, factory, bufferSize, new BlockingWaitStrategy());
    }

    /* *************************************** publish methods *************************************** */

    @Override
    public void publishEvent(EventTranslator<E> translator) {
        Assert.notNull(translator);
        long next = sequencer.next();
        translateThenPublish(translator, next);
    }

    @Override
    public boolean tryPublishEvent(EventTranslator<E> translator) {
        Assert.notNull(translator);
        try {
            long next = sequencer.tryNext();
            translateThenPublish(translator, next);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public void publishEvents(EventTranslator<E>[] translators) {
        Assert.notEmpty(translators);
        long nextEnd = sequencer.next(translators.length);
        translateThenPublishInBatch(translators, nextEnd);
    }

    @Override
    public boolean tryPublishEvents(EventTranslator<E>[] translators) {
        Assert.notEmpty(translators);
        try {
            long nextEnd = sequencer.tryNext(translators.length);
            translateThenPublishInBatch(translators, nextEnd);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public <A> void publishEvent(EventTranslator1Arg<E, A> translator, A arg) {
        Assert.notNull(translator);
        translateThenPublish(translator,sequencer.next(),arg);
    }

    @Override
    public <A> boolean tryPublishEvent(EventTranslator1Arg<E, A> translator, A arg) {
        Assert.notNull(translator);
        try {
            long next = sequencer.tryNext();
            translateThenPublish(translator, next, arg);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public <A> void publishEvents(EventTranslator1Arg<E, A> translator, A[] args) {
        Assert.notNull(translator);
        Assert.notEmpty(args);
        long nextEnd = sequencer.next(args.length);
        translateThenPublishInBatch(translator, nextEnd, args);
    }

    @Override
    public <A> boolean tryPublishEvents(EventTranslator1Arg<E, A> translator, A[] args) {
        Assert.notNull(translator);
        Assert.notEmpty(args);
        try{
            long nextEnd = sequencer.tryNext(args.length);
            translateThenPublishInBatch(translator, nextEnd, args);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public <A, B> void publishEvent(EventTranslator2Arg<E, A, B> translator, A arg0, B arg1) {
        Assert.notNull(translator);
        long next = sequencer.next();
        translateThenPublish(translator, next, arg0, arg1);
    }

    @Override
    public <A, B> boolean tryPublishEvent(EventTranslator2Arg<E, A, B> translator, A arg0, B arg1) {
        Assert.notNull(translator);
        try{
            long next = sequencer.tryNext();
            translateThenPublish(translator, next, arg0 ,arg1);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public <A, B> void publishEvents(EventTranslator2Arg<E, A, B> translator, A[] arg0, B[] arg1) {
        Assert.notNull(translator);
        Assert.equalLength(arg0,arg1);
        long nextEnd = sequencer.next(arg0.length);
        translateThenPublishInBatch(translator, nextEnd, arg0, arg1);
    }

    @Override
    public <A, B> boolean tryPublishEvents(EventTranslator2Arg<E, A, B> translator, A[] arg0, B[] arg1) {
        Assert.notNull(translator);
        Assert.equalLength(arg0,arg1);
        try {
            long nextEnd = sequencer.tryNext(arg0.length);
            translateThenPublishInBatch(translator, nextEnd, arg0, arg1);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public void publishEvent(EventTranslatorNArg<E> translator, Object... args) {
        Assert.notNull(translator);
        long next = sequencer.next();
        translateThenPublish(translator, next, args);
    }

    @Override
    public boolean tryPublishEvent(EventTranslatorNArg<E> translator, Object... args) {
        Assert.notNull(translator);
        try{
            long next = sequencer.tryNext();
            translateThenPublish(translator, next, args);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    @Override
    public void publishEvents(EventTranslatorNArg<E> translator, Object[]... args) {
        Assert.notNull(translator);
        Assert.notEmpty(args);
        long nextEnd = sequencer.next(args.length);
        translateThenPublishInBatch(translator, nextEnd, args);
    }

    @Override
    public boolean tryPublishEvents(EventTranslatorNArg<E> translator, Object[]... args) {
        Assert.notNull(translator);
        Assert.notEmpty(args);
        try{
            long nextEnd = sequencer.tryNext(args.length);
            translateThenPublishInBatch(translator, nextEnd, args);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    /* *************************************** private methods for publish*************************************** */
    private void translateThenPublish(EventTranslator<E> translator, long sequence){
        try {
            translator.translateTo(elementAt(sequence), sequence);
        }finally {
            sequencer.publish(sequence);
        }
    }

    private <A> void translateThenPublish(EventTranslator1Arg<E,A> translator, long sequence, A arg){
        try {
            translator.translateTo(elementAt(sequence), sequence, arg);
        }finally {
            sequencer.publish(sequence); // must publish
        }
    }

    private <A,B> void translateThenPublish(EventTranslator2Arg<E,A,B> translator, long sequence, A arg0, B arg1){
        try {
            translator.translateTo(elementAt(sequence), sequence, arg0, arg1);
        }finally {
            sequencer.publish(sequence); // must publish
        }
    }

    private void translateThenPublish(EventTranslatorNArg<E> translator, long sequence, Object... args){
        try {
            translator.translateTo(elementAt(sequence), sequence, args);
        }finally {
            sequencer.publish(sequence); // must publish
        }
    }

    private void translateThenPublishInBatch(EventTranslator<E>[] translators, long sequence){
        try {
            long from = sequence - translators.length +1;
            for(EventTranslator<E> translator : translators)
                translator.translateTo(elementAt(from),from++);
        }finally {
            sequencer.publish(sequence - translators.length +1, sequence);
        }
    }

    private <A> void translateThenPublishInBatch(EventTranslator1Arg<E,A> translator, long sequence, final A[] args){
        try {
            long from = sequence - args.length +1;
            for(A arg : args)
                translator.translateTo(elementAt(from),from++,arg);
        }finally {
            sequencer.publish(sequence - args.length +1, sequence); // must publish
        }
    }

    private <A,B> void translateThenPublishInBatch(EventTranslator2Arg<E,A,B> translator, long sequence, final A[] arg0, final B[] arg1){
        int batchSize = arg0.length;
        try {
            long from = sequence - batchSize +1;
            for(int i = 0; i < batchSize; i++)
                translator.translateTo(elementAt(from), from++, arg0[i], arg1[i]);
        }finally {
            sequencer.publish(sequence - batchSize +1, sequence); // must publish
        }
    }

    private void translateThenPublishInBatch(EventTranslatorNArg<E> translator, long sequence, final Object[][] args){
        try {
            long from = sequence - args.length +1;
            for (Object[] arg : args)
                translator.translateTo(elementAt(from), from++, arg);
        }finally {
            sequencer.publish(sequence - args.length +1, sequence); // must publish
        }
    }

    /* *************************************** cursor methods *************************************** */

    @Override
    public E get(long sequence) {
        return elementAt(sequence);
    }

    @Override
    public long next() {
        return sequencer.next();
    }

    @Override
    public long next(int n) {
        return sequencer.next(n);
    }

    @Override
    public long tryNext() throws CoreException {
        return sequencer.tryNext();
    }

    @Override
    public long tryNext(int n) throws CoreException {
        return sequencer.tryNext(n);
    }

    @Override
    public long getCursor() {
        return sequencer.getCursor();
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return sequencer.hasAvailableCapacity(requiredCapacity);
    }

    @Override
    public long remainingCapacity() {
        return sequencer.remainingCapacity();
    }

    @Override
    public void publish(long sequence) {
        sequencer.publish(sequence);
    }

    @Override
    public void publish(long from, long to) {
        sequencer.publish(from, to);
    }

    /* *************************************** sequencer methods *************************************** */

    @Override
    public void claim(long sequence) {
        sequencer.claim(sequence);
    }

    @Override
    public boolean isAvailable(long sequence) {
        return sequencer.isAvailable(sequence);
    }

    @Override
    public void addGatingSequences(AtomicLong... gatingSequences) {
        sequencer.addGatingSequences(gatingSequences);
    }

    @Override
    public boolean removeGatingSequence(AtomicLong sequence) {
        return sequencer.removeGatingSequence(sequence);
    }

    @Override
    public SequenceBarrier newBarrier(AtomicLong... sequencesToTrack) {
        return sequencer.newBarrier(sequencesToTrack);
    }

    @Override
    public long getMinSequence() {
        return sequencer.getMinSequence();
    }

    @Override
    public long getHighestPublishedSequence(long next, long available) {
        return sequencer.getHighestPublishedSequence(next, available);
    }

}
