package mx.edu.backendacademico.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Sin @Transactional: cada solicitud debe confirmar (commit) en PostgreSQL real.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdempotenciaAlumnoIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired AlumnoSpringDataRepository alumnos;

    @Test void dosSolicitudesSimultaneasConLaMismaClaveCreanUnSoloAlumno() throws Exception {
        String clave = "idem-a-" + UUID.randomUUID();
        String cuerpo = """
            {"matricula":"IDEM-A1","nombre":"Ada Concurrente","correo":"ada.idem@u.mx"}
            """;
        var barrera = new CyclicBarrier(2);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // Ambas tareas esperan en la barrera y salen a la vez hacia el servidor.
            Future<MockHttpServletResponse> primera = hilos.submit(() -> crear(barrera, clave, cuerpo));
            Future<MockHttpServletResponse> segunda = hilos.submit(() -> crear(barrera, clave, cuerpo));
            var r1 = primera.get(30, TimeUnit.SECONDS);
            var r2 = segunda.get(30, TimeUnit.SECONDS);

            assertEquals(201, r1.getStatus(), "primera: " + r1.getContentAsString());
            assertEquals(201, r2.getStatus(), "segunda: " + r2.getContentAsString());
            Number id1 = JsonPath.read(r1.getContentAsString(), "$.id");
            Number id2 = JsonPath.read(r2.getContentAsString(), "$.id");
            assertEquals(id1.longValue(), id2.longValue(), "ambas respuestas deben referirse al mismo alumno");
            assertEquals(1, contarPorMatricula("IDEM-A1"), "debe existir un solo alumno");
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test void laMismaClaveConOtroContenidoSeRechazaSinCrearNada() throws Exception {
        String clave = "idem-b-" + UUID.randomUUID();
        var primera = crear(null, clave, """
            {"matricula":"IDEM-B1","nombre":"Emmy Original","correo":"emmy.idem@u.mx"}
            """);
        assertEquals(201, primera.getStatus(), primera.getContentAsString());

        var segunda = crear(null, clave, """
            {"matricula":"IDEM-B2","nombre":"Emmy Distinta","correo":"otra.idem@u.mx"}
            """);
        assertEquals(409, segunda.getStatus(), segunda.getContentAsString());
        assertEquals("CLAVE_IDEMPOTENCIA_REUTILIZADA", JsonPath.read(segunda.getContentAsString(), "$.codigo"));
        assertEquals(1, contarPorMatricula("IDEM-B1"));
        assertEquals(0, contarPorMatricula("IDEM-B2"), "la segunda solicitud no debe crear alumno");
    }

    private MockHttpServletResponse crear(CyclicBarrier barrera, String clave, String cuerpo) throws Exception {
        if (barrera != null) barrera.await(10, TimeUnit.SECONDS);
        return mvc.perform(post("/api/v1/alumnos")
                .with(user("docente").roles("DOCENTE")).with(csrf())
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andReturn().getResponse();
    }

    private long contarPorMatricula(String matricula) {
        return alumnos.findAll().stream()
            .filter(a -> a.toDomain().matricula().equals(matricula)).count();
    }
}
