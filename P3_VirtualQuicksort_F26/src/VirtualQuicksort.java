import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Quicksort on a "virtual array" of records whose storage is a disk file. Each
 * array access becomes a request to the buffer pool for the bytes of a record.
 *
 * Several choices keep the number of pool requests and dirty blocks low:
 * <ul>
 * <li>Hoare partitioning needs only two scan positions, and it splits ranges
 * of equal keys evenly, so duplicate keys do not cause quadratic behavior.</li>
 * <li>Records with equal keys are never swapped, which avoids dirtying a
 * block when nothing would change.</li>
 * <li>The pivot is the median of three samples from a seeded generator, so no
 * input ordering is a likely worst case and runs remain repeatable.</li>
 * <li>Short ranges are copied out in one request, insertion sorted in memory,
 * and written back only if they changed.</li>
 * <li>Recursing only on the smaller side bounds the stack depth to O(log n).
 * </li>
 * </ul>
 *
 * @author Jai Sundaram, Yaswanth Uppalapati
 * @version 2026-11-22
 */
public class VirtualQuicksort {

    /** The number of bytes in a record. */
    public static final int RECORD_BYTES = 4;

    /** The number of bytes in a key, which is at the start of a record. */
    private static final int KEY_BYTES = 2;

    /** Ranges with at most this many records are insertion sorted. */
    private static final int SMALL_RANGE_RECORDS = 32;

    /** The number of equal parts a range is divided into for pivot samples. */
    private static final int PIVOT_SAMPLES = 3;

    /** The seed for pivot sampling, fixed so that runs are repeatable. */
    private static final long PIVOT_SEED = 3114L;

    /** The buffer pool through which all records are accessed. */
    private final BufferPool pool;

    /** The number of records in the virtual array. */
    private final int numRecords;

    /** Generates the pivot sample positions. */
    private final Random random;

    /** Receives the bytes of a key read from the pool. */
    private final byte[] keyBytes;

    /** Interprets keyBytes as a big-endian short. */
    private final ByteBuffer keyView;

    /** Holds the left record during a swap. */
    private final byte[] leftRecord;

    /** Holds the right record during a swap. */
    private final byte[] rightRecord;

    /** Holds the contents of a small range during insertion sort. */
    private final byte[] smallRange;

    /** Interprets smallRange as records. */
    private final ByteBuffer smallRangeView;

    /**
     * Creates a sorter for the records stored behind a buffer pool.
     *
     * @param pool
     *            The buffer pool for the file being sorted.
     * @param numRecords
     *            The number of records in the file.
     */
    public VirtualQuicksort(BufferPool pool, int numRecords) {
        this.pool = pool;
        this.numRecords = numRecords;
        random = new Random(PIVOT_SEED);
        keyBytes = new byte[KEY_BYTES];
        keyView = ByteBuffer.wrap(keyBytes);
        leftRecord = new byte[RECORD_BYTES];
        rightRecord = new byte[RECORD_BYTES];
        smallRange = new byte[SMALL_RANGE_RECORDS * RECORD_BYTES];
        smallRangeView = ByteBuffer.wrap(smallRange);
    }


    /**
     * Sorts all of the records into ascending order by key. Changes may remain
     * in the buffer pool until it is flushed.
     *
     * @throws IOException
     *             If a disk access fails.
     */
    public void sort() throws IOException {
        sortRange(0, numRecords - 1);
    }


    /**
     * Sorts a range of records.
     *
     * @param first
     *            The index of the first record in the range.
     * @param last
     *            The index of the last record in the range.
     * @throws IOException
     *             If a disk access fails.
     */
    private void sortRange(int first, int last) throws IOException {
        int low = first; // The first record not yet sorted
        int high = last; // The last record not yet sorted
        while (high - low + 1 > SMALL_RANGE_RECORDS) {
            int split = partition(low, high); // The left part's last record
            if (split - low < high - split) {
                sortRange(low, split);
                low = split + 1;
            }
            else {
                sortRange(split + 1, high);
                high = split;
            }
        }
        insertionSort(low, high);
    }


