package com.jftse.web.web;

import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.web.service.AccountWebService;
import com.jftse.web.service.AuditService;
import com.jftse.web.service.PlayerWebService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/** GM panel: find players, ban/unban, hand out items and gold. Gated on account.gameMaster. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AccountWebService accounts;
    private final PlayerWebService players;
    private final AuditService audit;

    private boolean denied(HttpServletRequest request) {
        return !Session.isGm(request);
    }

    private void common(HttpServletRequest request, Model model) {
        model.addAttribute("username", Session.username(request));
        model.addAttribute("isGm", true);
        model.addAttribute("csrf", Session.csrf(request));
    }

    @GetMapping
    public String home(HttpServletRequest request, Model model) {
        if (denied(request))
            return "redirect:/login";
        common(request, model);
        model.addAttribute("recent", audit.recent(20));
        return "admin/home";
    }

    @GetMapping("/players")
    public String search(HttpServletRequest request, Model model, @RequestParam(required = false) String q) {
        if (denied(request))
            return "redirect:/login";
        common(request, model);
        model.addAttribute("q", q);
        model.addAttribute("results", players.search(q, 50));
        return "admin/players";
    }

    @GetMapping("/player/{id}")
    public String player(HttpServletRequest request, Model model, @PathVariable Long id,
                         @RequestParam(required = false) String item) {
        if (denied(request))
            return "redirect:/login";
        common(request, model);
        Player player = players.findById(id).orElse(null);
        if (player == null)
            return "redirect:/admin/players";
        List<PlayerPocket> inventory = players.inventory(player);
        model.addAttribute("player", player);
        model.addAttribute("account", player.getAccount());
        model.addAttribute("inventory", inventory);
        model.addAttribute("itemQuery", item);
        if (item != null && !item.isBlank())
            model.addAttribute("products", players.searchProducts(item, 25));
        return "admin/player";
    }

    @PostMapping("/player/{id}/ban")
    public String ban(HttpServletRequest request, @PathVariable Long id,
                      @RequestParam String reason, @RequestParam int days,
                      @RequestParam(name = "_csrf", required = false) String csrf) {
        if (denied(request))
            return "redirect:/login";
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        Account account = player.getAccount();
        accounts.ban(account, reason, days);
        audit.record(Session.username(request), "ban",
                "%s (account %s) %d วัน · %s".formatted(player.getName(), account.getUsername(), days, reason));
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/unban")
    public String unban(HttpServletRequest request, @PathVariable Long id,
                        @RequestParam(name = "_csrf", required = false) String csrf) {
        if (denied(request))
            return "redirect:/login";
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        accounts.unban(player.getAccount());
        audit.record(Session.username(request), "unban", player.getName());
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/give-item")
    public String giveItem(HttpServletRequest request, @PathVariable Long id,
                           @RequestParam int productIndex, @RequestParam(defaultValue = "1") int quantity,
                           @RequestParam(name = "_csrf", required = false) String csrf) {
        if (denied(request))
            return "redirect:/login";
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        List<PlayerPocket> added = players.giveItem(id, productIndex, Math.max(1, quantity));
        String productName = players.findProduct(productIndex).map(p -> p.getName()).orElse("?");
        audit.record(Session.username(request), added.isEmpty() ? "give-item FAILED" : "give-item",
                "%s ← %s (product %d) x%d".formatted(player.getName(), productName, productIndex, quantity));
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/give-gold")
    public String giveGold(HttpServletRequest request, @PathVariable Long id, @RequestParam int amount,
                           @RequestParam(name = "_csrf", required = false) String csrf) {
        if (denied(request))
            return "redirect:/login";
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        players.addGold(player, amount);
        audit.record(Session.username(request), "give-gold", "%s %+d".formatted(player.getName(), amount));
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/give-ap")
    public String giveAp(HttpServletRequest request, @PathVariable Long id, @RequestParam int amount,
                         @RequestParam(name = "_csrf", required = false) String csrf) {
        if (denied(request))
            return "redirect:/login";
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        accounts.addAp(player.getAccount(), amount);
        audit.record(Session.username(request), "give-ap", "%s %+d".formatted(player.getName(), amount));
        return "redirect:/admin/player/" + id;
    }
}
