import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Generate a test data file of records. Each record is 4 bytes: 2 bytes for
 * the key (a java short, used for sorting), 2 bytes for the value (a java
 * short). A group of 1024 records is a block. Depending on the method, you can
 * generate two types of files: ASCII or raw binary shorts. In ASCII mode, the
 * records are constrained to specific values which align with specific ASCII
 * values. Reading this file as text/ascii will show a record in this format:
 * [space][letter][space][space]. In Binary mode, the keys and values of a
 * record are in the range [1-30000). Raw binary is not easily human-readable,
 * and often looks like textual garbage.
 *
 * @author Cliff Shaffer, Patrick Sullivan
 * @version 2026-11-08
 */
public class FileGenerator {
    /** The number of bytes in a record's key. */
    public static final int BYTES_IN_KEY = Short.BYTES;

    /** The number of bytes in a record's value. */
    public static final int BYTES_IN_VALUE = Short.BYTES;

    /** The number of bytes in a record. */
    public static final int BYTES_PER_RECORD = BYTES_IN_KEY + BYTES_IN_VALUE;

    /** The number of records in a block. */
    public static final int RECORDS_PER_BLOCK = 1024;

    /** The number of bytes in a block. */
    public static final int BYTES_PER_BLOCK = RECORDS_PER_BLOCK
        * BYTES_PER_RECORD;

    /** The raw binary data representing a double-space. */
    private static final short ASCII_BLANK_VALUE = 8224;

    /** The offset that reaches the ASCII key range, starting at ' A'. */
    private static final int ASCII_KEY_OFFSET = 8257;

    /** The number of characters in the alphabet range, from A to Z. */
    private static final int ASCII_KEY_RANGE = 26;

    /** The smallest random short in a binary file. */
    private static final int BINARY_MIN = 1;

    /** The exclusive upper limit of the random shorts in a binary file. */
    private static final int BINARY_LIMIT = 30000;

    /** The number of blocks of data to generate. */
    private final int numBlocks;

    /** The name of the file to generate. */
    private final String fname;

    /** The source of the random keys and values. */
    private Random rng;

    /**
     * Creates a FileGenerator object for making random files of data.
     *
     * @param fname
     *            the file name (example 'oneBlock.txt' or 'data.bin')
     * @param numBlocks
     *            number of blocks of data in the file
     */
    public FileGenerator(String fname, int numBlocks) {
        this.numBlocks = numBlocks;
        this.fname = fname;
        rng = new Random();
    }


    /**
     * [Optional] Sets the rng seed to make generation deterministic instead of
     * random. Files generated using the same seed will be exactly the same. Can
     * be helpful for consistent testing.
     *
     * @param seed
     *            the seed
     */
    public void setSeed(long seed) {
        rng.setSeed(seed);
    }


    /**
     * Generates a file using the given setup.
     *
     * @param ft
     *            type of file being generated, either binary or ASCII
     */
    public void generateFile(FileType ft) {
        DataOutputStream dos; // The stream that writes to the generated file
        try {
            dos = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(fname)));
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("ERROR: File not found. See System.err");
            return; // exit method early, dos is unusable already
        }

        try {
            if (ft == FileType.ASCII) {
                generateAsciiFile(dos);
            }
            else if (ft == FileType.BINARY) {
                generateBinaryFile(dos);
            }

            dos.flush(); // flush any contents stuck in buffer to file.
            dos.close(); // close file when done generating
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("ERROR: IOException in fileGen. See System.err");
            return;
        }
    }


    /**
     * Generates a file of random ASCII records. This is just to make it easier
     * for your visual inspection
     * Record keys are one space ' ' and a randomly ASCII charachter within in
     * the range ' A' to ' Z' .
     * Record values are always two spaces' '.
     *
     * @param dos
     *            The data output stream to write data to
     * @throws IOException
     *             if writing shorts encounters an issue
     */
    private void generateAsciiFile(DataOutputStream dos) throws IOException {
        int randKey; // The key of the record being generated

        for (int i = 0; i < numBlocks; i++) {
            for (int j = 0; j < RECORDS_PER_BLOCK; j++) {
                randKey = Math.abs(rng.nextInt() % ASCII_KEY_RANGE)
                    + ASCII_KEY_OFFSET;
                dos.writeShort(randKey); // THIS writes to the file!
                dos.writeShort(ASCII_BLANK_VALUE); // THIS writes to the file!
            }
        }
    }


    /**
     * Generates a file of random binary records. Record keys and values are
     * shorts in range [1-30000)
     *
     * @param dos
     *            The data output stream to write data to
     * @throws IOException
     *             if writing shorts encounters an issue
     */
    private void generateBinaryFile(DataOutputStream dos) throws IOException {
        int randKey; // The key of the record being generated
        int randVal; // The value of the record being generated
        int range = BINARY_LIMIT - BINARY_MIN; // The number of possible shorts
        for (int i = 0; i < numBlocks; i++) {
            for (int j = 0; j < RECORDS_PER_BLOCK; j++) {
                randKey = Math.abs(rng.nextInt() % range) + BINARY_MIN;
                randVal = Math.abs(rng.nextInt() % range) + BINARY_MIN;
                dos.writeShort((short)randKey); // THIS writes to the file!
                dos.writeShort((short)randVal); // THIS writes to the file!
            }
        }
    }
}
