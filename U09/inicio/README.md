# Pruebas del contrato HTTP y de unicidad en PostgreSQL

Este proyecto incluye dos pruebas para la creación de alumnos. Aunque ambas
tratan el flujo de creación, verifican responsabilidades distintas y se
ejecutan en capas diferentes.

## Respuesta HTTP de creación

`AlumnoControllerTest.crearDevuelveCreatedYLocation` usa `MockMvc` y un
`AlumnoService` simulado. Comprueba que un `POST /api/v1/alumnos` exitoso
responda con `201 Created`, que `Location` apunte al recurso creado
(`/api/v1/alumnos/42`) y que el cuerpo incluya su identificador.

Esta prueba detecta errores en el contrato del controlador, por ejemplo
responder `200 OK`, omitir `Location` o construir una ruta incorrecta. Como el
servicio está simulado, no comprueba que la base de datos persista el alumno ni
que aplique restricciones.

## Unicidad real en PostgreSQL

`PostgresIntegrationTest.postgresRechazaMatriculaDuplicadaConRestriccionUnica`
corre contra PostgreSQL 17 mediante Testcontainers. Inserta una matrícula y
trata de insertar la misma otra vez directamente a través del repositorio JPA,
sin pasar por la comprobación previa del servicio. Verifica que PostgreSQL
rechace la segunda inserción con SQLSTATE `23505` y la restricción
`uk_alumnos_matricula`.

Así se comprueba la protección persistente de la base de datos, incluso si una
solicitud evita la validación del servicio. Esta prueba no comprueba el estado
HTTP ni la cabecera `Location`.

## Por qué hacen falta ambas

Una respuesta HTTP correcta no garantiza que la base de datos impida
duplicados; y una restricción de unicidad no garantiza que la API use el
contrato HTTP esperado. La primera prueba aísla la interfaz HTTP, mientras que
la segunda verifica la integridad de los datos en el motor real. Por eso se
complementan, pero ninguna sustituye a la otra.

Como comprobación de que detectan sus respectivos problemas, se probó
temporalmente una respuesta `200` en el controlador y la prueba HTTP falló por
el estado esperado. También se retiró temporalmente la restricción única de la
migración y PostgreSQL aceptó la matrícula duplicada, haciendo fallar la
prueba de integración. Ambas implementaciones se restauraron después.

## Ejecución

Desde PowerShell, en `U09\inicio`:

```powershell
.\mvnw.cmd '-Dtest=AlumnoControllerTest,PostgresIntegrationTest' test
```

La prueba del controlador no necesita PostgreSQL. La prueba de integración
requiere Docker en ejecución para levantar su contenedor PostgreSQL.
