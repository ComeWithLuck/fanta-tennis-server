package com.jftse.web.web;

import com.jftse.entities.database.model.account.Account;
import com.jftse.web.service.AccountWebService;
import com.jftse.web.service.CashService;
import com.jftse.web.service.TopUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/** Player-facing C: top up with a TrueMoney voucher, convert C into AP. */
@Controller
@RequiredArgsConstructor
public class CashController {
    private final AccountWebService accounts;
    private final CashService cash;
    private final TopUpService topUp;

    private Account current(HttpServletRequest request) {
        Long accountId = Session.accountId(request);
        return accountId == null ? null : accounts.findById(accountId).orElse(null);
    }

    private void fill(Model model, Account account) {
        model.addAttribute("account", account);
        model.addAttribute("balanceC", cash.balance(account.getId()));
        model.addAttribute("history", cash.history(account.getId(), 20));
        model.addAttribute("online", cash.accountIsOnline(account));
        model.addAttribute("topUpEnabled", topUp.isEnabled());
        model.addAttribute("topUpMock", topUp.isMock());
        model.addAttribute("cPerBaht", CashService.C_PER_BAHT);
        model.addAttribute("apPerC", CashService.AP_PER_C);
    }

    @GetMapping("/cash")
    public String cash(HttpServletRequest request, Model model) {
        Account account = current(request);
        if (account == null)
            return "redirect:/login";
        fill(model, account);
        return "cash";
    }

    @PostMapping("/cash/topup")
    public String topup(HttpServletRequest request, Model model, @RequestParam String link,
                        @RequestParam(name = "_csrf", required = false) String csrf) {
        Session.requireCsrf(request, csrf);
        Account account = current(request);
        if (account == null)
            return "redirect:/login";

        if (!topUp.isEnabled()) {
            fill(model, account);
            model.addAttribute("error", "ระบบเติมเงินปิดอยู่");
            return "cash";
        }
        TopUpService.Outcome outcome = topUp.redeem(account, link);
        account = current(request);                 // balance changed
        fill(model, account);
        model.addAttribute(outcome.ok() ? "ok" : "error", outcome.message());
        return "cash";
    }

    @PostMapping("/cash/exchange")
    public String exchange(HttpServletRequest request, Model model, @RequestParam int amount,
                           @RequestParam(name = "_csrf", required = false) String csrf) {
        Session.requireCsrf(request, csrf);
        Account account = current(request);
        if (account == null)
            return "redirect:/login";

        String error = cash.exchangeToAp(account, amount);
        account = current(request);
        fill(model, account);
        if (error != null)
            model.addAttribute("error", error);
        else
            model.addAttribute("ok", "แลก %d C เป็น %d AP แล้ว".formatted(amount, amount * CashService.AP_PER_C));
        return "cash";
    }
}
