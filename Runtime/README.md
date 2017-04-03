# Runtime
The rattrap.patch is the runtime preparation for *Rattrap*. The work here includes the kernel extension for Android container and Android OS reduction for offloading. 
The patch is based on the android-x86 git version. The original git code can be found on [here](http://www.android-x86.org/releases/releasenote-4-4-r2).

The .config file is the kernel configuration when you compile the kernel extension.

## Build

Download the Android-x86 source into your directory.

    $ repo init -u git://gitscm.sf.net/gitroot/android-x86/manifest -b android-x86-4.4-r2
    $ repo sync

Apply the rattrap.patch.

    $ patch -p1 < rattrap.patch
Copy .config file into the kernel folder and buid it. After the kernel build, you can either install it or just install the kernel modules.

## Create

The rootfs.tar.gz is the root filesystem of *Cloud Android Container*. The newlxc.py script and rootfs.tar.gz together create individual *Cloud Android Container*.

The following command creates a simple *Cloud Android Contaienr* named 'test'.

	$ ./newlxc.py -n test 

It should be noted that, the creation of *Cloud Android Container* is handled by *Dispatcher*. There is no need to create them manually.