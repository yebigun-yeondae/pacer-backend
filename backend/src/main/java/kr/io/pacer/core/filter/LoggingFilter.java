package kr.io.pacer.core.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String ip     = resolveClientIp(request);

        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int  status  = response.getStatus();

            if (status >= 500) {
                log.error("[API] {} {} | ip={} | status={} | {}ms", method, uri, ip, status, elapsed);
            } else if (status >= 400) {
                log.warn ("[API] {} {} | ip={} | status={} | {}ms", method, uri, ip, status, elapsed);
            } else {
                log.info ("[API] {} {} | ip={} | status={} | {}ms", method, uri, ip, status, elapsed);
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
