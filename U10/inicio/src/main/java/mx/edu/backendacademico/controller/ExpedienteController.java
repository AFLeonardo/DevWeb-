package mx.edu.backendacademico.controller;

import mx.edu.backendacademico.dto.ExpedienteResponse;
import mx.edu.backendacademico.service.ExpedienteService;
import mx.edu.backendacademico.service.Solicitante;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/expedientes")
public class ExpedienteController {
    private final ExpedienteService service;

    public ExpedienteController(ExpedienteService service) { this.service = service; }

    // Sin parámetros de identidad: la cuenta sale del principal autenticado.
    @GetMapping("/mio")
    public ExpedienteResponse mio(Authentication autenticacion) {
        return service.mio(solicitante(autenticacion));
    }

    // El id elige el objeto; la regla por objeto decide si el principal puede verlo.
    @GetMapping("/{alumnoId}")
    public ExpedienteResponse consultar(@PathVariable Long alumnoId, Authentication autenticacion) {
        if (alumnoId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador positivo requerido");
        return service.consultar(solicitante(autenticacion), alumnoId);
    }

    private static Solicitante solicitante(Authentication autenticacion) {
        boolean admin = autenticacion.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new Solicitante(autenticacion.getName(), admin);
    }
}
