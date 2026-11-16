package io.github.f4iyy.ubc780xlt.transfer;

/** The user cancelled the transfer. */
public class TransferCancelledException extends Exception {

    private static final long serialVersionUID = 1L;

    public TransferCancelledException() {
        super("Transfer cancelled");
    }
}
