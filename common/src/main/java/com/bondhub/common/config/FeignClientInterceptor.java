package com.bondhub.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;

@Component
public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String value = request.getHeader(name);

                    if (name.equalsIgnoreCase("Authorization") ||
                            name.toLowerCase().startsWith("x-user-") ||
                            name.equalsIgnoreCase("X-Account-Id") ||
                            name.equalsIgnoreCase("X-JWT-Id") ||
                            name.equalsIgnoreCase("X-Remaining-TTL")) {

                        template.header(name, value);
                    }
                }
            }
        }
    }
}
