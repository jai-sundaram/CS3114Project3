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
 * @author Jai Sundaram, Yaswanth Uppalapati
 * @version 2026-11-22
 */
public class QuicksortTest extends TestCase {

    /** A data file that each test may create. */
    private static final String DATA_FILE = "quicksortTestData.bin";

    /** A statistics file that each test may create. */
    private static final String STAT_FILE = "quicksortTestStats.txt";

    /** The statistics file used by the sample sorting test. */
    private static final String SAMPLE_STAT_FILE = "stats.txt";

    /** The three block ASCII file made by the file generator test. */
    private static final String GENERATED_FILE = "threeBlock.txt";

    /** A small file that is already sorted. */
    private static final String SORTED_FILE = "tinySorted.txt";

    /** The one block ASCII file made by the file checker test. */
    private static final String CHECKED_FILE = "checkme.txt";

    /** The one block binary file made by the sample sorting test. */
    private static final String SAMPLE_FILE = "input.bin";

    /** The mode for opening a file only for reading. */
    private static final String READ_ONLY = "r";

    /** A path that names a directory, so it cannot be written as a file. */
    private static final String UNWRITABLE_FILE = ".";

    /** The number of blocks made by the file generator test. */
    private static final int GENERATED_BLOCKS = 3;

    /** The seed that makes the file generator test deterministic. */
    private static final long GENERATED_SEED = 33333333;

    /** The first key in the generated file, which looks like ' Q'. */
    private static final short GENERATED_FIRST_KEY = 8273;

    /** The byte position of the generated file's third record. */
    private static final int THIRD_RECORD_POSITION =
        2 * VirtualQuicksort.RECORD_BYTES;

    /** The third key in the generated file, which looks like ' E'. */
    private static final short GENERATED_THIRD_KEY = 8261;

    /** The seed that makes the file checker test deterministic. */
    private static final long CHECKED_SEED = 42;

    /** The number of records in a block. */
    private static final int BLOCK_RECORDS = FileGenerator.RECORDS_PER_BLOCK;

    /** The number of records in the files with hand-made key patterns. */
    private static final int PATTERN_RECORDS = 5 * BLOCK_RECORDS;

    /** The key of every record in the file whose keys are all equal. */
    private static final int EQUAL_KEY = 1234;

    /** The number of blocks in the ASCII sorting test. */
    private static final int ASCII_BLOCKS = 10;

    /** The seed that makes the ASCII sorting test deterministic. */
    private static final long ASCII_SEED = 7;

    /** The seed that makes the statistics test deterministic. */
    private static final long STATS_SEED = 11;

    /** Combines a file's size and pool size into a generator seed. */
    private static final long SEED_MULTIPLIER = 31;

    /**
     * Pairs of a number of blocks and a number of buffers, used by the random
     * binary file test.
     */
    private static final int[][] BLOCK_BUFFER_PAIRS = {
        { 1, 2 }, { 2, 1 }, { 5, 1 }, { 5, 3 }, { 8, 20 }, { 20, 2 }
    };

    /** The position of the number of blocks in a pair. */
    private static final int PAIR_BLOCKS = 0;

    /** The position of the number of buffers in a pair. */
    private static final int PAIR_BUFFERS = 1;

    /** The smallest number of buffers the program allows. */
    private static final int ONE_BUFFER = 1;

    /** A small pool size. */
    private static final int TWO_BUFFERS = 2;

    /** A pool size between the extremes. */
    private static final int THREE_BUFFERS = 3;

    /** A pool size between the extremes. */
    private static final int FOUR_BUFFERS = 4;

    /** The largest number of buffers the program allows. */
    private static final int MOST_BUFFERS = 20;

    /** Buffer counts the program must reject. */
    private static final String[] BAD_BUFFER_COUNTS = { "0", "21", "many" };

    /** Keys that are out of order. */
    private static final int[] UNSORTED_KEYS = { 2, 1 };

    /** The key of the only record in a one record file. */
    private static final int SINGLE_KEY = 5;

    /** The largest value written into a test record. */
    private static final int MAX_VALUE = 30000;

    /** The number of lines the program writes for each run. */
    private static final int STAT_LINES_PER_RUN = 5;

    /** The position of the file name line within a run's statistics. */
    private static final int NAME_LINE = 0;

