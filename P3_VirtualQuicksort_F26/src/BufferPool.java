import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * An LRU buffer pool that mediates all access to a binary file. It uses the
 * "message passing" style: clients ask for a number of bytes at a byte
 * position in the file, and the bytes are copied to or from the client's own
 * byte array. The pool knows nothing about records or keys.
 *
 * Disk writes are deferred until a dirty block is evicted or flushed, so a
 * block that is never modified is never written.
 *
 * @author {Your Name Here}
 * @version 2026-11-08
 */
public class BufferPool implements Closeable {

    /** The number of bytes in one block of the file. */
    public static final int BLOCK_BYTES = 4096;

    /** The block id held by a buffer that does not contain any block yet. */
    private static final int NO_BLOCK = -1;

    /** The file mode that allows both reading and writing. */
    private static final String READ_WRITE_MODE = "rw";

    /** The file holding the data that this pool caches. */
    private final RandomAccessFile file;

    /** The length of the file in bytes, used to size the final block. */
    private final long fileBytes;

    /** Maps a block id to the buffer holding it, or null if not in the pool. */
    private final Buffer[] residentBlocks;

    /**
     * The sentinel of a circular doubly linked list of all buffers. The
     * buffer after it is the most recently used and the buffer before it is
     * the least recently used.
     */
    private final Buffer lruList;

    /** The number of requests satisfied without going to the disk. */
    private long cacheHits;

    /** The number of blocks read from the disk. */
    private long diskReads;

    /** The number of blocks written to the disk. */
    private long diskWrites;

    /**
     * Opens a file and creates a pool for it. All buffers start out empty.
     *
     * @param fileName
     *            The name of the file to be accessed through the pool.
     * @param numBuffers
     *            The number of block-sized buffers to allocate.
     * @throws IOException
     *             If the file cannot be opened.
     */
    public BufferPool(String fileName, int numBuffers) throws IOException {
        file = new RandomAccessFile(fileName, READ_WRITE_MODE);
        fileBytes = file.length();
        // Round up so that a partial final block still gets an entry.
        residentBlocks = new Buffer[(int)((fileBytes + BLOCK_BYTES - 1)
            / BLOCK_BYTES)];
        lruList = new Buffer();
        lruList.next = lruList;
        lruList.previous = lruList;
        for (int i = 0; i < numBuffers; i++) {
            linkAtFront(new Buffer());
        }
    }


    /**
     * Copies bytes from the file (through the pool) into a client's array.
     * The requested range must lie within the file.
     *
     * @param destination
     *            The array that receives the bytes, starting at index 0.
     * @param numBytes
     *            The number of bytes to copy.
     * @param position
     *            The byte position in the file of the first byte to copy.
     * @throws IOException
     *             If the disk access fails.
     */
    public void getBytes(byte[] destination, int numBytes, long position)
        throws IOException {
        int copied = 0; // How many bytes of the request have been handled
        while (copied < numBytes) {
            long current = position + copied; // Next file position to copy
            int offset = (int)(current % BLOCK_BYTES); // Position in block
            // The request may continue into the next block
            int chunk = Math.min(numBytes - copied, BLOCK_BYTES - offset);
            Buffer buffer = acquire((int)(current / BLOCK_BYTES));
            System.arraycopy(buffer.data, offset, destination, copied, chunk);
            copied += chunk;
        }
    }


    /**
     * Copies bytes from a client's array into the file (through the pool).
     * The requested range must lie within the file.
     *
     * @param source
     *            The array that supplies the bytes, starting at index 0.
     * @param numBytes
     *            The number of bytes to copy.
     * @param position
     *            The byte position in the file of the first byte to replace.
     * @throws IOException
     *             If the disk access fails.
     */
    public void putBytes(byte[] source, int numBytes, long position)
        throws IOException {
        int copied = 0; // How many bytes of the request have been handled
        while (copied < numBytes) {
            long current = position + copied; // Next file position to copy
            int offset = (int)(current % BLOCK_BYTES); // Position in block
            // The request may continue into the next block
            int chunk = Math.min(numBytes - copied, BLOCK_BYTES - offset);
            Buffer buffer = acquire((int)(current / BLOCK_BYTES));
            System.arraycopy(source, copied, buffer.data, offset, chunk);
            buffer.dirty = true;
            copied += chunk;
        }
    }


    /**
     * Writes every modified block to the disk. The blocks remain in the pool.
     *
     * @throws IOException
     *             If the disk access fails.
     */
    public void flush() throws IOException {
        Buffer buffer = lruList.next; // The buffer currently being examined
        while (buffer != lruList) {
            writeIfDirty(buffer);
            buffer = buffer.next;
        }
    }


