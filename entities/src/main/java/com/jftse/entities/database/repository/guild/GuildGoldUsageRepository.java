package com.jftse.entities.database.repository.guild;

import com.jftse.entities.database.model.guild.GuildGoldUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildGoldUsageRepository extends JpaRepository<GuildGoldUsage, Long> {
    // The Club Gold tab's rows, newest first, with the player name (GuiCtrl_GuildGoldUseInfo stGuildGoldUseUserName).
    @EntityGraph(attributePaths = {"player"})
    Page<GuildGoldUsage> findAllByGuildIdOrderByDateDescIdDesc(Long guildId, Pageable pageable);
}
