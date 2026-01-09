package com.gabriel.qaphptranslationagent.model;

public record PageElement(
        String text,
        String selector,
        String currentKey
) {}
