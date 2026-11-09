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
    private BoundedAiResponseParser() { }

    static HttpMessageParser<ClassicHttpResponse> create(Http1Config config) {
        var parser = new DefaultHttpResponseParser(config);
        return (buffer, input) -> parser.parse(new HeaderBuffer(buffer), input);
    }

    private static final class HeaderBuffer implements SessionInputBuffer {
        private final SessionInputBuffer delegate;
        private int bytes;
        HeaderBuffer(SessionInputBuffer delegate) { this.delegate = delegate; }
        @Override public int readLine(CharArrayBuffer line, InputStream stream) throws IOException {
            int count = delegate.readLine(line, stream);
            if (count >= 0 && (bytes += count + 2) > 8192) throw new MessageConstraintException("ai_response_headers_too_large");
            return count;
        }
        @Override public int length() { return delegate.length(); }
        @Override public int capacity() { return delegate.capacity(); }
        @Override public int available() { return delegate.available(); }
        @Override public int read(byte[] bytes, int offset, int length, InputStream input) throws IOException { return delegate.read(bytes, offset, length, input); }
        @Override public int read(byte[] bytes, InputStream input) throws IOException { return delegate.read(bytes, input); }
        @Override public int read(InputStream input) throws IOException { return delegate.read(input); }
        @Override public HttpTransportMetrics getMetrics() { return delegate.getMetrics(); }
    }
}
