package com.ss.encrypt.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** 在不写入底层 response 的情况下捕获待加密正文。 */
public final class EncryptResponseBodyWrapper extends HttpServletResponseWrapper {

    private final LimitedByteArrayOutputStream body;
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean outputStreamRequested;
    private boolean writerRequested;

    public EncryptResponseBodyWrapper(
            HttpServletResponse response, long maxBytes) {
        super(response);
        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maxBytes must be between 1 and Integer.MAX_VALUE");
        }
        this.body = new LimitedByteArrayOutputStream((int) maxBytes);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (writerRequested) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        outputStreamRequested = true;
        if (outputStream == null) {
            outputStream = new CapturingServletOutputStream(body);
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (outputStreamRequested) {
            throw new IllegalStateException(
                    "getOutputStream() has already been called");
        }
        writerRequested = true;
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(body, charset()));
        }
        return writer;
    }

    /** 返回缓冲的业务响应正文。 */
    public String bodyAsString() {
        flushLocal();
        return body.toString(charset());
    }

    @Override
    public void flushBuffer() {
        flushLocal();
    }

    @Override
    public void resetBuffer() {
        super.resetBuffer();
        body.reset();
    }

    @Override
    public void reset() {
        super.reset();
        body.reset();
        outputStream = null;
        writer = null;
        outputStreamRequested = false;
        writerRequested = false;
    }

    @Override
    public void setContentLength(int length) {
        // 加密后正文长度由 filter 最终设置。
    }

    @Override
    public void setContentLengthLong(long length) {
        // 加密后正文长度由 filter 最终设置。
    }

    private Charset charset() {
        String encoding = getCharacterEncoding();
        return encoding == null
                ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    private void flushLocal() {
        if (writer != null) {
            writer.flush();
        }
        try {
            if (outputStream != null) {
                outputStream.flush();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot flush encrypted response buffer",
                    exception);
        }
    }

    private static final class LimitedByteArrayOutputStream
            extends ByteArrayOutputStream {
        private final int maxBytes;

        private LimitedByteArrayOutputStream(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int value) {
            ensureCapacityFor(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] value, int offset, int length) {
            ensureCapacityFor(length);
            super.write(value, offset, length);
        }

        private void ensureCapacityFor(int additionalBytes) {
            if (count + additionalBytes > maxBytes) {
                throw new ApiPayloadTooLargeException();
            }
        }
    }

    private static final class CapturingServletOutputStream
            extends ServletOutputStream {
        private final LimitedByteArrayOutputStream output;

        private CapturingServletOutputStream(
                LimitedByteArrayOutputStream output) {
            this.output = output;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            if (writeListener == null) {
                throw new IllegalArgumentException("writeListener must not be null");
            }
        }

        @Override
        public void write(int value) {
            output.write(value);
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            output.write(value, offset, length);
        }
    }
}
