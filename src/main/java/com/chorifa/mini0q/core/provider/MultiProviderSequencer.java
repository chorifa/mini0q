package com.chorifa.mini0q.core.provider;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.Util;
// import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

public class MultiProviderSequencer extends AbstractSequencer {

//    private static final Unsafe UNSAFE = Util.getUnsafe();
//    private static final long BASE = UNSAFE.arrayBaseOffset(int[].class);
//    private static final long SCALE = UNSAFE.arrayIndexScale(int[].class);

    private static final VarHandle AVAILABLE_REF_HANDLE;

    static {
        try {
            AVAILABLE_REF_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (IllegalArgumentException e){
            throw new CoreException("MultiProviderSequencer: cannot get VarHandle for int[]");
        }
    }

    private final AtomicLong cachedMinConsumerPos = new AtomicLong(Sequencer.INITIAL_CURSOR_VALUE);

    private final int[] availableRef;
    private final int indexMask;
    private final int indexShift;

    public MultiProviderSequencer(int bufferSize, WaitStrategy strategy) {
        super(bufferSize, strategy);
        // note cannot use parameter, caz may have be modify
        this.availableRef = new int[this.bufferSize];
        indexMask = this.bufferSize -1;
        indexShift = Util.log2(this.bufferSize);
        Arrays.fill(availableRef, -1);
    }

    @Override
    public void claim(long sequence) {
        cursor.lazySet(sequence);
    }

    @Override
    public boolean isAvailable(long sequence) {
        return (int)AVAILABLE_REF_HANDLE.getVolatile(availableRef, calIndex(sequence)) == calAvailableFlag(sequence);
        //return UNSAFE.getIntVolatile(availableRef, (calIndex(sequence)*SCALE)+BASE) == calAvailableFlag(sequence);
    }

    @Override
    public long getHighestPublishedSequence(long next, long available) {
        for(long sequence = next; sequence <= available; sequence++){
            if(!isAvailable(sequence)) return sequence-1;
        }
        return available;
    }

    @Override
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return hasAvailableCapacity(requiredCapacity, cursor.get());
    }

    private boolean hasAvailableCapacity(int requiredCapacity, long currentPos){
        long expectedValue = currentPos + requiredCapacity - bufferSize;
        long cachedGatingSequence = cachedMinConsumerPos.get();
        if(expectedValue > cachedGatingSequence || cachedGatingSequence > currentPos){
            cachedGatingSequence = Util.getMinSequence(gatingSequences, currentPos);
            cachedMinConsumerPos.lazySet(cachedGatingSequence);
            return expectedValue <= cachedGatingSequence;
        }
        return true;
    }

    @Override
    public long remainingCapacity() {
        long consumed = Util.getMinSequence(gatingSequences, cursor.get());
        return bufferSize - (cursor.get() - consumed);
    }

    @Override
    public long next() {
        return this.next(1);
    }

    @Override
    public long next(int n) {
        if(n < 1 || n > bufferSize)
            throw new IllegalArgumentException("MultiProviderSequencer: n must between 1 and buffer-size");
        long current;
        long next;
        long expectedValue;
        long cachedGatingSequence;
        int waitTimes = 0;
        do {
            current = cursor.get();
            next = current + n;
            expectedValue = next -bufferSize;
            cachedGatingSequence = cachedMinConsumerPos.get();
            if(expectedValue > cachedGatingSequence || cachedGatingSequence > current){
                cachedGatingSequence = Util.getMinSequence(gatingSequences, current);
                if(expectedValue > cachedGatingSequence){ // wait
                    if(waitTimes < WaitStrategy.threshold) {
                        LockSupport.parkNanos(2L);//WaitStrategy.Wait_Times[waitTimes]);
                        waitTimes++;
                    }
                    else {
                        LockSupport.parkNanos(WaitStrategy.Wait_Times[waitTimes - WaitStrategy.threshold]);
                        if (waitTimes - WaitStrategy.threshold < WaitStrategy.Wait_Times.length - 1) waitTimes++;
                    }
                    // TODO for test, delete in future
                    if(Thread.interrupted())
                        throw new CoreException();
                }else {
                    cachedMinConsumerPos.lazySet(cachedGatingSequence); // lazySet
                    waitTimes = 0;
                }
            }else if(cursor.compareAndSet(current, next)) break;
            else waitTimes = 0;
        }while (true);
        return next;
    }

    @Override
    public long tryNext() throws CoreException {
        return tryNext(1);
    }

    @Override
    public long tryNext(int n) throws CoreException {
        if(n < 1 || n > bufferSize) throw new IllegalArgumentException("MultiProviderSequencer: n must between 1 and buffer-size");
        long current;
        do {
            current = cursor.get();
            if(!hasAvailableCapacity(n,current))
                throw new CoreException("MultiProviderSequencer: do not have enough space for tryNext("+n+")");
        }while (!cursor.compareAndSet(current,current+n));
        return current+n;
    }

    @Override
    public void publish(long sequence) {
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void publish(long from, long to) {
        for(long l = from; l <= to; l++)
            setAvailable(l);
        waitStrategy.signalAllWhenBlocking();
    }

    private void setAvailable(long sequence){
        // putIntRelease
        // note: putOrderedInt NOT putOrderedLong, look up for a night to fix this
        AVAILABLE_REF_HANDLE.setRelease(availableRef, calIndex(sequence), calAvailableFlag(sequence));
        //UNSAFE.putOrderedInt(availableRef, (calIndex(sequence)*SCALE)+BASE, calAvailableFlag(sequence));
    }

    private int calIndex(long sequence){
        return ((int)sequence) & indexMask; // both ok
    }

    private int calAvailableFlag(long sequence){
        // overflow is ok, so long as calAvailableFlag(sequence) != calAvailableFlag(sequence+bufSize)
        return (int)(sequence >>> indexShift); // = seq / bufSize
    }

}
