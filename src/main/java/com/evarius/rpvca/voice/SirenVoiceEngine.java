package com.evarius.rpvca.voice;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.siren.SirenEmitter;
import com.evarius.rpvca.siren.SirenSignal;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns all Simple Voice Chat resources used by sirens.
 *
 * <p>The siren service remains independent from SVC lifecycle details. Signals and saved
 * announcements are sent through locational channels, while live microphone packets are
 * copied to each linked siren without suppressing normal proximity speech.</p>
 */
public final class SirenVoiceEngine {
    private static final String CATEGORY = "rp_siren";
    // Simple Voice Chat serializes category names with a hard limit of 16 characters.
    static final String CATEGORY_NAME = "Sirenen";
    private static final int SAMPLE_RATE = 48_000;
    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rp-vca-siren-audio");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<SirenSignal, short[]> SIGNAL_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Playback>> PLAYBACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, LiveSession> LIVE_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, RecordingSession> RECORDING_BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicLong> CONTROLLER_GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicInteger> PENDING_PLAYBACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PLAYBACK_DEADLINES = new ConcurrentHashMap<>();
    private static volatile VoicechatServerApi api;

    private SirenVoiceEngine() {
    }

    public static void attach(VoicechatServerApi voiceApi) {
        api = voiceApi;
        voiceApi.registerVolumeCategory(voiceApi.volumeCategoryBuilder()
                .setId(CATEGORY)
                .setName(CATEGORY_NAME)
                .setDescription("Räumliche Warnsignale und Lautsprecherdurchsagen von RP Voice Additions")
                .build());
        for (SirenSignal signal : SirenSignal.values()) {
            AUDIO_EXECUTOR.execute(() -> cacheSignal(signal, voiceApi));
        }
    }

    public static void detach() {
        PLAYBACKS.keySet().forEach(SirenVoiceEngine::stopController);
        LIVE_BY_PLAYER.values().forEach(LiveSession::close);
        LIVE_BY_PLAYER.clear();
        RECORDING_BY_PLAYER.values().forEach(RecordingSession::close);
        RECORDING_BY_PLAYER.clear();
        SIGNAL_CACHE.clear();
        CONTROLLER_GENERATIONS.clear();
        PENDING_PLAYBACKS.clear();
        PLAYBACK_DEADLINES.clear();
        api = null;
    }

    public static boolean available() {
        return api != null;
    }

    /** Returns whether this player can currently receive SVC audio. */
    public static boolean isPlayerListening(UUID playerId) {
        VoicechatServerApi voiceApi = api;
        if (voiceApi == null) return false;
        VoicechatConnection connection = voiceApi.getConnectionOf(playerId);
        return connection != null && connection.isInstalled() && connection.isConnected()
                && !connection.isDisabled();
    }

    /**
     * Ensures the bundled signal can be decoded before a scenario is accepted. Usually this is
     * already satisfied by the asynchronous preload performed when SVC starts.
     */
    public static boolean prepareSignal(SirenSignal signal) {
        VoicechatServerApi voiceApi = api;
        if (voiceApi == null) return false;
        short[] cached = SIGNAL_CACHE.get(signal);
        if (cached != null) return cached.length > 0;
        return cacheSignal(signal, voiceApi).length > 0;
    }

