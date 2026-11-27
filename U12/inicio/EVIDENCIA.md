# Evidencia del Reto U12

Ejecutado el 2026-11-26 en la rama `reto-u12`, con Windows 10, Windows PowerShell 5.1, Docker Desktop (Docker 29.9.2, Compose v5.6.1) y JDK Temurin 21.0.13 (el proyecto compila con `release 21`). Todos los comandos se lanzan desde la carpeta del proyecto (`U12/inicio`).

## Resumen

| Punto | Qué se demuestra | Resultado |
|---|---|---|
| 0 | Build y pruebas | OK: 21 ejecutadas, 0 fallidas, 0 errores, 0 omitidas |
| 1 | README con requisitos, build, inicio, peticiones, logs y cierre | OK: [README.md](README.md) |
| 2 | Script con 201, 400, 404, 409, 401 y 403 (dos causas) | OK: 7 de 7 casos |
| 3 | Falla controlada de PostgreSQL (readiness y liveness) | OK: 3 de 3 casos |
| 4 | Decisión sobre la caché | OK: [docs/decision-cache.md](docs/decision-cache.md) |
| 5 | Acceso por propietario y pruebas | OK: 6 pruebas MockMvc y 3 casos en el script |
| 6 | Este documento | OK |

El script de evidencia terminó con **13 de 13 casos OK, exit 0**. Lo ejecuté dos veces seguidas y las dos dieron 13/13: en la segunda, el registro `ALUMNO` ya existía (409) y el script lo reutilizó.

---

## 0. Construir y probar

**Comando**

```powershell
.\mvnw.cmd clean test package
```

**Esperado:** `BUILD SUCCESS`, más de 0 pruebas ejecutadas y ninguna fallida, y el JAR generado en `target`.

**Real**

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in ...BackendAcademicoApplicationTests
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in ...controller.AccesoPropietarioTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in ...controller.AlumnoControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in ...controller.SeguridadTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...domain.AlumnoTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in ...repository.PostgresIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in ...service.AlumnoServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in ...service.CalendarioClientTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: ...\target\backend-academico-0.0.12-SNAPSHOT.jar
[INFO] BUILD SUCCESS
```

Las 15 pruebas que ya existían siguen pasando, y se sumaron las 6 de `AccesoPropietarioTest`.

## 1. README

**Comando:** abrir [README.md](README.md).

**Esperado:** que explique los requisitos (JDK 25 o `release 21`, Docker Desktop), cómo construir, iniciar, hacer peticiones, ver logs y cerrar sin `-v`.

**Real:** las seis secciones están presentes. Los comandos del README son los mismos que se usan en este documento.

## Levantar los contenedores (previo a los puntos 2, 3 y 5)

```powershell
docker compose build
docker compose up -d
docker compose ps
```

```text
app Up 20 seconds
postgres Up 26 seconds (healthy)
```

## 2. Script de evidencia: éxito, errores y seguridad

**Comando**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\evidencia.ps1
```

El script usa solo `curl.exe`. Envía los JSON desde archivos temporales con `-d "@archivo"` y crea cada vez una matrícula nueva (`EV` + fecha y hora), así que se puede repetir. No ejecuta `docker compose down -v`.

| Caso | Petición | Esperado | Real | Causa |
|---|---|---|---|---|
| 1. Éxito | `POST /api/v1/alumnos` como docente, con `X-CSRF-TOKEN` y la cookie `JSESSIONID` | 201 + `Location` | **201**, `Location: /api/v1/alumnos/10` | — |
| 2. Validación | POST con `{"matricula":"","nombre":"A","correo":"no-es-correo"}` | 400 | **400** `DATOS_INVALIDOS` | Bean Validation marca los tres campos |
| 3. Ausencia | `GET /api/v1/alumnos/999999999` como docente | 404 | **404** `ALUMNO_NO_ENCONTRADO` | El id no existe |
| 4. Conflicto | Repetir el POST del caso 1 | 409 | **409** `MATRICULA_DUPLICADA` | La matrícula ya existe |
| 5a. Seguridad | `GET /api/v1/alumnos` sin credenciales | 401 | **401** + `WWW-Authenticate: Basic` | **Autenticación:** no se sabe quién es |
| 5b. Seguridad | POST como `alumno` **con** un token CSRF válido | 403 | **403** | **Autorización:** se sabe quién es, pero crear requiere DOCENTE o ADMIN |
| 5c. Seguridad | POST como `docente` **sin** token CSRF | 403 | **403** | **CSRF:** el rol es correcto, pero `CsrfFilter` rechaza una escritura sin token |

