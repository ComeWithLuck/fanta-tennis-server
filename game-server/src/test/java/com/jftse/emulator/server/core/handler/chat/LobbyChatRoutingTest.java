package com.jftse.emulator.server.core.handler.chat;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.command.CommandManager;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.packets.chat.S2CChatLobbyAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.chat.CMSGChatMessageLobby;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageLobby;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedDeque;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual handler, isolated recipients/transport/command registry. No Spring,
 * sockets, accounts, services or persistence are started by this test. */
class LobbyChatRoutingTest {
    private GameManager previousManager,manager;
    private CommandManager previousCommands,commands;
    private ConcurrentLinkedDeque<FTClient> recipients;

    @BeforeEach void setup() {
        previousManager=GameManager.getInstance();previousCommands=CommandManager.getInstance();
        manager=mock(GameManager.class);commands=mock(CommandManager.class);recipients=new ConcurrentLinkedDeque<>();
        ReflectionTestUtils.setField(GameManager.class,"instance",manager);
        ReflectionTestUtils.setField(CommandManager.class,"instance",commands);
        when(manager.getClients()).thenReturn(recipients);
        when(commands.isCommand(anyString())).thenReturn(false);
    }
    @AfterEach void restore() {
        ReflectionTestUtils.setField(GameManager.class,"instance",previousManager);
        ReflectionTestUtils.setField(CommandManager.class,"instance",previousCommands);
    }
    private FTClient participant(String name,boolean inLobby) {
        FTClient client=new FTClient();FTPlayer player=mock(FTPlayer.class);when(player.getName()).thenReturn(name);
        client.refreshPlayer(player);client.setInLobby(inLobby);
        FTConnection connection=mock(FTConnection.class);when(connection.getClient()).thenReturn(client);client.setConnection(connection);
        recipients.add(client);return client;
    }
    @Test void allGoesOnlyToLobbyRecipientsIncludingAuthoritativeSelfEchoAndPreservesWireFields() {
        FTClient sender=participant("ผู้ส่ง🎾",true),peer=participant("Peer",true),room=participant("Room",false),other=participant("Other",false);
        ReflectionTestUtils.setField(sender,"textMode",0xff12abcd);
        String message="  สวัสดี 日本語 <img src=x onerror=alert(1)> /!  ";
        new ChatMessageLobbyPacketHandler().handle(sender.getConnection(),CMSGChatMessageLobby.builder().unk((char)0x1234).message(message).build());
        var capture=ArgumentCaptor.forClass(SMSGChatMessageLobby.class);verify(sender.getConnection()).sendTCP(capture.capture());
        SMSGChatMessageLobby answer=capture.getValue();verify(peer.getConnection()).sendTCP(same(answer));
        verify(room.getConnection(),never()).sendTCP(any(SMSGChatMessageLobby.class));verify(other.getConnection(),never()).sendTCP(any(SMSGChatMessageLobby.class));
        assertEquals(0x1234,answer.getUnk());assertEquals("ผู้ส่ง🎾",answer.getSender());assertEquals(message,answer.getMessage());assertEquals(0xff12abcd,answer.getTextColor());
        byte[] wire=answer.toBytes();ByteBuffer bytes=ByteBuffer.wrap(wire).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x1706,bytes.getChar(4));assertEquals(wire.length-8,bytes.getChar(6));assertEquals(0x1234,bytes.getChar(8));
        System.out.println("LOBBY_CHAT_WIRE_BASE64="+Base64.getEncoder().encodeToString(wire));
        verify(commands,never()).handle(any(),any());
    }
    @Test void missingPlayerCannotBroadcastAndNoRecipientOutsideLobbyReceives() {
        FTClient sender=new FTClient();FTConnection connection=mock(FTConnection.class);when(connection.getClient()).thenReturn(sender);
        FTClient peer=participant("Peer",true);
        new ChatMessageLobbyPacketHandler().handle(connection,CMSGChatMessageLobby.builder().unk((char)0).message("ignored").build());
        verify(connection,never()).sendTCP(any(SMSGChatMessageLobby.class));verify(peer.getConnection(),never()).sendTCP(any(SMSGChatMessageLobby.class));
        verify(commands,never()).isCommand(anyString());
    }
    @Test void authoritativeRecipientGateIsLobbyFlagNotRoomTeamOrRequestPrefix() {
        FTClient sender=participant("Sender",false),peer=participant("Lobby",true);
        new ChatMessageLobbyPacketHandler().handle(sender.getConnection(),CMSGChatMessageLobby.builder().unk((char)0xffff).message("/ordinary !text").build());
        var capture=ArgumentCaptor.forClass(SMSGChatMessageLobby.class);verify(peer.getConnection()).sendTCP(capture.capture());
        assertEquals(0xffff,capture.getValue().getUnk());assertEquals("Sender",capture.getValue().getSender());
        verify(sender.getConnection(),never()).sendTCP(any(SMSGChatMessageLobby.class));
        // This documents the server's sender gate (hasPlayer), rather than
        // claiming it authorizes only lobby senders. The browser is stricter.
    }
    @Test void actualLegacySerializerOmitsOnlyTheColorTrailer() {
        var legacy=new S2CChatLobbyAnswerPacket((char)0x1234,"ผู้ส่ง🎾","  สวัสดี 日本語 <img src=x onerror=alert(1)> /!  ");
        byte[] wire=legacy.toBytes();ByteBuffer bytes=ByteBuffer.wrap(wire).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x1706,bytes.getChar(4));assertEquals(0x1234,bytes.getChar(8));
        var modern=SMSGChatMessageLobby.builder().unk((char)0x1234).sender("ผู้ส่ง🎾").message("  สวัสดี 日本語 <img src=x onerror=alert(1)> /!  ").textColor(0xff12abcd).build().toBytes();
        assertEquals(modern.length-4,wire.length);
        assertArrayEquals(java.util.Arrays.copyOfRange(modern,8,modern.length-4),java.util.Arrays.copyOfRange(wire,8,wire.length));
        System.out.println("LOBBY_CHAT_LEGACY_WIRE_BASE64="+Base64.getEncoder().encodeToString(wire));
    }
}
