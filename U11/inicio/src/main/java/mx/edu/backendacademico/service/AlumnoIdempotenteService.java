package mx.edu.backendacademico.service;

// Crea alumnos con Idempotency-Key: la restricción única de PostgreSQL decide
// qué solicitud crea y cuál sólo repite la respuesta. No hay estado en memoria.

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.exception.ClaveIdempotenciaException;
import mx.edu.backendacademico.repository.jpa.SolicitudIdempotenteEntity;
import mx.edu.backendacademico.repository.jpa.SolicitudIdempotenteRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AlumnoIdempotenteService {
    static final String RESTRICCION_UNICA = "uk_solicitud_idempotente";
    private static final int LONGITUD_MAXIMA_CLAVE = 100;

    private final AlumnoService alumnos;
    private final SolicitudIdempotenteRepository solicitudes;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;
    private final Clock reloj;

    public AlumnoIdempotenteService(AlumnoService alumnos, SolicitudIdempotenteRepository solicitudes,
                                    PlatformTransactionManager transacciones, Clock reloj) {
        this.alumnos = alumnos;
        this.solicitudes = solicitudes;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
        this.escritura.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.lectura.setReadOnly(true);
    }

    public Alumno crear(String solicitante, String clave, String matricula, String nombre, String correo) {
        Objects.requireNonNull(solicitante, "La clave de idempotencia requiere un usuario autenticado");
        if (clave == null || clave.isBlank() || clave.length() > LONGITUD_MAXIMA_CLAVE) {
            throw ClaveIdempotenciaException.invalida();
        }
        String huella = huella(matricula, nombre, correo);
        try {
            // 1. Una sola transacción: reservar clave, crear alumno y guardar el resultado.
            return escritura.execute(estado -> {
                var solicitud = solicitudes.saveAndFlush(
                    new SolicitudIdempotenteEntity(solicitante, clave, huella, LocalDateTime.now(reloj)));
                var creado = alumnos.crear(matricula, nombre, correo);
                solicitud.registrarAlumno(creado.id());
                return creado;
            });
        } catch (DataIntegrityViolationException ex) {
            if (!violaRestriccionUnica(ex)) throw ex;
            // 2. Otra transacción ya confirmó la clave. PostgreSQL abortó la nuestra,
            //    así que la fila ganadora se lee en una transacción nueva.
            return lectura.execute(estado -> repetir(solicitante, clave, huella));
        }
    }

    private Alumno repetir(String solicitante, String clave, String huella) {
        var previa = solicitudes.findBySolicitanteAndClave(solicitante, clave).orElseThrow(() ->
            new IllegalStateException("La restricción única se violó, pero la solicitud previa no existe"));
        // 3. Misma clave con otro contenido: no es un reintento, es un error del cliente.
        if (!previa.getHuella().equals(huella)) throw ClaveIdempotenciaException.reutilizada();
        return alumnos.buscar(previa.getAlumnoId());
    }

    private static boolean violaRestriccionUnica(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException restriccion &&
                RESTRICCION_UNICA.equalsIgnoreCase(restriccion.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    static String huella(String matricula, String nombre, String correo) {
        // Misma forma canónica que Alumno: "a1 " y "A1" describen al mismo alumno.
        String contenido = String.join("\u001F",
            matricula.strip().toUpperCase(Locale.ROOT), nombre.strip(), correo.strip());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(contenido.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("La JVM no ofrece SHA-256", ex);
        }
    }
}
