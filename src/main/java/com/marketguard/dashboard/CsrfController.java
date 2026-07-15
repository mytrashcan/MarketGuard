package com.marketguard.dashboard;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CsrfController {
    @GetMapping("/csrf")
    public CsrfTokenView csrf(CsrfToken token) {
        return new CsrfTokenView(token.getToken(), token.getHeaderName(), token.getParameterName());
    }
}
