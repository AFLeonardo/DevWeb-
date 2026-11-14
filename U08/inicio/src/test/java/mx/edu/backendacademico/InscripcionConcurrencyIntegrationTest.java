package mx.edu.backendacademico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.AlumnoEntity;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import mx.edu.backendacademico.repository.jpa.MateriaEntity;
import mx.edu.backendacademico.repository.jpa.MateriaRepository;
import mx.edu.backendacademico.service.InscripcionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.datasource.hikari.data-source-properties.ApplicationName=inscripcion-concurrency-test"
)
class InscripcionConcurrencyIntegrationTest {
    private static final String WAITING_ADMISSIONS_SQL = """
        select count(*)
        from pg_stat_activity
        where application_name = 'inscripcion-concurrency-test'
          and wait_event_type = 'Lock'
          and lower(query) like '%materias%'
        """;

    @Autowired private InscripcionService servicio;
    @Autowired private AlumnoSpringDataRepository alumnos;
    @Autowired private MateriaRepository materias;
    @Autowired private InscripcionRepository inscripciones;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @Test
    void dosAdmisionesSimultaneasRespetanElCupoConUnBloqueoDePostgresql() throws Exception {
        var sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var alumnoUno = alumnos.saveAndFlush(new AlumnoEntity(new Alumno(
            null, "C1" + sufijo, "Alumno uno", "uno-" + sufijo + "@prueba.invalid", EstatusAlumno.ACTIVO)));
        var alumnoDos = alumnos.saveAndFlush(new AlumnoEntity(new Alumno(
            null, "C2" + sufijo, "Alumno dos", "dos-" + sufijo + "@prueba.invalid", EstatusAlumno.ACTIVO)));
        var materia = materias.saveAndFlush(new MateriaEntity(
            "CONC-" + sufijo, "Materia concurrente", 4, 1));

        try {
            var resultados = admitirEnParaleloConEvidenciaDePostgres(
                alumnoUno.getId(), alumnoDos.getId(), materia.getId());

            assertEquals(1, resultados.stream().filter(r -> r.inscripcionId() != null).count());
            assertEquals(1, resultados.stream().filter(r -> "SIN_CUPO".equals(r.codigoError())).count());
            assertEquals(1L, inscripciones.countByMateriaId(materia.getId()));
        } finally {
            jdbc.update("delete from inscripciones where materia_id = ?", materia.getId());
            jdbc.update("delete from alumnos where id in (?, ?)", alumnoUno.getId(), alumnoDos.getId());
            jdbc.update("delete from materias where id = ?", materia.getId());
        }
    }

    private List<ResultadoAdmision> admitirEnParaleloConEvidenciaDePostgres(
            Long alumnoUnoId, Long alumnoDosId, Long materiaId) throws Exception {
        ExecutorService tareas = Executors.newFixedThreadPool(2);
        var listos = new CountDownLatch(2);
        var salida = new CountDownLatch(1);
        Future<ResultadoAdmision> tareaUno = tareas.submit(
            () -> admitirTrasLaSalida(alumnoUnoId, materiaId, listos, salida));
        Future<ResultadoAdmision> tareaDos = tareas.submit(
            () -> admitirTrasLaSalida(alumnoDosId, materiaId, listos, salida));

        try {
            assertTrue(listos.await(10, TimeUnit.SECONDS), "Las dos tareas Java deben estar listas");
            try (Connection bloqueador = dataSource.getConnection()) {
                assertEquals("PostgreSQL", bloqueador.getMetaData().getDatabaseProductName(),
                    "Esta prueba requiere PostgreSQL real");
                bloqueador.setAutoCommit(false);
                int pidBloqueador;
                try (var statement = bloqueador.createStatement();
                     var resultado = statement.executeQuery("select pg_backend_pid()")) {
                    resultado.next();
                    pidBloqueador = resultado.getInt(1);
                }
                try (PreparedStatement sentencia = bloqueador.prepareStatement(
                        "select id from materias where id = ? for update")) {
                    sentencia.setLong(1, materiaId);
                    sentencia.executeQuery().close();
                }

                salida.countDown();
                assertTrue(esperarBloqueosPostgres(pidBloqueador, 2, Duration.ofSeconds(10)),
                    "Deben observarse dos sesiones PostgreSQL esperando por el bloqueo de la materia");
                bloqueador.commit();
            }

            return List.of(
                tareaUno.get(20, TimeUnit.SECONDS),
                tareaDos.get(20, TimeUnit.SECONDS));
        } finally {
            salida.countDown();
            tareas.shutdownNow();
            assertTrue(tareas.awaitTermination(10, TimeUnit.SECONDS),
                "Las tareas de admisión deben terminar al liberar el bloqueo");
        }
    }

    private ResultadoAdmision admitirTrasLaSalida(
            Long alumnoId, Long materiaId, CountDownLatch listos, CountDownLatch salida) throws InterruptedException {
        listos.countDown();
        if (!salida.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("No se liberó la barrera de inicio");
        }
        try {
            return new ResultadoAdmision(servicio.inscribir(alumnoId, materiaId), null);
        } catch (OperacionAcademicaException ex) {
            return new ResultadoAdmision(null, ex.getCodigo());
        }
    }

    private boolean esperarBloqueosPostgres(int pidBloqueador, int cantidad, Duration limite)
            throws InterruptedException {
        long fin = System.nanoTime() + limite.toNanos();
        while (System.nanoTime() < fin) {
            Integer esperando = jdbc.queryForObject(WAITING_ADMISSIONS_SQL, Integer.class);
            Integer bloqueadasPorLaPrueba = jdbc.queryForObject("""
                select count(*)
                from pg_stat_activity
                where application_name = 'inscripcion-concurrency-test'
                  and wait_event_type = 'Lock'
                  and lower(query) like '%materias%'
                  and ? = any(pg_blocking_pids(pid))
                """, Integer.class, pidBloqueador);
            if (esperando != null && esperando >= cantidad
                    && bloqueadasPorLaPrueba != null && bloqueadasPorLaPrueba >= 1) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private record ResultadoAdmision(Long inscripcionId, String codigoError) {}
}
