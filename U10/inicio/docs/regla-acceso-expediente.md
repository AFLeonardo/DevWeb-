# Regla de acceso por objeto: expediente del alumno

Este documento fija la regla **antes** de escribir código. La implementación y las pruebas deben ajustarse a lo que se describe aquí, no al revés.

## 1. Problema

Un expediente reúne los datos de un alumno y su historial de inscripciones. Es información personal. Para protegerla no basta una regla de URL como "los alumnos pueden hacer `GET` en `/expedientes/**`": esa regla dice **qué tipo de usuario** puede usar la operación, pero no **sobre qué objeto**. Con solo esa regla, cualquier alumno podría cambiar el id de la URL y leer el expediente de otro. Esa falla se conoce como IDOR o *Broken Object Level Authorization*, y ocupa el primer lugar del OWASP API Top 10.

Hay dos preguntas separadas:

| Pregunta | Quién la responde | Con qué información |
|---|---|---|
| ¿El usuario está autenticado y puede usar la operación? | `SecurityFilterChain` (filtros) | Credenciales y URL |
| ¿Puede ver **este** expediente en particular? | `ExpedienteService` (aplicación) | El usuario autenticado y el dueño del objeto |

Los filtros no pueden responder la segunda pregunta porque no saben de quién es cada expediente. Por eso la regla por objeto vive en el servicio.

## 2. De dónde sale la identidad

**Regla de identidad:** la identidad del solicitante sale solo del principal autenticado (`Authentication.getName()`), que llena la cadena de filtros después de validar HTTP Basic.

- Ningún parámetro, encabezado ni campo del cuerpo (`usuario`, `matricula`, `alumnoId`) sirve como prueba de quién es el solicitante. Si el cliente los manda, se ignoran para decidir la identidad.
- La relación cuenta → alumno vive en el servidor, junto a la fuente de usuarios: `academico.cuentas.vinculos.<usuario>=<matrícula>`. El cliente no puede proponerla ni cambiarla.
- La matrícula del vínculo se normaliza igual que en el dominio (sin espacios y en mayúsculas).

Por ejemplo, el alumno `alumno` pide `/expedientes/mio?usuario=alumno2` y recibe **su propio** expediente (A001), no el de `alumno2`.

## 3. Operaciones

| Operación | Entrada que elige el objeto | Uso |
|---|---|---|
| `GET /api/v1/expedientes/mio` | Ninguna: el objeto se deriva del principal | El alumno consulta lo suyo; es la ruta recomendada |
| `GET /api/v1/expedientes/{alumnoId}` | `alumnoId` en la ruta | Consulta por id; aquí se aplica la regla por objeto |

En la segunda operación, `alumnoId` **elige** qué objeto se pide, pero **nunca prueba** quién lo pide.

## 4. Regla de acceso por objeto

Para `GET /expedientes/{alumnoId}`, la decisión se toma en este orden:

1. **Administrador** (`ROLE_ADMIN`): puede consultar cualquier expediente. Si el id no existe, recibe `404 ALUMNO_NO_ENCONTRADO`.
2. **Propietario**: la cuenta tiene un alumno vinculado y su `id` es igual a `alumnoId`. Se permite.
3. **Cualquier otro caso es un usuario ajeno**: se rechaza con `403 EXPEDIENTE_AJENO`. Esto incluye a otro alumno, a un docente y a una cuenta sin vínculo.

Para `GET /expedientes/mio`:

- Si la cuenta tiene un alumno vinculado y ese alumno existe, se devuelve su expediente.
- Si no, se responde `404 EXPEDIENTE_NO_VINCULADO`. Es el caso de docentes y administradores, que no tienen expediente propio.

### Tabla de decisión

| Solicitante | `/mio` | `/{id}` propio | `/{id}` de otro | `/{id}` inexistente |
|---|---|---|---|---|
| Sin credenciales | 401 | 401 | 401 | 401 |
| Alumno propietario | 200 | 200 | 403 | 403 |
| Alumno ajeno | 200 (el suyo) | 200 | 403 | 403 |
| Docente | 404 | — | 403 | 403 |
| Administrador | 404 | — | 200 | 404 |

