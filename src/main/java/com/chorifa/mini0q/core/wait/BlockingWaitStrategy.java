package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.TimeoutException;
import com.chorifa.mini0q.utils.Util;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingWaitStrategy implements WaitStrategy {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final long timeoutInNanos;
    private final boolean allowTimeout;

    public BlockingWaitStrategy(long timeoutInNanos) {
        this.timeoutInNanos = timeoutInNanos;
        this.allowTimeout = true;
    }

    public BlockingWaitStrategy() {
        this.allowTimeout = false;
        this.timeoutInNanos = 1000*1000*1000; // 1s
    }

    @Override
    public long waitForProducer(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws TimeoutException, InterruptedException, AlertException {
        long available; long start;
        if(cursor.get() < sequence){
            try {
                lock.lock();
                while (cursor.get() < sequence){
                    barrier.checkAlert();

                    start = System.nanoTime();
                    notEmpty.awaitNanos(timeoutInNanos); // wait 1s , not timeout
                    if(allowTimeout && (System.nanoTime()-start) > timeoutInNanos)
                        throw TimeoutException.INSTANCE;
                }
            }finally {
                lock.unlock();
            }
        }
        // busy-waiting
        while ((available = dependentSequence.get()) < sequence){
            barrier.checkAlert();
            Thread.onSpinWait();
        }

        return available;
    }

    @Override
    public void signalAllConsumerWhenBlocking() {
        try {
            lock.lock();
            notEmpty.signalAll();
        }finally {
            lock.unlock();
        }
    }

    @Override
    public long waitForConsumer(long expected, AtomicLong[] gates, long current, int times) {
        long min = Util.getMinSequence(gates, current);
        if(expected > min){
            if(times < WaitStrategy.threshold)
                LockSupport.parkNanos(2L);//WaitStrategy.Wait_Times[waitTimes]);
            else if(times - WaitStrategy.threshold < WaitStrategy.Wait_Times.length)
            LockSupport.parkNanos(WaitStrategy.Wait_Times[times - WaitStrategy.threshold]);
            else throw new CoreException("Producer: Reject add event into RingQueue.");
        }
        return min;
    }

    @Override
    public void signalAllProducerWhenBlocking() {
        // do nothing
    }

}
