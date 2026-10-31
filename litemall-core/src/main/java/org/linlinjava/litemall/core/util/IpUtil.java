package org.linlinjava.litemall.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP地址相关工具类
 */
public class IpUtil {

    private static final Log logger = LogFactory.getLog(IpUtil.class);
    private static final String UNKNOWN = "unknown";

    public static String getIpAddr(HttpServletRequest request) {
        String ipAddress = firstKnown(request.getHeader("x-forwarded-for"),
                request.getHeader("Proxy-Client-IP"),
                request.getHeader("WL-Proxy-Client-IP"));
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
            if ("127.0.0.1".equals(ipAddress)) {
                ipAddress = localHostAddress();
            }
        }
        return firstIp(ipAddress);
    }

    private static String firstKnown(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty() && !UNKNOWN.equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private static String localHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.error(e.getMessage(), e);
            return "";
        }
    }

    private static String firstIp(String ipAddress) {
        if (ipAddress == null) {
            return "";
        }
        int comma = ipAddress.indexOf(',');
        return comma >= 0 ? ipAddress.substring(0, comma) : ipAddress;
    }
}
