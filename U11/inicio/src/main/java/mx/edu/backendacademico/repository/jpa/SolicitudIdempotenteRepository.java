package mx.edu.backendacademico.repository.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudIdempotenteRepository extends JpaRepository<SolicitudIdempotenteEntity, Long> {
    // Coincide con la restricción única uk_solicitud_idempotente.
    Optional<SolicitudIdempotenteEntity> findBySolicitanteAndClave(String solicitante, String clave);
}
