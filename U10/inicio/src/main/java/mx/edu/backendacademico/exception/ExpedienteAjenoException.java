package mx.edu.backendacademico.exception;

// Se lanza antes de buscar el expediente: no revela si el id existe.
public class ExpedienteAjenoException extends RuntimeException {
    public ExpedienteAjenoException() {
        super("El expediente solicitado no pertenece a la cuenta autenticada");
    }
}
