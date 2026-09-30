package com.konqasasas.localreplay.replay.io;

import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayKeyBindings;
import com.konqasasas.localreplay.replay.data.ReplayMetadata;
import com.konqasasas.localreplay.replay.data.ReplayTick;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Strict reader/writer for Local Replay .lreplay files.
 *
 * The entire logical LRPL stream is wrapped in GZIP. New saves are always v2.
 * v1 is accepted only for backwards compatibility and is upgraded in memory by
 * deriving motion from adjacent positions and setting stateFlags to zero.
 */
public final class ReplayFileIO {

    public static final String FILE_EXTENSION = ".lreplay";
    public static final int MAX_TICKS = 1_000_000;

    private static final byte[] MAGIC = new byte[] {'L', 'R', 'P', 'L'};
    private static final int MAX_TEXT_CHARS = 64;
    private static final int MAX_TEXT_BYTES = 256;
    private static final long MAX_COMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_DECOMPRESSED_BYTES = 64L * 1024L * 1024L;

    private ReplayFileIO() {
    }

    /**
     * Saves a replay into the supplied replay directory using a collision-safe
     * timestamp filename. The returned path is the final committed file.
     */
    public static Path saveAutoNamed(Path replayDirectory, ReplayData data) throws ReplayFileException {
        if (replayDirectory == null) {
            throw new IllegalArgumentException("replayDirectory cannot be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }
        validateDataForSave(data);

        try {
            Files.createDirectories(replayDirectory);
        } catch (IOException exception) {
            throw new ReplayFileException("Could not create the replay directory.", exception);
        }

        Path destination = chooseUniquePath(replayDirectory, data.getMetadata().getCreatedAtEpochMillis());
        save(destination, data);
        return destination;
    }

    /**
     * Atomically saves a v2 replay where supported by the filesystem.
     */
    public static void save(Path destination, ReplayData data) throws ReplayFileException {
        if (destination == null) {
            throw new IllegalArgumentException("destination cannot be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }
        validateDataForSave(data);

        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new ReplayFileException("Replay path has no parent directory.");
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new ReplayFileException("Could not create the replay directory.", exception);
        }

        Path temp = null;
        try {
            temp = Files.createTempFile(parent, ".localreplay-", ".tmp");
            writeV2(temp, data);
            moveCommitted(temp, absolute);
            temp = null;
        } catch (ReplayFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ReplayFileException("Could not save replay file.", exception);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best effort only. Never hide the original save failure.
                }
            }
        }
    }

    /**
     * Loads and validates a whole replay in memory. Unknown versions and any
     * malformed numeric/flag/string data are rejected rather than guessed.
     */
    public static ReplayData load(Path source) throws ReplayFileException {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        Path absolute = source.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(absolute)) {
                throw new ReplayFileException("Replay file does not exist or is not a regular file.");
            }
            long compressedBytes = Files.size(absolute);
            if (compressedBytes <= 0L) {
                throw new ReplayFileException("Replay file is empty.");
            }
            if (compressedBytes > MAX_COMPRESSED_BYTES) {
                throw new ReplayFileException("Replay file is too large.");
            }
        } catch (ReplayFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ReplayFileException("Could not inspect replay file.", exception);
        }

