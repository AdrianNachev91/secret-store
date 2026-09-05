package photos.sluice.secrets;

import java.util.regex.Pattern;

/**
 * The names Windows resolves to a device rather than to a file or a folder. A credential's name
 * becomes a path segment, so it is checked against them on every platform rather than only on
 * Windows. Windows resolves one whatever extension follows it, so a file called {@code con.key}
 * would name the console instead. Refusing them everywhere means a name cannot work on the machine
 * it was written on and break on someone else's.
 *
 * <p>This is Microsoft's documented set, minus the superscript forms. Their guidance also reserves
 * {@code com¹}, {@code com²}, {@code com³} and the {@code lpt} equivalents, because Windows reads
 * ISO/IEC 8859-1 superscript digits as digits. A credential's name is refused a step earlier by
 * {@link SecretId}'s ASCII-only allowlist, so no superscript reaches this list.
 *
 * <p>{@code com0} and {@code lpt0} are deliberately absent, because that guidance does not list
 * them. Probes on two Windows machines created both as ordinary files, which is what
 * {@code ReservedDeviceNamesTest} pins.
 *
 * <p>Which of the listed names a machine actually reserves is not fixed. That is the real argument
 * for refusing them everywhere. {@code com1} is a symbolic link to a serial device, not a name the
 * path parser reserves outright. A machine with no such device therefore does not have it.
 *
 * <p>Two Windows versions disagreed about {@code com1}. A Windows 10 desktop refused it, and a
 * Windows Server 2025 runner created it as an ordinary file. A name whose meaning turns on
 * installed hardware must not decide where a credential is kept.
 */
final class ReservedDeviceNames {

    // Case-insensitive by construction. Windows reserves the name whatever case it is written in,
    // and a callers-must-lower-case-first rule would be a contract nothing enforces.
    private static final Pattern RESERVED =
            Pattern.compile("con|prn|aux|nul|com[1-9]|lpt[1-9]", Pattern.CASE_INSENSITIVE);

    /**
     * Prevents instantiation of this static utility class.
     */
    private ReservedDeviceNames() {}

    /**
     * Whether a path segment names a device Windows reserves.
     *
     * @param segment {@link String} the candidate path segment
     * @return boolean true if the segment names a reserved device
     */
    static boolean isReserved(final String segment) {
        return RESERVED.matcher(segment).matches();
    }
}
