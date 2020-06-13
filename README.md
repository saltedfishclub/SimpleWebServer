# SimpleWebServer
一个简单的基于Java NIO的短连接 Web Server  
提供四种常见服务器并发模型
## 模型列表
* Multithread Model Server - one connection per thread
* Reactor Model Server - basic reactor
* Reactor Model Server With Thread Pool - reactor + thread pool
* MultiReactor Model Server - one loop per thread