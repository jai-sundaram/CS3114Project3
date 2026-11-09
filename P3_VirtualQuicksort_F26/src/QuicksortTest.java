import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import student.TestCase;

/**
 * Tests the program from the command line entry point, including sort
 * correctness on several input shapes, statistics, and argument handling.
 *
 * @author {Your Name Here}
 * @version 2026-11-08
 */
public class QuicksortTest extends TestCase {

    /** A data file that each test may create. */
    private static final String DATA_FILE = "quicksortTestData.bin";

    /** A statistics file that each test may create. */
    private static final String STAT_FILE = "quicksortTestStats.txt";

    /** The number of records in a block. */
    private static final int BLOCK_RECORDS = FileGenerator.RECORDS_PER_BLOCK;

    /** The number of lines the program writes for each run. */
    private static final int STAT_LINES_PER_RUN = 5;

    /** The largest number of buffers the program allows. */
    private static final String MOST_BUFFERS = "20";

    /**
     * Sets up the tests that follow. In general, used for initialization.
     *
     * @throws Exception
     *             If the superclass setup fails.
     */
    public void setUp() throws Exception {
        super.setUp();
        systemOut().clearHistory();
    }


    /**
     * Removes the files created by a test.
     *
     * @throws Exception
     *             If the superclass cleanup fails.
     */
    public void tearDown() throws Exception {
        new File(DATA_FILE).delete();
        new File(STAT_FILE).delete();
        new File("stats.txt").delete();
        super.tearDown();
    }


    /**
     * Tests that the file generator makes files of the expected size and
     * contents.
     *
     * @throws IOException
     *             If a file access fails.
     */
    public void testFileGen() throws IOException {
        String fname = "threeBlock.txt"; // The file to generate
        int blocks = 3; // The number of blocks to generate
        FileGenerator fg = new FileGenerator(fname, blocks);
        fg.setSeed(33333333); // Non-random, to make generation deterministic
        fg.generateFile(FileType.ASCII);

        File f = new File(fname);
        long fileNumBytes = f.length();
        long calcedBytes = blocks * FileGenerator.BYTES_PER_BLOCK;
        assertEquals(calcedBytes, fileNumBytes); // size is correct!

        RandomAccessFile raf = new RandomAccessFile(f, "r");
        short firstKey = raf.readShort(); // reads two bytes
        assertEquals(8273, firstKey); // first key looks like ' Q'

        raf.seek(8); // moves to byte 8, which is beginning of third record
        short thirdKey = raf.readShort();
        assertEquals(8261, thirdKey); // third key looks like ' E'

        raf.close();
    }


