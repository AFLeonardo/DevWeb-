package mx.edu.backendacademico.repository.jpa;

// Spring Data construye una implementación a partir de la interfaz y sus consultas.
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AlumnoSpringDataRepository extends JpaRepository<AlumnoEntity, Long> {
    Optional<AlumnoEntity> findByMatriculaIgnoreCase(String matricula);
    boolean existsByMatriculaIgnoreCase(String matricula);
}
