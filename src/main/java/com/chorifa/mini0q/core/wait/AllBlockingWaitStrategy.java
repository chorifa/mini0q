package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.TimeoutException;
import com.chorifa.mini0q.utils.Util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AllBlockingWaitStrategy implements WaitStrategy {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final AtomicBoolean pN = new AtomicBoolean(false);
    private final AtomicBoolean cN = new AtomicBoolean(false);
    private final long timeoutInNanos;
    private final boolean allowTimeout;

    public AllBlockingWaitStrategy(long timeoutInNanos) {
        this.timeoutInNanos = timeoutInNanos;
        this.allowTimeout = true;
    }

    public AllBlockingWaitStrategy() {
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

                    cN.set(true);
                    if(cursor.get() >= sequence) break; // cas set may cost time
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
        if(cN.getAndSet(false)) {
            try {
                lock.lock();
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public long waitForConsumer(long expected, AtomicLong[] gates, long current, int times) throws InterruptedException {
        long min = Util.getMinSequence(gates, current);
        if(expected > min){
            try{
                lock.lock();
                while (expected > (min = Util.getMinSequence(gates, current))){
                    pN.set(true);
                    notFull.awaitNanos(timeoutInNanos);
                }
            }finally {
                lock.unlock();
            }
        }
        return min;
    }

    @Override
    public void signalAllProducerWhenBlocking() {
        if(pN.getAndSet(false)) {
            try {
                lock.lock();
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
