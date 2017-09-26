/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 heimuheimu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.heimuheimu.naivemonitor.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 对 {@link HttpURLConnection} 进行封装，简化 Http post 请求执行过程。
 * <br>将每次远程访问的操作记录下来可以在生产环境定位问题提供极大的便利，建议配置 Log4j 保存每一次的 Http post 请求：
 *
 * <p>注意：当前实现是非线程安全的，每个 NaiveHttpPost 实例对应一次 POST 请求执行，不可复用。</p>
 * <code>
 * log4j.logger.com.heimuheimu.naivemonitor.http.NaiveHttpPost=INFO, NAIVEMONITOR_HTTP_POST_LOG <br>
 * log4j.additivity.com.heimuheimu.naivemonitor.http.NaiveHttpPost=false <br>
 * log4j.appender.NAIVEMONITOR_HTTP_POST_LOG=org.apache.log4j.DailyRollingFileAppender <br>
 * log4j.appender.NAIVEMONITOR_HTTP_POST_LOG.file=${log.output.directory}/naivemonitor/http_post.log <br>
 * log4j.appender.NAIVEMONITOR_HTTP_POST_LOG.encoding=UTF-8 <br>
 * log4j.appender.NAIVEMONITOR_HTTP_POST_LOG.DatePattern=_yyyy-MM-dd <br>
 * log4j.appender.NAIVEMONITOR_HTTP_POST_LOG.layout=org.apache.log4j.PatternLayout <br>
 * log4j.appender.NAIVEMONITOR_HTTP_POST_LOG.layout.ConversionPattern=%d{ISO8601} %-5p [%F:%L] : %m%n <br>
 * </code>
 * @author heimuheimu
 */
public class NaiveHttpPost {
    
    private final static Logger LOGGER = LoggerFactory.getLogger(NaiveHttpPost.class);

    /**
     * Post 请求的 URL 地址
     */
    private final String url;

    /**
     * 实际发起 POST 请求使用的 {@link HttpURLConnection} 实例
     */
    private final HttpURLConnection urlConnection;

    /**
     * 当前 POST 请求是否已执行
     */
    private boolean isExecuted = false;

    /**
     * 构造一个 Http Post 请求
     *
     * @param url Post 请求 URL 地址
     * @param timeout 连接和操作超时时间，单位：毫秒
     * @throws IOException 如果在创建 {@link HttpURLConnection} 过程中发生错误，将抛出此异常
     */
    public NaiveHttpPost(String url, int timeout) throws IOException {
        try {
            this.url = url;
            this.urlConnection = (HttpURLConnection) new URL(url).openConnection();
            this.urlConnection.setConnectTimeout(timeout);
            this.urlConnection.setReadTimeout(timeout);
            this.urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);
        } catch (Exception e) {
            LOGGER.error("Create NaiveHttpPost failed: `" + e.getMessage() + "`. Url: `" + url + "`. Timeout: `"
                    + timeout + "`.", e);
            throw e;
        }
    }

    /**
     * 获得 {@link HttpURLConnection} 实例，可在 {@link #doPost(String)} 执行之前进行更定制化的设置，
     * 或者在 {@link #doPost(String)} 执行后获取更多的响应信息，例如响应状态码等
     *
     * @return {@link HttpURLConnection} 实例
     */
    public HttpURLConnection getUrlConnection() {
        return urlConnection;
    }

    /**
     * 执行 Http post 请求，并返回执行后的响应文本内容，该方法仅允许执行一次，并在该方法执行结束后会对网络资源（Socket 连接、输入流、输出流）进行关闭、释放。
     * <br>注意：该方法仅允许执行一次，后续调用将会抛出 {@link IllegalStateException} 异常
     *
     * @param body post 的数据内容，不允许为 {@code null} 或空字符串
     * @return Http post 请求执行后的响应文本内容
     * @throws IllegalStateException 方法被重复调用，将抛出此异常
     * @throws IllegalArgumentException post 的数据内容为 {@code null} 或空字符串，将抛出此异常
     * @throws IOException Http post 请求过程中发生 IO 错误，将抛出异常
     */
    public synchronized String doPost(String body) throws IllegalStateException, IllegalArgumentException, IOException {
        if (isExecuted) {
            LOGGER.error("Execute http post method failed: `#doPost(String) has been executed already`. Url: `{}`. Body: `{}`.", url, body);
            throw new IllegalStateException("Execute http post method failed: `#doPost(String) has been executed already`. Url: `"
                    + url + "`. Body: `" + body + "`.");
        }
        if (body == null || body.isEmpty()) {
            LOGGER.error("Execute http post method failed: `post body could not be null or empty`. Url: `{}`. Body: `{}`.", url, body);
            throw new IllegalArgumentException("Execute http post method failed: `post body could not be null or empty`. Url: `" +
                    url + "`. Body: `" + body + "`.");
        }
        long startTime = System.currentTimeMillis();
        OutputStream os = null;
        InputStream is = null;
        try {
            byte[] bodyBytes = body.getBytes("utf-8");
            os = urlConnection.getOutputStream();
            os.write(bodyBytes);
            os.flush();

            is = urlConnection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
            StringBuilder responseTextBuffer = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) {
                responseTextBuffer.append(line);
            }
            String responseText = responseTextBuffer.toString();
            LOGGER.info("Execute http post success. Cost: `{} ms`. Url: `{}`. Body: `{}`. Response text: `{}`.",
                    System.currentTimeMillis() - startTime, url, body, responseText);
            return responseText;
        } catch (Exception e) {
            LOGGER.error("Execute http post method failed: `" + e.getMessage() + "`. Url: `" + url + "`. Body: `" + body + "`.", e);
            throw e;
        } finally {
            isExecuted = true;
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {}
            }
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {}
            }
            try {
                urlConnection.disconnect();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 根据参数 Map 构造出 Http post 请求 body 内容，Map 的 Key 为参数名，Value 为参数值
     * <br>参数值将会通过 {@link URLEncoder#encode(String, String)} 方法进行 UTF-8 编码
     *
     * @param params Post 参数 Map
     * @return Http post 请求 body 内容
     */
    public static String getPostBody(Map<String, Object> params) {
        if (params != null && !params.isEmpty()) {
            StringBuilder buffer = new StringBuilder();
            for (String paramName : params.keySet()) {
                String value = String.valueOf(params.get(paramName));
                try {
                    value = URLEncoder.encode(value, "utf-8");
                } catch (UnsupportedEncodingException ignored) {} //should not happen
                buffer.append(paramName).append("=").append(value).append("&");
            }
            buffer.deleteCharAt(buffer.length() - 1);
            return buffer.toString();
        } else {
            return "";
        }
    }
}