En 5b y 5c el código es el mismo, pero la causa es distinta. En 5b la petición pasa CSRF y la frena la regla de rol (`hasAnyRole("DOCENTE","ADMIN")`). En 5c el rol es suficiente y la frena el filtro CSRF, que corre antes de la autorización.

**Salida real (fragmentos)**

```text
== 1. Éxito: crear alumno EV261126192052 (docente + CSRF + cookie)
HTTP 201  (esperado 201)
Location: /api/v1/alumnos/10
{"id":10,"matricula":"EV261126192052","nombre":"Alumno Evidencia","correo":"ev261126192052@u.mx","estatus":"ACTIVO"}
OK

== 2. Validación: datos inválidos
HTTP 400  (esperado 400)
Cuerpo: {"detail":"Revisa los campos indicados","instance":"/api/v1/alumnos","status":400,"title":"Bad Request",
 "type":"urn:backend-academico:problema:DATOS_INVALIDOS","codigo":"DATOS_INVALIDOS","campos":[
 {"campo":"nombre","mensaje":"size must be between 3 and 120"},
 {"campo":"correo","mensaje":"must be a well-formed email address"},
 {"campo":"matricula","mensaje":"must not be blank"}]}
OK

== 3. Ausencia: alumno inexistente
HTTP 404  (esperado 404)
Cuerpo: {"detail":"No existe el alumno con id 999999999",...,"codigo":"ALUMNO_NO_ENCONTRADO"}
OK

== 4. Conflicto: matrícula duplicada EV261126192052
HTTP 409  (esperado 409)
Cuerpo: {"detail":"Ya existe la matrícula EV261126192052",...,"codigo":"MATRICULA_DUPLICADA"}
OK

== 5a. Seguridad: sin credenciales
HTTP 401  (esperado 401)
WWW-Authenticate: Basic realm="Realm", charset="UTF-8"
{"timestamp":"2026-11-27T01:20:53.509Z","status":401,"error":"Unauthorized","path":"/api/v1/alumnos"}
OK

== 5b. Seguridad: rol insuficiente
HTTP 403  (esperado 403)
Cuerpo: {"timestamp":"2026-11-27T01:20:53.618Z","status":403,"error":"Forbidden","path":"/api/v1/alumnos"}
OK

== 5c. Seguridad: falta token CSRF
HTTP 403  (esperado 403)
Cuerpo: {"timestamp":"2026-11-27T01:20:53.655Z","status":403,"error":"Forbidden","path":"/api/v1/alumnos"}
OK
```

## 3. Falla controlada de PostgreSQL

Está incluida en el mismo script (sección 7). También puede hacerse a mano:

```powershell
docker compose stop postgres
curl.exe -i http://localhost:8080/actuator/health/readiness
curl.exe -i http://localhost:8080/actuator/health/liveness
docker compose start postgres
curl.exe -i http://localhost:8080/actuator/health/readiness   # repetir hasta que diga UP
```

| Caso | Esperado | Real |
|---|---|---|
| 7a. readiness sin base | 503 `DOWN` | **503** `{"status":"DOWN"}` |
| 7b. liveness sin base | 200 `UP` | **200** `{"status":"UP"}` |
| 7c. readiness con la base de nuevo | 200 `UP` | **200** `{"status":"UP"}`, sin reiniciar la app |

```text
== 7. Falla controlada: docker compose stop postgres
    Container inicio-postgres-1 Stopped
== 7a. Sin PostgreSQL: readiness
HTTP 503  (esperado 503)
Cuerpo: {"status":"DOWN"}
OK
== 7b. Sin PostgreSQL: liveness
HTTP 200  (esperado 200)
Cuerpo: {"status":"UP"}
OK
== 7. Recuperación: docker compose start postgres
    Container inicio-postgres-1 Started
Readiness volvió a UP: True
== 7c. Con PostgreSQL de nuevo: readiness
HTTP 200  (esperado 200)
Cuerpo: {"status":"UP"}
OK
```

**Por qué liveness no incluye la base.** En `application.properties`, `management.endpoint.health.group.readiness.include=readinessState,db` mete la base solo en readiness. Cada sonda responde una pregunta distinta:

