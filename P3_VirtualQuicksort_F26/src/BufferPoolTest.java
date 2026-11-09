import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import student.TestCase;

/**
 * Tests the buffer pool directly: its LRU replacement policy, its statistics,
 * and its handling of dirty blocks and of requests that span blocks.
 *
 * @author {Your Name Here}
 * @version 2026-11-08
 */
public class BufferPoolTest extends TestCase {

    /** The file that the pool under test uses. */
    private static final String POOL_FILE = "bufferPoolTestData.bin";

    /** The number of bytes in a block. */
    private static final int BLOCK = BufferPool.BLOCK_BYTES;

    /** The number of blocks in the test file. */
    private static final int NUM_BLOCKS = 3;

    /** The number of bytes to request in most tests. */
    private static final int REQUEST_BYTES = 2;

    /** Receives bytes from the pool and supplies bytes to it. */
    private byte[] space;

    /** The pool under test, closed after each test. */
    private BufferPool pool;

    /**
     * Creates a test file in which each byte identifies its block, and clears
     * the transfer space.
     *
     * @throws Exception
     *             If the setup fails.
     */
    public void setUp() throws Exception {
        super.setUp();
        byte[] contents = new byte[NUM_BLOCKS * BLOCK]; // The whole test file
        for (int block = 0; block < NUM_BLOCKS; block++) {
            Arrays.fill(contents, block * BLOCK, (block + 1) * BLOCK,
                (byte)block);
        }
        Files.write(new File(POOL_FILE).toPath(), contents);
        space = new byte[BLOCK];
    }


    /**
     * Closes the pool and removes the test file.
     *
     * @throws Exception
     *             If the cleanup fails.
     */
    public void tearDown() throws Exception {
        pool.close();
        new File(POOL_FILE).delete();
        super.tearDown();
    }


    /**
     * Tests that reads return the file's contents and count hits and misses.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testHitsAndReads() throws IOException {
        pool = new BufferPool(POOL_FILE, 1);
        pool.getBytes(space, REQUEST_BYTES, BLOCK);
        assertEquals(1, space[0]);
        assertEquals(1, pool.getDiskReads());
        assertEquals(0, pool.getCacheHits());

        pool.getBytes(space, REQUEST_BYTES, BLOCK + BLOCK - REQUEST_BYTES);
        assertEquals(1, pool.getDiskReads());
        assertEquals(1, pool.getCacheHits());
        assertEquals(0, pool.getDiskWrites());
    }


    /**
     * Tests that the least recently used block is evicted, rather than the
     * block that was loaded first.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testLeastRecentlyUsedIsEvicted() throws IOException {
        pool = new BufferPool(POOL_FILE, 2);
        pool.getBytes(space, REQUEST_BYTES, 0); // Load block 0
        pool.getBytes(space, REQUEST_BYTES, BLOCK); // Load block 1
        pool.getBytes(space, REQUEST_BYTES, 0); // Block 1 becomes the LRU
        assertEquals(2, pool.getDiskReads());
        assertEquals(1, pool.getCacheHits());

        pool.getBytes(space, REQUEST_BYTES, 2L * BLOCK); // Evicts block 1
        assertEquals(3, pool.getDiskReads());

        pool.getBytes(space, REQUEST_BYTES, 0); // Block 0 is still resident
        assertEquals(3, pool.getDiskReads());
        assertEquals(2, pool.getCacheHits());

        pool.getBytes(space, REQUEST_BYTES, BLOCK); // Block 1 was evicted
        assertEquals(4, pool.getDiskReads());
    }


    /**
     * Tests that a modified block is written only when it is evicted, that a
     * clean block is not written, and that the data survives the round trip.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testDirtyBlockWrittenOnEviction() throws IOException {
        pool = new BufferPool(POOL_FILE, 1);
        space[0] = 99;
        pool.putBytes(space, 1, BLOCK);
        assertEquals(0, pool.getDiskWrites());

        pool.getBytes(space, REQUEST_BYTES, 0); // Evicts the dirty block
        assertEquals(1, pool.getDiskWrites());

        pool.getBytes(space, REQUEST_BYTES, 2L * BLOCK); // Evicts a clean one
        assertEquals(1, pool.getDiskWrites());

        pool.getBytes(space, 1, BLOCK); // The change comes back from the disk
        assertEquals(99, space[0]);
    }


    /**
     * Tests that a modified block can be read back before reaching the disk.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testReadSeesPendingWrite() throws IOException {
        pool = new BufferPool(POOL_FILE, 2);
        space[0] = 42;
        pool.putBytes(space, 1, 0);
        space[0] = 0;
        pool.getBytes(space, 1, 0);
        assertEquals(42, space[0]);
        assertEquals(0, pool.getDiskWrites());
    }


    /**
     * Tests that flushing saves each modified block once and keeps it in the
     * pool, and that closing leaves the changes in the file.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testFlushAndClose() throws IOException {
        pool = new BufferPool(POOL_FILE, 3);
        space[0] = 77;
        pool.putBytes(space, 1, 0);
        pool.putBytes(space, 1, BLOCK);
        pool.getBytes(space, 1, 2L * BLOCK); // A clean block is not written

        pool.flush();
        assertEquals(2, pool.getDiskWrites());
        assertEquals(3, pool.getDiskReads());
        pool.flush();
        assertEquals(2, pool.getDiskWrites());
        pool.getBytes(space, 1, 0);
        assertEquals(3, pool.getDiskReads()); // Still resident after a flush

        space[0] = 88;
        pool.putBytes(space, 1, 2L * BLOCK);
        pool.close();
        assertEquals(3, pool.getDiskWrites());
        byte[] saved = Files.readAllBytes(new File(POOL_FILE).toPath());
        assertEquals(77, saved[0]);
        assertEquals(77, saved[BLOCK]);
        assertEquals(88, saved[2 * BLOCK]);
    }


    /**
     * Tests requests that cross a block boundary in both directions.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testRequestSpanningBlocks() throws IOException {
        pool = new BufferPool(POOL_FILE, 2);
        pool.getBytes(space, REQUEST_BYTES, BLOCK - 1);
        assertEquals(0, space[0]);
        assertEquals(1, space[1]);
        assertEquals(2, pool.getDiskReads());

        space[0] = 50;
        space[1] = 60;
        pool.putBytes(space, REQUEST_BYTES, BLOCK - 1);
        pool.close();
        assertEquals(2, pool.getDiskWrites());
        byte[] saved = Files.readAllBytes(new File(POOL_FILE).toPath());
        assertEquals(50, saved[BLOCK - 1]);
        assertEquals(60, saved[BLOCK]);
    }


    /**
     * Tests that a short final block neither fails to load nor grows the file.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testShortFinalBlock() throws IOException {
        byte[] shortFile = { 1, 2, 3, 4 }; // Less than one block of data
        Files.write(new File(POOL_FILE).toPath(), shortFile);
        pool = new BufferPool(POOL_FILE, 1);
        pool.getBytes(space, REQUEST_BYTES, REQUEST_BYTES);
        assertEquals(3, space[0]);
        pool.putBytes(space, 1, 0);
        pool.close();
        assertEquals(shortFile.length, new File(POOL_FILE).length());
    }
}
