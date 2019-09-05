package com.chorifa.mini0q;

import com.chorifa.mini0q.builder.RingQueueManager;
import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.RingQueue;
import com.chorifa.mini0q.core.consumer.EventHandler;
import com.chorifa.mini0q.core.event.EventFactory;
import com.chorifa.mini0q.core.event.EventTranslator;
import com.chorifa.mini0q.core.producer.ProducerType;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.Util;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CoreTest {

    private int inputSize = 255;
    private int size = Util.minPower2largerThan(inputSize);

    private int times = 2048*10; // loop * num_threads

    private boolean[] flags = new boolean[times];

    private EventTranslator<Event> translator = (Event event, long sequence)->{
        int tmp = event.times.get();
        if(tmp > 0) System.out.println("Translator: error at: "+sequence+" over producer or leak consume");
        if(tmp < 0) System.out.println("Translator: error at: "+sequence+" leak producer or over consume");
        if(event.times.incrementAndGet() != 1) System.out.println("Translator: error at: "+sequence);
        if(flags[(int)sequence]) System.out.println("Translator: error at: "+ sequence);
        else flags[(int)sequence] = true;
    };

    private EventHandler<Event> handler = (Event event, long sequence)->{
        int tmp = event.times.get();
        if(tmp > 1) System.out.println("Handler: error at: "+sequence+" over producer or leak consume");
        if(tmp < 1) System.out.println("Handler: error at: "+sequence+" over consume or leak producer");
        if(event.times.decrementAndGet() != 0) System.out.println("Handler: error at -> "+sequence);
    };

    private EventHandler<Event> multiHandler = (Event event, long sequence)->{
        if(event.times.get() < 1) System.out.println("get error");
        event.times.decrementAndGet();
    };

    private EventTranslator<Event> multiTranslator = (Event event, long sequence)->{
        int tmp = event.times.get();
        if(tmp > 0) System.out.println("Translator: error at: "+sequence+" over producer or leak consume");
        if(tmp < 0) System.out.println("Translator: error at: "+sequence+" leak producer or over consume");
        if(event.times.addAndGet(10) != 10) System.out.println("Translator: error at: "+sequence);
        if(flags[(int)sequence]) System.out.println("Translator: error at: "+ sequence);
        else flags[(int)sequence] = true;
    };

    private EventTranslator<Event> dependTranslator = (Event event, long sequence)->{
        int tmp = event.times.get();
        if(tmp > 0) System.out.println("Translator: error at TIMES1: "+sequence+" over producer or leak consume");
        if(tmp < 0) System.out.println("Translator: error at TIMES1: "+sequence+" leak producer or over consume");
        tmp = event.times2.get();
        if(tmp > 0) System.out.println("Translator: error at TIMES2: "+sequence+" over producer or leak consume");
        if(tmp < 0) System.out.println("Translator: error at TIMES2: "+sequence+" leak producer or over consume");
        if(event.times.incrementAndGet() != 1) System.out.println("Translator: error at: "+sequence);
        if(event.times2.incrementAndGet() != 1) System.out.println("Translator: error at: "+sequence);
        if(flags[(int)sequence]) System.out.println("Translator: error at: "+ sequence);
        else flags[(int)sequence] = true;
    };

    private EventHandler<Event> firstHandler = (Event event, long sequence)->{
        if(event.times.get() != 1) System.out.println("First Handler: get error at TIME1");
        if(event.times2.get() != 1) System.out.println("First Handler: get error at TIME2");
        event.times.decrementAndGet();
    };

    private EventHandler<Event> secondHandler = (Event event, long sequence)->{
        if(event.times.get() != 0) System.out.println("Second Handler: get error at TIME1");
        if(event.times2.get() != 1) System.out.println("First Handler: get error at TIME2");
        event.times2.decrementAndGet();
    };

    static class Event{
        AtomicInteger times = new AtomicInteger(0);
        AtomicInteger times2 = new AtomicInteger(0);
    }

    static class DefaultEventFactory<Event> implements EventFactory<CoreTest.Event>{
        @Override
        public CoreTest.Event newInstance() {
            return new CoreTest.Event();
        }
    }

    /**
     *  done
     *  1 producer, 0 consumer
     */
    @Test
    public void Producer1Consumer0(){
        RingQueue<Event> ringQueue = RingQueue.createSingleProducer(new DefaultEventFactory<Event>(), 100);
        ringQueue.addGatingSequences(new AtomicLong(-1));

        Thread thread = new Thread(()->{
            int i = 0;
            try {
                for(i = 0; i < 250; i++){
                    ringQueue.publishEvent(translator);
                }
            }catch (CoreException e){
                System.out.println("CoreException caught at "+i);
            }
        });
        System.out.println("start producer.");
        thread.start();

        try {
            Thread.sleep(2000); // sleep 2s
            thread.interrupt();
            Thread.sleep(2000); // sleep 2s
        }catch (Exception e){
            e.printStackTrace();
        }
        for(int i = 0; i < 128; i++){
            if(ringQueue.get(i).times.get() != 1)
                System.out.println("error at pos: "+i+" >>> multi produce");
        }

        for(int i = 0; i < 128; i++)
            if(!flags[i])
                System.out.println("error at pos: "+i+" >>> not produce, should not occur");

        for(int i = 128; i < flags.length; i++)
            if(flags[i])
                System.out.println("error at pos: "+i+" >>> do produce, should not occur");

    }

    /**
     *  done
     *  10 producer 0 consumer
     *  no over produce, no leak produce
     */
    @Test
    public void multiProducer0Consumer(){
        RingQueue<Event> ringQueue = RingQueue.createMultiProducer(new DefaultEventFactory<Event>(), inputSize);
        ringQueue.addGatingSequences(new AtomicLong(-1));

        Thread[] threads = new Thread[10];
        for(int i = 0; i < threads.length; i++){
            threads[i] = new Thread(()->{
                System.out.println("Thread start");
                int j = 0;
                try {
                    for(j = 0; j < 1000; j++){
                        ringQueue.publishEvent(translator);
                    }
                }catch (CoreException e){
                    System.out.println("CoreException caught at "+j);
                }
                System.out.println("run over.");
            });
        }
        System.out.println("start producers.");
        for(Thread thread : threads)
            thread.start();

        try {
            Thread.sleep(3000); // sleep 3s
            for(Thread thread : threads)
                thread.interrupt();
            Thread.sleep(5000); // sleep 5s
        }catch (Exception e){
            e.printStackTrace();
        }
        for(int i = 0; i < size; i++){
            if(ringQueue.get(i).times.get() != 1)
                System.out.println("error at pos: "+i+" >>> multi produce");
        }

        for(int i = 0; i < size; i++)
            if(!flags[i])
                System.out.println("error at pos: "+i+" >>> not produce, should not occur");

        for(int i = size; i < flags.length; i++)
            if(flags[i])
                System.out.println("error at pos: "+i+" >>> do produce, should not occur");

    }

    /**
     *  consumer after producer done. check over consume
     *  consumer together with producer. check wait strategy, check over/leak produce/consume
     */
    @Test
    public void OneConsumerAfterOneProducer(){
        RingQueueManager<Event> builder = RingQueueManager.createBuilder(new DefaultEventFactory<Event>(), 100, ProducerType.SINGLE)
                .handleEventIndependentWith(handler).getManager();
        RingQueue<Event> ringQueue = builder.get();
        Thread thread = new Thread(()->{
            int i = 0;
            try {
                for(i = 0; i < 129; i++){
                    ringQueue.publishEvent(translator);
                }
            }catch (CoreException e){
                System.out.println("CoreException caught at "+i);
            }
            System.out.println("Producer over.");
        });
        System.out.println("start producer.");
        thread.start();

        try{
            Thread.sleep(2000); //2s
            thread.interrupt();
            Thread.sleep(2000);
            builder.start(); // consumer start
            Thread.sleep(2000);
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        builder.stop(); // stop all the consumer

        for(int i = 0; i < size; i++){
            if(ringQueue.get(i).times.get() < 0)
                System.out.println("error at pos: "+i+" >>> over consume");
            else if(ringQueue.get(i).times.get() > 0)
                System.out.println("error at pos: "+i+" >>> leak consume");
        }
    }

    /**
     *  done
     *  1 consumer 1 producer seems producer, consumer, wait strategy works correctly
     */
    @Test
    public void OneConsumerTogetherWithOneProducer(){
        RingQueueManager<Event> builder = RingQueueManager.createBuilder(new DefaultEventFactory<Event>(), inputSize, ProducerType.SINGLE)
                .handleEventIndependentWith(handler).getManager();
        RingQueue<Event> ringQueue = builder.get();
        Thread thread = new Thread(()->{
            int i = 0;
            try {
                for(i = 0; i < 2499; i++){
                    //System.out.println("produce "+i);
                    ringQueue.publishEvent(translator);
                    //LockSupport.parkNanos(1000*1000*1000);
                }
            }catch (CoreException e){
                System.out.println("CoreException caught at "+i);
            }
            System.out.println("Producer over.");
        });
        System.out.println("start consumer.");
        builder.start();
        System.out.println("start producer.");
        thread.start();

        try{
            Thread.sleep(2000); //2s
            thread.interrupt();
            Thread.sleep(2000);
            builder.stop(); // stop all the consumer
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        for(int i = 0; i < size; i++){
            if(ringQueue.get(i).times.get() < 0)
                System.out.println("error at pos: "+i+" >>> over consume");
            else if(ringQueue.get(i).times.get() > 0)
                System.out.println("error at pos: "+i+" >>> leak consume");
        }
    }

    /**
     *  done
     *  setAvailable() in MultiProducer has bug, look for a whole night to fix it :(
     */
    @Test
    public void OneConsumerMultiProducer(){
        RingQueueManager<Event> builder = RingQueueManager.createBuilder(new DefaultEventFactory<Event>(), inputSize)
                .handleEventIndependentWith(handler).getManager();
        RingQueue<Event> ringQueue = builder.get();
        Thread[] threads = new Thread[10];
        for(int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;
                try {
                    for (j = 0; j < 2000; j++) {
                        ringQueue.publishEvent(translator);
                    }
                } catch (CoreException e) {
                    System.out.println("CoreException caught at " + j);
                }
                System.out.println("Producer over.");
            });
        }
        System.out.println("start consumer.");
        builder.start();
        System.out.println("start producer.");
        for(Thread thread : threads)
            thread.start();

        try{
            Thread.sleep(4000); // 4s
            for(Thread thread : threads)
                thread.interrupt();
            Thread.sleep(4000);
            builder.stop(); // stop all the consumer
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        int tmp;
        for(int i = 0; i < size; i++){
            tmp = ringQueue.get(i).times.get();
            if(tmp < 0) {
                System.out.println("error at pos: " + i + " >>> over consume >>> value is "+ tmp);
            }
            else if(tmp > 0) {
                System.out.println("error at pos: " + i + " >>> leak consume >>> value is "+ tmp);
            }
        }
    }

    /**
     * done
     * multi batchEventProcessors correct
     */
    @Test
    public void multiConsumerMultiProducer(){
        EventHandler<Event>[] handles = new EventHandler[10];
        for(int i = 0; i < 10; i++)
            handles[i] = multiHandler;
        RingQueueManager<Event> builder = RingQueueManager.createBuilder(new DefaultEventFactory<Event>(), inputSize)
                .handleEventIndependentWith(handles).getManager();
        RingQueue<Event> ringQueue = builder.get();
        Thread[] threads = new Thread[10];
        for(int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;
                try {
                    for (j = 0; j < 2000; j++) {
                        ringQueue.publishEvent(multiTranslator);
                    }
                } catch (CoreException e) {
                    System.out.println("CoreException caught at " + j);
                }
                System.out.println("Producer over.");
            });
        }
        System.out.println("start consumer.");
        builder.start();

        System.out.println("start producer.");
        for(Thread thread : threads)
            thread.start();

        try{
            Thread.sleep(4000); // 4s
            for(Thread thread : threads)
                thread.interrupt();
            Thread.sleep(4000);
            builder.stop(); // stop all the consumer
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        int tmp;
        for(int i = 0; i < size; i++){
            tmp = ringQueue.get(i).times.get();
            if(tmp < 0) {
                System.out.println("error at pos: " + i + " >>> over consume >>> value is "+ tmp);
            }
            else if(tmp > 0) {
                System.out.println("error at pos: " + i + " >>> leak consume >>> value is "+ tmp);
            }
        }
    }

    /**
     * done
     *  1 pool 10 consumers, 10 producers
     */
    @Test
    public void poolConsumerMultiProducer(){
        RingQueueManager<Event> builder = RingQueueManager.createBuilder(new DefaultEventFactory<Event>(), inputSize)
                .handleEventInPoolWith(handler,10).getManager();
        RingQueue<Event> ringQueue = builder.get();
        Thread[] threads = new Thread[10];
        for(int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;
                long start = System.currentTimeMillis();
                try {
                    for (j = 0; j < 2000; j++) {
                        ringQueue.publishEvent(translator);
                    }
                } catch (CoreException e) {
                    System.out.println("CoreException caught at " + j);
                }
                System.out.println("Producer over. using "+(System.currentTimeMillis()-start)+" ms.");
            });
        }
        System.out.println("start consumer.");
        builder.start();

        System.out.println("start producer.");
        for(Thread thread : threads)
            thread.start();

        try {
            Thread.sleep(4000); // 4s
            for(Thread thread : threads)
                thread.interrupt();
            Thread.sleep(2000);
            builder.stop(); // stop all the consumer
            Thread.sleep(2000);
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        int tmp;
        for(int i = 0; i < size; i++){
            tmp = ringQueue.get(i).times.get();
            if(tmp < 0) {
                System.out.println("error at pos: " + i + " >>> over consume >>> value is "+ tmp);
            }
            else if(tmp > 0) {
                System.out.println("error at pos: " + i + " >>> leak consume >>> value is "+ tmp);
            }
        }
    }

    /**
     *  done
     *  1 consumer -> 1 consumer
     *  10 consumers in POOL -> 1 consumer
     *  5 consumer in pool -> 5 consumer in pool
     */
    @Test
    public void dependConsumerMultiProducer(){
        RingQueueManager<Event> builder = RingQueueManager.createBuilder(new DefaultEventFactory<Event>(), inputSize)
                .handleEventInPoolWith(firstHandler,5).thenWithInPool(secondHandler,5).getManager();
        RingQueue<Event> ringQueue = builder.get();
        Thread[] threads = new Thread[10];
        for(int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                int j = 0;
                try {
                    for (j = 0; j < 2000; j++) {
                        ringQueue.publishEvent(dependTranslator);
                    }
                } catch (CoreException e) {
                    System.out.println("CoreException caught at " + j);
                }
                System.out.println("Producer over.");
            });
        }
        System.out.println("start consumer.");
        builder.start();

        System.out.println("start producer.");
        for(Thread thread : threads)
            thread.start();

        try {
            Thread.sleep(4000); // 4s
            for(Thread thread : threads)
                thread.interrupt();
            Thread.sleep(2000);
            builder.stop(); // stop all the consumer
            Thread.sleep(2000);
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        int tmp;
        for(int i = 0; i < size; i++){
            tmp = ringQueue.get(i).times.get();
            if(tmp < 0) {
                System.out.println("First: error at pos: " + i + " >>> over consume >>> value is "+ tmp);
            }
            else if(tmp > 0) {
                System.out.println("First: error at pos: " + i + " >>> leak consume >>> value is "+ tmp);
            }

            tmp = ringQueue.get(i).times2.get();
            if(tmp < 0) {
                System.out.println("Second: error at pos: " + i + " >>> over consume >>> value is "+ tmp);
            }
            else if(tmp > 0) {
                System.out.println("Second: error at pos: " + i + " >>> leak consume >>> value is "+ tmp);
            }
        }
    }

    @Test
    public void testPower2(){
        System.out.println(Util.minPower2largerThan(128));
        System.out.println(Util.log2(1024));
    }

}