    /**
     * Tests that the file checker tells sorted and unsorted files apart, and
     * that sorting an unsorted ASCII file satisfies it.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testCheckFile() throws Exception {
        assertTrue(CheckFile.check("tinySorted.txt"));

        String fname = "checkme.txt"; // The file to generate
        FileGenerator fg = new FileGenerator(fname, 1);
        fg.setSeed(42);
        fg.generateFile(FileType.ASCII);
        // Notice we *re-generate* this file each time the test runs.
        // That file persists after the test is over

        assertFalse(CheckFile.check(fname));

        Quicksort.main(new String[] { fname, "1", STAT_FILE });
        assertTrue(CheckFile.check(fname));
    }


    /**
     * Tests the program on a generated one block binary file.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testSorting() throws Exception {
        String fname = "input.bin"; // The file to generate
        FileGenerator fg = new FileGenerator(fname, 1);
        fg.generateFile(FileType.BINARY);

        assertFalse(CheckFile.check(fname)); // file shouldn't be sorted

        String[] args = new String[3];
        args[0] = fname; // the file to be sorted.
        args[1] = "1"; // number of buffers, can impact performance
        args[2] = "stats.txt"; // filename for sorting stats
        Quicksort.main(args);

        assertTrue(CheckFile.check(fname));
    }


    /**
     * Tests random binary files over a range of file and pool sizes, checking
     * that the file ends up sorted with the same records it started with.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testRandomBinaryFiles() throws Exception {
        int[][] configurations = { { 1, 2 }, { 2, 1 }, { 5, 1 }, { 5, 3 }, {
            8, 20 }, { 20, 2 } }; // Pairs of blocks and buffers
        for (int[] configuration : configurations) {
            FileGenerator generator = new FileGenerator(DATA_FILE,
                configuration[0]);
            generator.setSeed(configuration[0] * 31L + configuration[1]);
            generator.generateFile(FileType.BINARY);
            assertSortsCorrectly(String.valueOf(configuration[1]));
        }
    }


    /**
     * Tests a file with few distinct keys.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testAsciiFile() throws Exception {
        FileGenerator generator = new FileGenerator(DATA_FILE, 10);
        generator.setSeed(7);
        generator.generateFile(FileType.ASCII);
        assertSortsCorrectly("3");
    }


    /**
     * Tests a file in which every key is the same. Equal keys are never
     * swapped, so the file should not be written at all.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testAllEqualKeys() throws Exception {
        int[] keys = new int[5 * BLOCK_RECORDS]; // All keys in the file
        Arrays.fill(keys, 1234);
        writeKeys(keys);
        assertSortsCorrectly(MOST_BUFFERS);
        assertEquals("Disk Writes: 0", statLines().get(3));
    }


    /**
     * Tests a file that is already sorted, which should not be written at all.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testAlreadySorted() throws Exception {
        int[] keys = new int[5 * BLOCK_RECORDS]; // All keys in the file
        for (int i = 0; i < keys.length; i++) {
            keys[i] = i + 1;
        }
        writeKeys(keys);
        assertSortsCorrectly(MOST_BUFFERS);
        assertEquals("Disk Writes: 0", statLines().get(3));
    }


    /**
     * Tests a file in descending order.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testReverseSorted() throws Exception {
        int[] keys = new int[5 * BLOCK_RECORDS]; // All keys in the file
        for (int i = 0; i < keys.length; i++) {
            keys[i] = keys.length - i;
        }
        writeKeys(keys);
        assertSortsCorrectly("2");
    }


    /**
     * Tests a file whose keys rise and then fall, a known hazard for a
     * median-of-three Quicksort with fixed sample positions.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testOrganPipe() throws Exception {
        int[] keys = new int[5 * BLOCK_RECORDS]; // All keys in the file
        for (int i = 0; i < keys.length; i++) {
            keys[i] = Math.min(i, keys.length - i) + 1;
        }
        writeKeys(keys);
        assertSortsCorrectly("4");
    }


    /**
     * Tests the smallest possible ranges: files with zero records and with a
     * single record, neither of which should be changed.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testTrivialFiles() throws Exception {
        writeKeys(new int[0]);
        assertSortsCorrectly("1");
        assertEquals(0, new File(DATA_FILE).length());

        writeKeys(new int[] { 5 });
        assertSortsCorrectly("1");
        assertEquals(VirtualQuicksort.RECORD_BYTES, new File(DATA_FILE)
            .length());
    }


    /**
     * Tests that each run appends all of its statistics to the file rather
     * than replacing the earlier ones.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testStatsAreAppended() throws Exception {
        FileGenerator generator = new FileGenerator(DATA_FILE, 1);
        generator.setSeed(11);
        generator.generateFile(FileType.BINARY);

        Quicksort.main(new String[] { DATA_FILE, "1", STAT_FILE });
        Quicksort.main(new String[] { DATA_FILE, "1", STAT_FILE });

        List<String> lines = statLines(); // Everything the two runs wrote
        assertEquals(2 * STAT_LINES_PER_RUN, lines.size());
        assertEquals("Standard sort on " + DATA_FILE, lines.get(0));
        assertTrue(lines.get(1).startsWith("Cache Hits: "));
        assertEquals("Disk Reads: 1", lines.get(2));
        assertEquals("Disk Writes: 1", lines.get(3));
        assertTrue(lines.get(4).matches("Sort Time: \\d+ ms"));
        // The second run starts with a sorted file, so it writes nothing.
        assertEquals("Standard sort on " + DATA_FILE, lines.get(
            STAT_LINES_PER_RUN));
        assertEquals("Disk Writes: 0", lines.get(STAT_LINES_PER_RUN + 3));
    }


    /**
     * Tests that a wrong number of arguments gets a usage message.
     */
    public void testWrongArgumentCount() {
        Quicksort.main(new String[] { DATA_FILE });
        assertTrue(systemOut().getHistory().startsWith("Usage:"));
    }


