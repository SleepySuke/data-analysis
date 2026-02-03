# 该系统需要的配置服务

## Erlang

版本为25.3.1的Erlang

## RabbitMQ

版本为3.1.2

## 安装

先安装Erlang再安装RabbitMQ

然后在使用cmd以管理员身份运行

切换到安装好的RabbitMQ目录下的sbin文件

输入指令

~~~cmd
rabbitmq-plugins.bat enable rabbitmq_management
~~~

随后再依次输入

~~~cmd
rabbitmq-service.bat stop 
rabbitmq-service.bat install 
rabbitmq-service.bat start 
~~~

最后可以直接在浏览器上访问

http://localhost:15672/mgmt

账号:guest

密码:guest

15672 RabbitMQ web面板

5672 RabbitMQ
以上是本地下载mq

后续可以使用docker进行拉取镜像mq


![输入图片说明](%E6%B5%81%E7%A8%8B%E5%9B%BE.png)

