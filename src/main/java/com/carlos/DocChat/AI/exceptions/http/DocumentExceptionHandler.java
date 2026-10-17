package com.carlos.DocChat.AI.exceptions.http;

import com.carlos.DocChat.AI.exceptions.service.DocumentPersistenceException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotFoundException;
import com.carlos.DocChat.AI.exceptions.service.DocumentNotReadyException;
import com.carlos.DocChat.AI.exceptions.service.DocumentReadException;
import com.carlos.DocChat.AI.exceptions.service.DocumentStorageException;
import com.carlos.DocChat.AI.exceptions.service.InvalidPdfException;
import com.carlos.DocChat.AI.exceptions.service.InvalidQuestionException;
import com.carlos.DocChat.AI.exceptions.service.PdfTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(InvalidQuestionException.class)
    public ResponseEntity<ErrorResponse> invalidQuestion(InvalidQuestionException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidRequestBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("O corpo da requisição deve conter uma pergunta válida."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> invalidDocumentId(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("O identificador do documento deve ser um UUID válido."));
    }

    @ExceptionHandler(DocumentNotReadyException.class)
    public ResponseEntity<ErrorResponse> documentNotReady(DocumentNotReadyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({PdfTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> pdfTooLarge(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
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