    /** The position of the cache hits line within a run's statistics. */
    private static final int HITS_LINE = 1;

    /** The position of the disk reads line within a run's statistics. */
    private static final int READS_LINE = 2;

    /** The position of the disk writes line within a run's statistics. */
    private static final int WRITES_LINE = 3;

    /** The position of the sort time line within a run's statistics. */
    private static final int TIME_LINE = 4;

    /** The statistics line for a run that wrote nothing to the disk. */
    private static final String NO_WRITES = "Disk Writes: 0";

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
        new File(SAMPLE_STAT_FILE).delete();
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
        // Makes the file being tested
        FileGenerator generator = new FileGenerator(GENERATED_FILE,
            GENERATED_BLOCKS);
        generator.setSeed(GENERATED_SEED); // To make generation deterministic
        generator.generateFile(FileType.ASCII);

        File generated = new File(GENERATED_FILE); // The file made above
        assertEquals(GENERATED_BLOCKS * FileGenerator.BYTES_PER_BLOCK,
            generated.length());

        // Reads keys from the generated file
        RandomAccessFile reader = new RandomAccessFile(generated, READ_ONLY);
        assertEquals(GENERATED_FIRST_KEY, reader.readShort());
        reader.seek(THIRD_RECORD_POSITION);
        assertEquals(GENERATED_THIRD_KEY, reader.readShort());
        reader.close();
    }


    /**
     * Tests that the file checker tells sorted and unsorted files apart, and
     * that sorting an unsorted ASCII file satisfies it.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testCheckFile() throws Exception {
        assertTrue(CheckFile.check(SORTED_FILE));

        // Makes the unsorted file, which persists after the test is over
        FileGenerator generator = new FileGenerator(CHECKED_FILE, 1);
        generator.setSeed(CHECKED_SEED);
        generator.generateFile(FileType.ASCII);
        assertFalse(CheckFile.check(CHECKED_FILE));

        runProgram(CHECKED_FILE, ONE_BUFFER, STAT_FILE);
        assertTrue(CheckFile.check(CHECKED_FILE));
    }


    /**
     * Tests the program on a generated one block binary file.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testSorting() throws Exception {
        // Makes the unsorted file
        FileGenerator generator = new FileGenerator(SAMPLE_FILE, 1);
        generator.generateFile(FileType.BINARY);
        assertFalse(CheckFile.check(SAMPLE_FILE));

        runProgram(SAMPLE_FILE, ONE_BUFFER, SAMPLE_STAT_FILE);
        assertTrue(CheckFile.check(SAMPLE_FILE));
    }


    /**
     * Tests random binary files over a range of file and pool sizes, checking
     * that the file ends up sorted with the same records it started with.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testRandomBinaryFiles() throws Exception {
        // pair holds the number of blocks and buffers for one run
        for (int[] pair : BLOCK_BUFFER_PAIRS) {
            // Makes the file for this run
            FileGenerator generator = new FileGenerator(DATA_FILE,
                pair[PAIR_BLOCKS]);
            generator.setSeed(pair[PAIR_BLOCKS] * SEED_MULTIPLIER
                + pair[PAIR_BUFFERS]);
            generator.generateFile(FileType.BINARY);
            assertSortsCorrectly(pair[PAIR_BUFFERS]);
        }
    }


    /**
     * Tests a file with few distinct keys.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testAsciiFile() throws Exception {
        // Makes the file being sorted
        FileGenerator generator = new FileGenerator(DATA_FILE, ASCII_BLOCKS);
        generator.setSeed(ASCII_SEED);
        generator.generateFile(FileType.ASCII);
        assertSortsCorrectly(THREE_BUFFERS);
    }


    /**
     * Tests a file in which every key is the same. Equal keys are never
     * swapped, so the file should not be written at all.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testAllEqualKeys() throws Exception {
        int[] keys = new int[PATTERN_RECORDS]; // All keys in the file
        Arrays.fill(keys, EQUAL_KEY);
        writeKeys(keys);
        assertSortsCorrectly(MOST_BUFFERS);
        assertEquals(NO_WRITES, statLines().get(WRITES_LINE));
    }


    /**
     * Tests a file that is already sorted, which should not be written at all.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testAlreadySorted() throws Exception {
        int[] keys = new int[PATTERN_RECORDS]; // All keys in the file
        // i is the index of the key being set
        for (int i = 0; i < keys.length; i++) {
            keys[i] = i + 1;
        }
        writeKeys(keys);
        assertSortsCorrectly(MOST_BUFFERS);
        assertEquals(NO_WRITES, statLines().get(WRITES_LINE));
    }


    /**
     * Tests a file in descending order.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testReverseSorted() throws Exception {
        int[] keys = new int[PATTERN_RECORDS]; // All keys in the file
        // i is the index of the key being set
        for (int i = 0; i < keys.length; i++) {
            keys[i] = keys.length - i;
        }
        writeKeys(keys);
        assertSortsCorrectly(TWO_BUFFERS);
    }


    /**
     * Tests a file whose keys rise and then fall, a known hazard for a
     * median-of-three Quicksort with fixed sample positions.
     *
     * @throws Exception
     *             If a file access fails.
     */
    public void testOrganPipe() throws Exception {
        int[] keys = new int[PATTERN_RECORDS]; // All keys in the file
        // i is the index of the key being set
        for (int i = 0; i < keys.length; i++) {
            keys[i] = Math.min(i, keys.length - i) + 1;
        }
        writeKeys(keys);
        assertSortsCorrectly(FOUR_BUFFERS);
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
        assertSortsCorrectly(ONE_BUFFER);
        assertEquals(0, new File(DATA_FILE).length());

        writeKeys(new int[] { SINGLE_KEY });
        assertSortsCorrectly(ONE_BUFFER);
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
        // Makes the one block file being sorted
        FileGenerator generator = new FileGenerator(DATA_FILE, 1);
        generator.setSeed(STATS_SEED);
        generator.generateFile(FileType.BINARY);

        runProgram(DATA_FILE, ONE_BUFFER, STAT_FILE);
        runProgram(DATA_FILE, ONE_BUFFER, STAT_FILE);

        List<String> lines = statLines(); // Everything the two runs wrote
        assertEquals(2 * STAT_LINES_PER_RUN, lines.size());
        assertEquals("Standard sort on " + DATA_FILE, lines.get(NAME_LINE));
        assertTrue(lines.get(HITS_LINE).startsWith("Cache Hits: "));
        assertEquals("Disk Reads: 1", lines.get(READS_LINE));
        assertEquals("Disk Writes: 1", lines.get(WRITES_LINE));
        assertTrue(lines.get(TIME_LINE).matches("Sort Time: \\d+ ms"));
        // The second run starts with a sorted file, so it writes nothing.
        assertEquals("Standard sort on " + DATA_FILE, lines.get(
            STAT_LINES_PER_RUN + NAME_LINE));
        assertEquals(NO_WRITES, lines.get(STAT_LINES_PER_RUN + WRITES_LINE));
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
        writeKeys(UNSORTED_KEYS);
        // count is the rejected argument being tried
        for (String count : BAD_BUFFER_COUNTS) {
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
        runProgram(DATA_FILE, ONE_BUFFER, STAT_FILE);
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
        writeKeys(UNSORTED_KEYS);
        runProgram(DATA_FILE, ONE_BUFFER, UNWRITABLE_FILE);
        assertTrue(systemOut().getHistory().contains("I/O error"));
        assertTrue(CheckFile.check(DATA_FILE));
    }


    /**
     * Runs the program as it would be run from the command line.
     *
     * @param dataFile
     *            The name of the file to sort.
     * @param numBuffers
     *            The number of buffers to run with.
     * @param statFile
     *            The name of the statistics file.
     */
    private void runProgram(String dataFile, int numBuffers, String statFile) {
        Quicksort.main(new String[] { dataFile, String.valueOf(numBuffers),
            statFile });
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
        // Writes the records to the data file
        try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(DATA_FILE)))) {
            // i is the index of the record being written
            for (int i = 0; i < keys.length; i++) {
                out.writeShort(keys[i]);
                out.writeShort(i % MAX_VALUE + 1);
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
        int[] records = new int[count]; // Every record in the file
        // Reads the records from the data file
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
            new FileInputStream(DATA_FILE)))) {
            // i is the index of the record being read
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
    private void assertSortsCorrectly(int numBuffers) throws Exception {
        int[] expected = readSortedRecords(); // The original records
        new File(STAT_FILE).delete();
        runProgram(DATA_FILE, numBuffers, STAT_FILE);
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
