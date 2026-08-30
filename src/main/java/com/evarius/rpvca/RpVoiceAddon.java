package com.evarius.rpvca;

import com.evarius.rpvca.command.RpVoiceCommands;
import com.evarius.rpvca.config.ConfigManager;
import com.evarius.rpvca.content.ModContent;
import com.evarius.rpvca.network.CommunicationNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpVoiceAddon implements ModInitializer {
	public static final String MOD_ID = "rp-vca";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final ConfigManager CONFIGS = new ConfigManager();

	@Override
	public void onInitialize() {
		CONFIGS.load();
		ModContent.initialize();
		CommunicationNetworking.registerPayloads();
		CommunicationNetworking.registerServerReceiver();
		RpVoiceCommands.register();
		UseItemCallback.EVENT.register((player, world, hand) -> {
			RpVoiceServices services = RpVoiceServices.get();
			if (world.isClient() || services == null || !CONFIGS.devices().openGuiOnItemUse
					|| !(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
			if (services.deviceItems().shouldOpenRpVcaScreen(stack)) {
				CommunicationNetworking.openDevice(serverPlayer, "phone");
				return ActionResult.SUCCESS;
			}
			if (services.deviceItems().isRadio(stack)) {
				CommunicationNetworking.openDevice(serverPlayer, "radio");
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RpVoiceServices.start(server, CONFIGS);
			LOGGER.info("RP Voice Additions wurde gestartet");
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RpVoiceServices.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			RpVoiceServices services = RpVoiceServices.get();
			if (services != null) {
				services.sirens().tick();
			}
			if (services != null && server.getTicks() % 20 == 0) {
				services.phones().tick();
				server.getPlayerManager().getPlayerList().forEach(CommunicationNetworking::sync);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			RpVoiceServices services = RpVoiceServices.get();
			if (services != null) {
				services.phones().initializeProfile(handler.player);
				CommunicationNetworking.sync(handler.player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RpVoiceServices services = RpVoiceServices.get();
			if (services != null) {
				services.phones().onDisconnect(handler.player.getUuid());
				services.radios().onDisconnect(handler.player.getUuid());
				services.sirens().onDisconnect(handler.player.getUuid());
			}
		});
	}

	public static void reload(MinecraftServer server) {
		CONFIGS.load();
		RpVoiceServices.start(server, CONFIGS);
	}

	public static ConfigManager configs() {
		return CONFIGS;
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
