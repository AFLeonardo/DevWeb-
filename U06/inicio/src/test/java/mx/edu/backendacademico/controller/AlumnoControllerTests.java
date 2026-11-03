package mx.edu.backendacademico.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AlumnoControllerTests {
    private static final String ALUMNOS = "/api/v1/alumnos";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rechazaDatosInvalidosYDevuelveLosCampos() throws Exception {
        mockMvc.perform(post(ALUMNOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"matricula":"","nombre":"A","correo":"no-es-correo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos", hasSize(3)))
                .andExpect(jsonPath("$.campos[*].campo",
                        containsInAnyOrder("matricula", "nombre", "correo")));
    }

    @Test
    void respondeBadRequestCuandoNoPuedeLeerElJson() throws Exception {
        mockMvc.perform(post(ALUMNOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respondeNoEncontradoParaUnIdInexistente() throws Exception {
        mockMvc.perform(get(ALUMNOS + "/9223372036854775807"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }

    @Test
    void respondeConflictoAlCrearLaMismaMatriculaDosVeces() throws Exception {
        String matricula = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
        String alumno = """
                {"matricula":"%s","nombre":"Ada Lovelace","correo":"ada@universidad.mx"}
                """.formatted(matricula);

        mockMvc.perform(post(ALUMNOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alumno))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ALUMNOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alumno))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MATRICULA_DUPLICADA"));
    }
}