    /**
     * Flushes the pool and closes the file. The statistics remain available.
     *
     * @throws IOException
     *             If the disk access fails.
     */
    @Override
    public void close() throws IOException {
        try {
            flush();
        }
        finally {
            file.close();
        }
    }


    /**
     * Gets the number of requests satisfied without going to the disk.
     *
     * @return The number of cache hits so far.
     */
    public long getCacheHits() {
        return cacheHits;
    }


    /**
     * Gets the number of blocks read from the disk.
     *
     * @return The number of disk reads so far.
     */
    public long getDiskReads() {
        return diskReads;
    }


    /**
     * Gets the number of blocks written to the disk.
     *
     * @return The number of disk writes so far.
     */
    public long getDiskWrites() {
        return diskWrites;
    }


    /**
     * Finds the buffer for a block, loading the block if needed, and marks
     * that buffer as the most recently used.
     *
     * @param blockId
     *            The index of the block within the file.
     * @return The buffer that holds the block.
     * @throws IOException
     *             If the disk access fails.
     */
    private Buffer acquire(int blockId) throws IOException {
        Buffer buffer = residentBlocks[blockId]; // The block's current buffer
        if (buffer == null) {
            buffer = load(blockId);
        }
        else {
            cacheHits++;
        }
        unlink(buffer);
        linkAtFront(buffer);
        return buffer;
    }


    /**
     * Evicts the least recently used buffer and reads a block into it.
     *
     * @param blockId
     *            The index of the block within the file.
     * @return The buffer that now holds the block.
     * @throws IOException
     *             If the disk access fails.
     */
    private Buffer load(int blockId) throws IOException {
        Buffer victim = lruList.previous; // The least recently used buffer
        writeIfDirty(victim);
        if (victim.blockId != NO_BLOCK) {
            residentBlocks[victim.blockId] = null;
        }
        file.seek(startOf(blockId));
        file.readFully(victim.data, 0, lengthOf(blockId));
        diskReads++;
        victim.blockId = blockId;
        residentBlocks[blockId] = victim;
        return victim;
    }


    /**
     * Writes a buffer's block to the disk if it has unsaved changes.
     *
     * @param buffer
     *            The buffer to save.
     * @throws IOException
     *             If the disk access fails.
     */
    private void writeIfDirty(Buffer buffer) throws IOException {
        if (buffer.dirty) {
            file.seek(startOf(buffer.blockId));
            file.write(buffer.data, 0, lengthOf(buffer.blockId));
            diskWrites++;
            buffer.dirty = false;
        }
    }


    /**
     * Calculates the byte position in the file where a block begins.
     *
     * @param blockId
     *            The index of the block within the file.
     * @return The position of the block's first byte.
     */
    private long startOf(int blockId) {
        return (long)blockId * BLOCK_BYTES;
    }


    /**
     * Calculates the length of a block. Only a final block of a file whose
     * size is not a multiple of the block size can be short.
     *
     * @param blockId
     *            The index of the block within the file.
     * @return The number of bytes of the file stored in the block.
     */
    private int lengthOf(int blockId) {
        return (int)Math.min(BLOCK_BYTES, fileBytes - startOf(blockId));
    }


    /**
     * Removes a buffer from the LRU list.
     *
     * @param buffer
     *            The buffer to remove.
     */
    private void unlink(Buffer buffer) {
        buffer.previous.next = buffer.next;
        buffer.next.previous = buffer.previous;
    }


    /**
     * Inserts a buffer at the most recently used end of the LRU list.
     *
     * @param buffer
     *            The buffer to insert.
     */
    private void linkAtFront(Buffer buffer) {
        buffer.previous = lruList;
        buffer.next = lruList.next;
        lruList.next.previous = buffer;
        lruList.next = buffer;
    }


    /**
     * One block-sized buffer, which is also a node in the LRU list. The fields
     * are only used by the enclosing pool.
     */
    private static class Buffer {
        /** The contents of the block held by this buffer. */
        private final byte[] data = new byte[BLOCK_BYTES];

        /** The id of the block held by this buffer, or NO_BLOCK. */
        private int blockId = NO_BLOCK;

        /** Whether the data has changes that are not yet on the disk. */
        private boolean dirty;

        /** The neighbor on the most recently used side of this buffer. */
        private Buffer previous;

        /** The neighbor on the least recently used side of this buffer. */
        private Buffer next;
    }
}
