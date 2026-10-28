# wirehair-jni

面向 Java 17 项目的 Wirehair JNI 封装，计划用于 QOcr 二维码文件传输。

## 功能

- Java 编码器和解码器 API
- JNI 调用官方 Wirehair 原生库
- Windows x86_64 和 Linux x86_64
- GitHub Actions 自动构建
- SHA-256 和逐字节数据恢复验证
- 原生资源通过 AutoCloseable 释放

## 构建步骤

1. 将工程文件提交到 GitHub。
2. 打开 Actions。
3. 选择“构建 Wirehair JNI 多平台 JAR”。
4. 点击 Run workflow。
5. 等待 Windows、Linux 和 package 三个阶段完成。
6. 在 Artifacts 中下载 wirehair-jni-jar。

## 在 QOcr 中使用

将 wirehair-jni-1.0.0.jar 安装到本地 Maven 仓库：

mvn install:install-file \
-Dfile=wirehair-jni-1.0.0.jar \
-DgroupId=com.hrz \
-DartifactId=wirehair-jni \
-Dversion=1.0.0 \
-Dpackaging=jar

在 QOcr 的 pom.xml 中添加：
```xml
<dependency>
    <groupId>com.hrz</groupId>
    <artifactId>wirehair-jni</artifactId>
    <version>1.0.0</version>
</dependency>
```


## 注意事项

- 编码器和解码器必须及时关闭。
- 接收端必须保留发送端生成的 blockId。
- 恢复完成后必须进行文件摘要校验。
- 原生构建和恢复测试以 GitHub Actions 的实际运行结果为准。