package com.bank.digitalcore.carddispatch.observability;

import com.bank.digitalcore.carddispatch.api.CardDispatchConstants;
import io.micrometer.core.instrument.binder.httpcomponents.MicrometerHttpClientInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Propagates a safe request correlation identifier through logs and API responses.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String HEADER = CardDispatchConstants.CORRELATION_ID_HEADER;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /**
     * Adds a validated correlation identifier to the response and logging context.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("HTTP request completed method={} status={} durationMs={}",
                    request.getMethod(), response.getStatus(), durationMs);
            MDC.remove("correlationId");
        }
    }
}
