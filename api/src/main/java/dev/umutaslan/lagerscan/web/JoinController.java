package dev.umutaslan.lagerscan.web;

import dev.umutaslan.lagerscan.workspace.WorkspaceFilter;
import dev.umutaslan.lagerscan.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Pairing: the phone scans the QR code shown on the laptop and lands in the same workspace. */
@Controller
public class JoinController {
    private final WorkspaceService workspaces;

    public JoinController(WorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @GetMapping("/join/{token}")
    public void join(@PathVariable String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var ws = workspaces.findByJoinToken(token);
        if (ws.isEmpty()) {
            response.sendRedirect("/scan?joined=expired");
            return;
        }
        WorkspaceFilter.setCookie(response, ws.get().getId(), request.isSecure());
        response.sendRedirect("/scan?joined=1");
    }
}
