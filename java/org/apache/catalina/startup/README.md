# 启动类笔记

1. 通过await()使main线程等待，监听连接的线程池是deamon线程，当监听到shutdown时，while关闭，await()退出，主线程关闭

2. 通过反射调用配置文件的方法，依次向下调用load/init方法，加载不同级别的组件对象

3. bootstrap是动作类，应该为整个应用抽象一个对象类（catalina），而动作类持有对象类的对象