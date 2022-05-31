操作步骤大概是:
> 自建CA-->生成服务端和客户端私钥-->根据key生成csr文件-->根据ca证书server.csr、client.csr生成x509证书-->将key文件进行PKCS#8编码

实际应用中，CA是一个机构。而我们本地测试就没必要去进行申请的，所以自己建立一个CA。
> CA 拥有一个证书（内含公钥和私钥）。网上的公众用户通过验证 CA 的签字从而信任 CA ，任何人都可以得到 CA 的证书（含公钥），用以验证它所签发的证书。
如果用户想得到一份属于自己的证书，他应先向 CA 提出申请。在 CA 判明申请者的身份后，便为他分配一个公钥，并且 CA 将该公钥与申请者的身份信息绑在一起，并为之签字后，便形成证书发给申请者。
如果一个用户想鉴别另一个证书的真伪，他就用 CA 的公钥对那个证书上的签字进行验证，一旦验证通过，该证书就被认为是有效的。证书实际是由证书签证机关（CA）签发的对用户的公钥的认证。

建立CA之后，我们要创建client和server的证书，然后client和server的证书都有了，导入程序中就实现了双向认证，如果只是对server进行颁发证书的话，那就是单项认证的SSL。

## 下载OpenSSL
[下载Windows版openssl](http://slproweb.com/products/Win32OpenSSL.html) ,解压找到bin目录中的openssl.exe

## 建立CA
```txt
req -new -x509 -keyout ca.key -out ca.crt -days 36500
```
此时，会产生文件 ca.key,ca.crt

## 生成服务端和客户端私钥
服务端
```txt
genrsa -des3 -out server.key 1024
```
客户端
```txt
genrsa -des3 -out client.key 1024
```

## 根据key生成csr文件
注意，如果生成server.csr 后再 client.csr提示错误，关闭当前窗口，然后重新打开openssl.exe进行执行服务端

服务端
```txt
req -new -key server.key -out server.csr 
```

客户端
```txt
req -new -key client.key -out client.csr
```

## 根据ca证书server.csr、client.csr生成x509证书
如果上面没有生成 ca.key,ca.crt，这里就会报错，提示找不到文件

服务端
```txt
x509 -req -days 3650 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt
```

客户端
```txt
x509 -req -days 3650 -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt
```

## 将key文件进行PKCS#8编码

服务端
```txt
pkcs8 -topk8 -in server.key -out pkcs8_server.key -nocrypt
```

客户端
```txt
pkcs8 -topk8 -in client.key -out pkcs8_client.key -nocrypt
```

## 将bin文件夹下，如下文件复制出来
这里需要注意的是sercer端和client端使用的ca.crt使用的是同一个，意味着同一个CA进行颁发的证书
```txt
server端：ca.crt、server.crt、pkcs8_server.key
client端：ca.crt、client.crt、pkcs8_client.key
```
