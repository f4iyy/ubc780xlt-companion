package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.Response.Kind;
import org.junit.jupiter.api.Test;

class ResponseTest {

    @Test
    void parsesStatusLines() {
        assertEquals(Kind.OK, Response.parse("OK").kind());
        assertEquals(Kind.NG, Response.parse("NG").kind());
        assertEquals(Kind.ERR, Response.parse("ERR").kind());
        assertEquals(Kind.FER, Response.parse("FER").kind());
        assertEquals(Kind.ORER, Response.parse("ORER").kind());
    }

    @Test
    void anythingElseIsData() {
        Response r = Response.parse("SI BC780XLT,1000000001,103");
        assertEquals(Kind.DATA, r.kind());
        assertEquals("SI BC780XLT,1000000001,103", r.text());
    }

    @Test
    void stripsWhitespaceAndToleratesNull() {
        assertEquals(Kind.OK, Response.parse(" OK \n").kind());
        assertEquals("", Response.parse(null).text());
        assertEquals(Kind.DATA, Response.parse(null).kind());
    }

    @Test
    void okAndErrorHelpers() {
        assertTrue(Response.parse("OK").isOk());
        assertFalse(Response.parse("NG").isOk());
        assertTrue(Response.parse("ERR").isError());
        assertTrue(Response.parse("FER").isError());
        assertTrue(Response.parse("ORER").isError());
        assertFalse(Response.parse("NG").isError());
        assertFalse(Response.parse("OK").isError());
    }
}
