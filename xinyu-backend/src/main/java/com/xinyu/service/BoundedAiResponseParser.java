package com.xinyu.service;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.MessageConstraintException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.io.HttpMessageParser;
import org.apache.hc.core5.http.io.HttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;
import java.io.IOException;
import java.io.InputStream;

/** 在报文解析期间累计限制状态行及响应头，避免解析完整大头部后才做业务检查。 */
final class BoundedAiResponseParser {
    /** 状态行与全部响应头的累计字节上限（每行另计 CRLF 两字节）。 */
    private static final int MAX_HEADER_BYTES = 8192;

    private BoundedAiResponseParser() { }

    static HttpMessageParser<ClassicHttpResponse> create(Http1Config config) {
        var parser = new DefaultHttpResponseParser(config);
        return (buffer, input) -> parser.parse(new HeaderBuffer(buffer), input);
    }

    private static final class HeaderBuffer implements SessionInputBuffer {
        private final SessionInputBuffer delegate;
        private int consumedBytes;

        HeaderBuffer(SessionInputBuffer delegate) { this.delegate = delegate; }

        /** 唯一的增强点：每读一行就累计字节，超限立即中止解析。 */
        @Override
        public int readLine(CharArrayBuffer line, InputStream stream) throws IOException {
            int count = delegate.readLine(line, stream);
            if (count >= 0) {
                consumedBytes += count + 2;
                if (consumedBytes > MAX_HEADER_BYTES) {
                    throw new MessageConstraintException("ai_response_headers_too_large");
                }
            }
            return count;
        }

        // ---- 以下方法原样委托 ----
        @Override public int length() { return delegate.length(); }
        @Override public int capacity() { return delegate.capacity(); }
        @Override public int available() { return delegate.available(); }
        @Override public int read(byte[] bytes, int offset, int length, InputStream input) throws IOException { return delegate.read(bytes, offset, length, input); }
        @Override public int read(byte[] bytes, InputStream input) throws IOException { return delegate.read(bytes, input); }
        @Override public int read(InputStream input) throws IOException { return delegate.read(input); }
        @Override public HttpTransportMetrics getMetrics() { return delegate.getMetrics(); }
    }
}
