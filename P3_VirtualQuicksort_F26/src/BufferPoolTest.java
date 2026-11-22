import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import student.TestCase;

/**
 * Tests the buffer pool directly: its LRU replacement policy, its statistics,
 * and its handling of dirty blocks and of requests that span blocks.
 *
 * @author Jai Sundaram, Yaswanth Uppalapati
 * @version 2026-11-22
 */
public class BufferPoolTest extends TestCase {

    /** The file that the pool under test uses. */
    private static final String POOL_FILE = "bufferPoolTestData.bin";

    /** The number of bytes in a block. */
    private static final int BLOCK = BufferPool.BLOCK_BYTES;

    /** The number of blocks in the test file. */
    private static final int NUM_BLOCKS = 3;

    /** The byte position where the second block (block 1) starts. */
    private static final int BLOCK_ONE_START = BLOCK;

    /** The byte position where the third block (block 2) starts. */
    private static final int BLOCK_TWO_START = 2 * BLOCK;

    /** The number of bytes to request in most tests. */
    private static final int REQUEST_BYTES = 2;

    /** A pool size that holds only one block. */
    private static final int ONE_BUFFER = 1;

    /** A pool size that holds two of the file's blocks. */
    private static final int TWO_BUFFERS = 2;

    /** A pool size that holds every block of the file. */
    private static final int ALL_BUFFERS = NUM_BLOCKS;

    /** A byte written to a block that will be evicted. */
    private static final byte EVICTED_MARK = 99;

    /** A byte written to a block and read back before it reaches the disk. */
    private static final byte PENDING_MARK = 42;

    /** A byte written before the pool is flushed. */
    private static final byte FLUSHED_MARK = 77;

    /** A byte written before the pool is closed. */
    private static final byte CLOSED_MARK = 88;

    /** The byte written at the end of a block by a spanning request. */
    private static final byte SPAN_FIRST_MARK = 50;

    /** The byte written at the start of a block by a spanning request. */
    private static final byte SPAN_SECOND_MARK = 60;

    /** The contents of a file that is shorter than a block. */
    private static final byte[] SHORT_FILE = { 1, 2, 3, 4 };

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
        // block is the id of the block being filled
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
        pool = new BufferPool(POOL_FILE, ONE_BUFFER);
        pool.getBytes(space, REQUEST_BYTES, BLOCK_ONE_START);
        assertEquals(1, space[0]);
        assertEquals(1, pool.getDiskReads());
        assertEquals(0, pool.getCacheHits());

        // The last bytes of the same block
        pool.getBytes(space, REQUEST_BYTES, BLOCK_TWO_START - REQUEST_BYTES);
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
        pool = new BufferPool(POOL_FILE, TWO_BUFFERS);
        pool.getBytes(space, REQUEST_BYTES, 0); // Load block 0
        pool.getBytes(space, REQUEST_BYTES, BLOCK_ONE_START); // Load block 1
        pool.getBytes(space, REQUEST_BYTES, 0); // Block 1 becomes the LRU
        assertEquals(2, pool.getDiskReads());
        assertEquals(1, pool.getCacheHits());

        pool.getBytes(space, REQUEST_BYTES, BLOCK_TWO_START); // Evicts block 1
        assertEquals(3, pool.getDiskReads());

        pool.getBytes(space, REQUEST_BYTES, 0); // Block 0 is still resident
        assertEquals(3, pool.getDiskReads());
        assertEquals(2, pool.getCacheHits());

        pool.getBytes(space, REQUEST_BYTES, BLOCK_ONE_START); // Was evicted
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
        pool = new BufferPool(POOL_FILE, ONE_BUFFER);
        space[0] = EVICTED_MARK;
        pool.putBytes(space, 1, BLOCK_ONE_START);
        assertEquals(0, pool.getDiskWrites());

        pool.getBytes(space, REQUEST_BYTES, 0); // Evicts the dirty block
        assertEquals(1, pool.getDiskWrites());

        pool.getBytes(space, REQUEST_BYTES, BLOCK_TWO_START); // Evicts a clean
        assertEquals(1, pool.getDiskWrites());

        pool.getBytes(space, 1, BLOCK_ONE_START); // The change comes back
        assertEquals(EVICTED_MARK, space[0]);
    }


    /**
     * Tests that a modified block can be read back before reaching the disk.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testReadSeesPendingWrite() throws IOException {
        pool = new BufferPool(POOL_FILE, TWO_BUFFERS);
        space[0] = PENDING_MARK;
        pool.putBytes(space, 1, 0);
        space[0] = 0;
        pool.getBytes(space, 1, 0);
        assertEquals(PENDING_MARK, space[0]);
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
        pool = new BufferPool(POOL_FILE, ALL_BUFFERS);
        space[0] = FLUSHED_MARK;
        pool.putBytes(space, 1, 0);
        pool.putBytes(space, 1, BLOCK_ONE_START);
        pool.getBytes(space, 1, BLOCK_TWO_START); // A clean block isn't written

        pool.flush();
        assertEquals(2, pool.getDiskWrites());
        assertEquals(3, pool.getDiskReads());
        pool.flush();
        assertEquals(2, pool.getDiskWrites());
        pool.getBytes(space, 1, 0);
        assertEquals(3, pool.getDiskReads()); // Still resident after a flush

        space[0] = CLOSED_MARK;
        pool.putBytes(space, 1, BLOCK_TWO_START);
        pool.close();
        assertEquals(3, pool.getDiskWrites());
        // The file's contents after the pool is closed
        byte[] saved = Files.readAllBytes(new File(POOL_FILE).toPath());
        assertEquals(FLUSHED_MARK, saved[0]);
        assertEquals(FLUSHED_MARK, saved[BLOCK_ONE_START]);
        assertEquals(CLOSED_MARK, saved[BLOCK_TWO_START]);
    }


    /**
     * Tests requests that cross a block boundary in both directions.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testRequestSpanningBlocks() throws IOException {
        pool = new BufferPool(POOL_FILE, TWO_BUFFERS);
        pool.getBytes(space, REQUEST_BYTES, BLOCK_ONE_START - 1);
        assertEquals(0, space[0]);
        assertEquals(1, space[1]);
        assertEquals(2, pool.getDiskReads());

        space[0] = SPAN_FIRST_MARK;
        space[1] = SPAN_SECOND_MARK;
        pool.putBytes(space, REQUEST_BYTES, BLOCK_ONE_START - 1);
        pool.close();
        assertEquals(2, pool.getDiskWrites());
        // The file's contents after the pool is closed
        byte[] saved = Files.readAllBytes(new File(POOL_FILE).toPath());
        assertEquals(SPAN_FIRST_MARK, saved[BLOCK_ONE_START - 1]);
        assertEquals(SPAN_SECOND_MARK, saved[BLOCK_ONE_START]);
    }


    /**
     * Tests that a short final block neither fails to load nor grows the file.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testShortFinalBlock() throws IOException {
        Files.write(new File(POOL_FILE).toPath(), SHORT_FILE);
        pool = new BufferPool(POOL_FILE, ONE_BUFFER);
        pool.getBytes(space, REQUEST_BYTES, REQUEST_BYTES);
        assertEquals(SHORT_FILE[REQUEST_BYTES], space[0]);
        pool.putBytes(space, 1, 0);
        pool.close();
        assertEquals(SHORT_FILE.length, new File(POOL_FILE).length());
    }
}