    /**
     * Tests that an invalid buffer count is rejected without creating output.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testInvalidBufferCounts() throws Exception {
        writeKeys(new int[] { 2, 1 });
        String[] badCounts = { "0", "21", "many" }; // Values to reject
        for (String count : badCounts) {
            systemOut().clearHistory();
            Quicksort.main(new String[] { DATA_FILE, count, STAT_FILE });
            assertTrue(systemOut().getHistory().contains(
                "The number of buffers must be"));
            assertFalse(CheckFile.check(DATA_FILE));
            assertFalse(new File(STAT_FILE).exists());
        }
    }


    /**
     * Tests that a missing data file is reported and is not created.
     */
    public void testMissingDataFile() {
        Quicksort.main(new String[] { DATA_FILE, "1", STAT_FILE });
        assertTrue(systemOut().getHistory().contains("Data file not found"));
        assertFalse(new File(DATA_FILE).exists());
        assertFalse(new File(STAT_FILE).exists());
    }


    /**
     * Tests that a statistics file that cannot be written is reported, and
     * that the data file was still sorted.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testUnwritableStatFile() throws Exception {
        writeKeys(new int[] { 2, 1 });
        Quicksort.main(new String[] { DATA_FILE, "1", "." });
        assertTrue(systemOut().getHistory().contains("I/O error"));
        assertTrue(CheckFile.check(DATA_FILE));
    }


    /**
     * Writes the data file using the given keys. Each record's value is
     * derived from its position, so lost or mixed up records can be detected.
     *
     * @param keys
     *            The key of each record, in file order.
     * @throws IOException
     *             If the file cannot be written.
     */
    private void writeKeys(int[] keys) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(DATA_FILE)))) {
            for (int i = 0; i < keys.length; i++) {
                out.writeShort(keys[i]);
                out.writeShort(i % 30000 + 1);
            }
        }
    }


    /**
     * Reads the data file as an order-independent summary of its records.
     *
     * @return All of the records as big-endian integers in sorted order.
     * @throws IOException
     *             If the file cannot be read.
     */
    private int[] readSortedRecords() throws IOException {
        int count = (int)(new File(DATA_FILE).length()
            / VirtualQuicksort.RECORD_BYTES); // The number of records
        int[] records = new int[count];
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
            new FileInputStream(DATA_FILE)))) {
            for (int i = 0; i < count; i++) {
                records[i] = in.readInt();
            }
        }
        Arrays.sort(records);
        return records;
    }


    /**
     * Runs the program on the data file and checks that the result is sorted
     * by key and is a permutation of the original records.
     *
     * @param numBuffers
     *            The number of buffers to run with.
     * @throws Exception
     *             If a file access fails.
     */
    private void assertSortsCorrectly(String numBuffers) throws Exception {
        int[] expected = readSortedRecords(); // The original records
        new File(STAT_FILE).delete();
        Quicksort.main(new String[] { DATA_FILE, numBuffers, STAT_FILE });
        assertTrue(CheckFile.check(DATA_FILE));
        assertTrue(Arrays.equals(expected, readSortedRecords()));
    }


    /**
     * Reads the statistics file.
     *
     * @return The lines of the statistics file.
     * @throws IOException
     *             If the file cannot be read.
     */
    private List<String> statLines() throws IOException {
        return Files.readAllLines(new File(STAT_FILE).toPath());
    }
}
