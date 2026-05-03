package com.banking.handoff.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.banking.handoff.domain.FieldDefinition.Alignment;

class FieldDefinitionTest {

    @Test
    void shouldReturnFormatWhenSet() {
        FieldDefinition f = new FieldDefinition();
        f.setFormat("%.2f");
        assertThat(f.getFormat()).isEqualTo("%.2f");
    }

    @Test
    void shouldReturnNullWhenFormatNotSet() {
        FieldDefinition f = new FieldDefinition();
        assertThat(f.getFormat()).isNull();
    }

    @Test
    void shouldHaveDefaultAlignment() {
        FieldDefinition f = new FieldDefinition();
        assertThat(f.getAlignment()).isEqualTo(Alignment.LEFT);
    }

    @Test
    void shouldHaveDefaultPadChar() {
        FieldDefinition f = new FieldDefinition();
        assertThat(f.getPadChar()).isEqualTo(' ');
    }

    @Test
    void shouldReturnAllFieldsCorrectly() {
        FieldDefinition f = new FieldDefinition();
        f.setName("balance");
        f.setLength(15);
        f.setAlignment(Alignment.RIGHT);
        f.setPadChar('0');
        f.setFormat("%.2f");

        assertThat(f.getName()).isEqualTo("balance");
        assertThat(f.getLength()).isEqualTo(15);
        assertThat(f.getAlignment()).isEqualTo(Alignment.RIGHT);
        assertThat(f.getPadChar()).isEqualTo('0');
        assertThat(f.getFormat()).isEqualTo("%.2f");
    }
}