    public static boolean playSignal(UUID controllerId, SirenSignal signal,
                                     List<SirenEmitter> emitters, float distance, float gain) {
        VoicechatServerApi voiceApi = api;
        if (voiceApi == null || emitters.isEmpty()) {
            return false;
        }
        short[] pcm = SIGNAL_CACHE.get(signal);
        if (pcm == null && !prepareSignal(signal)) return false;
        pcm = SIGNAL_CACHE.get(signal);
        if (pcm == null || pcm.length == 0) return false;
        short[] preparedPcm = applyGain(pcm, gain);
        markPlaybackDeadline(controllerId, preparedPcm.length);
        long generation = generation(controllerId).get();
        AtomicInteger pending = PENDING_PLAYBACKS.computeIfAbsent(controllerId, ignored -> new AtomicInteger());
        pending.incrementAndGet();
        AUDIO_EXECUTOR.execute(() -> {
            try {
                if (generation(controllerId).get() == generation) {
                    playPcm(controllerId, preparedPcm, emitters, distance, voiceApi);
                }
            } catch (RuntimeException exception) {
                PLAYBACK_DEADLINES.remove(controllerId);
                RpVoiceAddon.LOGGER.error("Sirenen-Signal '{}' konnte nicht wiedergegeben werden",
                        signal.id(), exception);
            } finally {
                if (pending.decrementAndGet() == 0) PENDING_PLAYBACKS.remove(controllerId, pending);
            }
        });
        return true;
    }

    public static boolean playRecorded(UUID controllerId, byte[] pcmBytes,
                                       List<SirenEmitter> emitters, float distance, float gain) {
        VoicechatServerApi voiceApi = api;
        if (voiceApi == null || pcmBytes.length < 2 || emitters.isEmpty()) {
            return false;
        }
        short[] pcm = new short[pcmBytes.length / 2];
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        pcm = applyGain(pcm, gain);
        markPlaybackDeadline(controllerId, pcm.length);
        short[] preparedPcm = pcm;
        long generation = generation(controllerId).get();
        AtomicInteger pending = PENDING_PLAYBACKS.computeIfAbsent(controllerId, ignored -> new AtomicInteger());
        pending.incrementAndGet();
        AUDIO_EXECUTOR.execute(() -> {
            try {
                if (generation(controllerId).get() == generation) {
                    playPcm(controllerId, preparedPcm, emitters, distance, voiceApi);
                }
            } catch (RuntimeException exception) {
                PLAYBACK_DEADLINES.remove(controllerId);
                RpVoiceAddon.LOGGER.error("Gespeicherte Sirenen-Durchsage konnte nicht wiedergegeben werden",
                        exception);
            } finally {
                if (pending.decrementAndGet() == 0) PENDING_PLAYBACKS.remove(controllerId, pending);
            }
        });
        return true;
    }