        try (InputStream fileIn = Files.newInputStream(absolute);
             BufferedInputStream buffered = new BufferedInputStream(fileIn);
             GZIPInputStream gzip = new GZIPInputStream(buffered);
             LimitedInputStream limited = new LimitedInputStream(gzip, MAX_DECOMPRESSED_BYTES);
             DataInputStream in = new DataInputStream(limited)) {

            verifyMagic(in);
            int formatVersion = in.readInt();
            if (formatVersion != ReplayMetadata.FORMAT_VERSION_V1
                    && formatVersion != ReplayMetadata.FORMAT_VERSION_V2) {
                throw new ReplayFileException("Unsupported replay format version: " + formatVersion);
            }

            String minecraftVersion = readText(in, "minecraftVersion");
            String modVersion = readText(in, "modVersion");
            long createdAtEpochMillis = in.readLong();
            if (createdAtEpochMillis < 0L) {
                throw new ReplayFileException("Replay creation time is invalid.");
            }

            int tickCount = in.readInt();
            if (tickCount <= 0 || tickCount > MAX_TICKS) {
                throw new ReplayFileException("Replay tick count is invalid: " + tickCount);
            }

            ReplayKeyBindings bindings = new ReplayKeyBindings(
                    readText(in, "forward key"),
                    readText(in, "left key"),
                    readText(in, "back key"),
                    readText(in, "right key"),
                    readText(in, "jump key"),
                    readText(in, "sprint key"),
                    readText(in, "sneak key")
            );

            ReplayTick[] ticks;
            if (formatVersion == ReplayMetadata.FORMAT_VERSION_V2) {
                ticks = readV2Ticks(in, tickCount);
            } else {
                ticks = readAndUpgradeV1Ticks(in, tickCount);
            }

            if (in.read() != -1) {
                throw new ReplayFileException("Replay contains unexpected trailing data.");
            }

            // Loaded data is normalized to the current in-memory format. This
            // means re-saving an old v1 file always emits a clean v2 file.
            ReplayMetadata metadata = new ReplayMetadata(
                    ReplayMetadata.FORMAT_VERSION_CURRENT,
                    minecraftVersion,
                    modVersion,
                    createdAtEpochMillis,
                    tickCount,
                    bindings
            );
            return new ReplayData(metadata, ticks);
        } catch (ReplayFileException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new ReplayFileException("Replay file ended unexpectedly.", exception);
        } catch (IllegalArgumentException exception) {
            throw new ReplayFileException("Replay contains invalid data: " + exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ReplayFileException("Replay file is damaged or is not a valid GZIP replay.", exception);
        }
    }


