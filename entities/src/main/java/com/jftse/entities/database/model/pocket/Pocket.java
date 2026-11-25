package com.jftse.entities.database.model.pocket;

import com.jftse.entities.database.model.AbstractBaseModel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import javax.persistence.Column;
import javax.persistence.Entity;
import org.hibernate.envers.NotAudited;

@Getter
@Setter
@Audited
@Entity
public class Pocket extends AbstractBaseModel {
    private Integer belongings = 0;
    private Integer maxBelongings = 150;
    // Extra card slots bought with Item_Special 48 (notes-v174; the player-info byte after the pocket max, client 493c37).
    @NotAudited
    @Column(columnDefinition = "tinyint DEFAULT 0")
    private Byte cardSlotCount = 0;
}