    private static void playPcm(UUID controllerId, short[] pcm, List<SirenEmitter> emitters,
                                float distance, VoicechatServerApi voiceApi) {
        for (SirenEmitter emitter : emitters) {
            LocationalAudioChannel channel = createChannel(controllerId, emitter, distance, voiceApi);
            if (channel == null) {
                continue;
            }
            OpusEncoder encoder = voiceApi.createEncoder(OpusEncoderMode.AUDIO);
            AudioPlayer player = voiceApi.createAudioPlayer(channel, encoder, pcm);
            Playback playback = new Playback(controllerId, channel, player);
            PLAYBACKS.computeIfAbsent(controllerId, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(playback);
            player.setOnStopped(playback::close);
            player.startPlaying();
        }
    }

    public static boolean startLive(UUID playerId, UUID controllerId,
                                    List<SirenEmitter> emitters, float distance) {
        VoicechatServerApi voiceApi = api;
        if (voiceApi == null || emitters.isEmpty() || LIVE_BY_PLAYER.containsKey(playerId)) {
            return false;
        }
        List<LocationalAudioChannel> channels = new ArrayList<>();
        for (SirenEmitter emitter : emitters) {
            LocationalAudioChannel channel = createChannel(controllerId, emitter, distance, voiceApi);
            if (channel != null) {
                channels.add(channel);
            }
        }
        if (channels.isEmpty()) {
            return false;
        }
        LIVE_BY_PLAYER.put(playerId, new LiveSession(controllerId, channels));
        return true;
    }

    public static boolean stopLive(UUID playerId) {
        LiveSession session = LIVE_BY_PLAYER.remove(playerId);
        if (session == null) {
            return false;
        }
        session.close();
        return true;
    }

    public static boolean startRecording(UUID playerId, int maximumSeconds) {
        VoicechatServerApi voiceApi = api;
        if (voiceApi == null || RECORDING_BY_PLAYER.containsKey(playerId)) {
            return false;
        }
        RECORDING_BY_PLAYER.put(playerId,
                new RecordingSession(voiceApi.createDecoder(), Math.max(1, maximumSeconds) * SAMPLE_RATE));
        return true;
    }

    public static Optional<RecordedAudio> stopRecording(UUID playerId) {
        RecordingSession session = RECORDING_BY_PLAYER.remove(playerId);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(session.finish());
    }

    public static void handleMicrophone(UUID playerId, MicrophonePacket packet) {
        LiveSession live = LIVE_BY_PLAYER.get(playerId);
        if (live != null) {
            live.channels.forEach(channel -> channel.send(packet));
        }
        RecordingSession recording = RECORDING_BY_PLAYER.get(playerId);
        if (recording != null) {
            recording.capture(packet.getOpusEncodedData());
        }
    }

    public static void stopController(UUID controllerId) {
        generation(controllerId).incrementAndGet();
        PENDING_PLAYBACKS.remove(controllerId);
        PLAYBACK_DEADLINES.remove(controllerId);
        List<Playback> playbacks = PLAYBACKS.remove(controllerId);
        if (playbacks != null) {
            playbacks.forEach(Playback::close);
        }
        LIVE_BY_PLAYER.entrySet().removeIf(entry -> {
            if (!entry.getValue().controllerId.equals(controllerId)) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    public static boolean isControllerActive(UUID controllerId) {
        Long deadline = PLAYBACK_DEADLINES.get(controllerId);
        if (deadline != null && deadline <= System.currentTimeMillis()) {
            PLAYBACK_DEADLINES.remove(controllerId, deadline);
            deadline = null;
        }
        List<Playback> playbacks = PLAYBACKS.get(controllerId);
        return (playbacks != null && playbacks.stream().anyMatch(playback -> !playback.closed.get()))
                || deadline != null
                || PENDING_PLAYBACKS.containsKey(controllerId)
                || LIVE_BY_PLAYER.values().stream().anyMatch(session -> session.controllerId.equals(controllerId));
    }

    public static boolean isLive(UUID playerId) {
        return LIVE_BY_PLAYER.containsKey(playerId);
    }

    private static AtomicLong generation(UUID controllerId) {
        return CONTROLLER_GENERATIONS.computeIfAbsent(controllerId, ignored -> new AtomicLong());
    }

    private static void markPlaybackDeadline(UUID controllerId, int samples) {
        long durationMillis = Math.max(20L, samples * 1_000L / SAMPLE_RATE);
        PLAYBACK_DEADLINES.merge(controllerId, System.currentTimeMillis() + durationMillis, Math::max);
    }

    static short[] applyGain(short[] source, float gain) {
        float safeGain = Math.clamp(Float.isFinite(gain) ? gain : 1.0F, 0.0F, 1.0F);
        if (safeGain >= 0.9999F) return source;
        short[] scaled = new short[source.length];
        for (int index = 0; index < source.length; index++) {
            scaled[index] = (short) Math.clamp(Math.round(source[index] * safeGain),
                    Short.MIN_VALUE, Short.MAX_VALUE);
        }
        return scaled;
    }

    public static boolean isRecording(UUID playerId) {
        return RECORDING_BY_PLAYER.containsKey(playerId);
    }

    private static LocationalAudioChannel createChannel(UUID controllerId, SirenEmitter emitter,
                                                         float distance, VoicechatServerApi voiceApi) {
        UUID channelId = UUID.nameUUIDFromBytes((RpVoiceAddon.MOD_ID + ":siren:" + controllerId + ":"
                + emitter.sirenId() + ":" + UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        LocationalAudioChannel channel = voiceApi.createLocationalAudioChannel(
                channelId,
                voiceApi.fromServerLevel(emitter.world()),
                voiceApi.createPosition(emitter.pos().getX() + 0.5D,
                        emitter.pos().getY() + 0.5D, emitter.pos().getZ() + 0.5D));
        if (channel != null) {
            channel.setCategory(CATEGORY);
            channel.setDistance(distance);
        }
        return channel;
    }

    private static short[] decodeSignal(SirenSignal signal, VoicechatServerApi voiceApi) {
        try (InputStream stream = SirenVoiceEngine.class.getResourceAsStream(signal.resourcePath())) {
            if (stream == null) {
                RpVoiceAddon.LOGGER.error("Sirenen-Audiodatei fehlt: {}", signal.resourcePath());
                return new short[0];
            }
            // MediaHuman embeds a large ID3v2 metadata block (including artwork) in these files.
            // The native SVC/LAME decoder interprets that block inconsistently and may return only
            // one sample. Supplying the MPEG audio payload directly makes decoding deterministic.
            byte[] payload = mp3AudioPayload(stream.readAllBytes());
            de.maxhenkel.voicechat.api.mp3.Mp3Decoder decoder =
                    voiceApi.createMp3Decoder(new ByteArrayInputStream(payload));
            if (decoder == null) {
                throw new IOException("Simple Voice Chat could not create an MP3 decoder");
            }
            short[] decoded = decoder.decode();
            AudioFormat format = decoder.getAudioFormat();
            RpVoiceAddon.LOGGER.info("Sirenen-MP3 '{}' dekodiert: {} Rohsamples, {} Hz, {} Kanal/Kanäle",
                    signal.id(), decoded.length, format.getSampleRate(), format.getChannels());
            return toMono48Khz(decoded, format);
        } catch (IOException | RuntimeException exception) {
            RpVoiceAddon.LOGGER.error("Sirenen-Audiodatei konnte nicht dekodiert werden: {}",
                    signal.resourcePath(), exception);
            return new short[0];
        }
    }

    static byte[] mp3AudioPayload(byte[] source) {
        if (source.length < 10 || source[0] != 'I' || source[1] != 'D' || source[2] != '3') {
            return source;
        }
        int tagSize = ((source[6] & 0x7F) << 21)
                | ((source[7] & 0x7F) << 14)
                | ((source[8] & 0x7F) << 7)
                | (source[9] & 0x7F);
        int audioOffset = 10 + tagSize;
        // ID3v2.4 can append a ten-byte footer when flag 0x10 is set.
        if ((source[5] & 0x10) != 0) audioOffset += 10;
        if (audioOffset <= 10 || audioOffset >= source.length) {
            return source;
        }
        return java.util.Arrays.copyOfRange(source, audioOffset, source.length);
    }

    private static short[] cacheSignal(SirenSignal signal, VoicechatServerApi voiceApi) {
        short[] existing = SIGNAL_CACHE.get(signal);
        if (existing != null) return existing;
        short[] decoded = decodeSignal(signal, voiceApi);
        SIGNAL_CACHE.put(signal, decoded);
        if (decoded.length > 0) {
            RpVoiceAddon.LOGGER.info("Sirenen-Signal '{}' geladen ({} Samples, {} Sekunden)",
                    signal.id(), decoded.length, decoded.length / SAMPLE_RATE);
        }
        return decoded;
    }

    static short[] toMono48Khz(short[] source, AudioFormat format) {
        int channels = Math.max(1, format.getChannels());
        int sourceFrames = source.length / channels;
        if (sourceFrames == 0) {
            return new short[0];
        }
        short[] mono = new short[sourceFrames];
        for (int frame = 0; frame < sourceFrames; frame++) {
            long total = 0;
            for (int channel = 0; channel < channels; channel++) {
                total += source[frame * channels + channel];
            }
            mono[frame] = (short) (total / channels);
        }
        float sourceRate = format.getSampleRate() > 0 ? format.getSampleRate() : SAMPLE_RATE;
        if (Math.abs(sourceRate - SAMPLE_RATE) < 0.1F) {
            return mono;
        }
        // Cast before multiplication: multi-minute signals overflow an int at roughly 45k samples.
        int targetFrames = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                Math.round(mono.length * (double) SAMPLE_RATE / sourceRate)));
        short[] target = new short[targetFrames];
        for (int index = 0; index < targetFrames; index++) {
            double sourcePosition = index * sourceRate / SAMPLE_RATE;
            int low = Math.min((int) sourcePosition, mono.length - 1);
            int high = Math.min(low + 1, mono.length - 1);
            double fraction = sourcePosition - low;
            target[index] = (short) Math.clamp(Math.round(mono[low] * (1.0D - fraction)
                    + mono[high] * fraction), Short.MIN_VALUE, Short.MAX_VALUE);
        }
        return target;
    }

    public record RecordedAudio(byte[] pcm, long durationMillis) {
    }

    private static final class Playback {
        private final UUID controllerId;
        private final LocationalAudioChannel channel;
        private final AudioPlayer player;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Playback(UUID controllerId, LocationalAudioChannel channel, AudioPlayer player) {
            this.controllerId = controllerId;
            this.channel = channel;
            this.player = player;
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (!player.isStopped()) {
                player.stopPlaying();
            }
            channel.flush();
            // AudioPlayer owns and closes the encoder after its playback thread has stopped.
            List<Playback> siblings = PLAYBACKS.get(controllerId);
            if (siblings != null) {
                siblings.remove(this);
                if (siblings.isEmpty()) {
                    PLAYBACKS.remove(controllerId, siblings);
                    PLAYBACK_DEADLINES.remove(controllerId);
                }
            }
        }
    }

    private record LiveSession(UUID controllerId, List<LocationalAudioChannel> channels) {
        private void close() {
            channels.forEach(LocationalAudioChannel::flush);
        }
    }

    private static final class RecordingSession {
        private final OpusDecoder decoder;
        private final int maximumSamples;
        private final long startedNanos = System.nanoTime();
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private boolean closed;

        private RecordingSession(OpusDecoder decoder, int maximumSamples) {
            this.decoder = decoder;
            this.maximumSamples = maximumSamples;
        }

        private synchronized void capture(byte[] opus) {
            if (closed || pcm.size() / 2 >= maximumSamples) {
                return;
            }
            int timelineSample = (int) Math.min(maximumSamples,
                    (System.nanoTime() - startedNanos) * SAMPLE_RATE / 1_000_000_000L);
            appendSilence(Math.max(0, timelineSample - pcm.size() / 2));
            short[] decoded = decoder.decode(opus);
            append(decoded, Math.min(decoded.length, maximumSamples - pcm.size() / 2));
        }

        private synchronized RecordedAudio finish() {
            if (!closed) {
                int finalSample = (int) Math.min(maximumSamples,
                        (System.nanoTime() - startedNanos) * SAMPLE_RATE / 1_000_000_000L);
                appendSilence(Math.max(0, finalSample - pcm.size() / 2));
                closed = true;
                decoder.close();
            }
            byte[] bytes = pcm.toByteArray();
            return new RecordedAudio(bytes, bytes.length * 1_000L / (SAMPLE_RATE * 2L));
        }

        private void close() {
            finish();
        }

        private void appendSilence(int samples) {
            int allowed = Math.min(samples, maximumSamples - pcm.size() / 2);
            if (allowed > 0) {
                pcm.writeBytes(new byte[allowed * 2]);
            }
        }

        private void append(short[] values, int length) {
            ByteBuffer buffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < length; index++) {
                buffer.putShort(values[index]);
            }
            pcm.writeBytes(buffer.array());
        }
    }
}
