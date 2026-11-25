package com.jftse.emulator.server.core.matchplay;

import lombok.Getter;
import lombok.Setter;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.server.core.shared.packets.matchplay.SMSGPickupItemReward;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class MatchplayReward {
    private List<PlayerReward> playerRewards;

    private Map<Byte, ItemReward> slotRewards;
    private List<ItemReward> itemRewards; // display only

    @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private Room rewardRoom;
    @Getter(lombok.AccessLevel.NONE)
    private final Map<FTClient, Recipient> recipients = new IdentityHashMap<>();
    private record Recipient(RoomPlayer roomPlayer, long playerId, short position, long roomEpoch, long gameEpoch) {}
    public record ItemClaim(byte slot, ItemReward reward, boolean newlyClaimed) {}

    void bindRecipients(Room room, Collection<FTClient> clients, Integer sessionId) {
        rewardRoom = room;
        for (FTClient client : clients) {
            if (!client.hasPlayer() || client.getActiveRoom() != room || !Objects.equals(client.getGameSessionId(), sessionId)) continue;
            RoomPlayer rp = client.getRoomPlayer();
            if (rp == null || rp.getPosition() < 0 || rp.getPosition() >= 4) continue;
            recipients.put(client, new Recipient(rp, client.getPlayer().getId(), rp.getPosition(), client.getRoomEpoch(), client.getGameSessionEpoch()));
        }
    }

    public boolean isEligible(FTClient client) {
        Recipient recipient = recipients.get(client);
        return recipient != null && client.hasPlayer() && client.getActiveRoom() == rewardRoom
                && client.getRoomEpoch() == recipient.roomEpoch() && client.getGameSessionEpoch() == recipient.gameEpoch()
                && client.getPlayer().getId() == recipient.playerId() && client.getRoomPlayer() == recipient.roomPlayer()
                && recipient.roomPlayer().getPosition() >= 0 && recipient.roomPlayer().getPosition() < 4;
    }

    private ItemClaim ownedClaim(short position) {
        return slotRewards.entrySet().stream().filter(entry -> entry.getValue().getClaimed().get()
                && entry.getValue().getClaimedPlayerPosition() == position)
                .map(entry -> new ItemClaim(entry.getKey(), entry.getValue(), false)).findFirst().orElse(null);
    }

    public synchronized ItemClaim claimItem(FTClient client, byte slot) {
        if (!isEligible(client)) return null;
        short position = recipients.get(client).position();
        ItemClaim owned = ownedClaim(position);
        if (owned != null) return owned;
        ItemReward reward = slotRewards.get(slot);
        if (reward == null) return null;
        boolean claimed = reward.getClaimed().compareAndSet(false, true);
        if (claimed) reward.setClaimedPlayerPosition(position);
        return new ItemClaim(slot, reward, claimed);
    }

    public synchronized ItemClaim claimAnyItem(FTClient client) {
        if (!isEligible(client)) return null;
        ItemClaim owned = ownedClaim(recipients.get(client).position());
        if (owned != null) return owned;
        List<Byte> available = slotRewards.entrySet().stream().filter(entry -> !entry.getValue().getClaimed().get()).map(Map.Entry::getKey).toList();
        return available.isEmpty() ? null : claimItem(client, available.get((int) (Math.random() * available.size())));
    }

    public synchronized boolean allEligiblePlayersClaimed() {
        return recipients.keySet().stream().filter(this::isEligible).allMatch(client -> ownedClaim(recipients.get(client).position()) != null);
    }

    public static SMSGPickupItemReward pickupPacket(ItemClaim claim) {
        return SMSGPickupItemReward.builder().playerPos((byte) claim.reward().getClaimedPlayerPosition())
                .slot(claim.slot()).type((byte) 0).productIndex(claim.reward().getProductIndex()).quantity(claim.reward().getProductAmount()).build();
    }

    public void notifyClaim(ItemClaim claim) {
        SMSGPickupItemReward response = pickupPacket(claim);
        recipients.keySet().stream().filter(this::isEligible).filter(client -> client.getConnection() != null)
                .forEach(client -> client.getConnection().sendTCP(response));
    }

    public MatchplayReward(List<PlayerReward> playerRewards) {
        this.playerRewards = playerRewards;
        this.slotRewards = new HashMap<>(4);
        this.itemRewards = new ArrayList<>();
    }

    public MatchplayReward() {
        this.playerRewards = new ArrayList<>();
        this.slotRewards = new HashMap<>(4);
        this.itemRewards = new ArrayList<>();
    }

    public void addPlayerReward(PlayerReward playerReward) {
        playerRewards.add(playerReward);
    }

    public void addItemRewards(List<ItemReward> itemRewards) {
        this.itemRewards.addAll(itemRewards);
    }

    private void addSlotReward(byte slot, ItemReward itemReward) {
        slotRewards.put(slot, itemReward);
    }

    public PlayerReward getPlayerReward(int playerPosition) {
        return playerRewards.stream().filter(x -> x.getPlayerPosition() == playerPosition).findFirst().orElse(null);
    }

    public static double calculateAverageWeight(List<ItemReward> itemRewards) {
        double totalWeight = 0.0;
        int totalItems = 0;

        for (ItemReward itemReward : itemRewards) {
            Double weight = itemReward.getWeight();
            if (weight != null) {
                totalWeight += weight;
                totalItems++;
            }
        }

        return (totalItems > 0) ? totalWeight / totalItems : 20.0; // default weight
    }

    public static List<ItemReward> selectItemRewardsByWeight(List<ItemReward> rewards, int n, double defaultWeight) {
        List<ItemReward> selectedRewards = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (ItemReward reward : rewards) {
            weights.add(reward.getWeight() != null ? reward.getWeight() : defaultWeight);
        }

        for (int i = 0; i < n; i++) {
            ItemReward selectedReward = getItemRewardByWeight(rewards, weights);
            selectedRewards.add(new ItemReward(selectedReward));
        }
        return selectedRewards;
    }

    private static ItemReward getItemRewardByWeight(List<ItemReward> rewards, List<Double> weights) {
        double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = Math.random() * totalWeight;

        double cumulativeWeight = 0.0;
        for (int i = 0; i < rewards.size(); i++) {
            cumulativeWeight += weights.get(i);
            if (randomValue < cumulativeWeight) {
                return rewards.get(i);
            }
        }
        return rewards.getLast(); // fallback
    }

    public void assignItemRewardsToSlots(List<ItemReward> selectedRewards) {
        Collections.shuffle(selectedRewards);
        for (int i = 0; i < selectedRewards.size(); i++) {
            addSlotReward((byte) i, selectedRewards.get(i));
        }
    }

    public ItemReward getSlotReward(byte slot) {
        return slotRewards.get(slot);
    }

    @Getter
    @Setter
    public static class ItemReward {
        private int productIndex;
        private int productAmount;
        private Double weight;
        private AtomicBoolean claimed;
        private short claimedPlayerPosition;

        public ItemReward(int productIndex, int productAmount, Double weight) {
            this.productIndex = productIndex;
            this.productAmount = productAmount;
            this.weight = weight;
            this.claimed = new AtomicBoolean(false);
            this.claimedPlayerPosition = -1;
        }

        public ItemReward(ItemReward itemReward) {
            this.productIndex = itemReward.getProductIndex();
            this.productAmount = itemReward.getProductAmount();
            this.weight = itemReward.getWeight();
            this.claimed = new AtomicBoolean(itemReward.getClaimed().get());
            this.claimedPlayerPosition = itemReward.getClaimedPlayerPosition();
        }
    }
}
