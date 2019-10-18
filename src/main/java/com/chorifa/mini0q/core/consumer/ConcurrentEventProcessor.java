package com.chorifa.mini0q.core.consumer;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.DataProvider;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.core.producer.Sequencer;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.TimeoutException;

import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentEventProcessor<T> implements EventProcessor {

    private final AtomicBoolean runLock = new AtomicBoolean(false);
    private volatile boolean running = false;
    private final AtomicLong personalSequence = new AtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final DataProvider<T> provider;
    private final SequenceBarrier barrier;
    private final EventHandler<? super T> eventHandler;
    private final ExceptionHandler<? super T> exceptionHandler;
    private final AtomicLong poolSequence;
    private final TimeoutHandler timeoutHandler;

    /* ************************* for test ************************* */
    private final int num;
    /* ************************* for test ************************* */

    @SuppressWarnings("unchecked")
    public ConcurrentEventProcessor(int num, DataProvider<T> provider, SequenceBarrier barrier, EventHandler<? super T> eventHandler, AtomicLong poolSequence) {
        this.num = num;
        this.provider = provider;
        this.barrier = barrier;
        this.eventHandler = eventHandler;
        this.poolSequence = poolSequence;

        timeoutHandler = (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;
        exceptionHandler = (eventHandler instanceof ExceptionHandler) ? (ExceptionHandler<? super T>) eventHandler : DefaultExceptionHandler.INSTANCE;
    }

    @Override
    public AtomicLong getSequence() {
        return personalSequence;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop() {
        running = false;
        barrier.alert();
    }

    @Override
    public void run() {
        if(runLock.compareAndSet(false,true)){
            running = true;

            boolean processed = true;
            long cachedAvailable = Long.MIN_VALUE;
            long next = personalSequence.get();
            T event = null;
            /*TODO for test */
//            long startTime = -1, endTime = -1; long consume = 0;
//            try {
//                App.latch.await();
//                startTime = System.currentTimeMillis();
//            }catch (InterruptedException e){
//                System.err.println("consumer run error");
//            }
            /* for test */
            while (running){
                try{
                    if(processed){ // scramble next seq
                        processed = false;
                        do {
                            next = poolSequence.get()+1;
                            personalSequence.lazySet(next-1); // seen for producers
                        }while (!poolSequence.compareAndSet(next-1,next));

                        // signal producer to produce
                        barrier.notifyProducer();
                    }
                    if(cachedAvailable >= next){
                        event = provider.get(next);
                        eventHandler.onEvent(event,next);

                        /*TODO for test */
//                        consume++;
//                        endTime = System.currentTimeMillis();
                        /* for test */

                        processed = true; // maybe rearrange the instruction.
                    }else cachedAvailable = barrier.waitFor(next); // refresh cachedAvailable
                }catch (AlertException e){
                    System.out.println("Consumer alert -> stop.");

                    /*TODO fot test */
//                    if(startTime > 0) {
//                        App.consumerTimeMap[num] = endTime - startTime;
//                        App.consumerCountMap[num] = consume;
//                        App.lastTimeConsumeMap[num] = endTime;
//                    }
//                    else System.out.println("Consumer "+num+" error start.");
                    /* fot test */

                    break;
                }catch (TimeoutException e){
                    handleTimeout(next);
                }catch (Throwable t){
                    processed = true;
                    exceptionHandler.handleEventException(t,next,event);
                }

            }
        }
        else throw new CoreException("BatchEventProcessor: run failed in current Thread");
    }

    private void handleTimeout(long available){
        if(timeoutHandler != null){
            try{
                timeoutHandler.onTimeout(available);
            }catch (Throwable t){
                exceptionHandler.handleEventException(t,available,null);
            }
        }
    }

}
