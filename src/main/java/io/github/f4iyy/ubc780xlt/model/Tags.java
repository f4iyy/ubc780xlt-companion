package io.github.f4iyy.ubc780xlt.model;

/** Alpha tags: up to 16 printable ASCII characters. */
public final class Tags {

    public static final int MAX_LENGTH = 16;

    private Tags() {
    }

    public static boolean isValid(String tag) {
        if (tag == null || tag.length() > MAX_LENGTH) {
            return false;
        }
        return tag.chars().allMatch(c -> c >= 0x20 && c <= 0x7E);
    }

    /** Collapses blanks, replaces unsupported characters with {@code ?} and cuts to 16 characters. */
    public static String clean(String tag) {
        if (tag == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : tag.strip().toCharArray()) {
            sb.append(c >= 0x20 && c <= 0x7E ? c : '?');
        }
        String collapsed = sb.toString().replaceAll(" {2,}", " ");
        return collapsed.length() > MAX_LENGTH ? collapsed.substring(0, MAX_LENGTH).stripTrailing() : collapsed;
    }
}
