package io.github.f4iyy.ubc780xlt.protocol;

/** A line sent back by the scanner. */
public record Response(Kind kind, String text) {

    public enum Kind {
        /** {@code OK}: command accepted. */
        OK,
        /** {@code NG}: valid command, but not allowed in the current mode. */
        NG,
        /** {@code ERR}: invalid command or value. */
        ERR,
        /** {@code FER}: framing error. */
        FER,
        /** {@code ORER}: overrun error. */
        ORER,
        /** Any other line: the requested data. */
        DATA
    }

    public static Response parse(String line) {
        String text = line == null ? "" : line.strip();
        return switch (text) {
            case "OK" -> new Response(Kind.OK, text);
            case "NG" -> new Response(Kind.NG, text);
            case "ERR" -> new Response(Kind.ERR, text);
            case "FER" -> new Response(Kind.FER, text);
            case "ORER" -> new Response(Kind.ORER, text);
            default -> new Response(Kind.DATA, text);
        };
    }

    public boolean isOk() {
        return kind == Kind.OK;
    }

    /** True for {@code ERR}, {@code FER} and {@code ORER}. */
    public boolean isError() {
        return kind == Kind.ERR || kind == Kind.FER || kind == Kind.ORER;
    }
}
