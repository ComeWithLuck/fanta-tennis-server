package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.PetView;
import com.jftse.emulator.server.core.life.room.*;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.lobby.room.S2CPetRequestRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.shared.packets.pet.CMSGRequestPet;
import java.util.*;
import lombok.extern.log4j.Log4j2;

/** Isolated candidate: Basic or Battle two-human room only. No four-human Pet slot
 * reservation or request-ID/delivery guarantees are implied by this handler. */
@Log4j2
@PacketId(CMSGRequestPet.PACKET_ID)
public class RoomRequestPetPacketHandler implements PacketHandler<FTConnection, CMSGRequestPet> {
    private record Delivery(FTClient client, FTConnection connection, long epoch, Packet roster) { }

    @Override public void handle(FTConnection connection, CMSGRequestPet request) {
        final byte requestedSlot=request.getSlot();
        FTClient client=connection.getClient();
        if (client==null) return;
        Room room=client.getActiveRoom();
        if (room==null) return;
        synchronized (room) {
            RoomPlayer owner=RoomAccessPolicy.idleMember(client,room);
            if (owner==null || room.getMode()!=GameMode.BASIC && room.getMode()!=GameMode.BATTLE || room.getPlayers()!=2 ||
                    owner.getPosition()<0 || owner.getPosition()>1 || owner.getPosition()!=requestedSlot) {
                reject(connection,S2CPetRequestRoomAnswerPacket.NO_PERMISSION,requestedSlot);return;
            }
            final byte slot=(byte)owner.getPosition();
            final long epoch=client.getRoomEpoch();
            PetView old=owner.getPet(),proposed=null;
            final boolean isAdd=old==null;
            // Existing Pets can be removed after deselection/disable/conflict.
            if (isAdd) {
                if (room.getAllowBattlemon()==0) {reject(connection,S2CPetRequestRoomAnswerPacket.PET_NOT_ALLOWED,slot);return;}
                proposed=client.getActivePet();
                if (proposed==null) {reject(connection,S2CPetRequestRoomAnswerPacket.NO_PET_SELECTED,slot);return;}
                if (room.getRoomPlayerList().stream().anyMatch(p->p.getPosition()==slot+2)) {
                    reject(connection,S2CPetRequestRoomAnswerPacket.NO_FREE_SLOT,slot);return;
                }
            }
            final Packet ack;
            final List<Delivery> deliveries=new ArrayList<>();
            try {
                ack=new Packet(new S2CPetRequestRoomAnswerPacket(S2CPetRequestRoomAnswerPacket.SUCCESS,isAdd,slot,isAdd?proposed:old).toBytes());
                Set<FTConnection> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                for (FTClient recipient:GameManager.getInstance().getClientsInRoom(room.getRoomId())) {
                    FTConnection target=recipient.getConnection();
                    if (recipient.getActiveRoom()!=room || !recipient.hasPlayer() || target==null || !seen.add(target)) continue;
                    deliveries.add(new Delivery(recipient,target,recipient.getRoomEpoch(),RoomRosterUpdates.preparePetSnapshot(room,recipient,owner,proposed)));
                }
                if (client.getActiveRoom()!=room || client.getRoomEpoch()!=epoch || client.getRoomPlayer()!=owner)
                    throw new IllegalStateException("Room Pet owner changed while preparing snapshot");
            } catch (Exception error) {
                log.error("Room Pet preparation failed before commit",error);
                reject(connection,S2CPetRequestRoomAnswerPacket.CAN_NOT_ADD_PET,slot);return;
            }
            owner.setPet(proposed);
            // After commit, one transport failure cannot roll back other peers.
            for (Delivery delivery:deliveries) {
                FTClient recipient=delivery.client();
                if (recipient.getActiveRoom()!=room || recipient.getRoomEpoch()!=delivery.epoch() ||
                        recipient.getConnection()!=delivery.connection() || !recipient.hasPlayer()) continue;
                try {
                    var future=delivery.connection().sendTCP(ack,delivery.roster());
                    if (future!=null) future.addListener(done->{
                        if (!done.isSuccess()) log.warn("Committed room Pet packet write failed",done.cause());
                    });
                } catch (Exception error) {
                    log.warn("Committed room Pet packet send failed",error);
                }
            }
        }
    }
    private static void reject(FTConnection connection,byte result,byte slot) {
        try {connection.sendTCP(new S2CPetRequestRoomAnswerPacket(result,false,slot,null));}
        catch (Exception error) {log.warn("Room Pet rejection could not be sent",error);}
    }
}
