package com.chorifa.mini0q.core;

import com.chorifa.mini0q.utils.CoreException;

public interface Sequenced {

    int getBufferSize();

    boolean hasAvailableCapacity(int requiredCapacity);

    long remainingCapacity();

    long next() ;

    long next(int n) ;

    long tryNext() throws CoreException;

    long tryNext(int n) throws CoreException;

    void publish(long sequence);

    void publish(long from, long to);

}
