package com.ssa.plugin.mcp.service;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.Part;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 最小 {@link MultipartRequest} 适配器（替代 spring-test 的 MockMultipartHttpServletRequest）。
 *
 * <p>仅实现 fileApi 执行链真正调用的方法：
 * <ul>
 *   <li>{@link MultipartRequest#getFile(String)} / {@link MultipartRequest#getFiles(String)} /
 *       {@link MultipartRequest#getFileMap()} / {@link MultipartRequest#getMultiFileMap()} /
 *       {@link MultipartRequest#getFileNames()} / {@link MultipartRequest#getMultipartContentType(String)}
 *       （part 名固定 "file"）；</li>
 *   <li>{@code HttpServletRequest} 的 {@code getParameter*} 系列（由 FileApiManager 读取表单参数）。</li>
 * </ul>
 * 其余 HttpServletRequest 方法均抛 {@link UnsupportedOperationException}——它们不会被 fileApi 执行链调用，
 * 抛异常可让误用尽早暴露。
 *
 * <p>命名空间为 {@code jakarta.servlet}（Spring Boot 4.1 / Jakarta Servlet 6.1），绝不使用 {@code javax.servlet}。
 */
public class MapMultipartRequest implements HttpServletRequest, MultipartRequest {

    private final Map<String, MultipartFile> files;
    private final Map<String, String[]> formParams;

    public MapMultipartRequest(Map<String, MultipartFile> files, Map<String, String[]> formParams) {
        this.files = files == null ? Collections.emptyMap() : files;
        this.formParams = formParams == null ? Collections.emptyMap() : formParams;
    }

    // ============ MultipartRequest 实现 ============

    @Override
    public MultipartFile getFile(String name) {
        return files.get(name);
    }

    @Override
    public List<MultipartFile> getFiles(String name) {
        MultipartFile f = files.get(name);
        return f != null ? new ArrayList<>(Collections.singletonList(f)) : Collections.emptyList();
    }

    @Override
    public Map<String, MultipartFile> getFileMap() {
        return new HashMap<>(files);
    }

    @Override
    public MultiValueMap<String, MultipartFile> getMultiFileMap() {
        MultiValueMap<String, MultipartFile> m = new LinkedMultiValueMap<>();
        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            m.add(e.getKey(), e.getValue());
        }
        return m;
    }

    @Override
    public Iterator<String> getFileNames() {
        return new ArrayList<>(files.keySet()).iterator();
    }

    @Override
    public String getMultipartContentType(String paramOrFileName) {
        MultipartFile f = files.get(paramOrFileName);
        return f != null ? f.getContentType() : null;
    }

    // ============ HttpServletRequest: getParameter* 实现（fileApi 读取表单参数所需） ============

    @Override
    public String getParameter(String name) {
        String[] vals = formParams.get(name);
        return vals != null && vals.length > 0 ? vals[0] : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        return formParams.get(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(formParams.keySet());
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return new HashMap<>(formParams);
    }

    // ============ HttpServletRequest: 其余方法统一抛 UnsupportedOperationException ============

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("MapMultipartRequest 不支持该方法（fileApi 桥接无需真实 HTTP 请求）");
    }

    @Override
    public Object getAttribute(String name) {
        throw unsupported();
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw unsupported();
    }

    @Override
    public String getCharacterEncoding() {
        throw unsupported();
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        throw unsupported();
    }

    @Override
    public int getContentLength() {
        throw unsupported();
    }

    @Override
    public long getContentLengthLong() {
        throw unsupported();
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        throw unsupported();
    }

    @Override
    public String getProtocol() {
        throw unsupported();
    }

    @Override
    public String getScheme() {
        throw unsupported();
    }

    @Override
    public String getServerName() {
        throw unsupported();
    }

    @Override
    public int getServerPort() {
        throw unsupported();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        throw unsupported();
    }

    @Override
    public String getRemoteAddr() {
        throw unsupported();
    }

    @Override
    public String getRemoteHost() {
        throw unsupported();
    }

    @Override
    public void setAttribute(String name, Object o) {
        throw unsupported();
    }

    @Override
    public void removeAttribute(String name) {
        throw unsupported();
    }

    @Override
    public Locale getLocale() {
        throw unsupported();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        throw unsupported();
    }

    @Override
    public boolean isSecure() {
        throw unsupported();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw unsupported();
    }

    @Override
    public int getRemotePort() {
        throw unsupported();
    }

    @Override
    public String getLocalAddr() {
        throw unsupported();
    }

    @Override
    public String getLocalName() {
        throw unsupported();
    }

    @Override
    public int getLocalPort() {
        throw unsupported();
    }

    @Override
    public ServletContext getServletContext() {
        throw unsupported();
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw unsupported();
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw unsupported();
    }

    @Override
    public boolean isAsyncStarted() {
        throw unsupported();
    }

    @Override
    public boolean isAsyncSupported() {
        throw unsupported();
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw unsupported();
    }

    @Override
    public DispatcherType getDispatcherType() {
        throw unsupported();
    }

    @Override
    public String getRequestId() {
        throw unsupported();
    }

    @Override
    public String getProtocolRequestId() {
        throw unsupported();
    }

    @Override
    public ServletConnection getServletConnection() {
        throw unsupported();
    }

    @Override
    public String getAuthType() {
        throw unsupported();
    }

    @Override
    public Cookie[] getCookies() {
        throw unsupported();
    }

    @Override
    public long getDateHeader(String name) {
        throw unsupported();
    }

    @Override
    public String getHeader(String name) {
        throw unsupported();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        throw unsupported();
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        throw unsupported();
    }

    @Override
    public int getIntHeader(String name) {
        throw unsupported();
    }

    @Override
    public String getMethod() {
        return "POST";
    }

    @Override
    public String getPathInfo() {
        throw unsupported();
    }

    @Override
    public String getPathTranslated() {
        throw unsupported();
    }

    @Override
    public String getContextPath() {
        throw unsupported();
    }

    @Override
    public String getQueryString() {
        throw unsupported();
    }

    @Override
    public String getRemoteUser() {
        throw unsupported();
    }

    @Override
    public boolean isUserInRole(String role) {
        throw unsupported();
    }

    @Override
    public Principal getUserPrincipal() {
        throw unsupported();
    }

    @Override
    public String getRequestedSessionId() {
        throw unsupported();
    }

    @Override
    public String getRequestURI() {
        throw unsupported();
    }

    @Override
    public StringBuffer getRequestURL() {
        throw unsupported();
    }

    @Override
    public String getServletPath() {
        throw unsupported();
    }

    @Override
    public HttpSession getSession(boolean create) {
        throw unsupported();
    }

    @Override
    public HttpSession getSession() {
        throw unsupported();
    }

    @Override
    public String changeSessionId() {
        throw unsupported();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw unsupported();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw unsupported();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw unsupported();
    }

    @Override
    public boolean authenticate(jakarta.servlet.http.HttpServletResponse response) throws IOException, ServletException {
        throw unsupported();
    }

    @Override
    public void login(String username, String password) throws ServletException {
        throw unsupported();
    }

    @Override
    public void logout() throws ServletException {
        throw unsupported();
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        throw unsupported();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        throw unsupported();
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handler) throws IOException, ServletException {
        throw unsupported();
    }

    @Override
    public HttpServletMapping getHttpServletMapping() {
        throw unsupported();
    }

    @Override
    public Map<String, String> getTrailerFields() {
        throw unsupported();
    }

    @Override
    public boolean isTrailerFieldsReady() {
        throw unsupported();
    }
}
