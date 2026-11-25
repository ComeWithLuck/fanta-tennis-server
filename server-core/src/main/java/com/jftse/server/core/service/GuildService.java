package com.jftse.server.core.service;

import com.jftse.entities.database.model.guild.Guild;
import com.jftse.entities.database.model.guild.GuildGoldUsage;
import org.springframework.data.domain.Page;

import java.util.List;

public interface GuildService {
    Guild save(Guild guild);

    void remove(Long guildId);

    Guild findByName(String name);

    List<Guild> findAllByNameContaining(String name);

    Guild findById(Long id);

    Guild findWithMembersById(Long id);

    Guild findWithMembersByPlayerId(Long playerId);

    List<Guild> findAll();

    List<Guild> findAll(int offset);

    Page<Guild> findAllRankingPage(int mode, int page);

    Guild changeInformation(Long guildId, String introduction, byte minLevel, boolean isPublic, Byte[] allowedCharacterTypes);

    void changeReverseMemberStatus(Long guildId, int playerId, boolean isApproved);

    List<Guild> findAllGuildLeagues(int page);

    // Club Gold tab (0x202C / 0x202D): ten usage rows per page, newest first.
    List<GuildGoldUsage> findGoldUsage(Long guildId, int page);

    // Club gold withdrawal (0x2029 / 0x202A): result 0 / -1 not the master / -2 short of club gold / -3 out of 5000..500000;
    // on 0 the club gold drops, a usage row is logged and the new club gold is returned in result[1].
    int[] withdrawGold(Long playerId, int amount);

    // A finished match's club share (ClubMatchReward): adds gold / points to the player's club; false without a club.
    boolean addMatchReward(Long playerId, int gold, int points);

    // Club house (castle tab, 0x2044 / 0x2046): {result, accessLimit, admissionFee}; result 0, -1 no club / no castle.
    int[] castleInfo(Long playerId);

    // 0x2046: 0 changed, -1 no club / no castle, -2 not the master or out of range (MSG_GUILD_CASTLE_INFO_CHANGE_FAILED_2).
    int changeCastle(Long playerId, int accessLimit, int admissionFee);

    // The clubs that own a club house (castleOwner), for the chat server's club house rooms (notes-v405).
    List<Guild> findClubHouses();

    // Entering a club's house (chat room list, 5a1b90 / 4a7750): {result, fee}. A member of that club enters free when the
    // access limit lets their rank in (2+ every member, the master always, a sub master from 1); anyone else only at 3
    // (everyone) and pays the admission fee. -25 not allowed (E_ROOM_NOT_GUILD_MEMBER), -30 short of gold
    // (MSG_YOU_CAT_NOT_ENTER_CASTLE_NEED_GOLD), -10 no such club house. Only a check: the caller takes the fee from the player
    // and then adds it with addClubGold.
    int[] enterClubHouse(Long playerId, Long guildId, int playerGold);

    // Adds gold to a club (the club house admission fee goes to the club gold [I]).
    void addClubGold(Long guildId, int gold);
}
