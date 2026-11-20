package mx.edu.backendacademico.service;

import java.util.Objects;
import java.util.Optional;
import mx.edu.backendacademico.config.CuentasAlumnoProperties;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.ExpedienteResponse;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.exception.ExpedienteAjenoException;
import mx.edu.backendacademico.exception.ExpedienteNoVinculadoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla de acceso por objeto para expedientes:
 * 1. ADMIN puede consultar cualquier expediente.
 * 2. El propietario (cuenta vinculada a ese alumno) puede consultar el suyo.
 * 3. Cualquier otro solicitante es ajeno: 403, decidido antes de buscar el objeto.
 */
@Service
@Transactional(readOnly = true)
public class ExpedienteService {
    static final int MAX_INSCRIPCIONES = 50;

    private final AlumnoRepository alumnos;
    private final InscripcionService inscripciones;
    private final CuentasAlumnoProperties cuentas;

    public ExpedienteService(AlumnoRepository alumnos, InscripcionService inscripciones,
                             CuentasAlumnoProperties cuentas) {
        this.alumnos = alumnos;
        this.inscripciones = inscripciones;
        this.cuentas = cuentas;
    }

    public ExpedienteResponse mio(Solicitante solicitante) {
        // La única entrada es el principal; no hay id ni usuario provisto por el cliente.
        var propio = alumnoDe(solicitante).orElseThrow(ExpedienteNoVinculadoException::new);
        return expediente(propio);
    }

    public ExpedienteResponse consultar(Solicitante solicitante, Long alumnoId) {
        Objects.requireNonNull(alumnoId, "alumnoId");
        if (solicitante.administrador()) {
            // ADMIN: un id inexistente sí se informa como 404.
            return expediente(alumnos.buscarPorId(alumnoId)
                .orElseThrow(() -> new AlumnoNoEncontradoException(alumnoId)));
        }
        // Propietario: compara con el id del alumno vinculado, no con datos de la petición.
        var propio = alumnoDe(solicitante)
            .filter(a -> a.id().equals(alumnoId))
            .orElseThrow(ExpedienteAjenoException::new);
        return expediente(propio);
    }

    private Optional<Alumno> alumnoDe(Solicitante solicitante) {
        return cuentas.matriculaDe(solicitante.usuario()).flatMap(alumnos::buscarPorMatricula);
    }

    private ExpedienteResponse expediente(Alumno alumno) {
        var pagina = PageRequest.of(0, MAX_INSCRIPCIONES, Sort.by("fecha").and(Sort.by("id")));
        return new ExpedienteResponse(AlumnoMapper.toResponse(alumno),
            inscripciones.historial(alumno.id(), pagina));
    }
}
