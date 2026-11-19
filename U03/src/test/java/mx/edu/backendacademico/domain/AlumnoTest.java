package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        // Comprueba tanto el resultado como la ausencia de efectos laterales.
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void reactivarConservaLosDatosSinModificarElOriginal() {
        var baja = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.BAJA);

        var activo = baja.reactivar();

        assertNotSame(baja, activo);
        assertEquals(new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO), activo);
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
    }

    @Test void igualdadDeValorNoEsIdentidadAcademica() {
        var institucional = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var alternativo = new Alumno(1L, "A001", "Ada", "ada.personal@u.mx", EstatusAlumno.ACTIVO);

        // El record compara todos sus componentes; la matrícula expresa identidad académica.
        assertEquals(institucional.id(), alternativo.id());
        assertEquals(institucional.matricula(), alternativo.matricula());
        assertNotEquals(institucional, alternativo);
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
            new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }
}
