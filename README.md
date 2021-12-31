# SimpleWebServer

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/aa8578729e3648df8198ea888206a7cd)](https://app.codacy.com/manual/NaturalSelect/SimpleWebServer?utm_source=github.com&utm_medium=referral&utm_content=NaturalSelect/SimpleWebServer&utm_campaign=Badge_Grade_Dashboard)

一个简单的基于Java NIO的短连接 Http Server  
提供四种常见服务器并发模型,本项目是 **_SaltedFish Club保姆级NIO教程_** 的一部分

---

## 模型列表
* Multithread Model Server - one connection per thread
* Reactor Model Server - basic reactor
* Reactor Model Server With Thread Pool - reactor + thread pool
* MultiReactor Model Server - one loop per thread

### **Multithread Model - one connection per thread**

这是最简单的并发服务器模型,通过为每一个连接分配一个线程的方式支持并发。

在大量活跃连接时表现出**非常糟糕的性能**(_由于大量的上下文切换和创建销毁线程的开销_),而在大量非活跃连接时会导致大量的内存浪费(_线程的栈空间_)。

这个模型不适用于大量连接(_即使是短连接_),**为它添加线程池并不能解决这个问题**(_连接会占用线程池的线程导致工作线程阻塞最终导致线程池停止处理任务或不得不扩大,但加入线程池后能适用于大量短连接_)。

### **Reactor Model - basic reactor**

这是最简单的事件驱动服务器模型,**它只有一条线程,但能支持大量连接**(_无论它们是短连接还是长连接_)。

虽然能使用于大量连接但由于只有一条线程,处理能力是有限的(_在一段时间只能处理一个连接_)。

并且**由于Reactor模型的特性,操作将会被割裂**,原来的操作将分成几步完成,这会提高开发人员的负担,并带来更高的复杂度(_造成回调地狱_)。

**值得注意的是必须在服务器开始工作前设置好一个统一的回调**(_您可以通过在channel中添加回调队列来缓解这种情况_),否则当事件到达时,将出现不知道调用哪个回调的局面。

### **Reactor Model With Thread Pool - reactor + thread pool**

这个模型是basic reactor的改进,通过添加一个线程池来处理业务逻辑达到,业务与I/O分离的效果。

业务代码不再占用I/O线程的处理时间,而是移动到专用的线程池中通过线程池进行业务处理,最后将响应汇总给I/O线程,并最终由I/O线程发送响应。

这个模型能在处理大量连接的同时显著提高CPU利用率(_当负载增加时_),但不能同时对多个连接进行I/O操作,适用于计算密集型应用。

### **MultiReactor Model - one loop per thread**

这个模型是basic reactor的改进,通过多个I/O线程执行不同的事件循环来提高CPU利用率(_在负载增加时_)。

由于I/O线程的增加导致服务器可以真正意义上的同时处理多个客户,但I/O线程彼此之间无法相互通信,导致任务可能分配不均。然而,在较大负载时仍有相当高的性能。

**适用于处理突发的大量I/O**,并且可以通过设计I/O线程间互相通信的协议使计算任务可以在I/O线程之间流通(_例如增加公用BlockingQueue_),**可能是最灵活的配置**。