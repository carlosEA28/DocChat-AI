package com.carlos.DocChat.AI.util.helpers;

import com.carlos.DocChat.AI.exceptions.service.InvalidPdfException;
import com.carlos.DocChat.AI.exceptions.service.PdfTooLargeException;

import java.util.Locale;

public final class PdfValidationHelper {
    private static final long MAX_PDF_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    private PdfValidationHelper() {
    }

    public static void validateSize(long size) {
        if (size > MAX_PDF_SIZE) {
            throw new PdfTooLargeException("O PDF deve ter no máximo 10 MB.");
        }
    }

    public static void validateType(String filename, String contentType) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidPdfException("Envie um arquivo com extensão .pdf e nome válido.");
        }
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidPdfException("O tipo de conteúdo deve ser application/pdf.");
        }
    }

    public static void validateSignature(byte[] content) {
        if (content.length < PDF_SIGNATURE.length) {
            throw new InvalidPdfException("O arquivo não possui uma assinatura PDF válida.");
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (content[index] != PDF_SIGNATURE[index]) {
                throw new InvalidPdfException("O arquivo não possui uma assinatura PDF válida.");
            }
        }
    }
}
