package com.jftse.emulator.server.core.handler.shop;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.core.packets.inventory.S2CInventoryItemsPlacePacket;
import com.jftse.emulator.server.core.packets.pet.S2CPetAddPacket;
import com.jftse.emulator.server.core.packets.shop.S2CRedeemCouponPacket;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.entities.database.model.coupon.Coupon;
import com.jftse.entities.database.model.item.Product;
import com.jftse.entities.database.model.pet.Pet;
import com.jftse.entities.database.model.pocket.PlayerPocket;
import com.jftse.server.core.handler.PacketHandler;
import com.jftse.server.core.handler.PacketId;
import com.jftse.server.core.item.PetCreated;
import com.jftse.server.core.service.CouponService;
import com.jftse.server.core.service.InventoryService;
import com.jftse.server.core.service.ProductService;
import com.jftse.server.core.shared.packets.shop.CMSGRedeemCouponCode;
import lombok.extern.log4j.Log4j2;

import java.util.List;

// C2S 0x2137 {UTF-16 serial, 12..16 chars} -> S2C 0x2138 {short result, string serial, int productIndex, byte useOption}
// (client GuiPop_CouponInput 553ec0 / answer 4dbbd0; notes-v174, user-approved 2026-11-10). The answer only drives the client's
// result popup, so the granted product goes to the pocket like a shop buy (InventoryService.addItem) and is placed with 0x1B69.
// Results: -1 MSG_ERR_NA_COUPON, -2 / -3 MSG_ERR_USED_COUPON, -4 MSG_ERR_NOT_PERIOD_COUPON, else MSG_ERR_WRONG_COUPON (-5).
@Log4j2
@PacketId(CMSGRedeemCouponCode.PACKET_ID)
public class CouponRedeemRequestPacketHandler implements PacketHandler<FTConnection, CMSGRedeemCouponCode> {
    private final CouponService couponService;
    private final ProductService productService;
    private final InventoryService inventoryService;

    public CouponRedeemRequestPacketHandler() {
        couponService = ServiceManager.getInstance().getCouponService();
        productService = ServiceManager.getInstance().getProductService();
        inventoryService = ServiceManager.getInstance().getInventoryService();
    }

    @Override
    public void handle(FTConnection connection, CMSGRedeemCouponCode packet) {
        FTClient client = connection.getClient();
        if (!client.hasPlayer() || client.getAccount() == null)
            return;

        String code = packet.getCouponCode() == null ? "" : packet.getCouponCode().trim();
        if (code.length() < 12 || code.length() > 16) {
            connection.sendTCP(new S2CRedeemCouponPacket(S2CRedeemCouponPacket.WRONG, code, 0, (byte) 0));
            return;
        }

        FTPlayer player = client.getPlayer();
        long accountId = client.getAccount().getId();
        Coupon coupon = couponService.findByCode(code);
        short result = couponService.check(coupon, accountId);
        Product product = result == 0 ? productService.findProductByProductItemIndex(coupon.getProductIndex()) : null;
        if (result == 0 && product == null) {
            log.warn("Coupon {} names a missing product {}", coupon.getCode(), coupon.getProductIndex());
            result = S2CRedeemCouponPacket.NA_COUPON;
        }
        if (result != 0) {
            connection.sendTCP(new S2CRedeemCouponPacket(result, code, 0, (byte) 0));
            return;
        }

        byte option = coupon.getUseOption() == null ? 0 : coupon.getUseOption();
        int quantity = switch (option) {
            case 0 -> product.getUse0() == 0 ? 1 : product.getUse0();
            case 1 -> product.getUse1();
            case 2 -> product.getUse2();
            default -> 1;
        };
        List<PlayerPocket> items = inventoryService.addItem(player.getId(), product.getProductIndex(), quantity, List.of(h -> {
            if (h instanceof PetCreated(Pet pet))
                connection.sendTCP(new S2CPetAddPacket(pet));
        }));
        couponService.redeem(coupon, accountId, player.getId());

        connection.sendTCP(new S2CRedeemCouponPacket(S2CRedeemCouponPacket.SUCCESS, coupon.getCode(), product.getProductIndex(), option));
        if (!items.isEmpty())
            connection.sendTCP(new S2CInventoryItemsPlacePacket(items));
    }
}
