package com.osiris.autoplug.client.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.event.FocusEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
public class HintTextFieldTest {

    private HintTextField textField;

    @BeforeEach
    public void setUp() {
        textField = new HintTextField("Enter text here");
    }

    @Test
    public void testTextIsClearedOnFocus() {
        FocusEvent mockFocusEvent = Mockito.mock(FocusEvent.class);
        textField.focusGained(mockFocusEvent);
        assertEquals("", textField.getText());
        assertEquals(Color.BLACK, textField.getForeground());
    }

}

