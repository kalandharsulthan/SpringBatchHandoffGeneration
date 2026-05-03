package com.banking.handoff.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class FieldDefinition {

    @NotBlank
    private String name;

    @Positive
    private int length;

    private Alignment alignment = Alignment.LEFT;

    private char padChar = ' ';

    private String format;

    public enum Alignment {
        LEFT, RIGHT
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment;
    }

    public char getPadChar() {
        return padChar;
    }

    public void setPadChar(char padChar) {
        this.padChar = padChar;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
