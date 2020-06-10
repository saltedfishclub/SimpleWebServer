package cc.sfclub.io;

import java.util.concurrent.atomic.AtomicBoolean;

//自旋锁
public class SpinLock {
    private AtomicBoolean _locked;

    public SpinLock()
    {
        _locked = new AtomicBoolean(false);
    }

    public void lock() 
    {
        while(_locked.compareAndExchange(false, true))
        {
            Thread.yield();
        }    
    }

    public void unlock() {
        _locked.set(false);
    }
}