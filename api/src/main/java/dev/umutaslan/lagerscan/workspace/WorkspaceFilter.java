package dev.umutaslan.lagerscan.workspace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves (or creates) the visitor's workspace from an HttpOnly cookie for every API call. */
@Component
public class WorkspaceFilter extends OncePerRequestFilter {
    public static final String COOKIE = "ls_ws";
    public static final String ATTRIBUTE = "workspaceId";

    private final WorkspaceService workspaces;

    public WorkspaceFilter(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/actuator") || path.equals("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var id = cookie(request);
        var ws = id == null ? null : workspaces.find(id).orElse(null);
        if (ws == null) {
            ws = workspaces.create();
            setCookie(response, ws.getId(), request.isSecure());
        }
        request.setAttribute(ATTRIBUTE, ws.getId());
        chain.doFilter(request, response);
    }

    static UUID cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> COOKIE.equals(c.getName())).findFirst().map(c -> {
            try { return UUID.fromString(c.getValue()); } catch (IllegalArgumentException e) { return null; }
        }).orElse(null);
    }

    public static void setCookie(HttpServletResponse response, UUID id, boolean secure) {
        var cookie = ResponseCookie.from(COOKIE, id.toString())
            .httpOnly(true).secure(secure).sameSite("Strict").path("/").maxAge(Duration.ofDays(7)).build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
