package mx.edu.backendacademico.exception;

// La cuenta autenticada no tiene un alumno asociado (p. ej., docente o admin).
public class ExpedienteNoVinculadoException extends RuntimeException {
    public ExpedienteNoVinculadoException() {
        super("La cuenta autenticada no tiene un expediente asociado");
    }
}
