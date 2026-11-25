package mx.edu.backendacademico.repository.jpa;

// Fila que recuerda qué alumno produjo una clave de idempotencia de un solicitante.

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_idempotentes")
public class SolicitudIdempotenteEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String clave;
    private String solicitante;
    private String huella;
    @Column(name = "alumno_id")
    private Long alumnoId;
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    protected SolicitudIdempotenteEntity() { }

    public SolicitudIdempotenteEntity(String solicitante, String clave, String huella, LocalDateTime fechaCreacion) {
        this.solicitante = solicitante;
        this.clave = clave;
        this.huella = huella;
        this.fechaCreacion = fechaCreacion;
    }

    public void registrarAlumno(Long alumnoId) {
        // La entidad está administrada: el UPDATE se envía al confirmar la transacción.
        this.alumnoId = alumnoId;
    }

    public String getHuella() { return huella; }
    public Long getAlumnoId() { return alumnoId; }
}
