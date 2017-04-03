#  Cloud Android Container Document



## Introduction

----

This document describes how to set up and run Android OS in the ordinary Linux Containers. For the purposer of running android code in x86 GNU-Linux server, we modified android source code and the linux kernel it uses. The modification work is based on [Android-x86 project](http://www.android-x86.org/). With our effort, Android OS can finally  run in the Linux Containers.

We provide you with a virtual-box image, which has been installed with android-x86 kernel. Just download the image from [pan.baidu.com](https://pan.baidu.com/s/1o84UdVc)(password `itpa`) and open it with virtualbox, then enter the 32-bit Ubuntu 15.04 operator system with username `root` and password `5967903`. If you want to set up your android container step by step, the following instructions may be helpful to you.



## 1. Requirements

----

- X86-Linux environment(Ubuntu recommended)
- Sun Java 6 JDK, JRE
- Version Control tools Repo and Git




## 2. Build the kernel

In this section we describe how to get android-x86 source code and build a customized kernel. Containers share OS kernel interfaces with little overhead,  but are unable to support multiple kernels. The kernel restriction has significantly weaken the generality of containers and we use android-x86 kernel instead of mainstream Linux kernel.

----

#### 2.1 Get Android-x86 source code

Our work is based on the android-x86 git version. The origin git code can be found on [here](http://www.android-x86.org/releases/releasenote-4-4-r2). Download the `Android-x86-4.4-r2` into your directory.

```
$ repo init -u git://gitscm.sf.net/gitroot/android-x86/manifest -b android-x86-4.4-r2
$ repo sync
```



####  2.2 Patch source code

After downloading the source code, apply the [rattrap.patch](https://github.com/SongWuCloud/Rattrap/blob/master/Runtime/rattrap.patch).

```
patch -p1 < rattrap.patch
```

We also provide a [tool](https://github.com/SongWuCloud/Rattrap/blob/master/tools/patch-split.sh) for you, that may help you split the patch file created by `repo diff` command. 



#### 2.3 Compile Android-x86 Kernel

After downloading the android-x86 source code, we are ready to build the kernel. Follow the AOSP page "[Establishing a Build Environment](http://source.android.com/source/initializing.html)" to configure your build environment. The following libraries must be installed for 32 bit kernel build environment. 

```
apt-get install build-essential \
    curl \
    git \
    g++-multilib \
    zlib1g:i386 \ 
    libxml2-utils \
    squashfs-tools \
    dosfstools \
    mtools \
    python \
    python-libxml2 \
    python-mako \
    bison \
    zip \
    unzip \ 
    bc \
    gperf \
    gettext \
    genisoimage \
    syslinux \
    libncurses5-dev \
    libncursesw5-dev
```



We also provide a [dockerfile](https://github.com/CGCL-codes/Rattrap/blob/master/tools/Dockerfile) for 32 bit Android-x86 Kernel build environment. Before compiling kernel, copy the [.config](https://github.com/SongWuCloud/Rattrap/blob/master/Runtime/.config) file into the  `$KITKAT-x86/kernel/arch/x86/configs` folder and rename it (suppose the name of your configure file is my_config), and then build it with following commands.

```
. build/envsetup.sh
lunch android-x86-eng
make -jX iso_img TARGET_PRODUCT=android-_x86 TARGET_KERNEL_CONFIG=my_config
```



Since we are going to run android OS in Linux container, android drivers must be built directly into the kernel. The following kernel options must exist in your configuration file.

```
CONFIG_ANDROID=y
CONFIG_ANDROID_BINDER_IPC=y
CONFIG_ANDROID_BINDER_IPC_32BIT=y
CONFIG_ASHMEM=y
CONFIG_ANDROID_LOGGER=y
CONFIG_ANDROID_TIMED_OUTPUT=y
CONFIG_ANDROID_TIMED_GPIO=y
CONFIG_ANDROID_LOW_MEMORY_KILLER=y
CONFIG_ANDROID_LOW_MEMORY_KILLER_AUTODETECT_OOM_ADJ_VALUES=y
CONFIG_ANDROID_INTF_ALARM_DEV=y
CONFIG_SYNC=y
CONFIG_SW_SYNC=y
CONFIG_SW_SYNC_USER=y
```

If you want to build the kernel and its modules alone, by changing the goal `iso_img` to `kernel`. More details that you can find on [Android-x86 website](http://www.android-x86.org/documents/customizekernel).



#### 2.4 Install Kernel Modules

After compiling the android-x86 successfully, enter the `$OUT/target/product/x86/obj/kernel` folder and execute the following commands.

```
make modules_install && make install
```

Then `reboot` system and choose the `android-x86 kernel` to enter.



## 3. Android Container Setup

----

#### 3.1 Container configure

Using `apt-get install lxc` command to install `lxc tool` on your host. After that, create folder `android` beneath the  `/var/lib/lxc`  directory to save container files. Then create `config` file and  `rootfs` folder within the `android` folder. We provide a base configuration file for our android container.

```
lxc.network.type = veth
lxc.network.link = lxcbr0
lxc.network.flags = up
lxc.rootfs = /var/lib/lxc/android/rootfs
lxc.aa_allow_incomplete = 1
lxc.cgroup.devices.allow = a
lxc.aa_profile = unconfined
lxc.init_cmd = /init
lxc.autodev = 0
lxc.pts = 1024
lxc.tty = 4
```



#### 3.2 Root filesystem configure 

After building android-x86, you can find a folder named `root` in `$OUT/target/product/x86` directory.  Copy the `$OUT/target/product/x86/system` to `$OUT/target/product/x86/root/`, then enter `root` directory and copy all files to `/var/lib/lxc/android/rootfs` directory.

We also provide an example of android container root filesystem. The [newlxc.py](https://github.com/SongWuCloud/Rattrap/blob/master/Runtime/newlxc.py) script and [rootfs.tar.gz](https://github.com/SongWuCloud/Rattrap/blob/master/Runtime/rootfs.tar.gz) together create individual Cloud Android Container.

The following command creates a simple Cloud Android Container named 'test'.

```
$ ./newlxc.py -n test
```



#### 3.3  Init.rc configure

We construct the file system according to initrd.img before starting a Cloud Android Container. Containers are populated with Android rootfs and start directly by executing `/init`.  When the init process starts, it will parse the `init.rc` file and initializes core system services. Some critical system services must be started by init process, but some may be not necessary in the cloud (such as wifi and bluetooth hardware devices). You can decide which system services need to start by modifying the `init.rc` file.



## 4. Start Cloud Android Container

----

We provide an offloading framework and six benchmark workloads. The workloads are as follows, and details can be found in our paper.

- [AntiVirus](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/AntiVirus/AndroidManifest.xml)
- [CalculusTools](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/CalculusTools/AndroidManifest.xml)
- [CuckooChessAPK](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/CuckooChessAPK/AndroidManifest.xml)
- [FaceDetect](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/FaceDetect/AndroidManifest.xml)
- [Linpack](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/Linpack/AndroidManifest.xml)
- [OCR](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/OCR/AndroidManifest.xml)

**DirectoryService** is actually the dispatcher in our rattrap. To run it, you need to export the project as a runnable jar (suppose the name of jar is DirService.jar) and run

```
$ java -jar DirServic.jar
```

You need a MySQL DB to maintain the states of Cloud Android Containers by import the [sql file](https://github.com/SongWuCloud/Rattrap/blob/master/Framework/androidlxc.sql) we provide.

**LxcOff-Library** is the offloading library acuqired when you want to modify your application to adopt Rattrap. The implementation details can be found in the workload source code.

**LxcOff-Server** is the server part running in Cloud Android Container, which is placed in `$ROOTFS/system` directory. You should not pay more attention to it, since the rootfs has installed it.



## Useful Resources

----

[http://www.android-x86.org/getsourcecode](http://www.android-x86.org/getsourcecode)

[http://www.android-x86.org/documents/customizekernel](http://www.android-x86.org/documents/customizekernel)

[configuring the kernel](http://www.linux.org/threads/the-linux-kernel-configuring-the-kernel-part-1.4274/)

[android-base.cfg](https://android.googlesource.com/kernel/common.git/+/android-4.4/android/configs/android-base.cfg)

[android-recommended.cfg](https://android.googlesource.com/kernel/common.git/+/android-4.4/android/configs/android-recommended.cfg)

[lxc-container-conf](https://linuxcontainers.org/lxc/manpages//man5/lxc.container.conf.5.html)

[android-2.2.3_r2-init-readme.txt](https://android.googlesource.com/platform/system/core/+/android-2.2.3_r2/init/readme.txt)

[android's Init](http://cecs.wright.edu/~pmateti/Courses/4900/Lectures/Internals/Embedded-Android-228-247-pm.pdf)









