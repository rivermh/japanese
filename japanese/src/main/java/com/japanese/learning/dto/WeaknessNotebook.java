package com.japanese.learning.dto;

import java.util.List;

public record WeaknessNotebook(
        List<WeaknessNoteItem> recent,
        List<WeaknessNoteItem> frequent,
        List<WeaknessNoteItem> consecutive,
        List<WeaknessNoteItem> improving,
        int availableForFocusedReview
) { }
