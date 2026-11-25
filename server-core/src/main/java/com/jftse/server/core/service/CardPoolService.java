package com.jftse.server.core.service;

// Card open (0x1DE3, notes-v175): a weighted roll over the cardpool rows of a pack; -1 when the pack has no (positive) rows.
public interface CardPoolService {
    int roll(int packItemIndex);
}