### Decisiones de diseño y por qué

**Permiso antes que existencia.** Para quien no es administrador, el servicio compara `alumnoId` con el id de **su propio** alumno y no busca el objeto pedido. Por eso un usuario ajeno recibe el mismo 403 tanto si el id existe como si no: no puede usar las respuestas para averiguar qué ids existen, y el sistema nunca lee un expediente que no debía leer. El administrador sí recibe 404, porque para él la existencia no es un secreto.

**403 y no 404 para el ajeno.** Ocultar con 404 también sería válido. Elegimos 403 porque el curso distingue con claridad entre 401 (no sé quién eres), 403 (sé quién eres y no puedes) y 404 (no existe). El punto anterior ya evita la enumeración de ids.

**El docente es ajeno.** El expediente es personal. Un docente consulta alumnos e inscripciones con sus propias operaciones (`/alumnos`, `/inscripciones`), no con el expediente.

**Excepción propia, no `AccessDeniedException`.** El rechazo ocurre dentro de MVC. Si se lanzara `AccessDeniedException`, el `@ExceptionHandler(Exception.class)` de `GlobalExceptionHandler` la atraparía antes que Spring Security y respondería 500. Por eso se lanza `ExpedienteAjenoException`, que se convierte en `ProblemDetail` con `codigo = EXPEDIENTE_AJENO`.

**Dos formatos de 403.** Este 403 nace en MVC y lleva `application/problem+json`. Los 403 de los filtros (rol insuficiente, falta CSRF) conservan el cuerpo por defecto de Boot (`timestamp`, `status`, `error`, `path`). No hay que suponer que todos los errores de seguridad tienen el mismo formato.

## 5. Casos de prueba que la implementación debe cumplir

**Propietario**
- `alumno` → `/mio` → 200, matrícula A001.
- `alumno` → `/mio?usuario=alumno2&matricula=A002` → 200, matrícula **A001** (no hay suplantación).
- `alumno` → `/{id de A001}` → 200.

**Usuario ajeno**
- `alumno2` → `/{id de A001}` → 403 `EXPEDIENTE_AJENO`, y no se consulta el historial de A001.
- `alumno` → `/999` (no existe) → 403 `EXPEDIENTE_AJENO`, el mismo resultado que con un id existente.
- `docente` → `/{id}` → 403; `docente` → `/mio` → 404 `EXPEDIENTE_NO_VINCULADO`.

**Administrador**
- `admin` → `/{id de A002}` → 200.
- `admin` → `/999` → 404 `ALUMNO_NO_ENCONTRADO`.
- `admin` → `/mio` → 404 `EXPEDIENTE_NO_VINCULADO`.

**Sin credenciales**
- `/mio` → 401.

## 6. Correspondencia con la implementación

| Pieza | Archivo |
|---|---|
| Regla por objeto | `src/main/java/.../service/ExpedienteService.java` |
| Identidad verificada (sin Spring Security en el servicio) | `src/main/java/.../service/Solicitante.java` |
| Construcción del solicitante desde `Authentication` | `src/main/java/.../controller/ExpedienteController.java` |
| Vínculo cuenta → matrícula | `src/main/java/.../config/CuentasAlumnoProperties.java` y `application.properties` |
| Regla de URL (solo autenticación) | `src/main/java/.../config/SecurityConfig.java` |
| Errores 403 y 404 como ProblemDetail | `src/main/java/.../exception/GlobalExceptionHandler.java` |
| Pruebas de la regla aislada | `src/test/java/.../service/ExpedienteServiceTest.java` |
| Pruebas a través de los filtros | `src/test/java/.../controller/ExpedienteSeguridadTest.java` |

## 7. Pendiente detectado

`GET /api/v1/alumnos/{id}` permite que cualquier `ROLE_ALUMNO` lea los datos de cualquier alumno. Tiene el mismo problema de acceso por objeto que este documento resuelve para el expediente, y debería recibir la misma regla.
