package mx.edu.backendacademico.exception;

public class MatriculaDuplicadaException {
    public MatriculaDuplicadaException(String matricula) {
        super("Ya existe la matricula " + matricula);
    }
}
