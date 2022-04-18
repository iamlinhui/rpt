### 内网穿透工具

> 一个可用于内网穿透的工具，将局域网个人电脑、服务器映射到公网。

> 支持任何TCP上层协议，可用于远程桌面、访问内网网站、SSH访问、远程连接打印机、本地支付接口调试、微信小程序调试...

> 支持HTTP端口复用，可用于内网反向代理，共用服务端80/443端口。支持HTTP请求升级为WebSocket，HTTP2。

> SSL双端验证，数据加密传输。

> 部署简单，提供桌面客户端。

![img_1.png](doc/img_1.png)
![img_2.png](doc/img_2.png)
![img_3.png](doc/img_3.png)

> http代理、https代理(eg:本地支付接口调试,本地微信公众号调试)\
> 远程桌面(eg:远程办公)\
> socks5代理(eg:网络代理)\
> ssh访问(eg:远程连接内网服务器)

运行于TCP协议之上的协议：

- HTTP协议：超文本传输协议，用于普通浏览
- HTTPS协议：安全超文本传输协议，身披SSL外衣的HTTP协议
- FTP协议：文件传输协议，用于文件传输
- POP3协议：邮局协议，收邮件使用
- SMTP协议：简单邮件传输协议，用来发送电子邮件
- Telnet协议：远程登陆协议，通过一个终端登陆到网络
- SSH协议：安全外壳协议，用于加密安全登陆，替代安全性差的Telnet协议
- RDP协议：远程桌面协议，是一个多通道的协议，让用户连上提供终端服务的电脑
- SOCKS协议: 防火墙安全会话转换协议

![img.png](doc/img.png)

## 服务端配置`server.yml`

```yaml
#服务端绑定IP
serverIp: 0.0.0.0
#服务端与客户端通讯端口
serverPort: 6167
#服务端暴露的HTTP复用端口
httpPort: 80
#服务端的传输速度限制 单位bytes/s 为0则不限制
serverLimit: 1048576
#授权给客户端的秘钥
clientKey:
  - b0cc39c7-1b78-4ff6-9486-020399f569e9
  - 4befea7e-a61c-4979-b012-47659bab6f21
```

## 客户端配置`client.yml`

```yaml
#服务端IP
serverIp: 127.0.0.1
#服务端与客户端通讯端口
serverPort: 6167
#授权给客户端的秘钥
clientKey: b0cc39c7-1b78-4ff6-9486-020399f569e9

# remotePort与localPort映射配置
config:
  - # 传输协议类型 (TCP或者HTTP) 不填写默认就是TCP
    proxyType: TCP
    # 客户端连接目标IP
    localIp: 127.0.0.1
    # 客户端连接目标端口
    localPort: 3389
    # 服务暴露端口
    remotePort: 4389
    # 描述
    description: rdp
    
  - proxyType: TCP
    localIp: 127.0.0.1
    localPort: 6379
    remotePort: 7379
    description: redis
    
  - proxyType: HTTP
    localIp: 127.0.0.1
    localPort: 8080
    domain: localhost
    description: tomcat
```

## 部署

首先在jar包的当前目录,新建conf文件夹,并将相应的配置文件(`client.yml`或者`server.yml`)放进去

启动脚本`start.sh`

```shell
java -server -d64 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dnetworkaddress.cache.ttl=600 \
     -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai \
     -Dclient.encoding.override=UTF-8 -Dfile.encoding=UTF-8 -Xbootclasspath/a:./conf \
     -jar rpt*.jar  > /dev/null 2>&1 & echo $! > pid.file &
```

关闭脚本`stop.sh`

```shell
kill $(cat pid.file)
```

## SSL证书申请

详细操作步骤看这里
[OpenSSL申请证书](https://github.com/iamlinhui/rpt/wiki/OpenSSL证书申请)

## 替换证书

如果需要替换证书则:

client端需要在conf文件夹里面放置`client.crt`和`pkcs8_client.key`和`ca.crt`

server端需要在conf文件夹里面放置`server.crt`和`pkcs8_server.key`和`ca.crt`

## 注册Windows服务

下载 [winsw工具](https://github.com/winsw/winsw/releases) ,将WinSW-x64.exe文件重命名为rpt-client.exe, 和rpt-client.jar放在同一个目录中,
在该目录中新建文件rpt-client.xml文件,写入如下内容

```xml

<service>
    <id>rpt-client</id>
    <name>rpt-client</name>
    <description>rpt-client</description>
    <executable>java</executable>
    <arguments>-server -d64 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dnetworkaddress.cache.ttl=600 -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai -Dclient.encoding.override=UTF-8 -Dfile.encoding=UTF-8 -Xbootclasspath/a:./conf -jar rpt-client.jar
    </arguments>
</service>
```

执行`rpt-client.exe install`即可完成注册为Windows服务

## 其他

Java命令行添加外部文件到classpath，从而实现读取外部配置文件

```text
对于jar包启动，使用-Xbootclasspath/a:命令；对于class启动，使用-cp命令。

两种方法分别是：
1. java -Xbootclasspath/a:/etc/hadoop/conf:/etc/hive/conf -jar example.jar
2. java -cp /etc/hadoop/conf:/etc/hive/conf:./example.jar example.Main.class
注意事项：
（1）-Xbootclasspath/a:要在-jar之前
（2）-Xbootclasspath/a:和后面的参数之间不能有空格
（3）example.Main.class是jar包的主类，要把相应的jar包放到classpath参数中。
（4）文件路径之间使用分隔符（win下为分号，linux下为冒号）
```

