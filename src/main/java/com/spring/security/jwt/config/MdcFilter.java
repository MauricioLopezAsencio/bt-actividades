package com.spring.security.jwt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String txId = Optional.ofNullable(req.getHeader("X-Transaction-Id"))
                .orElse(UUID.randomUUID().toString());
        MDC.put("transactionId", txId);
        MDC.put("method", req.getMethod());
        MDC.put("path", req.getRequestURI());
        res.setHeader("X-Transaction-Id", txId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
