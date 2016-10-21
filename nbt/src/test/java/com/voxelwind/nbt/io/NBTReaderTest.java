package com.voxelwind.nbt.io;

import com.voxelwind.nbt.tags.CompoundTag;
import com.voxelwind.nbt.tags.StringTag;
import com.voxelwind.nbt.tags.Tag;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class NBTReaderTest {
    @Test
    public void readHelloWorld() throws IOException {
        String path = getClass().getClassLoader().getResource("hello_world.nbt").getFile();
        if (isWindows()) {
            path = path.substring(1);
        }

        Tag readTag;
        try (NBTReader reader = NBTReaders.createBigEndianReader(Files.newInputStream(Paths.get(path)))) {
            readTag = reader.readTag();
        }

        assertTrue("Read tag is not an compound tag, violates NBT standard!", readTag instanceof CompoundTag);
        CompoundTag compoundTag = (CompoundTag) readTag;
        assertEquals("hello world", compoundTag.getName());
        assertTrue("Read compound tag lacks named tag 'name'", compoundTag.getValue().containsKey("name"));
        Tag<?> nameTagRaw = compoundTag.getValue().get("name");
        assertTrue("'name' tag inside read compound is not a TAG_String", nameTagRaw instanceof StringTag);
        assertEquals("Bananrama", nameTagRaw.getValue());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}