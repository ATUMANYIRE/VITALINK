package org.vitalink.api;

import static org.vitalink.api.Contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/login") public Session login(@Valid @RequestBody Login login,HttpServletRequest request) {
        return auth.login(login,request.getRemoteAddr());
    }
    @PostMapping("/logout") public void logout(@AuthenticationPrincipal User user,@RequestHeader("Authorization") String header) {
        auth.logout(header.substring(7),user);
    }
}
