package com.voxelgame.audio;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;

/**
 * Decodes sound clips (OGG Vorbis via STB, plus plain PCM WAV) into OpenAL
 * buffers.
 *
 * Folders are scanned as "0.ogg"/"0.wav", "1.ogg"/"1.wav", ... until the
 * first gap, so a surface's variants only need a rename to 0..N. Buffers
 * are cached, and {@link #randomBuffer} picks a random variant every time
 * it is called.
 */
public class SoundLoader {

    /** Folder -> buffer ids. */
    private static final Map<String, int[]> cache = new HashMap<>();

    public static int randomBuffer(String folder) {
        int[] buffers = cache.get(folder);
        if (buffers == null) {
            buffers = loadFolder(folder);
            cache.put(folder, buffers);
        }
        if (buffers.length == 0) return 0;
        return buffers[(int) (Math.random() * buffers.length)];
    }

    private static int[] loadFolder(String folder) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int id = loadClip(folder + "/" + i + ".ogg");
            if (id == 0) id = loadClip(folder + "/" + i + ".wav");
            if (id == 0) break;
            ids.add(id);
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        if (out.length > 0) {
            System.out.println("SoundLoader: loaded " + out.length
                + " clip(s) from " + folder);
        } else {
            System.out.println("SoundLoader: no clips found in " + folder);
        }
        return out;
    }

    private static byte[] readAll(String path) {
        try (InputStream is = SoundLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return is.readAllBytes();
        } catch (IOException ignored) {}
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            // Missing files simply end the numbered scan of a folder
            return null;
        }
    }

    /** Decode one clip into a fresh OpenAL buffer. Returns 0 on failure. */
    private static int loadClip(String path) {
        byte[] data = readAll(path);
        if (data == null) return 0;

        if (path.endsWith(".ogg")) return loadOgg(path, data);
        if (path.endsWith(".wav")) return loadWav(path, data);
        return 0;
    }

    // ------------------------------------------------------------------
    // OGG Vorbis
    // ------------------------------------------------------------------

    private static int loadOgg(String path, byte[] data) {
        ByteBuffer input = MemoryUtil.memAlloc(data.length).put(data).flip();
        IntBuffer error = MemoryUtil.memAllocInt(1);
        STBVorbisInfo info = STBVorbisInfo.malloc();

        long handle = STBVorbis.stb_vorbis_open_memory(input, error, null);
        if (handle == 0L) {
            System.err.println("SoundLoader: STB failed to decode " + path
                + " (error " + (error.get(0) & 0xFFFFFFFFL) + ")");
            MemoryUtil.memFree(input);
            MemoryUtil.memFree(error);
            info.free();
            return 0;
        }

        try {
            STBVorbis.stb_vorbis_get_info(handle, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();
            int samplesPerChannel = STBVorbis.stb_vorbis_stream_length_in_samples(handle);

            ShortBuffer pcm = MemoryUtil.memAllocShort(samplesPerChannel * channels);
            int decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                handle, channels, pcm);
            if (decoded <= 0) {
                System.err.println("SoundLoader: no samples decoded from " + path);
                MemoryUtil.memFree(pcm);
                return 0;
            }
            pcm.limit(decoded * channels);

            return makeBuffer(pcm, channels, sampleRate);
        } finally {
            STBVorbis.stb_vorbis_close(handle);
            MemoryUtil.memFree(input);
            MemoryUtil.memFree(error);
            info.free();
        }
    }

    // ------------------------------------------------------------------
    // RIFF WAV (PCM 8/16-bit and IEEE float)
    // ------------------------------------------------------------------

    private static int loadWav(String path, byte[] data) {
        if (data.length < 44
            || data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F'
            || data[8] != 'W' || data[9] != 'A' || data[10] != 'V' || data[11] != 'E') {
            System.err.println("SoundLoader: not a RIFF/WAVE file: " + path);
            return 0;
        }

        int pos = 12;
        int format = 0, channels = 0, sampleRate = 0, bits = 0;
        int dataStart = -1, dataLen = 0;

        while (pos + 8 <= data.length) {
            int chunkSize = (data[pos + 4] & 0xFF) | ((data[pos + 5] & 0xFF) << 8)
                | ((data[pos + 6] & 0xFF) << 16) | ((data[pos + 7] & 0xFF) << 24);
            boolean isFmt = data[pos] == 'f' && data[pos + 1] == 'm'
                && data[pos + 2] == 't' && data[pos + 3] == ' ';
            boolean isData = data[pos] == 'd' && data[pos + 1] == 'a'
                && data[pos + 2] == 't' && data[pos + 3] == 'a';

            if (isFmt && chunkSize >= 16) {
                format = LE16(data, pos + 8);
                channels = LE16(data, pos + 10);
                sampleRate = LE32(data, pos + 12);
                bits = LE16(data, pos + 22);
            } else if (isData) {
                dataStart = pos + 8;
                dataLen = chunkSize;
                break;
            }
            // Chunks are word-aligned
            pos = pos + 8 + chunkSize + (chunkSize & 1);
        }

        if (dataStart < 0 || format == 0 || channels == 0 || bits == 0) {
            System.err.println("SoundLoader: malformed WAV: " + path);
            return 0;
        }
        if (dataStart + dataLen > data.length) dataLen = data.length - dataStart;

        ShortBuffer pcm;
        if (format == 1 && bits == 16) {
            pcm = MemoryUtil.memAllocShort(dataLen / 2);
            ByteBuffer bb = ByteBuffer.wrap(data, dataStart, dataLen)
                .order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
            ShortBuffer sb = bb.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            pcm.put(sb);
            pcm.flip();
        } else if (format == 1 && bits == 8) {
            int samples = dataLen;
            pcm = MemoryUtil.memAllocShort(samples);
            for (int i = 0; i < samples; i++) {
                pcm.put((short) (((data[dataStart + i] & 0xFF) - 128) << 8));
            }
            pcm.flip();
        } else if (format == 3 && bits == 32) {
            int samples = dataLen / 4;
            pcm = MemoryUtil.memAllocShort(samples);
            ByteBuffer bb = ByteBuffer.wrap(data, dataStart, dataLen)
                .order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samples; i++) {
                float f = bb.getFloat();
                if (f > 1.0f) f = 1.0f;
                if (f < -1.0f) f = -1.0f;
                pcm.put((short) (f * 32767.0f));
            }
            pcm.flip();
        } else {
            System.err.println("SoundLoader: unsupported WAV format "
                + format + "/" + bits + ": " + path);
            return 0;
        }

        return makeBuffer(pcm, channels, sampleRate);
    }

    private static int LE16(byte[] d, int i) {
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8);
    }

    private static int LE32(byte[] d, int i) {
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8)
            | ((d[i + 2] & 0xFF) << 16) | ((d[i + 3] & 0xFF) << 24);
    }

    // ------------------------------------------------------------------

    /** Upload interleaved 16-bit PCM to OpenAL and free the buffer. */
    private static int makeBuffer(ShortBuffer pcm, int channels, int sampleRate) {
        int bufferId = alGenBuffers();
        alBufferData(bufferId,
            channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16,
            pcm, sampleRate);
        MemoryUtil.memFree(pcm);
        return bufferId;
    }

    /** Release every cached buffer; called once at shutdown. */
    public static void cleanup() {
        for (int[] buffers : cache.values()) {
            if (buffers.length > 0) alDeleteBuffers(buffers);
        }
        cache.clear();
    }
}
