package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.TimeoutException;
import com.chorifa.mini0q.utils.Util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class LiteBlockingWaitStrategy implements WaitStrategy{

    private final Object lock = new Object();
    private final AtomicBoolean needNotify = new AtomicBoolean(false);
    private final long timeoutInNanos;
    private final boolean allowTimeout;

    public LiteBlockingWaitStrategy(long timeoutInNanos) {
        this.timeoutInNanos = timeoutInNanos;
        this.allowTimeout = true;
    }

    public LiteBlockingWaitStrategy() {
        this.allowTimeout = false;
        this.timeoutInNanos = 1000*1000*1000; // 1s
    }

    @Override
    public long waitForProducer(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws InterruptedException, TimeoutException, AlertException {
        long available; long start;
        if(cursor.get() < sequence){
            synchronized (lock){
                while (cursor.get() < sequence){
                    barrier.checkAlert();

                    needNotify.set(true);
                    if(cursor.get() >= sequence) break; // cas set may cost time
                    start = System.nanoTime();
                    lock.wait(timeoutInNanos/1000000,(int)(timeoutInNanos%1000000)); // wait 1s , not timeout
                    if(allowTimeout && (System.nanoTime()-start) > timeoutInNanos)
                        throw TimeoutException.INSTANCE;
                }
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
        if(needNotify.getAndSet(false)){
            synchronized (lock){
                lock.notifyAll();
            }
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
