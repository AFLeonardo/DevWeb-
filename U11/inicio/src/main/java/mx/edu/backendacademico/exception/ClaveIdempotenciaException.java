package mx.edu.backendacademico.exception;

// Errores de aplicación de la cabecera Idempotency-Key: clave inválida o reutilizada.

public class ClaveIdempotenciaException extends RuntimeException {
    public static final String INVALIDA = "CLAVE_IDEMPOTENCIA_INVALIDA";
    public static final String REUTILIZADA = "CLAVE_IDEMPOTENCIA_REUTILIZADA";

    private final String codigo;

    private ClaveIdempotenciaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public static ClaveIdempotenciaException invalida() {
        return new ClaveIdempotenciaException(INVALIDA,
            "Idempotency-Key debe tener entre 1 y 100 caracteres visibles");
    }

    public static ClaveIdempotenciaException reutilizada() {
        return new ClaveIdempotenciaException(REUTILIZADA,
            "La clave de idempotencia ya se usó con un contenido distinto");
    }

    public String getCodigo() { return codigo; }
}
