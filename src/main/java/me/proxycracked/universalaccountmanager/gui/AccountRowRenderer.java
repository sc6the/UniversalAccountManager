package me.proxycracked.universalaccountmanager.gui;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;
import me.proxycracked.universalaccountmanager.auth.Account;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.SessionManager;
import me.proxycracked.universalaccountmanager.hypixel.HypixelBanCheck;
import me.proxycracked.universalaccountmanager.skin.SkinHeadCache;
import me.proxycracked.universalaccountmanager.utils.TextFormatting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;

import org.apache.commons.lang3.StringUtils;

/**
 * Draws one row of the account list.
 *
 * <p>Same layout as the bundled renderer; it exists so the new {@code msa} and {@code offline}
 * types get their own badge instead of being labelled MS, and so offline accounts are not struck
 * through for having no Microsoft token.</p>
 */
public final class AccountRowRenderer {
    private static final int LIST_WIDTH = 360;
    private static final int HEAD_SIZE = 24;

    private AccountRowRenderer() {
    }

    public static boolean draw(int x, int y, int id) {
        if (id < 0 || id >= UniversalAccountManager.accounts.size()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        Account account = UniversalAccountManager.accounts.get(id);
        Session active = SessionManager.get();
        boolean unavailable = Boolean.FALSE.equals(account.getAvailable()) && !AccountTypes.isOffline(account);

        int headX = x + 2;
        int headY = y + 1;
        ResourceLocation head = SkinHeadCache.get(account.getUsername(), account.getUuid());
        if (head != null) {
            if (unavailable) {
                GlStateManager.color(0.45F, 0.45F, 0.45F, 1.0F);
            } else {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
            mc.getTextureManager().bindTexture(head);
            Gui.drawScaledCustomSizeModalRect(headX, headY, 0.0F, 0.0F, 64, 64, HEAD_SIZE, HEAD_SIZE, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            Gui.drawRect(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0xFF222222);
            Gui.drawRect(headX + 1, headY + 1, headX + HEAD_SIZE - 1, headY + HEAD_SIZE - 1, 0xFF3A3A3A);
            font.drawStringWithShadow("?", headX + HEAD_SIZE / 2 - font.getStringWidth("?") / 2, headY + HEAD_SIZE / 2 - 4, 0xFF808080);
        }

        String username = account.getUsername();
        if (StringUtils.isBlank(username)) {
            username = "&7&l?";
        } else if (unavailable) {
            username = "&8&m" + username + "&r";
        } else if (!StringUtils.isBlank(account.getAccessToken()) && account.getAccessToken().equals(active.getToken())) {
            username = "&a&l" + username;
        } else if (username.equals(active.getUsername())) {
            username = "&a" + username;
        }

        // The bundled renderer used a black star here; that glyph is not in the 1.8.9 ASCII
        // sheet, so with a resource-pack font it comes out as garbage in front of the name.
        String pinPrefix = account.isPinned() ? "&e*&r " : "";
        String rendered = TextFormatting.translate(pinPrefix + "&r" + username + "&r");
        font.drawStringWithShadow(rendered, headX + HEAD_SIZE + 6, y + 4, 0xFFFFFFFF);
        font.drawStringWithShadow(
            TextFormatting.translate(AccountTypes.badge(account)),
            headX + HEAD_SIZE + 6 + font.getStringWidth(rendered) + 6,
            y + 4,
            0xFFFFFFFF
        );

        String banRendered = TextFormatting.translate(HypixelBanCheck.renderStatus(account));
        font.drawStringWithShadow(banRendered, x + LIST_WIDTH - 14 - font.getStringWidth(banRendered), y + 4, 0xFFFFFFFF);

        String subtitle = subtitle(account);
        if (!subtitle.isEmpty()) {
            font.drawStringWithShadow(TextFormatting.translate(subtitle), headX + HEAD_SIZE + 6, y + 16, 0xFFFFFFFF);
        }
        return true;
    }

    private static String subtitle(Account account) {
        if (AccountTypes.isOffline(account)) {
            return "&8offline mode only&r";
        }
        String uuid = account.getUuid();
        if (uuid != null && uuid.length() >= 8) {
            return "&8uuid: " + uuid.substring(0, 8) + "&r";
        }
        return "";
    }
}
