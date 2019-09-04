package com.chorifa.mini0q.core.consumer;

import com.chorifa.mini0q.utils.CoreException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultExceptionHandler implements ExceptionHandler<Object> {

    private static final Logger logger = Logger.getLogger(DefaultExceptionHandler.class.getName());

    static final DefaultExceptionHandler INSTANCE = new DefaultExceptionHandler();

    private DefaultExceptionHandler(){}

    @Override
    public void handleEventException(Throwable ex, long sequence, Object event) {
        logger.log(Level.SEVERE, "Exception processing: "+sequence+" on "+event,ex);
        throw new CoreException(ex);
    }

}
