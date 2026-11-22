import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/*
 * On my honor:
 *
 * - I have not used source code obtained from another
 *   current or former student, or any other unauthorized
 *   source, either modified or unmodified.
 *
 * - All source code and documentation used in my program
 *   is either my original work, was derived by me from
 *   the source code published in the textbook for this
 *   course, or was developed with assistance disclosed
 *   below in accordance with course policies.
 *
 * - I have not discussed coding details about this
 *   project with anyone other than my partner (in the
 *   case of a joint submission), instructor, ACM/UPE
 *   tutors, or the TAs assigned to this course.
 *   I understand that I may discuss the concepts of
 *   this program with other students, and that another
 *   student may help me debug my program so long as
 *   neither of us writes anything during the discussion
 *   or modifies any computer file during the discussion.
 *   I have violated neither the spirit nor letter of
 *   this restriction.
 *
 * - Regarding the use of generative AI tools or other
 *   assistance, I declare ONE of the following:
 *
 *
 *   OPTION 2: Assistance Disclosure
 *
 *   During the preparation of this assignment,
 *   Jai Sundaram and Yaswanth Uppalapati used Claude Code
 *   and Codex in Quicksort.java, BufferPool.java,
 *   VirtualQuicksort.java, BufferPoolTest.java,
 *   QuicksortTest.java, Examples.java, FileGenerator.java,
 *   CheckFile.java, and FileType.java for assistance with
 *   debugging, code optimization, and syntax improvement.
 *
 *   After using this tool, I/we reviewed and edited
 *   the content as needed to ensure its accuracy and
 *   take full responsibility for the submitted work
 *   in relation to grading.
 *
 *   I understand that I am responsible for being able
 *   to complete and explain this work without the use
 *   of assistance.
 *
 * - I understand that disclosing the use of a tool
 *   does not automatically make its use authorized.
 *   All assistance must comply with the course's
 *   academic integrity policies.
 */

/**
 * The class containing the main method. Sorts a binary file of 4-byte records
 * in place using Quicksort. The file itself acts as the array, and all
 * accesses to it pass through an LRU buffer pool. Run statistics are appended
 * to a separate statistics file.
 *
 * @author Jai Sundaram, Yaswanth Uppalapati
 * @version 2026-11-22
 */
public class Quicksort {

    /** The number of command line arguments the program requires. */
    private static final int NUM_ARGS = 3;

    /** The position of the data file name in the arguments. */
    private static final int DATA_FILE_ARG = 0;

    /** The position of the number of buffers in the arguments. */
    private static final int NUM_BUFFERS_ARG = 1;

    /** The position of the statistics file name in the arguments. */
    private static final int STAT_FILE_ARG = 2;

    /** The fewest buffers the program allows. */
    private static final int MIN_BUFFERS = 1;

    /** The most buffers the program allows. */
    private static final int MAX_BUFFERS = 20;

    /** Opens the statistics file so that new lines go after the old ones. */
    private static final boolean APPEND_TO_FILE = true;

    /** Starts the statistics line naming the sorted file. */
    private static final String FILE_LABEL = "Standard sort on ";

    /** Starts the statistics line for the number of cache hits. */
    private static final String HITS_LABEL = "Cache Hits: ";

    /** Starts the statistics line for the number of disk reads. */
    private static final String READS_LABEL = "Disk Reads: ";

    /** Starts the statistics line for the number of disk writes. */
    private static final String WRITES_LABEL = "Disk Writes: ";

    /** Starts the statistics line for the running time of the sort. */
    private static final String TIME_LABEL = "Sort Time: ";

    /** Ends the statistics line for the running time of the sort. */
    private static final String TIME_UNITS = " ms";

    /**
     * Sorts a file and appends statistics about the run. Problems are reported
     * on standard output.
     *
     * @param args
     *            The data file name, the number of buffers (1 to 20), and the
     *            statistics file name.
     */
    public static void main(String[] args) {
        if (args.length != NUM_ARGS) {
            System.out.println("Usage: java Quicksort <data-file-name> "
                + "<numb-buffers> <stat-file-name>");
            return;
        }
        try {
            int numBuffers = Integer.parseInt(args[NUM_BUFFERS_ARG]);
            if (numBuffers < MIN_BUFFERS || numBuffers > MAX_BUFFERS) {
                System.out.println("The number of buffers must be from "
                    + MIN_BUFFERS + " to " + MAX_BUFFERS + ".");
            }
            else if (!new File(args[DATA_FILE_ARG]).isFile()) {
                System.out.println("Data file not found: "
                    + args[DATA_FILE_ARG]);
            }
            else {
                sortFile(args[DATA_FILE_ARG], numBuffers, args[STAT_FILE_ARG]);
            }
        }
        catch (NumberFormatException e) {
            System.out.println("The number of buffers must be an integer: "
                + args[NUM_BUFFERS_ARG]);
        }
        catch (IOException e) {
            System.out.println("I/O error: " + e.getMessage());
        }
    }


    /**
     * Sorts a file, flushes the results to disk, and records the statistics.
     * Only the sort itself is timed.
     *
     * @param dataFile
     *            The name of the file to sort.
     * @param numBuffers
     *            The number of buffers in the buffer pool.
     * @param statFile
     *            The name of the file to append the statistics to.
     * @throws IOException
     *             If a disk access fails.
     */
    private static void sortFile(
        String dataFile,
        int numBuffers,
        String statFile)
        throws IOException {
        // The number of records in the file
        int numRecords = (int)(new File(dataFile).length()
            / VirtualQuicksort.RECORD_BYTES);
        BufferPool pool; // Declared here so its statistics outlive the file
        long sortMillis; // The running time of the sort in milliseconds
        // The pool for the data file, closed (and so flushed) after the sort
        try (BufferPool opened = new BufferPool(dataFile, numBuffers)) {
            pool = opened;
            // Sorts the records that the pool gives access to
            VirtualQuicksort sorter = new VirtualQuicksort(pool, numRecords);
            long startTime = System.currentTimeMillis(); // When the sort began
            sorter.sort();
            sortMillis = System.currentTimeMillis() - startTime;
        }
        writeStats(statFile, dataFile, pool, sortMillis);
    }


    /**
     * Appends the statistics for one run to the statistics file.
     *
     * @param statFile
     *            The name of the file to append to.
     * @param dataFile
     *            The name of the file that was sorted.
     * @param pool
     *            The buffer pool that holds the access counts.
     * @param sortMillis
     *            The running time of the sort in milliseconds.
     * @throws IOException
     *             If the statistics file cannot be written.
     */
    private static void writeStats(
        String statFile,
        String dataFile,
        BufferPool pool,
        long sortMillis)
        throws IOException {
        // Writes lines to the end of the statistics file
        try (PrintWriter out = new PrintWriter(new FileWriter(statFile,
            APPEND_TO_FILE))) {
            out.println(FILE_LABEL + dataFile);
            out.println(HITS_LABEL + pool.getCacheHits());
            out.println(READS_LABEL + pool.getDiskReads());
            out.println(WRITES_LABEL + pool.getDiskWrites());
            out.println(TIME_LABEL + sortMillis + TIME_UNITS);
        }
    }
}
