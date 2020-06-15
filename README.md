# SimpleWebServer

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/aa8578729e3648df8198ea888206a7cd)](https://app.codacy.com/manual/NaturalSelect/SimpleWebServer?utm_source=github.com&utm_medium=referral&utm_content=NaturalSelect/SimpleWebServer&utm_campaign=Badge_Grade_Dashboard)

一个简单的基于Java NIO的短连接 Web Server  
提供四种常见服务器并发模型
## 模型列表
* Multithread Model Server - one connection per thread
* Reactor Model Server - basic reactor
* Reactor Model Server With Thread Pool - reactor + thread pool
* MultiReactor Model Server - one loop per thread
## Benchmark
### 配置
* OS:       Kali x64 虚拟机
* 内存:     2GB
* CPU:      I5 9400F 3 cores
* 测试工具:  Apache Benchmark
* 命令行:    ab -c 300 -t 60 url
### SimpleWebServer 与 Iris(Golang)
SimpleWebServer  
![SimpleWebServer](/img/SimpleWebServer.png)  
Iris(Golang)  
![Golang](/img/Golang.png)
### SimpleWebServer 与 C++
SimpleWebServer  
![SimpleWebServer](/img/SimpleWebServer.png)  
C++  
![CPP](/img/CPP.png)