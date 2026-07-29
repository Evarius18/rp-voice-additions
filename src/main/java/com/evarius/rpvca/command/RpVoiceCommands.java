package com.evarius.rpvca.command;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.config.SpeechConfig;
import com.evarius.rpvca.state.PlayerProfiles;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class RpVoiceCommands {
    private RpVoiceCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("rpvoice")
                    .executes(context -> status(context.getSource()))
                    .then(literal("range")
                            .executes(context -> rangeStatus(context.getSource()))
                            .then(argument("mode", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        RpVoiceServices services = RpVoiceServices.get();
                                        if (services != null) {
                                            for (SpeechConfig.Mode mode : services.configs().speech().modes) builder.suggest(mode.id);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> setRange(context.getSource(), StringArgumentType.getString(context, "mode")))))
                    .then(literal("reload").requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> reload(context.getSource()))));

            dispatcher.register(literal("phone")
                    .executes(context -> phoneStatus(context.getSource()))
                    .then(literal("call").then(argument("destination", StringArgumentType.word())
                            .executes(context -> call(context.getSource(), StringArgumentType.getString(context, "destination")))))
                    .then(literal("answer").executes(context -> answer(context.getSource())))
                    .then(literal("decline").executes(context -> decline(context.getSource())))
                    .then(literal("hangup").executes(context -> hangup(context.getSource())))
                    .then(literal("speaker").executes(context -> speaker(context.getSource())))
                    .then(literal("contacts")
                            .executes(context -> listContacts(context.getSource()))
                            .then(literal("add").then(argument("name", StringArgumentType.word())
                                    .then(argument("number", StringArgumentType.word())
                                            .executes(context -> addContact(context.getSource(),
                                                    StringArgumentType.getString(context, "name"),
                                                    StringArgumentType.getString(context, "number"))))))
                            .then(literal("remove").then(argument("name", StringArgumentType.word())
                                    .executes(context -> removeContact(context.getSource(),
                                            StringArgumentType.getString(context, "name")))))));

            dispatcher.register(literal("radio")
                    .executes(context -> radioStatus(context.getSource()))
                    .then(literal("tune").then(argument("channel", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                RpVoiceServices services = RpVoiceServices.get();
                                if (services != null) {
                                    for (RadioConfig.Channel channel : services.configs().radio().channels) builder.suggest(channel.id);
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> tune(context.getSource(), StringArgumentType.getString(context, "channel")))))
                    .then(literal("off").executes(context -> radioOff(context.getSource())))
                    .then(literal("transmit").executes(context -> radioTransmit(context.getSource())))
                    .then(literal("channels").executes(context -> listChannels(context.getSource()))));

            dispatcher.register(literal("celltower").requires(source -> source.hasPermissionLevel(2))
                    .then(literal("list").executes(context -> listTowers(context.getSource()))));
        });
    }

    private static int status(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        phoneStatus(source);
        radioStatus(source);
        rangeStatus(source);
        return 1;
    }

    private static int rangeStatus(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        SpeechConfig.Mode mode = services().speech().mode(player.getUuid());
        source.sendFeedback(() -> Text.literal("§6Sprechmodus §7| §f" + mode.displayName + " §7(" + mode.distance + " Blöcke)"), false);
        return 1;
    }

    private static int setRange(ServerCommandSource source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        SpeechConfig.Mode mode = services().speech().setMode(player.getUuid(), id);
        if (mode == null) {
            source.sendError(Text.literal("Unbekannter Sprechmodus."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§aSprechmodus: " + mode.displayName + " (" + mode.distance + " Blöcke)"), false);
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        RpVoiceAddon.reload(source.getServer());
        source.sendFeedback(() -> Text.literal("§aRP Voice Additions Konfiguration neu geladen."), true);
        return 1;
    }

    private static int phoneStatus(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        source.sendFeedback(() -> Text.literal(services().phones().status(player)), false);
        return 1;
    }

    private static int call(ServerCommandSource source, String destination) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerProfiles.Profile profile = services().profiles().getOrCreate(player.getUuid(), player.getGameProfile().getName());
        return services().phones().call(player, profile.contacts.getOrDefault(destination, destination)) ? 1 : 0;
    }

    private static int answer(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().phones().answer(source.getPlayerOrThrow()) ? 1 : 0;
    }

    private static int decline(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().phones().decline(source.getPlayerOrThrow()) ? 1 : 0;
    }

    private static int hangup(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().phones().hangup(source.getPlayerOrThrow()) ? 1 : 0;
    }

    private static int speaker(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        services().phones().toggleSpeaker(source.getPlayerOrThrow());
        return 1;
    }

    private static int listContacts(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerProfiles.Profile profile = services().profiles().getOrCreate(player.getUuid(), player.getGameProfile().getName());
        source.sendFeedback(() -> Text.literal(profile.contacts.isEmpty() ? "§7Keine Kontakte gespeichert."
                : "§6Kontakte: §f" + profile.contacts), false);
        return profile.contacts.size();
    }

    private static int addContact(ServerCommandSource source, String name, String number)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerProfiles.Profile profile = services().profiles().getOrCreate(player.getUuid(), player.getGameProfile().getName());
        profile.contacts.put(name, number);
        services().profiles().save();
        source.sendFeedback(() -> Text.literal("§aKontakt gespeichert: " + name + " → " + number), false);
        return 1;
    }

    private static int removeContact(ServerCommandSource source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerProfiles.Profile profile = services().profiles().getOrCreate(player.getUuid(), player.getGameProfile().getName());
        boolean removed = profile.contacts.remove(name) != null;
        services().profiles().save();
        source.sendFeedback(() -> Text.literal(removed ? "§7Kontakt entfernt." : "§cKontakt nicht gefunden."), false);
        return removed ? 1 : 0;
    }

    private static int radioStatus(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        source.sendFeedback(() -> Text.literal(services().radios().status(player)), false);
        return 1;
    }

    private static int tune(ServerCommandSource source, String channel) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().radios().tune(source.getPlayerOrThrow(), channel) ? 1 : 0;
    }

    private static int radioOff(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        services().radios().off(source.getPlayerOrThrow());
        return 1;
    }

    private static int radioTransmit(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        services().radios().toggleTransmit(source.getPlayerOrThrow());
        return 1;
    }

    private static int listChannels(ServerCommandSource source) {
        String channels = services().configs().radio().channels.stream()
                .map(channel -> channel.id + "=" + channel.displayName)
                .reduce((first, second) -> first + ", " + second).orElse("keine");
        source.sendFeedback(() -> Text.literal("§6Funkkanäle: §f" + channels), false);
        return services().configs().radio().channels.size();
    }

    private static int listTowers(ServerCommandSource source) {
        int count = services().towers().all().size();
        source.sendFeedback(() -> Text.literal("§6Registrierte Mobilfunkmasten: §f" + count), false);
        return count;
    }

    private static RpVoiceServices services() {
        RpVoiceServices services = RpVoiceServices.get();
        if (services == null) throw new IllegalStateException("RP Voice Additions ist noch nicht gestartet");
        return services;
    }
}
