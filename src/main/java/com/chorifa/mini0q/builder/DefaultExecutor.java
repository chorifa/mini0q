package com.chorifa.mini0q.builder;

import com.chorifa.mini0q.utils.CoreException;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultExecutor implements Executor {
    private final ThreadFactory factory;
    private final Queue<Thread> threads = new ConcurrentLinkedQueue<>();

    DefaultExecutor(ThreadFactory factory) {
        this.factory = factory == null ? DefaultThreadFactory.INSTANCE : factory;
    }

    // TODO change to lazy-singleton
    DefaultExecutor(){
        this.factory = DefaultThreadFactory.INSTANCE;
    }

    @Override
    public void execute(Runnable command) {
        // one task one new thread
        final Thread thread = factory.newThread(command);
        if(thread == null)
            throw new CoreException("DefaultExecutor: cannot create a new thread.");
        thread.setDaemon(true);
        thread.start();
        threads.add(thread);
    }

    public void interrupt(){
        for(Thread thread : threads)
            if(thread.isAlive())
                thread.interrupt();
    }

    @Override
    public String toString() {
        return "BasicExecutor{" +
                "threads=" + dumpThreadInfo() +
                '}';
    }

    private String dumpThreadInfo() {
        final StringBuilder sb = new StringBuilder();

        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for (Thread t : threads) {
            ThreadInfo threadInfo = threadMXBean.getThreadInfo(t.getId());
            sb.append("{");
            sb.append("name=").append(t.getName()).append(",");
            sb.append("id=").append(t.getId()).append(",");
            sb.append("state=").append(threadInfo.getThreadState()).append(",");
            sb.append("lockInfo=").append(threadInfo.getLockInfo());
            sb.append("}");
        }

        return sb.toString();
    }

    public static class DefaultThreadFactory implements ThreadFactory{

        private AtomicInteger cnt = new AtomicInteger(0);

        public static DefaultThreadFactory INSTANCE = new DefaultThreadFactory();

        private DefaultThreadFactory(){}

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r,"Thread -"+cnt.getAndIncrement()+" >>> created By DefaultThreadFactory.");
        }

    }

}
