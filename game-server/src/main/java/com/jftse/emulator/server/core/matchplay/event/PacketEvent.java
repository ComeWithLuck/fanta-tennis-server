package com.jftse.emulator.server.core.matchplay.event;

import com.jftse.emulator.server.core.constants.PacketEventType;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.protocol.Packet;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.function.BooleanSupplier;

@Getter
@Setter
public class PacketEvent extends AbstractFireableEvent {
    private FTConnection sender;
    private FTClient client;
    private Packet packet;
    private PacketEventType packetEventType; // unused as of current state, might be useful later on
    private BooleanSupplier deliveryCondition;

    @Builder
    public PacketEvent(FTConnection sender, FTClient client, Packet packet, PacketEventType packetEventType, long currentTime, long delayMS, BooleanSupplier deliveryCondition) {
        super(currentTime, delayMS);

        this.sender = sender;
        this.client = client;
        this.packet = packet;
        this.packetEventType = packetEventType;
        this.deliveryCondition = deliveryCondition;
    }

    @Override
    protected void execute() {
        if (deliveryCondition == null || deliveryCondition.getAsBoolean()) sender.sendTCP(packet);
    }
}
