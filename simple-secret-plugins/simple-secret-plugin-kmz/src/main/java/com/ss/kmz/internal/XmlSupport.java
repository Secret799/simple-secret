package com.ss.kmz.internal;

import com.ss.kmz.exception.KmzException;

import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * XML 入口共用的安全配置和有界读取工具。
 */
public final class XmlSupport {

    private XmlSupport() {
    }

    /**
     * 创建禁止 DTD 和外部实体的 StAX 工厂。
     *
     * @return 新创建的对象
     */
    public static XMLInputFactory newSecureInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    /**
     * 在不关闭输入流的前提下读取最多 {@code maxBytes} 字节。

     *
     * @param inputStream 输入流
     * @param maxBytes 最大允许字节数
     * @param contentName 内容名称
     * @return 返回的 {@code byte[]} 结果
     */
    public static byte[] readLimited(InputStream inputStream, int maxBytes, String contentName) {
        if (inputStream == null) {
            throw new IllegalArgumentException(contentName + " input stream must not be null");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        try {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new KmzException(contentName + " exceeds maximum size of " + maxBytes + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new KmzException("failed to read " + contentName, exception);
        }
    }
}
