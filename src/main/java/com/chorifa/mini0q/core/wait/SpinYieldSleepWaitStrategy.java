package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.TimeoutException;

public class SpinYieldSleepWaitStrategy implements WaitStrategy {

    private final int spinTimes;
    private final long sleepTimeNs;
    private final boolean allowTimeout;
    private final int maxSleepCount;

    public SpinYieldSleepWaitStrategy() {
        allowTimeout = false;
        spinTimes = 200;
        maxSleepCount = 0;
        sleepTimeNs = 1000*1000*1000; // 1s
    }

    public SpinYieldSleepWaitStrategy(int spinTimes, long sleepTimeNs) {
        this.spinTimes = spinTimes;
        this.sleepTimeNs = sleepTimeNs;
        allowTimeout = false;
        maxSleepCount = 0;
    }

    public SpinYieldSleepWaitStrategy(int spinTimes, long sleepTimeNs, int maxSleepCount) {
        this.spinTimes = spinTimes;
        this.sleepTimeNs = sleepTimeNs;
        this.maxSleepCount = maxSleepCount;
        allowTimeout = true;
    }

    @Override
    public long waitFor(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws TimeoutException, InterruptedException, AlertException {
        long available;
        long cnt = spinTimes;
        long slept = 0;
        while ((available = dependentSequence.get()) < sequence){
            barrier.checkAlert();

            if(allowTimeout && slept == maxSleepCount)
                throw TimeoutException.INSTANCE;

            if(cnt > 100){
                --cnt;
            }else if(cnt > 0){
                --cnt;
                Thread.yield();
            }else{
                slept++;
                Thread.sleep(sleepTimeNs/1000000, (int) (sleepTimeNs%1000000));
            }
        }

        return available;
    }

    @Override
    public void signalAllWhenBlocking() {

    }

}
