package io.github.f4iyy.ubc780xlt.model;

/** Immutable content of one scan bank (its name). */
public record ScanBankData(String tag) {

    public ScanBankData {
        tag = tag == null ? "" : tag;
    }
}
