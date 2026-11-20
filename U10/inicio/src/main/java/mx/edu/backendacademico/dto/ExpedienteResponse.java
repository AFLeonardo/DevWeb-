package mx.edu.backendacademico.dto;

// Expediente: datos del alumno y la primera página de su historial.
public record ExpedienteResponse(AlumnoResponse alumno,
                                 PaginaResponse<InscripcionResponse> inscripciones) { }
