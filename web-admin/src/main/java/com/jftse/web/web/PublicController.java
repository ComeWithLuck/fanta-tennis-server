package com.jftse.web.web;

import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.Player;
import com.jftse.web.service.AccountWebService;
import com.jftse.web.service.CashService;
import com.jftse.web.service.PlayerWebService;
import com.jftse.web.service.TierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

/** Register, log in, and look at your own account. */
@Controller
@RequiredArgsConstructor
public class PublicController {
    private final AccountWebService accounts;
    private final PlayerWebService players;
    private final TierService tiers;
    private final CashService cash;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(HttpServletRequest request, Model model,
                           @RequestParam String username, @RequestParam String password,
                           @RequestParam(required = false) String password2,
                           @RequestParam(required = false) String email,
                           @RequestParam(name = "_csrf", required = false) String csrf) {
        Session.requireCsrf(request, csrf);
        model.addAttribute("form", username);

        String error = validate(username, password, password2);
        if (error == null && accounts.findByUsername(username).isPresent())
            error = "ชื่อผู้ใช้นี้ถูกใช้แล้ว";
        if (error != null) {
            model.addAttribute("error", error);
            return "register";
        }
        Account account = accounts.register(username, password, email);
        Session.login(request, account, tiers.tierOf(account));
        return "redirect:/me";
    }

    private String validate(String username, String password, String password2) {
        if (username == null || !AccountWebService.USERNAME.matcher(username).matches())
            return "ชื่อผู้ใช้ต้องเป็น a-z A-Z 0-9 _ ยาว 4-16 ตัว";
        if (password == null || password.length() < AccountWebService.MIN_PASSWORD
                || password.length() > AccountWebService.MAX_PASSWORD)
            return "รหัสผ่านต้องยาว %d-%d ตัว".formatted(AccountWebService.MIN_PASSWORD, AccountWebService.MAX_PASSWORD);
        if (password2 != null && !password.equals(password2))
            return "รหัสผ่านทั้งสองช่องไม่ตรงกัน";
        return null;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(HttpServletRequest request, Model model,
                        @RequestParam String username, @RequestParam String password,
                        @RequestParam(name = "_csrf", required = false) String csrf) {
        Session.requireCsrf(request, csrf);
        Optional<Account> account = accounts.findByUsername(username);
        if (account.isEmpty() || !accounts.passwordMatches(account.get(), password)) {
            model.addAttribute("error", "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
            return "login";
        }
        Session.login(request, account.get(), tiers.tierOf(account.get()));
        return "redirect:/me";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request,
                         @RequestParam(name = "_csrf", required = false) String csrf) {
        Session.requireCsrf(request, csrf);
        Session.logout(request);
        return "redirect:/";
    }

    @GetMapping("/me")
    public String me(HttpServletRequest request, Model model) {
        Long accountId = Session.accountId(request);
        if (accountId == null)
            return "redirect:/login";
        Optional<Account> account = accounts.findById(accountId);
        if (account.isEmpty()) {
            Session.logout(request);
            return "redirect:/login";
        }
        model.addAttribute("account", account.get());
        model.addAttribute("characters", players.findByAccount(accountId));
        model.addAttribute("playerTier", tiers.tierOf(account.get()));
        model.addAttribute("balanceC", cash.balance(accountId));
        return "me";
    }

    @PostMapping("/me/password")
    public String changePassword(HttpServletRequest request, Model model,
                                 @RequestParam String current, @RequestParam String password,
                                 @RequestParam(required = false) String password2,
                                 @RequestParam(name = "_csrf", required = false) String csrf) {
        Session.requireCsrf(request, csrf);
        Long accountId = Session.accountId(request);
        if (accountId == null)
            return "redirect:/login";
        Account account = accounts.findById(accountId).orElseThrow();

        String error = null;
        if (!accounts.passwordMatches(account, current))
            error = "รหัสผ่านเดิมไม่ถูกต้อง";
        else
            error = validate(account.getUsername(), password, password2);

        if (error != null) {
            model.addAttribute("account", account);
            model.addAttribute("characters", players.findByAccount(accountId));
            model.addAttribute("playerTier", tiers.tierOf(account));
            model.addAttribute("balanceC", cash.balance(accountId));
            model.addAttribute("error", error);
            return "me";
        }
        accounts.changePassword(account, password);
        return "redirect:/me?changed";
    }
}
