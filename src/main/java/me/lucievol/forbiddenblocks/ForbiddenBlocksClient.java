package me.lucievol.forbiddenblocks;

import me.lucievol.forbiddenblocks.config.ForbiddenBlocksConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;

public class ForbiddenBlocksClient implements ClientModInitializer {
    private static final Set<String> forbiddenItems = new HashSet<>();

    @Override
    public void onInitializeClient() {
        // Initialize configuration
        ForbiddenBlocksConfig.init();

        // Register block placement prevention
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> onBlockPlace(player, hand));

        // Register keybind from config
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && ForbiddenBlocksConfig.forbidKey.wasPressed()) {
                forbidItem(client.player);
            }
        });
    }

    private static ActionResult onBlockPlace(PlayerEntity player, Hand hand) {
        if (!(player instanceof ClientPlayerEntity clientPlayer)) {
            return ActionResult.PASS;
        }

        if (hand == Hand.MAIN_HAND) {
            String itemName = clientPlayer.getMainHandStack().getName().getString();
            if (forbiddenItems.contains(itemName)) {
                clientPlayer.sendMessage(Text.of("§cYou cannot place this item! (Client-Side Block)"), false);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    private static void forbidItem(ClientPlayerEntity player) {
        if (player == null) return;
        String itemName = player.getMainHandStack().getName().getString();
        if (!forbiddenItems.contains(itemName)) {
            forbiddenItems.add(itemName);
            player.sendMessage(Text.of("§e" + itemName + " is now forbidden to place. (Client-Side)"), false);
        } else {
            forbiddenItems.remove(itemName);
            player.sendMessage(Text.of("§a" + itemName + " is now allowed again. (Client-Side)"), false);
        }
    }
}
