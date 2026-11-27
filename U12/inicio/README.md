# Backend Académico — Unidad 12

API REST de alumnos, materias e inscripciones hecha con Spring Boot 4.1.0. Usa PostgreSQL 17, seguridad HTTP Basic con CSRF, caché local, Actuator y una tarea programada.

Todo lo que sigue se hace desde una terminal (PowerShell en Windows), sin IDE.

## 1. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | **25** (el proyecto compila con `release 21`; un JDK 21 también sirve) | Compilar y ejecutar las pruebas |
| Docker Desktop | Iniciado, con Docker Compose v2 | Ejecutar PostgreSQL y la aplicación |
| curl.exe | Viene con Windows 10/11 | Hacer peticiones HTTP |

No hace falta instalar Maven, porque el proyecto trae su propio *wrapper* (`mvnw.cmd`).

> En PowerShell, `curl` es un alias de `Invoke-WebRequest`. Escribe siempre **`curl.exe`**.

El puerto **8080** debe estar libre. Si ya tienes otra instancia corriendo, detenla antes (`docker compose down` en su carpeta).

## 2. Construir y probar

```powershell
.\mvnw.cmd clean test package
```

Debe terminar con `BUILD SUCCESS` y generar `target\backend-academico-0.0.12-SNAPSHOT.jar`.
La prueba `PostgresIntegrationTest` usa Testcontainers, así que necesita Docker Desktop encendido.

## 3. Iniciar

```powershell
docker compose build      # crea la imagen con el JAR de target\
docker compose up -d      # levanta postgres y app en segundo plano
docker compose ps         # postgres debe salir "healthy" y app "Up"
```

Para comprobar que la aplicación ya responde:

```powershell
curl.exe http://localhost:8080/actuator/health/readiness
# {"status":"UP"}
```

## 4. Ejecutar peticiones

Cuentas de laboratorio (son públicas, no son credenciales reales):

| Usuario | Contraseña | Rol | Puede |
|---|---|---|---|
| `alumno` | `alumno123` | ALUMNO | Listar alumnos y consultar **solo su propio** registro |
| `docente` | `docente123` | DOCENTE | Leer y escribir alumnos e inscripciones |
| `admin` | `admin123` | ADMIN | Lo mismo que DOCENTE, más `/actuator/metrics` |

### Script de evidencia (lo más rápido)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\evidencia.ps1
```

Muestra el código HTTP y el cuerpo de estos casos: éxito 201, validación 400, ausencia 404, conflicto 409, rechazos de seguridad 401 y 403, acceso por propietario y la falla controlada de PostgreSQL. Termina con un resumen y sale con código 0 si todo salió como se esperaba.
Agrega `-SinFallaPostgres` si no quieres que detenga la base.

### A mano con curl.exe

Lectura:

```powershell
curl.exe -u alumno:alumno123 http://localhost:8080/api/v1/alumnos
```

Las escrituras (POST, PUT y DELETE) necesitan un **token CSRF** y la **cookie de sesión** con la que se obtuvo ese token:

```powershell
# 1. Pedir el token y guardar la cookie JSESSIONID
$token = (curl.exe -s -u docente:docente123 -c cookies.txt http://localhost:8080/api/v1/csrf | ConvertFrom-Json).token

# 2. Enviar el JSON desde un archivo (evita problemas de comillas en PowerShell)
curl.exe -i -u docente:docente123 -b cookies.txt -H "X-CSRF-TOKEN: $token" `
  -H "Content-Type: application/json" -d "@alumno.json" http://localhost:8080/api/v1/alumnos
```

Ejemplo de `alumno.json`: `{"matricula":"A012","nombre":"Ada Lovelace","correo":"ada.u12@u.mx"}`

El archivo `requests.http` trae más ejemplos, y el contrato completo está en `src/main/resources/static/openapi.yaml` (también en http://localhost:8080/openapi.yaml).

### Salud y métricas

```powershell
curl.exe http://localhost:8080/actuator/health/liveness     # ¿el proceso está vivo?
curl.exe http://localhost:8080/actuator/health/readiness    # ¿puede atender? (incluye la base)
curl.exe -u admin:admin123 http://localhost:8080/actuator/metrics/academico.resumen.ejecuciones
```

## 5. Ver logs

```powershell
docker compose logs app            # todo el log de la aplicación
docker compose logs -f app         # seguirlo en vivo (Ctrl+C para salir)
docker compose logs --tail 50 postgres
```

Cada minuto aparece en el log la línea `Total de inscripciones=...`, que escribe la tarea programada.

## 6. Cerrar

```powershell
docker compose down
```

Esto **conserva los datos**, porque el volumen `datos_academicos` no se borra. No uses `docker compose down -v`: la opción `-v` elimina el volumen y con él toda la base.

## Documentos relacionados

- [EVIDENCIA.md](EVIDENCIA.md): comandos, resultados esperados y resultados reales del reto.
- [docs/decision-cache.md](docs/decision-cache.md): qué se cachea, cómo se invalida y sus límites.
- [docs/acceso-propietario.md](docs/acceso-propietario.md): regla para que un alumno vea solo su registro.
