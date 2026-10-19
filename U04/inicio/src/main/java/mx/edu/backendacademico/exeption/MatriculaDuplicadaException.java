package mx.edu.backendacademico.exeption;

public class MatriculaDuplicadaException {
    public MatriculaDuplicadaException(String matricula) {
        super("Ya existe la matricula " + matricula);
    }
}
