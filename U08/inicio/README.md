# Prueba de admisiones concurrentes

La prueba `InscripcionConcurrencyIntegrationTest` comprueba en PostgreSQL que dos alumnos distintos no puedan ocupar el único lugar de una materia al mismo tiempo. Invoca el bean Spring real `InscripcionService`; no reemplaza el servicio ni los repositorios con mocks.

## Preparación y ejecución

Se requiere Docker en ejecución y JDK 21. Desde PowerShell, en `U08\inicio`:

```powershell
docker compose up -d --wait
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=InscripcionConcurrencyIntegrationTest test
```

La aplicación usa por omisión la base local `academico` (usuario/contraseña `academico`), según `application.properties`. Si la conexión es distinta, define `DB_URL`, `DB_USER` y `DB_PASSWORD` antes de ejecutar. La prueba falla expresamente si la base no es PostgreSQL: H2 no demuestra sus bloqueos de filas ni proporciona `pg_stat_activity`.

Los datos de prueba se identifican con un sufijo aleatorio y se eliminan al terminar. La prueba necesita que Flyway haya creado las tablas y que el usuario de base de datos pueda consultar la actividad de sus propias sesiones.

## Dónde esperan los hilos

1. Dos tareas de un `ExecutorService` esperan en una barrera Java para arrancar juntas. Cada una invoca `InscripcionService.inscribir` desde un hilo distinto, por lo que Spring abre una transacción independiente para cada llamada.
2. Para que la espera sea observable y no dependa de la velocidad de la máquina, el hilo de prueba abre una tercera conexión y mantiene `SELECT ... FOR UPDATE` sobre la fila de la materia.
3. Al abrir la barrera, ambas tareas intentan adquirir el bloqueo pesimista de esa misma fila mediante `MateriaRepository.bloquearPorId`. En este punto los dos backends PostgreSQL quedan esperando un bloqueo; el hilo de prueba comprueba `wait_event_type = 'Lock'`, la consulta sobre `materias` y `pg_blocking_pids(...)`.
4. Cuando la prueba confirma las dos esperas, libera su bloqueo. PostgreSQL concede el acceso a una transacción, que inscribe al primer alumno. La segunda transacción continúa después, observa que el cupo está lleno y recibe `SIN_CUPO`. Al final se verifica que existe exactamente una inscripción.

## Evidencia de exclusión en PostgreSQL

El resultado de negocio por sí solo (una inscripción y un rechazo por cupo) no permite distinguir un bloqueo de base de datos de un `synchronized` o un `ReentrantLock` local. La evidencia de esta prueba es adicional y directa: antes de soltar la fila, exige ver **dos sesiones de PostgreSQL** con la consulta de admisión activa, esperando en un evento `Lock`; también verifica que al menos una esté bloqueada por el PID de la conexión que la prueba mantiene abierta.

Un monitor local impediría que una de las tareas llegara a PostgreSQL mientras la otra está dentro de la sección crítica. En ese caso no aparecerían dos backends de admisión esperando simultáneamente y la aserción de la prueba fallaría. La conexión bloqueadora de la prueba es sólo un mecanismo de sincronización/observación; la exclusión que serializa las llamadas de producción es el bloqueo de fila que solicita el servicio en PostgreSQL.

Como inspección manual opcional mientras la prueba espera, puede consultarse la actividad desde `psql` conectado al mismo servidor:

```sql
SELECT pid, wait_event_type, wait_event, pg_blocking_pids(pid) AS bloqueadores, query
FROM pg_stat_activity
WHERE datname = current_database()
  AND wait_event_type = 'Lock'
  AND lower(query) LIKE '%materias%';
```

El bloqueo se libera y las filas de prueba se limpian al finalizar. La prueba no activa el registro de parámetros SQL.
