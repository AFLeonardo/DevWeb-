package mx.edu.backendacademico.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Seguridad activa (filtros + SecurityConfig real): usuario "alumno" es dueño de la matrícula ALUMNO.
@WebMvcTest(AlumnoController.class)
@Import(SecurityConfig.class)
class AccesoPropietarioTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    @BeforeEach void datos() {
        when(service.buscar(10L)).thenReturn(new Alumno(10L, "ALUMNO", "Alumno Propio", "propio@u.mx", EstatusAlumno.ACTIVO));
        when(service.buscar(20L)).thenReturn(new Alumno(20L, "A002", "Otro Alumno", "otro@u.mx", EstatusAlumno.ACTIVO));
        when(service.buscar(99L)).thenThrow(new AlumnoNoEncontradoException(99L));
    }

    @Test void propietarioPuedeConsultarSuRegistro() throws Exception {
        mvc.perform(get("/api/v1/alumnos/10").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matricula").value("ALUMNO"));
    }

    @Test void alumnoNoPuedeConsultarOtroRegistro() throws Exception {
        mvc.perform(get("/api/v1/alumnos/20").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void alumnoRecibe403AunqueElIdNoExista() throws Exception {
        // No se revela si el id existe a quien no tiene acceso completo.
        mvc.perform(get("/api/v1/alumnos/99").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden());
    }

    @Test void docentePuedeConsultarCualquierRegistro() throws Exception {
        mvc.perform(get("/api/v1/alumnos/20").with(httpBasic("docente", "docente123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matricula").value("A002"));
    }

    @Test void adminPuedeConsultarCualquierRegistro() throws Exception {
        mvc.perform(get("/api/v1/alumnos/10").with(httpBasic("admin", "admin123")))
            .andExpect(status().isOk());
    }

    @Test void docenteRecibe404SiNoExiste() throws Exception {
        mvc.perform(get("/api/v1/alumnos/99").with(httpBasic("docente", "docente123")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }
}
