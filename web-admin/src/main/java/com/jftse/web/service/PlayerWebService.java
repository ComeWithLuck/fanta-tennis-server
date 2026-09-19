package com.jftse.web.service;

import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.player.Player;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.entities.database.repository.item.ProductRepository;
import com.jftse.entities.database.repository.player.PlayerRepository;
import com.jftse.entities.database.repository.pocket.PlayerPocketRepository;
import com.jftse.server.core.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlayerWebService {
    private final PlayerRepository playerRepository;
    private final PlayerPocketRepository playerPocketRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public List<Player> search(String query, int limit) {
        if (query == null || query.isBlank())
            return playerRepository.findAllByAlreadyCreatedTrue(PageRequest.of(0, limit)).getContent();
        String q = query.trim();
        List<Player> found = new ArrayList<>(playerRepository.findAllByName(q));
        if (found.isEmpty()) {
            // fall back to a scan: the repository has no "contains" finder and the player table is small
            playerRepository.findAll().stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(q.toLowerCase()))
                    .limit(limit)
                    .forEach(found::add);
        }
        return found;
    }

    public Optional<Player> findById(Long id) {
        return playerRepository.findById(id);
    }

    public List<Player> findByAccount(Long accountId) {
        return playerRepository.findAllByAccount_Id(accountId);
    }

    public List<PlayerPocket> inventory(Player player) {
        if (player.getPocket() == null)
            return List.of();
        return playerPocketRepository.findAll().stream()
                .filter(pp -> pp.getPocket() != null && pp.getPocket().getId().equals(player.getPocket().getId()))
                .toList();
    }

    public Optional<Product> findProduct(int productIndex) {
        return Optional.ofNullable(productRepository.findProductByProductIndex(productIndex));
    }

    public List<Product> searchProducts(String name, int limit) {
        String q = name == null ? "" : name.trim().toLowerCase();
        return productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .filter(p -> q.isEmpty() || (p.getName() != null && p.getName().toLowerCase().contains(q))
                        || String.valueOf(p.getProductIndex()).equals(q))
                .limit(limit)
                .toList();
    }

    /** Uses the emulator's own inventory code, so the item lands exactly like a shop purchase. */
    @Transactional
    public List<PlayerPocket> giveItem(long playerId, int productIndex, int quantity) {
        return inventoryService.addItem(playerId, productIndex, quantity, List.of());
    }

    @Transactional
    public Player addGold(Player player, int amount) {
        int gold = player.getGold() == null ? 0 : player.getGold();
        player.setGold(Math.max(0, gold + amount));
        return playerRepository.save(player);
    }
}
