package com.banking.handoff.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.banking.handoff.domain.FieldDefinition;
import com.banking.handoff.domain.FieldDefinition.Alignment;

class FixedWidthFormatterTest {

    private FixedWidthFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new FixedWidthFormatter();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formattingCases")
    void shouldFormatCorrectly(String description, Object input, FieldDefinition field, String expected) {
        String result = formatter.format(input, field);

        assertThat(result)
                .describedAs(description)
                .isEqualTo(expected)
                .hasSize(field.getLength());
    }

    static Stream<Arguments> formattingCases() {
        return Stream.of(
                Arguments.of(
                        "null value becomes spaces for LEFT alignment",
                        null, field("f", 10, Alignment.LEFT, ' ', null),
                        "          "),

                Arguments.of(
                        "empty string padded with spaces on right (LEFT)",
                        "", field("f", 5, Alignment.LEFT, ' ', null),
                        "     "),

                Arguments.of(
                        "exact-length value needs no padding",
                        "ABCDE", field("f", 5, Alignment.LEFT, ' ', null),
                        "ABCDE"),

                Arguments.of(
                        "short value padded on right (LEFT alignment)",
                        "ABC", field("f", 7, Alignment.LEFT, ' ', null),
                        "ABC    "),

                Arguments.of(
                        "short value padded on left (RIGHT alignment)",
                        "ABC", field("f", 7, Alignment.RIGHT, ' ', null),
                        "    ABC"),

                Arguments.of(
                        "value longer than length is truncated from right",
                        "ABCDEFGH", field("f", 5, Alignment.LEFT, ' ', null),
                        "ABCDE"),

                Arguments.of(
                        "zero-padded RIGHT alignment for numeric field",
                        "12345", field("f", 10, Alignment.RIGHT, '0', null),
                        "0000012345"),

                Arguments.of(
                        "BigDecimal formatted with format pattern then zero-padded",
                        new BigDecimal("1234.56"),
                        field("balance", 15, Alignment.RIGHT, '0', "%.2f"),
                        "000000001234.56"),

                Arguments.of(
                        "integer value right-aligned with zero padding",
                        42, field("f", 8, Alignment.RIGHT, '0', null),
                        "00000042"),

                Arguments.of(
                        "LEFT alignment with custom pad char",
                        "XY", field("f", 5, Alignment.LEFT, '*', null),
                        "XY***"),

                Arguments.of(
                        "blank format string is ignored — uses toString()",
                        "ABC", field("f", 6, Alignment.LEFT, ' ', ""),
                        "ABC   "),

                Arguments.of(
                        "whitespace-only format string is ignored — uses toString()",
                        "ABC", field("f", 6, Alignment.LEFT, ' ', "   "),
                        "ABC   "),

                Arguments.of(
                        "double 42.0 with %.2f differs from toString 42.0",
                        42.0, field("f", 10, Alignment.RIGHT, '0', "%.2f"),
                        "0000042.00"),

                Arguments.of(
                        "double 100.0 with %.2f differs from toString 100.0",
                        100.0, field("f", 9, Alignment.RIGHT, '0', "%.2f"),
                        "000100.00")
        );
    }

    private static FieldDefinition field(String name, int length, Alignment alignment, char padChar, String format) {
        FieldDefinition f = new FieldDefinition();
        f.setName(name);
        f.setLength(length);
        f.setAlignment(alignment);
        f.setPadChar(padChar);
        f.setFormat(format);
        return f;
    }
}
