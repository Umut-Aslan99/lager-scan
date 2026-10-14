package dev.umutaslan.lagerscan.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.filter.OncePerRequestFilter;

/** Angular routes are client-side: forward them to index.html. Static files are served by Spring Boot. */
@Controller
class SpaController {
    @GetMapping({"/scan", "/etiketten", "/artikel/**"})
    public String app() { return "forward:/index.html"; }
}

/** Security headers for every response; camera allowed for this origin only (the scanner needs it). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityHeaders extends OncePerRequestFilter {
    private static final Pattern HASHED = Pattern.compile("^/(media/)?[\\w.-]+-[A-Za-z0-9]{8}\\.(js|css|woff2?)$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
                + "media-src 'self' blob:; connect-src 'self'; worker-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(self), microphone=(), geolocation=()");
        var path = request.getRequestURI();
        if (HASHED.matcher(path).matches()) {
            // Angular emits content-hashed file names (main-G3Z5APTK.js, media/inter-…-2ZT4Y5UQ.woff2): cache forever.
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        } else if (!path.startsWith("/api/")) {
            // index.html, ngsw.json, ngsw-worker.js, theme.js …: always revalidate so updates arrive.
            response.setHeader("Cache-Control", "no-cache");
        }
        chain.doFilter(request, response);
    }
}
