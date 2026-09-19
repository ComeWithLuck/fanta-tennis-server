package com.jftse.web.service;

import com.jftse.entities.database.model.log.GameLog;
import com.jftse.entities.database.model.log.GameLogType;
import com.jftse.entities.database.repository.log.GameLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Every GM action is written to the emulator's own gamelog table (type GENERAL, prefixed [WEB-GM])
 * so it shows up next to in-game events instead of living in a separate place.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AuditService {
    public static final String PREFIX = "[WEB-GM]";

    private final GameLogRepository gameLogRepository;

    public void record(String gmUsername, String action, String detail) {
        String content = "%s %s: %s%s".formatted(PREFIX, gmUsername, action, detail == null ? "" : " · " + detail);
        GameLog entry = new GameLog();
        entry.setGameLogType(GameLogType.GENERAL);
        entry.setContent(content);
        gameLogRepository.save(entry);
        log.info(content);
    }

    public List<GameLog> recent(int limit) {
        return gameLogRepository.findAll().stream()
                .filter(l -> l.getContent() != null && l.getContent().startsWith(PREFIX))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(limit)
                .toList();
    }
}
