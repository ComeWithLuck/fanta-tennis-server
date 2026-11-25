package com.jftse.entities.database.model.player;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.Column;
import javax.persistence.Entity;

@Getter
@Setter
@Audited
@Entity
public class BattlemonSlotEquipment extends AbstractBaseModel {
    // The owning player, one row each (local/server-ddl-battlemon-slots-20261123.sql). A plain id rather than Player's
    // commented-out @ManyToOne battlemonSlotEquipment_id, so Player / player_aud keep their columns; the row is made lazily.
    @Column(name = "player_id", unique = true)
    private Long playerId;

    private Integer slot1 = 0;
    private Integer slot2 = 0;
}
