# Acceso por propietario: `GET /api/v1/alumnos/{id}`

## Regla

| Rol | Resultado |
|---|---|
| DOCENTE, ADMIN | Pueden consultar cualquier alumno. Si el id no existe, reciben 404. |
| ALUMNO | Solo puede consultar **su propio** registro. Si pide uno ajeno o inexistente, recibe **403** `ACCESO_DENEGADO`. |

## Vínculo entre usuario y alumno

**Criterio:** un alumno es dueño del registro cuya **matrícula coincide con su nombre de usuario**, sin distinguir mayúsculas.

Con la cuenta de laboratorio `alumno` / `alumno123`, el registro propio es el que tiene matrícula `ALUMNO`. El script `scripts/evidencia.ps1` lo crea si todavía no existe.

Lo elegí por estas razones:

- No requiere tablas, columnas ni migraciones nuevas, y tampoco credenciales adicionales.
- En una universidad es común que la matrícula sea el usuario de acceso.

## Dónde está implementado

- `controller/AlumnoController.buscar`: si el usuario es DOCENTE o ADMIN, devuelve el registro. Si no, lo lee y compara su matrícula con `Authentication.getName()`. Si no coinciden, o si el registro no existe, lanza `AccessDeniedException`.
- `exception/GlobalExceptionHandler.accesoDenegado`: convierte esa excepción en un 403 con `ProblemDetail`. Sin este manejador, el `@ExceptionHandler(Exception.class)` general la habría convertido en un 500.
- Pruebas: `AccesoPropietarioTest` usa MockMvc con la `SecurityConfig` real y prueba seis casos: propietario permitido, alumno ajeno 403, inexistente 403 para alumno, docente permitido, admin permitido y 404 para docente.

Un ALUMNO recibe 403 también cuando el id no existe, para que no pueda averiguar qué ids existen probando números.

## Límites

- **Depende de una convención.** Si una matrícula no coincide con ningún usuario, ese alumno no puede ver su registro. En un sistema real convendría una columna `usuario` en `alumnos`, o un claim del token (OIDC), en lugar de comparar textos.
- **Solo cubre `GET /alumnos/{id}`.** `GET /api/v1/alumnos` (la lista) sigue abierta a ALUMNO, como pedía el contrato anterior, y deja ver todos los registros. El siguiente paso sería restringir la lista a DOCENTE y ADMIN, o filtrarla al propio alumno.
- Los usuarios están en memoria (`InMemoryUserDetailsManager`), así que no se pueden dar de alta alumnos-usuario sin cambiar código.
- La comprobación está en el controlador, no en el servicio. Si otro controlador reutiliza `AlumnoService.buscar`, tendría que repetirla.
