package io.github.f4iyy.ubc780xlt.model.xlsx;

import java.io.IOException;

/** The workbook cannot be read as scanner programming; the message says where. */
public class XlsxException extends IOException {

    private static final long serialVersionUID = 1L;

    public XlsxException(String message) {
        super(message);
    }

    public XlsxException(String message, Throwable cause) {
        super(message, cause);
    }
}
