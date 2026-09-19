package com.jftse.web.web;

import com.jftse.web.model.AccountTier;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;

/**
 * Every page needs the same three things for the header: who is signed in, what they may see, and a
 * CSRF token for the logout form. Putting it here means a controller can never forget one (the /me page
 * used to show "log in / register" in the header because it did not pass the username).
 */
@ControllerAdvice
public class GlobalModel {
    @ModelAttribute("username")
    public String username(HttpServletRequest request) {
        return Session.username(request);
    }

    @ModelAttribute("tier")
    public AccountTier tier(HttpServletRequest request) {
        return Session.tier(request);
    }

    @ModelAttribute("isGm")
    public boolean isGm(HttpServletRequest request) {
        return Session.tier(request) == AccountTier.GM;
    }

    @ModelAttribute("canModerate")
    public boolean canModerate(HttpServletRequest request) {
        return Session.tier(request).canModerate();
    }

    @ModelAttribute("csrf")
    public String csrf(HttpServletRequest request) {
        return Session.csrf(request);
    }
}