    /**
     * Rearranges a range into a left part with keys no greater than the pivot
     * and a right part with keys no less than the pivot. Both parts are
     * guaranteed to be non-empty because the pivot is the median of samples
     * from distinct positions.
     *
     * @param low
     *            The index of the first record in the range.
     * @param high
     *            The index of the last record in the range.
     * @return The index of the last record in the left part.
     * @throws IOException
     *             If a disk access fails.
     */
    private int partition(int low, int high) throws IOException {
        short pivot = choosePivot(low, high); // The key separating the parts
        int left = low - 1; // The scan position moving to the right
        int right = high + 1; // The scan position moving to the left
        while (true) {
            short leftKey; // The key at the left scan position
            do {
                left++;
                leftKey = readKey(left);
            }
            while (leftKey < pivot);

            short rightKey; // The key at the right scan position
            do {
                right--;
                rightKey = readKey(right);
            }
            while (rightKey > pivot);

            if (left >= right) {
                return right;
            }
            if (leftKey != rightKey) {
                swap(left, right);
            }
        }
    }


    /**
     * Chooses a pivot as the median of keys sampled from each third of a
     * range.
     *
     * @param low
     *            The index of the first record in the range.
     * @param high
     *            The index of the last record in the range.
     * @return The pivot key.
     * @throws IOException
     *             If a disk access fails.
     */
    private short choosePivot(int low, int high) throws IOException {
        int third = (high - low + 1) / PIVOT_SAMPLES; // Length of each third
        // A key sampled from the first third of the range
        short first = readKey(low + random.nextInt(third));
        // A key sampled from the middle third of the range
        short second = readKey(low + third + random.nextInt(third));
        // A key sampled from the last third of the range
        short last = readKey(low + third + third + random.nextInt(third));
        return (short)Math.max(Math.min(first, second), Math.min(Math.max(
            first, second), last));
    }


    /**
     * Sorts a small range by copying it into memory. The range is written back
     * only if the records actually moved.
     *
     * @param low
     *            The index of the first record in the range.
     * @param high
     *            The index of the last record in the range.
     * @throws IOException
     *             If a disk access fails.
     */
    private void insertionSort(int low, int high) throws IOException {
        int numBytes = (high - low + 1) * RECORD_BYTES; // The range's length
        long position = positionOf(low); // The range's location in the file
        pool.getBytes(smallRange, numBytes, position);
        boolean moved = false; // Whether any record changed position
        // i is the byte offset of the record being inserted
        for (int i = RECORD_BYTES; i < numBytes; i += RECORD_BYTES) {
            int record = smallRangeView.getInt(i); // The record to insert
            short key = smallRangeView.getShort(i); // The key to insert
            int hole = i; // The open position for the record being inserted
            while (hole > 0 && smallRangeView.getShort(hole
                - RECORD_BYTES) > key) {
                smallRangeView.putInt(hole, smallRangeView.getInt(hole
                    - RECORD_BYTES));
                hole -= RECORD_BYTES;
            }
            smallRangeView.putInt(hole, record);
            moved |= hole != i;
        }
        if (moved) {
            pool.putBytes(smallRange, numBytes, position);
        }
    }


    /**
     * Reads the key of a record.
     *
     * @param index
     *            The index of the record.
     * @return The record's key.
     * @throws IOException
     *             If a disk access fails.
     */
    private short readKey(int index) throws IOException {
        pool.getBytes(keyBytes, KEY_BYTES, positionOf(index));
        return keyView.getShort(0);
    }


    /**
     * Exchanges two records.
     *
     * @param left
     *            The index of one record.
     * @param right
     *            The index of the other record.
     * @throws IOException
     *             If a disk access fails.
     */
    private void swap(int left, int right) throws IOException {
        long leftPosition = positionOf(left); // The first record's location
        long rightPosition = positionOf(right); // The second one's location
        pool.getBytes(leftRecord, RECORD_BYTES, leftPosition);
        pool.getBytes(rightRecord, RECORD_BYTES, rightPosition);
        pool.putBytes(rightRecord, RECORD_BYTES, leftPosition);
        pool.putBytes(leftRecord, RECORD_BYTES, rightPosition);
    }


    /**
     * Calculates the byte position in the file of a record.
     *
     * @param index
     *            The index of the record.
     * @return The position of the record's first byte.
     */
    private long positionOf(int index) {
        return (long)index * RECORD_BYTES;
    }
}
