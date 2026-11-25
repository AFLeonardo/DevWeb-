-- Registra cada creación de alumno solicitada con la cabecera Idempotency-Key.
-- alumno_id queda NULL sólo dentro de la transacción que reserva la clave;
-- ninguna otra transacción puede observar ese estado intermedio.
CREATE TABLE solicitudes_idempotentes (
    id BIGSERIAL PRIMARY KEY,
    clave VARCHAR(100) NOT NULL,
    solicitante VARCHAR(100) NOT NULL,
    huella VARCHAR(64) NOT NULL,
    alumno_id BIGINT,
    fecha_creacion TIMESTAMP NOT NULL,
    -- PostgreSQL decide qué solicitud gana cuando dos llegan con la misma clave.
    CONSTRAINT uk_solicitud_idempotente UNIQUE (solicitante, clave),
    CONSTRAINT ck_huella_sha256 CHECK (LENGTH(huella) = 64),
    -- Si el alumno se elimina, su registro de idempotencia deja de tener sentido.
    CONSTRAINT fk_solicitud_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos(id) ON DELETE CASCADE
);
