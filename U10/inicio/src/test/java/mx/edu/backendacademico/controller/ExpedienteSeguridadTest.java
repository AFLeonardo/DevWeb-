package mx.edu.backendacademico.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Optional;
import mx.edu.backendacademico.config.CuentasAlumnoProperties;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.dto.PaginaResponse;
import mx.edu.backendacademico.repository.AlumnoRepository;
import mx.edu.backendacademico.service.ExpedienteService;
import mx.edu.backendacademico.service.InscripcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Recorre la cadena de filtros real: la identidad llega por HTTP Basic, no por parámetros.
@WebMvcTest(ExpedienteController.class)
@Import({SecurityConfig.class, ExpedienteService.class, ExpedienteSeguridadTest.Cuentas.class})
@TestPropertySource(properties = {
    "academico.cuentas.vinculos.alumno=A001",
    "academico.cuentas.vinculos.alumno2=A002"})
class ExpedienteSeguridadTest {
    @TestConfiguration
    @EnableConfigurationProperties(CuentasAlumnoProperties.class)
    static class Cuentas { }

    @Autowired MockMvc mvc;
    @MockitoBean AlumnoRepository alumnos;
    @MockitoBean InscripcionService inscripciones;

    @BeforeEach void datos() {
        var ada = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var alan = new Alumno(2L, "A002", "Alan", "alan@u.mx", EstatusAlumno.ACTIVO);
        when(alumnos.buscarPorMatricula("A001")).thenReturn(Optional.of(ada));
        when(alumnos.buscarPorMatricula("A002")).thenReturn(Optional.of(alan));
        when(alumnos.buscarPorId(1L)).thenReturn(Optional.of(ada));
        when(alumnos.buscarPorId(2L)).thenReturn(Optional.of(alan));
        when(alumnos.buscarPorId(999L)).thenReturn(Optional.empty());
        when(inscripciones.historial(anyLong(), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 50, 0, 0));
    }

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/expedientes/mio")).andExpect(status().isUnauthorized());
    }

    // --- Propietario ---
    @Test void propietarioObtieneMiExpediente() throws Exception {
        mvc.perform(get("/api/v1/expedientes/mio").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.matricula").value("A001"));
    }

    @Test void unParametroDeUsuarioNoSustituyeAlPrincipal() throws Exception {
        // Intento de suplantación: se ignora; manda la cuenta autenticada.
        mvc.perform(get("/api/v1/expedientes/mio").param("usuario", "alumno2").param("matricula", "A002")
                .with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.matricula").value("A001"));
    }

    @Test void propietarioConsultaSuExpedientePorId() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.id").value(1));
    }

    // --- Usuario ajeno ---
    @Test void alumnoAjenoRecibe403ConProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("alumno2", "alumno2123")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_AJENO"));
        verify(inscripciones, never()).historial(eq(1L), any());
    }

    @Test void ajenoNoDistingueIdsExistentesDeInexistentes() throws Exception {
        mvc.perform(get("/api/v1/expedientes/999").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_AJENO"));
    }

    @Test void docenteEsAjenoAlExpedientePersonal() throws Exception {
        mvc.perform(get("/api/v1/expedientes/1").with(httpBasic("docente", "docente123")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_AJENO"));
        mvc.perform(get("/api/v1/expedientes/mio").with(httpBasic("docente", "docente123")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_NO_VINCULADO"));
    }

    // --- Administrador ---
    @Test void administradorConsultaCualquierExpediente() throws Exception {
        mvc.perform(get("/api/v1/expedientes/2").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alumno.matricula").value("A002"));
    }

    @Test void administradorRecibe404ParaIdInexistente() throws Exception {
        mvc.perform(get("/api/v1/expedientes/999").with(httpBasic("admin", "admin123")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }

    @Test void administradorNoTieneExpedientePropio() throws Exception {
        mvc.perform(get("/api/v1/expedientes/mio").with(httpBasic("admin", "admin123")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_NO_VINCULADO"));
    }
}
