package com.carlos.DocChat.AI.exceptions.http;

import com.carlos.DocChat.AI.exceptions.service.DocumentPersistenceException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentReadException;
import com.carlos.DocChat.AI.exceptions.service.DocumentStorageException;
import com.carlos.DocChat.AI.exceptions.service.InvalidPdfException;
import com.carlos.DocChat.AI.exceptions.service.PdfTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class DocumentExceptionHandler {
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> documentNotFound(DocumentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(InvalidPdfException.class)
    public ResponseEntity<ErrorResponse> invalidPdf(InvalidPdfException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({PdfTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> pdfTooLarge(Exception exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("O PDF deve ter no máximo 10 MB."));
    }

    @ExceptionHandler(DocumentStorageException.class)
    public ResponseEntity<ErrorResponse> storageFailure(DocumentStorageException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("Não foi possível armazenar o PDF."));
    }

    @ExceptionHandler(DocumentReadException.class)
    public ResponseEntity<ErrorResponse> readFailure(DocumentReadException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Não foi possível ler o PDF."));
    }

    @ExceptionHandler(DocumentPersistenceException.class)
    public ResponseEntity<ErrorResponse> persistenceFailure(DocumentPersistenceException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Não foi possível registrar o PDF."));
    }

    public record ErrorResponse(String error) {
    }
}
