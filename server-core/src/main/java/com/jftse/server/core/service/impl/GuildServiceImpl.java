package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildGoldUsage;
import com.jftse.entities.database.model.guild.GuildMember;
import com.jftse.entities.database.repository.guild.GuildGoldUsageRepository;
import com.jftse.entities.database.repository.guild.GuildMemberRepository;
import com.jftse.entities.database.repository.guild.GuildRepository;
import com.jftse.server.core.service.GuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GuildServiceImpl implements GuildService {
    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildGoldUsageRepository guildGoldUsageRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Guild save(Guild guild) {
        return guildRepository.save(guild);
    }

    @Override
    @Transactional
    public void remove(Long guildId) {
        guildRepository.deleteById(guildId);
    }

    @Override
    @Transactional(readOnly = true)
    public Guild findByName(String name) {
        List<Guild> guildList = guildRepository.findAllByName(name);
        return !guildList.isEmpty() ? guildList.getFirst() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Guild> findAllByNameContaining(String name) {
        return guildRepository.findAllByNameContainingIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Guild findById(Long id) {
        return guildRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Guild findWithMembersById(Long id) {
        return guildRepository.findWithMembersById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Guild findWithMembersByPlayerId(Long playerId) {
        GuildMember gm = guildMemberRepository.findByPlayerId(playerId).orElse(null);
        if (gm != null) {
            return findWithMembersById(gm.getGuild().getId());
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Guild> findAll() {
        return guildRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Guild> findAll(int offset) {
        Long lastId = offset > 0 ? (long) offset : 0L;

        List<Long> ids = guildRepository.findIdsByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, 10));
        if (ids.isEmpty())
            return List.of();

        return guildRepository.findWithMembersByIdIn(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Guild> findAllGuildLeagues(int page) {
        int pageIndex = Math.max(page - 1, 0);
        return guildRepository.findAllByOrderByLeaguePointsDescIdAsc(PageRequest.of(pageIndex, 10));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Guild> findAllRankingPage(int mode, int page) {
        int pageIndex = Math.max(page, 0);

        String pointsProperty;
        if (mode == 0) {
            pointsProperty = "clubPoints";
        } else if (mode == 1) {
            pointsProperty = "leaguePoints";
        } else {
            return Page.empty(PageRequest.of(pageIndex, 10));
        }

        PageRequest pageRequest = PageRequest.of(
                pageIndex,
                10,
                Sort.by(pointsProperty).descending().and(Sort.by("id").ascending())
        );

        return guildRepository.findAll(pageRequest);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Guild changeInformation(Long guildId, String introduction, byte minLevel, boolean isPublic, Byte[] allowedCharacterTypes) {
        Optional<Guild> guildOptional = guildRepository.findWithMembersById(guildId);
        if (guildOptional.isPresent()) {
            Guild guild = guildOptional.get();
            guild.setIntroduction(introduction);
            guild.setLevelRestriction(minLevel);
            guild.setIsPublic(isPublic);
            guild.setAllowedCharacterType(allowedCharacterTypes);
            return guildRepository.save(guild);
        } else {
            return null;
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeReverseMemberStatus(Long guildId, int playerId, boolean isApproved) {
        Optional<Guild> guildOptional = guildRepository.findWithMembersById(guildId);
        if (guildOptional.isPresent()) {
            Guild guild = guildOptional.get();
            GuildMember reverseMember = guild.getMemberList().stream()
                    .filter(gm -> gm.getPlayer().getId() == playerId)
                    .findFirst()
                    .orElse(null);

            if (reverseMember != null) {
                if (isApproved) {
                    reverseMember.setWaitingForApproval(false);
                } else {
                    guild.getMemberList().removeIf(x -> x.getId().equals(reverseMember.getId()));
                }
                guildRepository.save(guild);
            }
        }
    }

    public static final int GOLD_ROWS = 10, WITHDRAW_MIN = 5000, WITHDRAW_MAX = 500000;

    @Override
    @Transactional(readOnly = true)
    public List<GuildGoldUsage> findGoldUsage(Long guildId, int page) {
        return guildGoldUsageRepository.findAllByGuildIdOrderByDateDescIdDesc(guildId, PageRequest.of(Math.max(0, page), GOLD_ROWS)).getContent();
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int[] withdrawGold(Long playerId, int amount) {
        if (amount < WITHDRAW_MIN || amount > WITHDRAW_MAX)
            return new int[] {-3, 0};
        Optional<GuildMember> member = guildMemberRepository.findByPlayerId(playerId);
        if (member.isEmpty() || Boolean.TRUE.equals(member.get().getWaitingForApproval()) || member.get().getMemberRank() == null || member.get().getMemberRank() != 3)
            return new int[] {-1, 0};
        Guild guild = member.get().getGuild();
        int gold = guild.getGold() == null ? 0 : guild.getGold();
        if (gold < amount)
            return new int[] {-2, gold};
        guild.setGold(gold - amount);
        GuildGoldUsage usage = new GuildGoldUsage();
        usage.setGuild(guild);
        usage.setPlayer(member.get().getPlayer());
        usage.setDate(new java.util.Date());
        usage.setGoldUsed(amount);
        usage.setGoldRemaining(gold - amount);
        guildGoldUsageRepository.save(usage);
        guildRepository.save(guild);
        return new int[] {0, gold - amount};
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean addMatchReward(Long playerId, int gold, int points) {
        Optional<GuildMember> member = guildMemberRepository.findByPlayerId(playerId);
        if (member.isEmpty() || Boolean.TRUE.equals(member.get().getWaitingForApproval()) || member.get().getGuild() == null)
            return false;
        Guild guild = member.get().getGuild();
        guild.setGold((guild.getGold() == null ? 0 : guild.getGold()) + Math.max(0, gold));
        guild.setClubPoints((guild.getClubPoints() == null ? 0 : guild.getClubPoints()) + Math.max(0, points));
        // Club level and castle (user-approved server rule 2026-11-25) [I]: JFTSE had no club levelling and the client
        // holds no club-level table, so level = 1 + clubPoints / 100 (at most 10) and a club of level >= 5 owns its club house.
        guild.setLevel((byte) clubLevel(guild.getClubPoints()));
        guild.setCastleOwner(guild.getLevel() >= CASTLE_LEVEL);
        guildRepository.save(guild);
        return true;
    }

    public static final int LEVEL_POINTS = 100, MAX_LEVEL = 10, CASTLE_LEVEL = 5, MAX_ADMISSION_FEE = 1000;

    public static int clubLevel(Integer clubPoints) {
        return Math.min(MAX_LEVEL, 1 + Math.max(0, clubPoints == null ? 0 : clubPoints) / LEVEL_POINTS);
    }

    @Override
    @Transactional(readOnly = true)
    public int[] castleInfo(Long playerId) {
        Optional<GuildMember> member = guildMemberRepository.findByPlayerId(playerId);
        if (member.isEmpty() || Boolean.TRUE.equals(member.get().getWaitingForApproval()) || member.get().getGuild() == null
                || !Boolean.TRUE.equals(member.get().getGuild().getCastleOwner()))
            return new int[] {-1, 0, 0};
        Guild guild = member.get().getGuild();
        return new int[] {0, guild.getCastleAccessLimit() == null ? 3 : guild.getCastleAccessLimit(),
                guild.getCastleAdmissionFee() == null ? 0 : guild.getCastleAdmissionFee()};
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int changeCastle(Long playerId, int accessLimit, int admissionFee) {
        Optional<GuildMember> member = guildMemberRepository.findByPlayerId(playerId);
        if (member.isEmpty() || Boolean.TRUE.equals(member.get().getWaitingForApproval()) || member.get().getGuild() == null
                || !Boolean.TRUE.equals(member.get().getGuild().getCastleOwner()))
            return -1;
        if (member.get().getMemberRank() == null || member.get().getMemberRank() != 3 || accessLimit < 0 || accessLimit > 3
                || admissionFee < 0 || admissionFee > MAX_ADMISSION_FEE)
            return -2;
        Guild guild = member.get().getGuild();
        guild.setCastleAccessLimit((byte) accessLimit);
        guild.setCastleAdmissionFee(admissionFee);
        guildRepository.save(guild);
        return 0;
    }

    @Override
    public List<Guild> findClubHouses() {
        return guildRepository.findAll().stream().filter(g -> Boolean.TRUE.equals(g.getCastleOwner())).toList();
    }

    @Override
    public int[] enterClubHouse(Long playerId, Long guildId, int playerGold) {
        Guild guild = guildId == null ? null : guildRepository.findById(guildId).orElse(null);
        if (guild == null || !Boolean.TRUE.equals(guild.getCastleOwner()))
            return new int[] {-10, 0};
        int access = guild.getCastleAccessLimit() == null ? 3 : guild.getCastleAccessLimit();
        Optional<GuildMember> member = guildMemberRepository.findByPlayerId(playerId);
        boolean own = member.isPresent() && !Boolean.TRUE.equals(member.get().getWaitingForApproval())
                && member.get().getGuild() != null && guildId.equals(member.get().getGuild().getId());
        if (own) {
            int rank = member.get().getMemberRank() == null ? 1 : member.get().getMemberRank();
            boolean allowed = access >= 2 || rank == 3 || (rank == 2 && access >= 1);
            return new int[] {allowed ? 0 : -25, 0};
        }
        if (access != 3)
            return new int[] {-25, 0};
        int fee = guild.getCastleAdmissionFee() == null ? 0 : Math.max(0, guild.getCastleAdmissionFee());
        if (playerGold < fee)
            return new int[] {-30, 0};
        return new int[] {0, fee};
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addClubGold(Long guildId, int gold) {
        Guild guild = guildId == null || gold <= 0 ? null : guildRepository.findById(guildId).orElse(null);
        if (guild == null)
            return;
        guild.setGold((guild.getGold() == null ? 0 : guild.getGold()) + gold);
        guildRepository.save(guild);
    }
}
