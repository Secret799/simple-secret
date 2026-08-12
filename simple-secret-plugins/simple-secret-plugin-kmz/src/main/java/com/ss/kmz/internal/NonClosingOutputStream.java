package com.ss.kmz.internal;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 允许包装流完成关闭流程，但只刷新、不关闭调用方输出流。
 */
public final class NonClosingOutputStream extends FilterOutputStream {

    /**
     * 创建包装流。
     *
     * @param outputStream 输出流
     */
    public NonClosingOutputStream(OutputStream outputStream) {
        super(outputStream);
    }

    /** 仅刷新调用方输出流。 */
    @Override
    public void close() throws IOException {
        flush();
    }
}
