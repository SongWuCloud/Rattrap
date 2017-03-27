# Rattrap

The container-based cloud platform for mobile code offloading. *Rattrap* provides mobile code runtime environments throuph *Cloud Android Container*.

![](http://7xlcoc.com1.z0.glb.clouddn.com/rattrap_framework.png)

We introduce two basic concepts here to help understand *Rattrap*.
## Mobile code offloading
>Mobile offloading is a key concept in mobile cloud. It means mobile apps can offload computation-intensive code to cloud to use the computing power supplied by cloud infrastructure.

It's different from traditional client-server app solution because in mobile offloading developers don't need to develop the server side at all! All the computation logic is in the app. In this case, the app can decide whether the computation should go to cloud according to the context in the mobile device, such as power and network latency.

If you wannna know more about mobile offloading, you can google it.

## About Cloud Android container
In our offloading framework, the cloud runtime is not VM or JVM. We use OS-level virtualization "Linux Container (LXC)" as the runtime for mobile code. For the purpose of running android code in x86 GNU-Linux server, we modified android source code and the linux kernel it uses. The modification work is based on Android-x86 project. With our effort, android os can finally run in the ordinary linux containers!

# Composition
The *Rattrap* project here is composed of 2 parts. The details can be found in README of the 2 folders. 

## Runtime

Runtime shows how to build *Rattrap* with an existing cloud server.

## Framework

Framework consists of the code offloading frameworks and our 6 workloads which have been adopted to *Rattrap*.

## How to cite

*Rattrap* is built for comparison with current cloud platform based on VM.  If you are using it for your research, please do not forget to cite. （Song Wu, Chao Niu, Jia Rao, Hai Jin and Xiaohai Dai, “Container-Based Cloud Platform for Mobile Computation Offloading”, in Proceedings of IPDPS’17）

Thanks! 

