package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mx.edu.backendacademico.config.CuentasAlumnoProperties;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.dto.PaginaResponse;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.exception.ExpedienteAjenoException;
import mx.edu.backendacademico.exception.ExpedienteNoVinculadoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpedienteServiceTest {
    private final Alumno ada = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
    private final Alumno alan = new Alumno(2L, "A002", "Alan", "alan@u.mx", EstatusAlumno.ACTIVO);
    private final AlumnoRepository alumnos = mock(AlumnoRepository.class);
    private final InscripcionService inscripciones = mock(InscripcionService.class);
    private ExpedienteService servicio;

    @BeforeEach void preparar() {
        when(alumnos.buscarPorMatricula("A001")).thenReturn(Optional.of(ada));
        when(alumnos.buscarPorMatricula("A002")).thenReturn(Optional.of(alan));
        when(alumnos.buscarPorId(1L)).thenReturn(Optional.of(ada));
        when(alumnos.buscarPorId(2L)).thenReturn(Optional.of(alan));
        when(inscripciones.historial(anyLong(), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 50, 0, 0));
        // "a001" en minúsculas comprueba que el vínculo se normaliza como la matrícula.
        servicio = new ExpedienteService(alumnos, inscripciones,
            new CuentasAlumnoProperties(Map.of("alumno", "a001", "alumno2", "A002")));
    }

    @Test void miExpedienteSeResuelveDesdeLaCuenta() {
        var exp = servicio.mio(new Solicitante("alumno", false));
        assertEquals("A001", exp.alumno().matricula());
        verify(inscripciones).historial(eq(1L), any());
    }

    @Test void cuentaSinVinculoNoTieneExpediente() {
        assertThrows(ExpedienteNoVinculadoException.class, () -> servicio.mio(new Solicitante("docente", false)));
    }

    @Test void propietarioConsultaSuExpediente() {
        assertEquals(1L, servicio.consultar(new Solicitante("alumno", false), 1L).alumno().id());
    }

    @Test void ajenoRecibeRechazoSinLeerElObjetoSolicitado() {
        assertThrows(ExpedienteAjenoException.class, () -> servicio.consultar(new Solicitante("alumno", false), 2L));
        // La decisión no depende del objeto ajeno: ni se busca ni se lee su historial.
        verify(alumnos, never()).buscarPorId(anyLong());
        verify(inscripciones, never()).historial(anyLong(), any());
    }

    @Test void ajenoConIdInexistenteRecibeElMismoRechazo() {
        assertThrows(ExpedienteAjenoException.class, () -> servicio.consultar(new Solicitante("alumno", false), 999L));
    }

    @Test void administradorConsultaCualquierExpediente() {
        assertEquals("A002", servicio.consultar(new Solicitante("admin", true), 2L).alumno().matricula());
    }

    @Test void administradorRecibeAusenciaRealParaIdInexistente() {
        when(alumnos.buscarPorId(999L)).thenReturn(Optional.empty());
        assertThrows(AlumnoNoEncontradoException.class, () -> servicio.consultar(new Solicitante("admin", true), 999L));
    }
}
