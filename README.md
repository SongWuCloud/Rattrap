# 1. Rattrap

The container-based cloud platform for mobile code offloading. *Rattrap* provides mobile code runtime environments throuph *Cloud Android Container*.

![](https://github.com/zjsyhjh/android-container/blob/master/png/rattrap_framework.png?raw=true)

We introduce two basic concepts here to help understand *Rattrap*.
### 1.1 Mobile code offloading
>Mobile offloading is a key concept in mobile cloud. It means mobile apps can offload computation-intensive code to cloud to use the computing power supplied by cloud infrastructure.

It's different from traditional client-server app solution because in mobile offloading developers don't need to develop the server side at all! All the computation logic is in the app. In this case, the app can decide whether the computation should go to cloud according to the context in the mobile device, such as power and network latency.

We have implemented a [container based computational code offloading framework](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/README.md), which contains six instances. If you wannna know more about mobile offloading, you can google it.

### 1.2 Cloud Android container
In our offloading framework, the cloud runtime is not VM or JVM. We use OS-level virtualization "Linux Container (LXC)" as the [runtime](https://github.com/SongWuCloud/Rattrap/blob/master/Runtime/README.md) for mobile code. For the purpose of running android code in x86 GNU-Linux server, we modified android source code and the linux kernel it uses. The modification work is based on Android-x86 project. With our effort, android os can finally run in the ordinary linux containers!

# 2. How to use

We provide a detailed description of the [document](https://github.com/SongWuCloud/Rattrap/blob/master/docs/CAC.md) to you. This document describes how to set up and run Android OS in the ordinary Linux Containers. You can set up your own Android container according the documentation.

# 3. How to cite

*Rattrap* is built for comparison with current cloud platform based on VM.  If you are using it for your research, please do not forget to cite. [（Song Wu, Chao Niu, Jia Rao, Hai Jin and Xiaohai Dai, “Container-Based Cloud Platform for Mobile Computation Offloading”, in Proceedings of IPDPS’17）](http://grid.hust.edu.cn/wusong/file/ipdps17.pdf)

Thanks! 

