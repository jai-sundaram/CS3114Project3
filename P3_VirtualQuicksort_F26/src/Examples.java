import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Small demonstrations of the techniques this project needs: sorting records
 * held in a byte array with a ByteBuffer, and copying a file.
 *
 * @author CS3114 Instructors and TAs
 * @version 2026-11-08
 */
public class Examples {

    /** The number of bytes in one record. */
    private static final int REC_BYTES_LENGTH = 4;

    /** The number of different suffixes for the name of a copied file. */
    private static final int COPY_NAME_CHOICES = 30;

    /**
     * Sorts two sample records and prints them, then copies this source file.
     *
     * @param args
     *            The command line arguments, which are not used.
     */
    public static void main(String[] args) {
        // rec0 key is bytes 3 4 combined into a short; value is 8 and 8
        // rec1 key is bytes 2 5 combined into a short; value is 9 and 9
        byte[] someTwoRecs = { 3, 4, 8, 8, 2, 5, 9, 9 };
        sortTwoRecords(someTwoRecs);
        for (byte b : someTwoRecs) {
            System.out.print(b + " ");
        }
        System.out.println();

        // Demo of copying a file....
        // The suffix for the name of the copy
        int randNum = (int)(System.currentTimeMillis() % COPY_NAME_CHOICES);
        // inside src folder is Examples.java
        Path src = Paths.get("src", "Examples.java");
        // The location of the copy
        Path dest = Paths.get("src", "ExamplesCopy" + randNum + ".java");
        try {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(
                "Copied myself! Refresh project in eclipse to see...");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Puts two records stored in a byte array into ascending order by key.
     *
     * @param recs
     *            An array that holds exactly two records.
     */
    public static void sortTwoRecords(byte[] recs) {
        final int recLen = REC_BYTES_LENGTH; // just for a shorter name
        assert recs.length == 2 * recLen;

        // Creates a ByteBuffer using an existing byte array.
        // Note that telling bf to do actions will do them to the array,
        // and actions that happen to that array will be visible by bf.
        // bf is an object with a reference to that array.
        ByteBuffer bf = ByteBuffer.wrap(recs);

        short key0 = bf.getShort(); // record zero's key is at byte zero
        short key1 = bf.getShort(1 * recLen); // key1 begins at byte pos 4

        if (key1 < key0) {
            byte[] swapArea = new byte[recLen]; // a one record swap area
            System.arraycopy(recs, 0 * recLen, swapArea, 0, recLen);
            System.arraycopy(recs, 1 * recLen, recs, 0 * recLen, recLen);
            System.arraycopy(swapArea, 0, recs, 1 * recLen, recLen);
        }
        assert bf.getShort(0) < bf.getShort(1 * recLen); // they are sorted!
    }
}
