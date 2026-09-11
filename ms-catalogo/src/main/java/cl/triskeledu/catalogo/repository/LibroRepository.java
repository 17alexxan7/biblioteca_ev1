package cl.triskeledu.catalogo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import cl.triskeledu.catalogo.model.Libro;
import java.util.Optional;

@Repository
public interface LibroRepository extends JpaRepository<Libro, Long> {
    
    Optional<Libro> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);
    List<Libro> findByTituloContainingIgnoreCase(String titulo);
    List<Libro> findByCategorias_Id(Long categoriaId);
    
}

