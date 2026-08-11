# Simple Secret UDP Plugin

`simple-secret-plugin-udp` 提供 UDP 单播和组播监听能力。模块仅使用 JDK 17 API，没有生产依赖，也不依赖 Spring。

## Maven 依赖

推荐先导入 `simple-secret-common-bom`，再按需声明插件：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.ss</groupId>
            <artifactId>simple-secret-common-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-plugin-udp</artifactId>
    </dependency>
</dependencies>
```

## 单播监听

`UdpUnicastManager` 按绑定 IP 和端口管理监听器，重复启动相同监听地址时返回 `false`：

```java
import com.ss.udp.UdpMessageHandler;
import com.ss.udp.UdpUnicastListener;
import com.ss.udp.UdpUnicastManager;

import java.nio.charset.StandardCharsets;

UdpMessageHandler handler = packet -> {
    String body = new String(
            packet.getData(), packet.getOffset(), packet.getLength(),
            StandardCharsets.UTF_8);
    System.out.printf("received from %s:%d: %s%n",
            packet.getAddress().getHostAddress(), packet.getPort(), body);
};

UdpUnicastListener listener = new UdpUnicastListener(
        "0.0.0.0", 9000, handler);
listener.setMaxMessageLength(8 * 1024);

UdpUnicastManager manager = new UdpUnicastManager();
boolean started = manager.startListener(listener);

// 应用停止时释放 socket；重复停止返回 false。
manager.stopListener("0.0.0.0", 9000);
```

## 组播监听

组播必须指定实际属于本机网卡的数值 IP，不能使用 `0.0.0.0`：

```java
import com.ss.udp.UdpMulticastListener;
import com.ss.udp.UdpMulticastManager;

UdpMulticastListener listener = new UdpMulticastListener(
        "239.10.10.10",
        9001,
        "192.168.1.20",
        packet -> System.out.println("bytes=" + packet.getLength()));
listener.setMaxMessageLength(64 * 1024);

UdpMulticastManager manager = new UdpMulticastManager();
manager.joinGroup(listener);

// 应用停止时离开组播并关闭 socket。
manager.leaveGroup("239.10.10.10", 9001, "192.168.1.20");
```

IPv4 组播与本地 IPv4 网卡配对，IPv6 组播与本地 IPv6 网卡配对。地址参数只接受数值 IP，插件不会为主机名执行 DNS 查询。

## 错误处理

处理器抛出的异常、socket 创建失败和接收错误会传给 `onError`。错误回调自身的异常只记录日志，不会覆盖原始错误：

```java
UdpMessageHandler handler = new UdpMessageHandler() {
    @Override
    public void handle(java.net.DatagramPacket packet) {
        // 处理数据报
    }

    @Override
    public void onError(Exception exception) {
        // 记录指标或触发告警
    }
};
```

消息回调在监听线程中同步执行。耗时工作应提交给业务线程池，避免阻塞后续数据报接收。每次回调获得独立 payload 缓冲区，后续接收不会覆盖已交付的数据。

## 长度与生命周期

- `setMaxMessageLength` 必须在线程启动前调用，范围为 1 到 65507 字节。
- 超过配置长度的数据报会按 `DatagramSocket` 语义被截断，插件不会自动拼包。
- 监听器是 daemon 线程；应用仍应通过 `stopListener`、`leaveGroup` 或 `shutdownAll` 显式释放资源。
- 监听线程因绑定、入组或接收错误退出后，管理器会自动移除对应实例。
- UDP 没有连接状态，长时间没有收到消息不会触发 socket 重建。

