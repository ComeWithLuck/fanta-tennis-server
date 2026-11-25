package com.jftse.server.core.service;

import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.player.Player;

import java.util.List;

public interface PetService {
    Pet findById(Long id);
    List<Pet> findAllByPlayerId(Long playerId);
    Pet createPet(Integer itemIndex, Player player);
    Pet save(Pet pet);
    // Alive again with full energy / hunger and a full life span (Root of life tree: "maximizes ... life, energy and hunger").
    Pet revive(Pet pet);
}
