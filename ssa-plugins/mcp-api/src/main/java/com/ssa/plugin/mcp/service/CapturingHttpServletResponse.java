package com.ssa.plugin.mcp.service;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * 捕获输出流的 {@link HttpServletResponse} 桩（零依赖手写，替代 spring-test 的 MockHttpServletResponse）。
 *
 * <p>用途：下载类 fileApi 工具执行时，{@code FileApiDownload.exec} 会把文件字节写入
 * {@link #getOutputStream()}，本类把字节攒进内存 {@link ByteArrayOutputStream}，
 * 供 handler 通过 {@link #getCapturedBytes()} 取回并 base64 内联回传。
 *
 * <p>注意 Jakarta Servlet 中 {@code ServletRequest} 与 {@code ServletResponse} 是<b>兄弟</b>接口，
 * {@code HttpServletResponse} 仅继承 {@code ServletResponse}（不继承 {@code ServletRequest}），
 * 因此本类只需实现 {@code ServletResponse}+{@code HttpServletResponse} 的方法。
 *
 * <p>仅 {@link #getOutputStream()} 真实写入；{@code reset/setContentType/setCharacterEncoding/setHeader/
 * setContentLength*} 等方法仅记录或空实现（下载只需把字节写进流）。其余方法按需要返回安全默认值或抛
 * {@link UnsupportedOperationException}。命名空间为 {@code jakarta.servlet}（Jakarta Servlet 6.1）。
 */
public class CapturingHttpServletResponse implements HttpServletResponse {

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private ServletOutputStream out;
    private String characterEncoding = "UTF-8";
    private String contentType;
    private int status = HttpServletResponse.SC_OK;
    private Locale locale = Locale.getDefault();

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (out == null) {
            out = new CapturingServletOutputStream(buf);
        }
        return out;
    }

    /** 取回下载写入的字节 */
    public byte[] getCapturedBytes() {
        return buf.toByteArray();
    }

    // ============ ServletResponse: 编码 / 类型 / 长度 ============

    @Override
    public void setContentType(String type) {
        this.contentType = type;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public void setContentLength(int len) {
        // 下载仅捕获字节，长度无需处理
    }

    @Override
    public void setContentLengthLong(long len) {
        // 下载仅捕获字节，长度无需处理
    }

    // ============ 缓冲 / 提交状态 ============

    @Override
    public void reset() {
        buf.reset();
    }

    @Override
    public void resetBuffer() {
        buf.reset();
    }

    @Override
    public void flushBuffer() throws IOException {
        // ByteArrayOutputStream 无需刷盘，空实现
    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void setBufferSize(int size) {
        // 空实现
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void setLocale(Locale loc) {
        this.locale = loc != null ? loc : Locale.getDefault();
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    // ============ HttpServletResponse: 状态 / 头 / 编码 ============

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setHeader(String name, String value) {
        // 下载不回读响应头，空实现
    }

    @Override
    public void addHeader(String name, String value) {
        // 空实现
    }

    @Override
    public void setDateHeader(String name, long date) {
        // 空实现
    }

    @Override
    public void addDateHeader(String name, long date) {
        // 空实现
    }

    @Override
    public void setIntHeader(String name, int value) {
        // 空实现
    }

    @Override
    public void addIntHeader(String name, int value) {
        // 空实现
    }

    @Override
    public void addCookie(Cookie cookie) {
        // 空实现
    }

    @Override
    public boolean containsHeader(String name) {
        return false;
    }

    @Override
    public String getHeader(String name) {
        return null;
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getHeaderNames() {
        return Collections.emptyList();
    }

    @Override
    public String encodeURL(String url) {
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return url;
    }

    @Override
    public void sendError(int sc) throws IOException {
        throw unsupported();
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        throw unsupported();
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        throw unsupported();
    }

    /** Jakarta Servlet 6.1 新增的抽象方法：带状态码与缓冲清除策略的重定向 */
    @Override
    public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
        throw unsupported();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("CapturingHttpServletResponse 不支持该方法（下载桥接只需捕获输出流）");
    }

    // ============ 捕获用 ServletOutputStream ============

    /** 写入 ByteArrayOutputStream 的最小 ServletOutputStream 实现 */
    private static final class CapturingServletOutputStream extends ServletOutputStream {
        private final java.io.OutputStream delegate;

        CapturingServletOutputStream(java.io.OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // 同步写，无需监听
        }
    }
}
