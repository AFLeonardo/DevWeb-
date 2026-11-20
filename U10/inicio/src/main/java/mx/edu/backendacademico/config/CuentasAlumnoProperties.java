package mx.edu.backendacademico.config;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vincula cada cuenta autenticada con la matrícula de su alumno.
 * El vínculo vive junto al almacén de usuarios, nunca en la petición HTTP.
 */
@ConfigurationProperties("academico.cuentas")
public record CuentasAlumnoProperties(Map<String, String> vinculos) {
    public CuentasAlumnoProperties {
        vinculos = vinculos == null ? Map.of() : Map.copyOf(vinculos);
    }

    public Optional<String> matriculaDe(String usuario) {
        // La matrícula se normaliza igual que en el dominio.
        return Optional.ofNullable(vinculos.get(usuario)).map(m -> m.strip().toUpperCase(Locale.ROOT));
    }
}
