-- Evoluciona el esquema existente: no recrea la tabla ni toca V1 (su checksum ya está en el historial).
-- El dominio Java rechaza nombres en blanco con isBlank(); la base aplica ahora la misma regla
-- para escrituras que no pasen por la aplicación. Se exige al menos un carácter que no sea espacio
-- en blanco (cubre también tabuladores y saltos de línea, que TRIM no elimina).
-- Al añadir el CHECK, PostgreSQL revisa las filas ya guardadas: si alguna lo incumple, la sentencia
-- falla y Flyway revierte la transacción completa, sin registrar V2 como aplicada.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (nombre ~ '[^[:space:]]');
