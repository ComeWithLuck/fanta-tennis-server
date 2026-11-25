package com.jftse.emulator.server.core.handler.chat;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.interaction.PlayerScriptableImpl;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.chat.S2CChatRoomAnswerPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.protocol.Packet;
import com.jftse.server.core.shared.packets.chat.SMSGChatMessageRoom;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual serializers and scriptable receipt routing. No script execution,
 * Spring context, sockets, services or database are started. */
class RoomChatLegacyReceiptTest {
    private static final String SENDER="Room ผู้ส่ง🎾",MESSAGE="日本語 <img src=x onerror=alert(1)> /! สวัสดี";
    private GameManager previousManager;
    private ServiceManager previousServices;
    @BeforeEach void isolate() {
        previousManager=GameManager.getInstance();previousServices=ServiceManager.getInstance();
        ReflectionTestUtils.setField(GameManager.class,"instance",mock(GameManager.class));
        ReflectionTestUtils.setField(ServiceManager.class,"instance",mock(ServiceManager.class));
    }
    @AfterEach void restore() {
        ReflectionTestUtils.setField(GameManager.class,"instance",previousManager);
        ReflectionTestUtils.setField(ServiceManager.class,"instance",previousServices);
    }
    @Test void actualLegacyAndModernSerializersHaveOneBytePrefixAndOnlyFourByteTrailerDifference() {
        byte[] legacy=new S2CChatRoomAnswerPacket((byte)2,SENDER,MESSAGE).toBytes();
        byte[] modern=SMSGChatMessageRoom.builder().type((byte)0).sender(SENDER).message(MESSAGE).textColor(0xff12abcd).build().toBytes();
        for(byte[] raw:new byte[][]{legacy,modern}){ByteBuffer bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);assertEquals(0x1778,bytes.getChar(4));assertEquals(raw.length-8,bytes.getChar(6));}
        assertEquals(2,legacy[8]);assertEquals(0,modern[8]);assertEquals(legacy.length+4,modern.length);
        assertArrayEquals(Arrays.copyOfRange(legacy,9,legacy.length),Arrays.copyOfRange(modern,9,modern.length-4));
        assertEquals(0xff12abcd,ByteBuffer.wrap(modern).order(ByteOrder.LITTLE_ENDIAN).getInt(modern.length-4));
        System.out.println("ROOM_CHAT_LEGACY_WIRE_BASE64="+Base64.getEncoder().encodeToString(legacy));
        System.out.println("ROOM_CHAT_MODERN_WIRE_BASE64="+Base64.getEncoder().encodeToString(modern));
    }
    private FTClient basicRoomClient(FTConnection connection,boolean inLobby) {
        FTClient client=new FTClient();FTPlayer player=mock(FTPlayer.class);when(player.getId()).thenReturn(17L);client.refreshPlayer(player);
        Room room=new Room();room.setMode((byte)0);room.setPlayers((byte)2);client.setActiveRoom(room);client.setInLobby(inLobby);client.setConnection(connection);return client;
    }
    @Test void actualDefaultSendChatInNormalBasicRoomEmitsLegacyType2WithoutCallingPersistence() {
        FTConnection connection=mock(FTConnection.class);FTClient client=basicRoomClient(connection,false);
        new PlayerScriptableImpl(client).sendChat(SENDER,MESSAGE);
        var capture=ArgumentCaptor.forClass(Packet.class);verify(connection).sendTCP(capture.capture());
        assertArrayEquals(new S2CChatRoomAnswerPacket((byte)2,SENDER,MESSAGE).toBytes(),capture.getValue().toBytes());
    }
    @Test void actualExplicitSendChatKeepsByteModesAndAnActiveRoomSelectsRoomEvenIfLobbyFlagIsTrue() {
        for(Integer mode:new Integer[]{null,0,1,255}) {
            FTConnection connection=mock(FTConnection.class);FTClient client=basicRoomClient(connection,true);
            new PlayerScriptableImpl(client).sendChat(SENDER,MESSAGE,mode);
            var capture=ArgumentCaptor.forClass(Packet.class);verify(connection).sendTCP(capture.capture());
            byte expected=(byte)(mode==null?0:mode.intValue());
            assertArrayEquals(new S2CChatRoomAnswerPacket(expected,SENDER,MESSAGE).toBytes(),capture.getValue().toBytes());
        }
    }
}
