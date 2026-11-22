/**
 * The kinds of data file that FileGenerator can create.
 *
 * @author Cliff Shaffer, Patrick Sullivan
 * @version 2026-11-08
 */
public enum FileType {
    /** Records of raw binary shorts, which are not easily human-readable. */
    BINARY,

    /** Records whose bytes read as text, for easier visual inspection. */
    ASCII
}
