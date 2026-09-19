package com.jftse.web.web;

import com.jftse.entities.database.model.account.Account;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.web.model.AccountTier;
import com.jftse.web.service.AccountWebService;
import com.jftse.web.service.AuditService;
import com.jftse.web.service.PlayerWebService;
import com.jftse.web.service.TierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * GM panel.
 *
 * STAFF may look at players and hand out temporary bans (up to {@link #STAFF_MAX_BAN_DAYS} days) and
 * lift them again. Everything that changes a balance, bans forever or moves someone between tiers is
 * GM only.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    public static final int STAFF_MAX_BAN_DAYS = 7;

    private final AccountWebService accounts;
    private final PlayerWebService players;
    private final TierService tiers;
    private final AuditService audit;

    private AccountTier tier(HttpServletRequest request) {
        return Session.tier(request);
    }

    private void requireGm(HttpServletRequest request) {
        if (!tier(request).canGrant())
            throw new SecurityException("ต้องเป็น GM เท่านั้น");
    }

    private void requireModerator(HttpServletRequest request) {
        if (!tier(request).canModerate())
            throw new SecurityException("ต้องเป็นทีมงานหรือ GM");
    }

    @GetMapping
    public String home(HttpServletRequest request, Model model) {
        if (!tier(request).canModerate())
            return "redirect:/login";
        model.addAttribute("recent", audit.recent(20));
        model.addAttribute("staffMaxBanDays", STAFF_MAX_BAN_DAYS);
        return "admin/home";
    }

    @GetMapping("/players")
    public String search(HttpServletRequest request, Model model, @RequestParam(required = false) String q) {
        if (!tier(request).canModerate())
            return "redirect:/login";
        List<Player> results = players.search(q, 50);
        Map<Long, AccountTier> tierByAccount = tiers.tiersOf(results.stream().map(Player::getAccount).toList());
        model.addAttribute("q", q);
        model.addAttribute("results", results);
        model.addAttribute("tierByAccount", tierByAccount);
        return "admin/players";
    }

    @GetMapping("/player/{id}")
    public String player(HttpServletRequest request, Model model, @PathVariable Long id,
                         @RequestParam(required = false) String item) {
        if (!tier(request).canModerate())
            return "redirect:/login";
        Player player = players.findById(id).orElse(null);
        if (player == null)
            return "redirect:/admin/players";
        Account account = player.getAccount();
        model.addAttribute("player", player);
        model.addAttribute("account", account);
        model.addAttribute("playerTier", tiers.tierOf(account));
        model.addAttribute("allTiers", List.of(AccountTier.NORMAL, AccountTier.STAFF, AccountTier.GM));
        model.addAttribute("inventory", players.inventory(player));
        model.addAttribute("staffMaxBanDays", STAFF_MAX_BAN_DAYS);
        model.addAttribute("itemQuery", item);
        if (item != null && !item.isBlank())
            model.addAttribute("products", players.searchProducts(item, 25));
        return "admin/player";
    }

    @PostMapping("/player/{id}/ban")
    public String ban(HttpServletRequest request, @PathVariable Long id,
                      @RequestParam String reason, @RequestParam(defaultValue = "7") int days,
                      @RequestParam(defaultValue = "false") boolean permanent,
                      @RequestParam(name = "_csrf", required = false) String csrf) {
        requireModerator(request);
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        Account account = player.getAccount();
        AccountTier actor = tier(request);

        if (permanent) {
            if (!actor.canBanPermanently())
                throw new SecurityException("ทีมงานแบนถาวรไม่ได้ ต้องให้ GM ทำ");
            tiers.permanentBan(account, reason);
            audit.record(Session.username(request), "ban (ถาวร)",
                    "%s (%s) · %s".formatted(player.getName(), account.getUsername(), reason));
        } else {
            if (actor == AccountTier.STAFF && days > STAFF_MAX_BAN_DAYS)
                throw new SecurityException("ทีมงานแบนได้สูงสุด %d วัน".formatted(STAFF_MAX_BAN_DAYS));
            tiers.softBan(account, reason, days);
            audit.record(Session.username(request), "softban",
                    "%s (%s) %d วัน · %s".formatted(player.getName(), account.getUsername(), days, reason));
        }
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/unban")
    public String unban(HttpServletRequest request, @PathVariable Long id,
                        @RequestParam(name = "_csrf", required = false) String csrf) {
        requireModerator(request);
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        Account account = player.getAccount();
        if (tiers.tierOf(account) == AccountTier.BAN && !tier(request).canBanPermanently())
            throw new SecurityException("ปลดแบนถาวรได้เฉพาะ GM");
        tiers.unban(account);
        audit.record(Session.username(request), "unban", player.getName());
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/tier")
    public String setTier(HttpServletRequest request, @PathVariable Long id, @RequestParam AccountTier tier,
                          @RequestParam(name = "_csrf", required = false) String csrf) {
        requireGm(request);
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        Account account = player.getAccount();
        tiers.setRole(account, tier, Session.username(request));
        audit.record(Session.username(request), "set-tier",
                "%s (%s) → %s".formatted(player.getName(), account.getUsername(), tier.name()));
        // if a GM demoted themselves, the header has to stop showing the GM menu right away
        if (account.getId().equals(Session.accountId(request)))
            Session.refreshTier(request, tier);
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/give-item")
    public String giveItem(HttpServletRequest request, @PathVariable Long id,
                           @RequestParam int productIndex, @RequestParam(defaultValue = "1") int quantity,
                           @RequestParam(name = "_csrf", required = false) String csrf) {
        requireGm(request);
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
        requireGm(request);
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        players.addGold(player, amount);
        audit.record(Session.username(request), "give-gold", "%s %+d".formatted(player.getName(), amount));
        return "redirect:/admin/player/" + id;
    }

    @PostMapping("/player/{id}/give-ap")
    public String giveAp(HttpServletRequest request, @PathVariable Long id, @RequestParam int amount,
                         @RequestParam(name = "_csrf", required = false) String csrf) {
        requireGm(request);
        Session.requireCsrf(request, csrf);
        Player player = players.findById(id).orElseThrow();
        accounts.addAp(player.getAccount(), amount);
        audit.record(Session.username(request), "give-ap", "%s %+d".formatted(player.getName(), amount));
        return "redirect:/admin/player/" + id;
    }
}