- **Liveness:** "¿El proceso está sano o hay que reiniciarlo?" Si incluyera la base, una caída de PostgreSQL haría que el orquestador reiniciara la aplicación. Reiniciarla no arregla la base, y si hay varias réplicas, todas entrarían en un ciclo de reinicios al mismo tiempo.
- **Readiness:** "¿Puedo mandarle tráfico ahora?" Sin base, la respuesta correcta es que no (503): el balanceador deja de enviarle peticiones, pero el proceso sigue vivo y vuelve a estar listo solo, en cuanto el pool de conexiones (Hikari) logra reconectarse. Eso es lo que se vio en el caso 7c.

## 4. Decisión sobre la caché

**Comando:** abrir [docs/decision-cache.md](docs/decision-cache.md).

**Esperado:** que explique qué se cachea, por qué la clave basta, la invalidación con `TransactionAwareCacheManagerProxy`, los límites (sin TTL, sin tamaño máximo, sin compartir entre réplicas, lectura obsoleta) y por qué la caché no decide el cupo.

**Real:** el documento cubre todos esos puntos. La invalidación ya se había comprobado con los logs SQL en `evidencia/comprobacion-u12.md` (paso 5): el primer GET hizo SELECT, el segundo no, y después del PUT hubo un nuevo SELECT.

## 5. Acceso por propietario

La regla y sus límites están en [docs/acceso-propietario.md](docs/acceso-propietario.md). El criterio es que el usuario autenticado debe tener el mismo nombre que la matrícula del alumno. `openapi.yaml` se actualizó: `GET /api/v1/alumnos/{id}` ahora describe la regla y el 403 `ACCESO_DENEGADO`.

**Pruebas con seguridad activa:** `AccesoPropietarioTest` usa `@WebMvcTest` con `@Import(SecurityConfig.class)` y credenciales HTTP Basic reales de laboratorio.

```powershell
.\mvnw.cmd test "-Dtest=AccesoPropietarioTest"
```

| Prueba | Esperado | Real |
|---|---|---|
| `propietarioPuedeConsultarSuRegistro` (alumno pide matrícula ALUMNO) | 200 | OK |
| `alumnoNoPuedeConsultarOtroRegistro` (alumno pide A002) | 403 `ACCESO_DENEGADO` | OK |
| `alumnoRecibe403AunqueElIdNoExista` | 403 | OK |
| `docentePuedeConsultarCualquierRegistro` | 200 | OK |
| `adminPuedeConsultarCualquierRegistro` | 200 | OK |
| `docenteRecibe404SiNoExiste` | 404 | OK |

**Contra los contenedores** (sección 6 del script):

| Caso | Esperado | Real |
|---|---|---|
| Preparación: crear la matrícula `ALUMNO` como docente | 201 la primera vez, 409 después | 201 (1.ª corrida), 409 (2.ª corrida) |
| 6a. `alumno` consulta su registro (id 11) | 200 | **200** `{"id":11,"matricula":"ALUMNO",...}` |
| 6b. `alumno` consulta un registro ajeno (id 10) | 403 | **403** `{"detail":"Sólo puedes consultar tu propio registro",...,"codigo":"ACCESO_DENEGADO"}` |
| 6c. `docente` consulta el registro ajeno (id 10) | 200 | **200** `{"id":10,"matricula":"EV261126192052",...}` |

## Resumen que imprime el script

```text
Caso                                                            Esperado Real Resultado
1. Éxito: crear alumno EV261126192052 (docente + CSRF + cookie) 201      201  OK
2. Validación: datos inválidos                                  400      400  OK
3. Ausencia: alumno inexistente                                 404      404  OK
4. Conflicto: matrícula duplicada EV261126192052                409      409  OK
5a. Seguridad: sin credenciales                                 401      401  OK
5b. Seguridad: rol insuficiente                                 403      403  OK
5c. Seguridad: falta token CSRF                                 403      403  OK
6a. Propietario: alumno consulta su registro (id 11)            200      200  OK
6b. Propietario: alumno consulta registro ajeno (id 10)         403      403  OK
6c. Propietario: docente consulta registro ajeno (id 10)        200      200  OK
7a. Sin PostgreSQL: readiness                                   503      503  OK
7b. Sin PostgreSQL: liveness                                    200      200  OK
7c. Con PostgreSQL de nuevo: readiness                          200      200  OK

Casos: 13  OK: 13  Fallidos: 0
```

## Cierre

```powershell
docker compose down      # sin -v: el volumen datos_academicos se conserva
```
