package com.chorifa.mini0q.core.producer;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.Util;
//import jdk.internal.vm.annotation.Contended;

public class SingleProducerSequencer extends AbstractSequencer {

    public long p1,p2,p3,p4,p5,p6,p7;
    //@Contended(value = "pos")
    private long writePos = AtomicLong.INITIAL_VALUE;
    //@Contended(value = "pos")
    private long cachedMinConsumerPos = AtomicLong.INITIAL_VALUE;
    public long p8,p9,p10,p11,p12,p13,p14;

    public SingleProducerSequencer(int bufferSize, WaitStrategy strategy) {
        super(bufferSize, strategy);
    }

    @Override
    public void claim(long sequence) {
        this.writePos = sequence;
    }

    @Override
    public boolean isAvailable(long sequence) {
        return sequence <= cursor.get(); // sequence <= this.writePos
    }

    @Override
    public long getHighestPublishedSequence(long next, long available) {
        return available;
    }

    @Override
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(int requiredCapacity, boolean doWrite){
        long writePos = this.writePos;

        long expectValue = writePos + requiredCapacity - bufferSize;
        long cachedGatingPos = cachedMinConsumerPos;
        if(expectValue > cachedGatingPos || cachedGatingPos > writePos){
            if(doWrite) cursor.volatileSet(writePos);
            cachedGatingPos = Util.getMinSequence(gatingSequences, writePos);
            this.cachedMinConsumerPos = cachedGatingPos;
            return expectValue <= cachedGatingPos;
        }
        return true;
    }

    @Override
    public long remainingCapacity() {
        long writePos = this.writePos;
        return bufferSize - (writePos - Util.getMinSequence(gatingSequences, writePos));
    }

    @Override
    public long next() {
        return next(1);
    }

    @Override
    public long next(int n) {
        if(n < 1 || n > bufferSize) throw new IllegalArgumentException("SingleProviderSequencer: n must between 1 and buffer-size");

        // only have one provider, hence next() must after publish(), all slot before write must be written.
        long writePos = this.writePos;
        long nextPos = writePos + n;
        // last time write in corresponding slot
        long expectedValue = nextPos - bufferSize;
        long cachedGatingSequence = this.cachedMinConsumerPos;
        int waitTimes = 0;
        if(expectedValue > cachedGatingSequence || cachedGatingSequence > writePos) {
            // volatile set
            cursor.volatileSet(writePos);
            do {
                try {
                    cachedGatingSequence = waitStrategy.waitForConsumer(expectedValue, gatingSequences, writePos, waitTimes);
                } catch (InterruptedException e) {
                    throw new CoreException("MultiProducerSequencer: wait for consumer occur interrupt.");
                }
            }while (expectedValue > cachedGatingSequence);
            /*
            while (expectedValue > (cachedGatingSequence = Util.getMinSequence(gatingSequences, writePos))) {
                LockSupport.parkNanos(2L);
                // TODO for test, delete in future
                if(Thread.interrupted())
                    throw new CoreException();
            }*/
            this.cachedMinConsumerPos = cachedGatingSequence;
        }
        this.writePos = nextPos;
        return nextPos;
    }

    @Override
    public long tryNext() throws CoreException {
        return tryNext(1);
    }

    @Override
    public long tryNext(int n) throws CoreException {
        if(n < 1 || n > bufferSize) throw new IllegalArgumentException("SingleProviderSequencer: n must between 1 and buffer-size");
        // caz there is only one provider, hence we can directly
        // set writePos after judgement
        if(hasAvailableCapacity(n,true)){
            writePos += n;
            return writePos;
        }else throw new CoreException("SingleProviderSequencer: do not have enough space for tryNext("+n+")");
    }

    @Override
    public void publish(long sequence) {
        cursor.volatileSet(sequence);
        waitStrategy.signalAllConsumerWhenBlocking();
    }

    @Override
    public void publish(long from, long to) {
        publish(to);
    }
}