    /**
     * Reads only the validated header used by Replay Library rows. Full tick
     * validation still happens in {@link #load(Path)} when the user opens it.
     */
    public static ReplayFileSummary readSummary(Path source) throws ReplayFileException {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        Path absolute = source.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(absolute)) {
                throw new ReplayFileException("Replay file does not exist or is not a regular file.");
            }
            long compressedBytes = Files.size(absolute);
            if (compressedBytes <= 0L) {
                throw new ReplayFileException("Replay file is empty.");
            }
            if (compressedBytes > MAX_COMPRESSED_BYTES) {
                throw new ReplayFileException("Replay file is too large.");
            }
        } catch (ReplayFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ReplayFileException("Could not inspect replay file.", exception);
        }

        try (InputStream fileIn = Files.newInputStream(absolute);
             BufferedInputStream buffered = new BufferedInputStream(fileIn);
             GZIPInputStream gzip = new GZIPInputStream(buffered);
             LimitedInputStream limited = new LimitedInputStream(gzip, MAX_DECOMPRESSED_BYTES);
             DataInputStream in = new DataInputStream(limited)) {

            verifyMagic(in);
            int formatVersion = in.readInt();
            if (formatVersion != ReplayMetadata.FORMAT_VERSION_V1
                    && formatVersion != ReplayMetadata.FORMAT_VERSION_V2) {
                throw new ReplayFileException("Unsupported replay format version: " + formatVersion);
            }

            String minecraftVersion = readText(in, "minecraftVersion");
            String modVersion = readText(in, "modVersion");
            long createdAtEpochMillis = in.readLong();
            if (createdAtEpochMillis < 0L) {
                throw new ReplayFileException("Replay creation time is invalid.");
            }

            int tickCount = in.readInt();
            if (tickCount <= 0 || tickCount > MAX_TICKS) {
                throw new ReplayFileException("Replay tick count is invalid: " + tickCount);
            }

            // Validate the remainder of the fixed header as well. The library
            // does not need these names, but a truncated header should never be
            // presented as a healthy replay.
            readText(in, "forward key");
            readText(in, "left key");
            readText(in, "back key");
            readText(in, "right key");
            readText(in, "jump key");
            readText(in, "sprint key");
            readText(in, "sneak key");

            return new ReplayFileSummary(
                    absolute,
                    formatVersion,
                    minecraftVersion,
                    modVersion,
                    createdAtEpochMillis,
                    tickCount
            );
        } catch (ReplayFileException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new ReplayFileException("Replay file ended during its header.", exception);
        } catch (IllegalArgumentException exception) {
            throw new ReplayFileException("Replay header contains invalid data: " + exception.getMessage(), exception);
        } catch (IOException exception) {
            throw new ReplayFileException("Replay file is damaged or is not a valid GZIP replay.", exception);
        }
    }

    private static void writeV2(Path path, ReplayData data) throws ReplayFileException {
        ReplayMetadata metadata = data.getMetadata();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(path))))) {
            out.write(MAGIC);
            out.writeInt(ReplayMetadata.FORMAT_VERSION_CURRENT);
            writeText(out, metadata.getMinecraftVersion(), "minecraftVersion");
            writeText(out, metadata.getModVersion(), "modVersion");
            out.writeLong(metadata.getCreatedAtEpochMillis());
            out.writeInt(data.size());

            ReplayKeyBindings bindings = metadata.getRecordedKeyBindings();
            writeText(out, bindings.getForward(), "forward key");
            writeText(out, bindings.getLeft(), "left key");
            writeText(out, bindings.getBack(), "back key");
            writeText(out, bindings.getRight(), "right key");
            writeText(out, bindings.getJump(), "jump key");
            writeText(out, bindings.getSprint(), "sprint key");
            writeText(out, bindings.getSneak(), "sneak key");

            for (int i = 0; i < data.size(); i++) {
                ReplayTick tick = data.getTick(i);
                out.writeDouble(tick.getX());
                out.writeDouble(tick.getY());
                out.writeDouble(tick.getZ());
                out.writeDouble(tick.getMotionX());
                out.writeDouble(tick.getMotionY());
                out.writeDouble(tick.getMotionZ());
                out.writeFloat(tick.getYaw());
                out.writeFloat(tick.getPitch());
                out.writeByte(tick.getInputFlags());
                out.writeByte(tick.getStateFlags());
            }
        } catch (IOException exception) {
            throw new ReplayFileException("Could not write replay data.", exception);
        }
    }

    private static ReplayTick[] readV2Ticks(DataInputStream in, int tickCount) throws IOException, ReplayFileException {
        ReplayTick[] ticks = new ReplayTick[tickCount];
        for (int i = 0; i < tickCount; i++) {
            try {
                ticks[i] = new ReplayTick(
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble(),
                        in.readFloat(),
                        in.readFloat(),
                        in.readByte(),
                        in.readByte()
                );
            } catch (IllegalArgumentException exception) {
                throw new ReplayFileException("Invalid tick " + i + ": " + exception.getMessage(), exception);
            }
        }
        return ticks;
    }

    private static ReplayTick[] readAndUpgradeV1Ticks(DataInputStream in, int tickCount)
            throws IOException, ReplayFileException {
        double[] x = new double[tickCount];
        double[] y = new double[tickCount];
        double[] z = new double[tickCount];
        float[] yaw = new float[tickCount];
        float[] pitch = new float[tickCount];
        byte[] input = new byte[tickCount];

        for (int i = 0; i < tickCount; i++) {
            x[i] = in.readDouble();
            y[i] = in.readDouble();
            z[i] = in.readDouble();
            yaw[i] = in.readFloat();
            pitch[i] = in.readFloat();
            input[i] = in.readByte();
            validateV1Sample(i, x[i], y[i], z[i], yaw[i], pitch[i], input[i]);
        }

        ReplayTick[] ticks = new ReplayTick[tickCount];
        for (int i = 0; i < tickCount; i++) {
            double motionX;
            double motionY;
            double motionZ;
            if (tickCount == 1) {
                motionX = 0.0D;
                motionY = 0.0D;
                motionZ = 0.0D;
            } else if (i < tickCount - 1) {
                motionX = x[i + 1] - x[i];
                motionY = y[i + 1] - y[i];
                motionZ = z[i + 1] - z[i];
            } else {
                motionX = x[i] - x[i - 1];
                motionY = y[i] - y[i - 1];
                motionZ = z[i] - z[i - 1];
            }

            try {
                ticks[i] = new ReplayTick(
                        x[i], y[i], z[i],
                        motionX, motionY, motionZ,
                        yaw[i], pitch[i], input[i], (byte) 0
                );
            } catch (IllegalArgumentException exception) {
                throw new ReplayFileException("Invalid upgraded v1 tick " + i + ": " + exception.getMessage(), exception);
            }
        }
        return ticks;
    }

    private static void validateV1Sample(
            int index,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            byte inputFlags
    ) throws ReplayFileException {
        if (!isFinite(x) || !isFinite(y) || !isFinite(z) || !isFinite(yaw) || !isFinite(pitch)) {
            throw new ReplayFileException("Invalid v1 tick " + index + ": non-finite numeric value.");
        }
        int flags = inputFlags & 0xFF;
        if ((flags & ~ReplayTick.ALL_INPUT_MASK) != 0) {
            throw new ReplayFileException("Invalid v1 tick " + index + ": unsupported input flag bits.");
        }
    }

    private static void verifyMagic(DataInputStream in) throws IOException, ReplayFileException {
        for (int i = 0; i < MAGIC.length; i++) {
            if (in.readByte() != MAGIC[i]) {
                throw new ReplayFileException("Invalid replay magic. Expected LRPL.");
            }
        }
    }

    private static String readText(DataInputStream in, String fieldName) throws IOException, ReplayFileException {
        int byteLength = in.readInt();
        if (byteLength <= 0 || byteLength > MAX_TEXT_BYTES) {
            throw new ReplayFileException(fieldName + " length is invalid: " + byteLength);
        }
        byte[] bytes = new byte[byteLength];
        in.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.trim().isEmpty() || value.length() > MAX_TEXT_CHARS) {
            throw new ReplayFileException(fieldName + " is blank or too long.");
        }
        // Enforce canonical UTF-8. Invalid byte sequences would otherwise be
        // silently replaced with U+FFFD by String decoding.
        byte[] canonical = value.getBytes(StandardCharsets.UTF_8);
        if (canonical.length != bytes.length) {
            throw new ReplayFileException(fieldName + " is not valid UTF-8.");
        }
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != canonical[i]) {
                throw new ReplayFileException(fieldName + " is not valid UTF-8.");
            }
        }
        return value.trim();
    }

    private static void writeText(DataOutputStream out, String value, String fieldName)
            throws IOException, ReplayFileException {
        if (value == null) {
            throw new ReplayFileException(fieldName + " cannot be null.");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_CHARS) {
            throw new ReplayFileException(fieldName + " is blank or too long.");
        }
        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > MAX_TEXT_BYTES) {
            throw new ReplayFileException(fieldName + " UTF-8 length is invalid.");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void validateDataForSave(ReplayData data) throws ReplayFileException {
        int tickCount = data.size();
        if (tickCount <= 0 || tickCount > MAX_TICKS) {
            throw new ReplayFileException("Replay tick count is invalid: " + tickCount);
        }
        ReplayMetadata metadata = data.getMetadata();
        if (metadata.getTickCount() != tickCount) {
            throw new ReplayFileException("Replay metadata tick count does not match data.");
        }
        if (metadata.getCreatedAtEpochMillis() < 0L) {
            throw new ReplayFileException("Replay creation time is invalid.");
        }
        // Constructors already validate tick numeric values and flag masks.
        // Touch every tick here so a future ReplayData implementation cannot
        // accidentally defer/null out entries without the writer noticing.
        for (int i = 0; i < tickCount; i++) {
            if (data.getTick(i) == null) {
                throw new ReplayFileException("Replay contains a null tick at " + i + ".");
            }
        }
    }

    private static Path chooseUniquePath(Path directory, long epochMillis) throws ReplayFileException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        String baseName = format.format(new Date(epochMillis));

        for (int suffix = 1; suffix <= 10_000; suffix++) {
            String name = suffix == 1
                    ? baseName + FILE_EXTENSION
                    : baseName + "_" + suffix + FILE_EXTENSION;
            Path candidate = directory.resolve(name);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new ReplayFileException("Could not choose a unique replay filename.");
    }

    private static void moveCommitted(Path temp, Path destination) throws IOException {
        try {
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, destination);
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** Limits decompressed input to prevent a tiny gzip bomb from allocating an unbounded replay. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                addCount(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                addCount(read);
            }
            return read;
        }

        private void addCount(long amount) throws IOException {
            count += amount;
            if (count > limit) {
                throw new IOException("Replay decompressed data exceeds safety limit.");
            }
        }
    }
}
