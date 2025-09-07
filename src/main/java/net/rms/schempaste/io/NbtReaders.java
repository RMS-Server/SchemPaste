package net.rms.schempaste.io;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.tukaani.xz.LZMAInputStream;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;


public final class NbtReaders {
    private NbtReaders() {
    }

    public static NbtCompound readCompressedAuto(Path file) throws IOException {

        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            return NbtIo.readCompressed(is);
        } catch (IOException ignored) {

        }


        try (InputStream base = new BufferedInputStream(Files.newInputStream(file)); LZMAInputStream lzma = new LZMAInputStream(base); java.io.DataInputStream dis = new java.io.DataInputStream(lzma)) {
            return NbtIo.read(dis);
        } catch (EOFException e) {
            throw e;
        } catch (IOException e) {

            try (InputStream raw = new BufferedInputStream(Files.newInputStream(file)); java.io.DataInputStream dis = new java.io.DataInputStream(raw)) {
                return NbtIo.read(dis);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }
}
