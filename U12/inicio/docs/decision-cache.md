# Decisión: caché de alumnos

**Estado:** aceptada (laboratorio U12)
**Código:** `config/CacheConfig.java` y `service/AlumnoService.java`

## Qué se cachea

| Caché | Método | Clave | Contenido |
|---|---|---|---|
| `alumnosLista` | `AlumnoService.listar()` | `SimpleKey.EMPTY` (el método no tiene parámetros) | La lista completa de alumnos |
| `alumnos` | `AlumnoService.buscar(id)` | `#id` | Un alumno por su id |

Solo se cachean **lecturas de alumnos**, que se consultan seguido y cambian poco. Las inscripciones y el cupo no se cachean.

## Por qué la clave es suficiente

- `listar()` no recibe parámetros, así que su resultado depende únicamente de los datos y una sola entrada basta.
- `buscar(id)` depende solo del `id`. El resultado no cambia según quién pregunte: la regla de propietario se revisa en el controlador, **después** de leer de la caché. Por eso no hace falta meter el usuario en la clave, y un alumno nunca recibe algo que otro dejó cacheado "a su nombre".
- Las excepciones no se cachean. Si un `id` no existe, la siguiente consulta vuelve a ir a la base.

## Invalidación

`crear`, `actualizar` y `eliminar` llevan `@CacheEvict(cacheNames = {"alumnos", "alumnosLista"}, allEntries = true)`, es decir, vacían ambas cachés. Es una medida tosca pero segura: cualquier cambio puede alterar la lista, así que no tiene caso borrar entradas una por una.

El `CacheManager` va envuelto en `TransactionAwareCacheManagerProxy`. Mientras haya una transacción activa, el proxy **pospone** el `evict` hasta el *commit*:

1. Si el `evict` ocurriera antes del commit, otra petición podría leer la base todavía sin el cambio y volver a guardar en la caché el dato viejo.
2. Con el proxy, la caché se limpia solo cuando el cambio ya es visible en la base.
3. Si la transacción hace *rollback*, la caché no se toca, y está bien, porque el dato no cambió.

En la comprobación del resultado esto se vio con `SPRING_JPA_SHOW_SQL=true`: el primer GET hizo `SELECT`, el segundo no, y después de un PUT el siguiente GET volvió a hacer `SELECT`.

## Límites conocidos

| Límite | Consecuencia | Qué haría en producción |
|---|---|---|
| **Sin TTL** | Una entrada vive hasta el próximo `evict` o hasta reiniciar. Un cambio hecho por fuera de la app (SQL directo, otra aplicación) no se ve. | Caffeine con `expireAfterWrite` |
| **Sin tamaño máximo** | `ConcurrentMapCacheManager` crece con cada `id` distinto que se consulta, y la memoria puede crecer sin control. | Caffeine con `maximumSize` |
| **No se comparte entre réplicas** | Cada instancia tiene su propia caché. Si la réplica A actualiza, la réplica B sigue sirviendo el dato viejo. | Redis o una caché distribuida, o bien no cachear |
| **Lectura obsoleta en concurrencia** | Una lectura que empezó antes del commit puede terminar después del `evict` y volver a guardar el valor viejo. El proxy reduce esa ventana, pero no la elimina. | TTL corto como red de seguridad |

Para un laboratorio con una sola instancia y pocos datos, estos límites son aceptables.

## Por qué NO se usa la caché para decidir cupo

`InscripcionService.inscribir` decide si hay cupo contando inscripciones **en la base**, dentro de una transacción y con un **bloqueo pesimista** de la materia (`materias.bloquearPorId`). Esa decisión tiene que ser exacta en el momento de la escritura:

- Un conteo cacheado puede estar viejo, ya sea por falta de TTL, por otra réplica o por concurrencia, y con eso se admitirían más alumnos que el cupo.
- La caché no participa del bloqueo. Dos peticiones simultáneas leerían el mismo valor cacheado y las dos pensarían que queda un lugar.
- Evitarse una consulta pequeña no compensa el riesgo de romper una regla de negocio.

Regla general: la caché sirve para **mostrar**, nunca para **decidir** una escritura.
