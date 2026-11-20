package mx.edu.backendacademico.service;

/**
 * Identidad ya verificada por la cadena de filtros.
 * El controlador la construye desde el principal; el cliente no puede proponerla.
 */
public record Solicitante(String usuario, boolean administrador) {
    public Solicitante {
        if (usuario == null || usuario.isBlank()) throw new IllegalArgumentException("Usuario requerido");
    }
}
