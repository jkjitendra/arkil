package com.arkil.web;

import com.arkil.policy.ClientContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home/Dashboard controller for authenticated users.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ClientContextHolder clientContextHolder;

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            model.addAttribute("username", authentication.getName());
            model.addAttribute("authorities", authentication.getAuthorities());
        }

        if (clientContextHolder.hasContext()) {
            model.addAttribute("clientId", clientContextHolder.getContext().getClientId());
        }

        return "home";
    }
}
