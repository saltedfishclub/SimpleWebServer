package cc.sfclub.io;

import java.util.concurrent.atomic.AtomicBoolean;

//自旋锁
public class SpinLock {
    //atomic boolean记录锁是否被锁定
    private AtomicBoolean locked_;

    public SpinLock()
    {
        //初始化为未锁定
        locked_ = new AtomicBoolean(false);
    }

    //锁定
    public void lock() 
    {
        //while 直到有线程释放锁并原子性地获得锁
        while(!locked_.compareAndExchange(false, true))
        {
            Thread.yield();
        }    
    }

    //解锁
    public void unlock() 
    {
        //释放锁
        locked_.set(false);
    }
}