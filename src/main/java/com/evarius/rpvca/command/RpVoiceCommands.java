package com.evarius.rpvca.command;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.config.SpeechConfig;
import com.evarius.rpvca.permissions.VanillaPermissionLevels;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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
                                            for (String mode : services.configs().speech().modeOrder) {
                                                builder.suggest(mode.toLowerCase(java.util.Locale.ROOT));
                                            }
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> setRange(context.getSource(), StringArgumentType.getString(context, "mode")))))
                    .then(literal("phone")
                            .then(literal("history")
                                    .then(literal("clear")
                                            .then(argument("player", StringArgumentType.word())
                                                    .executes(context -> clearPlayerHistory(context.getSource(),
                                                            StringArgumentType.getString(context, "player")))))
                                    .then(literal("clear-all")
                                            .executes(context -> clearAllHistories(context.getSource())))))
                    .then(literal("reload").requires(source -> VanillaPermissionLevels.has(source, 2))
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

            dispatcher.register(literal("celltower").requires(source -> VanillaPermissionLevels.has(source, 2))
                    .then(literal("list").executes(context -> listTowers(context.getSource()))));
        });
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        phoneStatus(source);
        radioStatus(source);
        rangeStatus(source);
        return 1;
    }

    private static int rangeStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeechConfig.Mode mode = services().speech().mode(player.getUUID());
        source.sendSuccess(() -> Component.translatable("command.rp-vca.speech.status",
                Component.translatable(com.evarius.rpvca.speech.SpeechMode.translationKey(mode.id)),
                "whisper".equals(mode.id) ? "SVC" : mode.distance), false);
        return 1;
    }

    private static int setRange(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeechConfig.Mode mode = services().speech().setMode(player.getUUID(), id);
        if (mode == null) {
            source.sendFailure(Component.translatable("command.rp-vca.speech.unknown"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.rp-vca.speech.changed",
                Component.translatable(com.evarius.rpvca.speech.SpeechMode.translationKey(mode.id)),
                "whisper".equals(mode.id) ? "SVC" : mode.distance), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        RpVoiceAddon.reload(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.rp-vca.reload"), true);
        return 1;
    }

    private static int phoneStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal(services().phones().status(player)), false);
        return 1;
    }

    private static int call(CommandSourceStack source, String destination) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return services().phones().call(player, destination) ? 1 : 0;
    }

    private static int answer(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().phones().answer(source.getPlayerOrException()) ? 1 : 0;
    }

    private static int decline(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().phones().decline(source.getPlayerOrException()) ? 1 : 0;
    }

    private static int hangup(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().phones().hangup(source.getPlayerOrException()) ? 1 : 0;
    }

    private static int speaker(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        services().phones().toggleSpeaker(source.getPlayerOrException());
        return 1;
    }

    private static int listContacts(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.util.Map<String, String> contacts = services().phones().getContacts(player);
        source.sendSuccess(() -> contacts.isEmpty()
                ? Component.translatable("command.rp-vca.contacts.empty")
                : Component.translatable("command.rp-vca.contacts.list", contacts), false);
        return contacts.size();
    }

    private static int addContact(CommandSourceStack source, String name, String number)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        com.evarius.rpvca.api.ContactMutationResult result =
                services().phones().upsertContact(player, name, number);
        source.sendSuccess(() -> result.successful()
                ? Component.translatable("command.rp-vca.contact.saved", name, number)
                : Component.translatable("command.rp-vca.contact.failed", result), false);
        return result.successful() ? 1 : 0;
    }

    private static int removeContact(CommandSourceStack source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        com.evarius.rpvca.api.ContactMutationResult result =
                services().phones().removeContact(player, name);
        source.sendSuccess(() -> result.successful()
                ? Component.translatable("command.rp-vca.contact.removed")
                : Component.translatable("command.rp-vca.contact.failed", result), false);
        return result.successful() ? 1 : 0;
    }

    private static int clearPlayerHistory(CommandSourceStack source, String playerName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer administrator = source.getPlayerOrException();
        java.util.Optional<java.util.UUID> target = services().phones().findPlayerIdByName(playerName);
        if (target.isEmpty()) {
            source.sendFailure(Component.translatable("command.rp-vca.profile.not_found"));
            return 0;
        }
        com.evarius.rpvca.api.HistoryMutationResult result =
                services().phones().clearCallHistory(administrator, target.get());
        source.sendSuccess(() -> result.successful()
                ? Component.translatable("command.rp-vca.history.player_cleared", playerName)
                : Component.translatable("command.rp-vca.history.failed", result), true);
        return result.successful() ? 1 : 0;
    }

    private static int clearAllHistories(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        com.evarius.rpvca.api.HistoryMutationResult result =
                services().phones().clearAllCallHistories(source.getPlayerOrException());
        source.sendSuccess(() -> result.successful()
                ? Component.translatable("command.rp-vca.history.all_cleared")
                : Component.translatable("command.rp-vca.history.failed", result), true);
        return result.successful() ? 1 : 0;
    }

    private static int radioStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal(services().radios().status(player)), false);
        return 1;
    }

    private static int tune(CommandSourceStack source, String channel) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return services().radios().tune(source.getPlayerOrException(), channel) ? 1 : 0;
    }

    private static int radioOff(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        services().radios().off(source.getPlayerOrException());
        return 1;
    }

    private static int radioTransmit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        services().radios().toggleTransmit(source.getPlayerOrException());
        return 1;
    }

    private static int listChannels(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        java.util.List<RadioConfig.Channel> available = services().radios()
                .accessibleChannels(source.getPlayerOrException());
        String channels = available.stream()
                .map(channel -> channel.id + "=" + channel.displayName)
                .reduce((first, second) -> first + ", " + second).orElse("keine");
        source.sendSuccess(() -> Component.translatable("command.rp-vca.radio.channels", channels), false);
        return available.size();
    }

    private static int listTowers(CommandSourceStack source) {
        int count = Math.toIntExact(services().towers().count(
                com.evarius.rpvca.state.TowerRegistry.TowerType.CELLULAR));
        source.sendSuccess(() -> Component.translatable("command.rp-vca.towers", count), false);
        return count;
    }

    private static RpVoiceServices services() {
        RpVoiceServices services = RpVoiceServices.get();
        if (services == null) throw new IllegalStateException("RP Voice Additions ist noch nicht gestartet");
        return services;
    }
}
