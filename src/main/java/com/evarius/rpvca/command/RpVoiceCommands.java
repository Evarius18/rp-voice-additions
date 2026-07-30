package com.evarius.rpvca.command;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.RpVoiceServices;
import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.config.SpeechConfig;
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
        source.sendFeedback(() -> Text.translatable("command.rp-vca.speech.status",
                Text.translatable(com.evarius.rpvca.speech.SpeechMode.translationKey(mode.id)),
                "whisper".equals(mode.id) ? "SVC" : mode.distance), false);
        return 1;
    }

    private static int setRange(ServerCommandSource source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        SpeechConfig.Mode mode = services().speech().setMode(player.getUuid(), id);
        if (mode == null) {
            source.sendError(Text.translatable("command.rp-vca.speech.unknown"));
            return 0;
        }
        source.sendFeedback(() -> Text.translatable("command.rp-vca.speech.changed",
                Text.translatable(com.evarius.rpvca.speech.SpeechMode.translationKey(mode.id)),
                "whisper".equals(mode.id) ? "SVC" : mode.distance), false);
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        RpVoiceAddon.reload(source.getServer());
        source.sendFeedback(() -> Text.translatable("command.rp-vca.reload"), true);
        return 1;
    }

    private static int phoneStatus(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        source.sendFeedback(() -> Text.literal(services().phones().status(player)), false);
        return 1;
    }

    private static int call(ServerCommandSource source, String destination) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        return services().phones().call(player, destination) ? 1 : 0;
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
        java.util.Map<String, String> contacts = services().phones().getContacts(player);
        source.sendFeedback(() -> contacts.isEmpty()
                ? Text.translatable("command.rp-vca.contacts.empty")
                : Text.translatable("command.rp-vca.contacts.list", contacts), false);
        return contacts.size();
    }

    private static int addContact(ServerCommandSource source, String name, String number)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        com.evarius.rpvca.api.ContactMutationResult result =
                services().phones().upsertContact(player, name, number);
        source.sendFeedback(() -> result.successful()
                ? Text.translatable("command.rp-vca.contact.saved", name, number)
                : Text.translatable("command.rp-vca.contact.failed", result), false);
        return result.successful() ? 1 : 0;
    }

    private static int removeContact(ServerCommandSource source, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        com.evarius.rpvca.api.ContactMutationResult result =
                services().phones().removeContact(player, name);
        source.sendFeedback(() -> result.successful()
                ? Text.translatable("command.rp-vca.contact.removed")
                : Text.translatable("command.rp-vca.contact.failed", result), false);
        return result.successful() ? 1 : 0;
    }

    private static int clearPlayerHistory(ServerCommandSource source, String playerName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity administrator = source.getPlayerOrThrow();
        java.util.Optional<java.util.UUID> target = services().phones().findPlayerIdByName(playerName);
        if (target.isEmpty()) {
            source.sendError(Text.translatable("command.rp-vca.profile.not_found"));
            return 0;
        }
        com.evarius.rpvca.api.HistoryMutationResult result =
                services().phones().clearCallHistory(administrator, target.get());
        source.sendFeedback(() -> result.successful()
                ? Text.translatable("command.rp-vca.history.player_cleared", playerName)
                : Text.translatable("command.rp-vca.history.failed", result), true);
        return result.successful() ? 1 : 0;
    }

    private static int clearAllHistories(ServerCommandSource source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        com.evarius.rpvca.api.HistoryMutationResult result =
                services().phones().clearAllCallHistories(source.getPlayerOrThrow());
        source.sendFeedback(() -> result.successful()
                ? Text.translatable("command.rp-vca.history.all_cleared")
                : Text.translatable("command.rp-vca.history.failed", result), true);
        return result.successful() ? 1 : 0;
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
        source.sendFeedback(() -> Text.translatable("command.rp-vca.radio.channels", channels), false);
        return services().configs().radio().channels.size();
    }

    private static int listTowers(ServerCommandSource source) {
        int count = services().towers().all().size();
        source.sendFeedback(() -> Text.translatable("command.rp-vca.towers", count), false);
        return count;
    }

    private static RpVoiceServices services() {
        RpVoiceServices services = RpVoiceServices.get();
        if (services == null) throw new IllegalStateException("RP Voice Additions ist noch nicht gestartet");
        return services;
    }
}
