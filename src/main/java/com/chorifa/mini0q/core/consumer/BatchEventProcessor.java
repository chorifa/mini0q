package com.chorifa.mini0q.core.consumer;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.DataProvider;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.core.producer.Sequencer;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.TimeoutException;

import java.util.concurrent.atomic.AtomicBoolean;

public class BatchEventProcessor<T> implements EventProcessor {

    private final AtomicBoolean runLock = new AtomicBoolean(false);
    private volatile boolean running = false;
    private ExceptionHandler<? super T> exceptionHandler = DefaultExceptionHandler.INSTANCE;
    private final DataProvider<T> dataProvider;
    private final SequenceBarrier barrier;
    private final EventHandler<? super T> eventHandler;
    private final AtomicLong sequence = new AtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final TimeoutHandler timeoutHandler;

    @SuppressWarnings("unchecked")
    public BatchEventProcessor(DataProvider<T> dataProvider, SequenceBarrier barrier, EventHandler<? super T> eventHandler) {
        this.dataProvider = dataProvider;
        this.barrier = barrier;
        this.eventHandler = eventHandler;
        timeoutHandler = (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;

        if(eventHandler instanceof ExceptionHandler)
            exceptionHandler = (ExceptionHandler<? super T>) eventHandler;
    }

    @Override
    public AtomicLong getSequence() {
        return sequence;
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
            T event = null;
            long next = sequence.get()+1L; // used by consumer
            long available;
            while (running){
                try {
                    available = barrier.waitFor(next);
                    while (next <= available){ // do consume
                        event = dataProvider.get(next);
                        eventHandler.onEvent(event,next);
                        next++;
                    }
                    sequence.lazySet(available); // seen for provider
                    barrier.notifyProducer(); // notify
                }catch (AlertException e){
                    System.out.println("Consumer alert -> stop.");
                    break;
                }catch (final TimeoutException e){
                    handleTimeout(next); // wait for next encounter exception
                }catch (final Throwable e){
                    exceptionHandler.handleEventException(e,next,event);
                    sequence.lazySet(next); // consume until to next in this loop
                    next++;
                }
            }
        }
        else throw new CoreException("BatchEventProcessor: run failed in current Thread");
    }

    private void handleTimeout(long available){
        if(timeoutHandler != null){
            try {
                timeoutHandler.onTimeout(available);
            }catch (Throwable e){
                exceptionHandler.handleEventException(e, available, null);
            }
        }
    }

}
