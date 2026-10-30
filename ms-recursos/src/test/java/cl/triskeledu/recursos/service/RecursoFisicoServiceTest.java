package cl.triskeledu.recursos.service;

import cl.triskeledu.common.exception.DuplicateResourceException;
import cl.triskeledu.common.exception.EntityNotFoundException;
import cl.triskeledu.common.exception.ReferentialIntegrityException;
import cl.triskeledu.recursos.client.CatalogoClient;
import cl.triskeledu.recursos.dto.RecursoFisicoRequest;
import cl.triskeledu.recursos.dto.RecursoFisicoResponse;
import cl.triskeledu.recursos.mapper.RecursoFisicoMapper;
import cl.triskeledu.recursos.model.HistorialEventoRecursoFisico;
import cl.triskeledu.recursos.model.LibroProyeccion;
import cl.triskeledu.recursos.model.MantenimientoRecurso;
import cl.triskeledu.recursos.model.RecursoFisico;
import cl.triskeledu.recursos.repository.LibroProyeccionRepository;
import cl.triskeledu.recursos.repository.RecursoFisicoRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para RecursoFisicoService.
 * Se aíslan repositorios, mapper y cliente Feign con Mockito
 * (sin BD, sin Eureka y sin contexto Spring).
 */
@ExtendWith(MockitoExtension.class)
class RecursoFisicoServiceTest {

    @Mock
    private RecursoFisicoRepository recursoRepository;

    @Mock
    private LibroProyeccionRepository libroProyeccionRepository;

    @Mock
    private RecursoFisicoMapper recursoMapper;

    @Mock
    private CatalogoClient catalogoClient;

    @InjectMocks
    private RecursoFisicoService recursoService;

    private final Faker faker = new Faker();

    @BeforeEach
    void setUp() {
        // Simula RecursoFisico -> RecursoFisicoResponse aplanando el libro.
        lenient().when(recursoMapper.toResponse(any(RecursoFisico.class))).thenAnswer(inv -> {
            RecursoFisico r = inv.getArgument(0);
            RecursoFisicoResponse resp = new RecursoFisicoResponse();
            resp.setId(r.getId());
            resp.setSku(r.getSku());
            resp.setTipoRecurso(r.getTipoRecurso());
            resp.setEstadoFisico(r.getEstadoFisico());
            resp.setDisponible(r.getDisponible());
            if (r.getLibro() != null) {
                resp.setIsbn(r.getLibro().getIsbn());
                resp.setTitulo(r.getLibro().getTitulo());
            }
            return resp;
        });

        lenient().when(recursoMapper.toResponseList(anyList())).thenAnswer(inv -> {
            List<RecursoFisico> recursos = inv.getArgument(0);
            return recursos.stream().map(recursoMapper::toResponse).toList();
        });

        // updateEntity es void: se copia lo que copiaría el mapper real.
        lenient().doAnswer(inv -> {
            RecursoFisicoRequest req = inv.getArgument(0);
            RecursoFisico r = inv.getArgument(1);
            r.setSku(req.getSku());
            r.setTipoRecurso(req.getTipoRecurso());
            r.setEstadoFisico(req.getEstadoFisico());
            r.setDisponible(req.getDisponible());
            return null;
        }).when(recursoMapper).updateEntity(any(RecursoFisicoRequest.class), any(RecursoFisico.class));
    }

    //  Helpers 

    private String fakeSku() {
        return "SKU-" + faker.number().digits(6);
    }

    private String fakeIsbn() {
        return "978" + faker.number().digits(10);
    }

    private LibroProyeccion crearProyeccion(String isbn) {
        return LibroProyeccion.builder().isbn(isbn).titulo(faker.book().title()).build();
    }

    private RecursoFisico crearRecurso(Long id) {
        return RecursoFisico.builder()
                .id(id)
                .sku(fakeSku())
                .tipoRecurso("Notebook")
                .estadoFisico("Excelente")
                .disponible(true)
                .build();
    }

    private RecursoFisicoRequest crearRequest(String tipo, String isbn) {
        RecursoFisicoRequest r = new RecursoFisicoRequest();
        r.setSku(fakeSku());
        r.setTipoRecurso(tipo);
        r.setIsbn(isbn);
        r.setEstadoFisico("Buen estado");
        r.setDisponible(true);
        return r;
    }

    //  Consultas 

    @Test
    void findAll_DeberiaRetornarListaDeRecursos_CuandoExistenRegistros() {
        RecursoFisico r1 = crearRecurso(1L);
        RecursoFisico r2 = crearRecurso(2L);
        when(recursoRepository.findAll()).thenReturn(List.of(r1, r2));

        List<RecursoFisicoResponse> resultado = recursoService.findAll();

        assertEquals(2, resultado.size());
        assertEquals(r1.getSku(), resultado.get(0).getSku());
        verify(recursoRepository).findAll();
    }

    @Test
    void findById_DeberiaRetornarRecurso_CuandoIdExiste() {
        RecursoFisico r = crearRecurso(10L);
        when(recursoRepository.findById(10L)).thenReturn(Optional.of(r));

        RecursoFisicoResponse resultado = recursoService.findById(10L);

        assertEquals(10L, resultado.getId());
        assertEquals(r.getSku(), resultado.getSku());
    }

    @Test
    void findById_DeberiaLanzarEntityNotFoundException_CuandoIdNoExiste() {
        when(recursoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.findById(999L));
    }

    @Test
    void findBySku_DeberiaRetornarRecurso_CuandoSkuExiste() {
        RecursoFisico r = crearRecurso(3L);
        when(recursoRepository.findBySku(r.getSku())).thenReturn(Optional.of(r));

        RecursoFisicoResponse resultado = recursoService.findBySku(r.getSku());

        assertEquals(r.getSku(), resultado.getSku());
    }

    @Test
    void findBySku_DeberiaLanzarEntityNotFoundException_CuandoSkuNoExiste() {
        String sku = fakeSku();
        when(recursoRepository.findBySku(sku)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.findBySku(sku));
    }

    @Test
    void findByDisponible_DeberiaRetornarSoloRecursosFiltrados() {
        RecursoFisico r = crearRecurso(4L);
        when(recursoRepository.findByDisponible(true)).thenReturn(List.of(r));

        List<RecursoFisicoResponse> resultado = recursoService.findByDisponible(true);

        assertEquals(1, resultado.size());
        verify(recursoRepository).findByDisponible(true);
    }

    @Test
    void findByTipoRecurso_DeberiaRetornarSoloRecursosDelTipo() {
        RecursoFisico r = crearRecurso(5L);
        when(recursoRepository.findByTipoRecurso("Notebook")).thenReturn(List.of(r));

        List<RecursoFisicoResponse> resultado = recursoService.findByTipoRecurso("Notebook");

        assertEquals(1, resultado.size());
        assertEquals("Notebook", resultado.get(0).getTipoRecurso());
    }

    //  create 

    @Test
    void create_DeberiaGuardarRecursoDisponibleTipoLibro_CuandoSkuEsUnicoEIsbnExiste() {
        String isbn = fakeIsbn();
        // El request dice Notebook, pero create siempre fuerza "Libro".
        RecursoFisicoRequest request = crearRequest("Notebook", isbn);
        LibroProyeccion proyeccion = crearProyeccion(isbn);
        when(recursoRepository.findBySku(request.getSku())).thenReturn(Optional.empty());
        when(catalogoClient.existsByIsbn(isbn)).thenReturn(true);
        when(libroProyeccionRepository.findByIsbn(isbn)).thenReturn(Optional.of(proyeccion));
        when(recursoRepository.save(any(RecursoFisico.class))).thenAnswer(inv -> {
            RecursoFisico r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        RecursoFisicoResponse resultado = recursoService.create(request);

        assertEquals(100L, resultado.getId());
        assertEquals(isbn, resultado.getIsbn());
        assertEquals(proyeccion.getTitulo(), resultado.getTitulo());

        ArgumentCaptor<RecursoFisico> captor = ArgumentCaptor.forClass(RecursoFisico.class);
        verify(recursoRepository).save(captor.capture());
        RecursoFisico guardado = captor.getValue();
        assertEquals("Libro", guardado.getTipoRecurso(), "create fuerza el tipo Libro");
        assertTrue(guardado.getDisponible(), "El recurso nuevo debe quedar disponible");
        assertSame(proyeccion, guardado.getLibro());
    }

    @Test
    void create_DeberiaLanzarDuplicateResourceException_CuandoSkuYaExiste() {
        RecursoFisicoRequest request = crearRequest("Libro", fakeIsbn());
        RecursoFisico existente = crearRecurso(2L);
        existente.setSku(request.getSku());
        when(recursoRepository.findBySku(request.getSku())).thenReturn(Optional.of(existente));

        assertThrows(DuplicateResourceException.class, () -> recursoService.create(request));

        verify(catalogoClient, never()).existsByIsbn(anyString());
        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    @Test
    void create_DeberiaLanzarEntityNotFoundException_CuandoIsbnNoExisteEnCatalogo() {
        RecursoFisicoRequest request = crearRequest("Libro", fakeIsbn());
        when(recursoRepository.findBySku(request.getSku())).thenReturn(Optional.empty());
        when(catalogoClient.existsByIsbn(request.getIsbn())).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> recursoService.create(request));

        verify(libroProyeccionRepository, never()).findByIsbn(anyString());
        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    @Test
    void create_DeberiaLanzarEntityNotFoundException_CuandoNoExisteProyeccionLocal() {
        RecursoFisicoRequest request = crearRequest("Libro", fakeIsbn());
        when(recursoRepository.findBySku(request.getSku())).thenReturn(Optional.empty());
        when(catalogoClient.existsByIsbn(request.getIsbn())).thenReturn(true);
        when(libroProyeccionRepository.findByIsbn(request.getIsbn())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.create(request));

        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    //  update 

    @Test
    void update_NoDeberiaValidarSkuNiLibro_CuandoElSkuNoCambiaYElTipoNoEsLibro() {
        RecursoFisico existente = crearRecurso(5L);
        RecursoFisicoRequest request = crearRequest("Tablet", null);
        // Mismo SKU con otro case: la comparación es equalsIgnoreCase.
        request.setSku(existente.getSku().toLowerCase());
        when(recursoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(recursoRepository.save(any(RecursoFisico.class))).thenAnswer(inv -> inv.getArgument(0));

        RecursoFisicoResponse resultado = recursoService.update(5L, request);

        assertEquals("Tablet", resultado.getTipoRecurso());
        assertNull(resultado.getIsbn(), "Un recurso que no es Libro no tiene ISBN");
        verify(recursoRepository, never()).findBySku(anyString());
        verify(libroProyeccionRepository, never()).findById(anyString());
        verify(recursoRepository).save(any(RecursoFisico.class));
    }

    @Test
    void update_DeberiaAsociarLibro_CuandoElTipoEsLibroYElSkuCambiaYEsUnico() {
        String isbn = fakeIsbn();
        RecursoFisico existente = crearRecurso(5L);
        RecursoFisicoRequest request = crearRequest("Libro", isbn);
        LibroProyeccion proyeccion = crearProyeccion(isbn);
        when(recursoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(recursoRepository.findBySku(request.getSku())).thenReturn(Optional.empty());
        // update busca la proyección con findById(isbn).
        when(libroProyeccionRepository.findById(isbn)).thenReturn(Optional.of(proyeccion));
        when(recursoRepository.save(any(RecursoFisico.class))).thenAnswer(inv -> inv.getArgument(0));

        RecursoFisicoResponse resultado = recursoService.update(5L, request);

        assertEquals(isbn, resultado.getIsbn());
        assertEquals(proyeccion.getTitulo(), resultado.getTitulo());
        verify(recursoRepository).findBySku(request.getSku());
    }

    @Test
    void update_DeberiaLanzarDuplicateResourceException_CuandoElNuevoSkuPerteneceAOtro() {
        RecursoFisico existente = crearRecurso(5L);
        RecursoFisicoRequest request = crearRequest("Notebook", null);
        RecursoFisico otro = crearRecurso(8L);
        otro.setSku(request.getSku());
        when(recursoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(recursoRepository.findBySku(request.getSku())).thenReturn(Optional.of(otro));

        assertThrows(DuplicateResourceException.class, () -> recursoService.update(5L, request));

        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    @Test
    void update_DeberiaLanzarIllegalArgumentException_CuandoElTipoEsLibroYNoHayIsbn() {
        RecursoFisico existente = crearRecurso(5L);
        RecursoFisicoRequest request = crearRequest("Libro", "  ");
        request.setSku(existente.getSku());
        when(recursoRepository.findById(5L)).thenReturn(Optional.of(existente));

        assertThrows(IllegalArgumentException.class, () -> recursoService.update(5L, request));

        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    @Test
    void update_DeberiaLanzarEntityNotFoundException_CuandoElIsbnNoExisteEnLaProyeccion() {
        String isbn = fakeIsbn();
        RecursoFisico existente = crearRecurso(5L);
        RecursoFisicoRequest request = crearRequest("Libro", isbn);
        request.setSku(existente.getSku());
        when(recursoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(libroProyeccionRepository.findById(isbn)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.update(5L, request));

        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    @Test
    void update_DeberiaLanzarEntityNotFoundException_CuandoIdNoExiste() {
        when(recursoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> recursoService.update(404L, crearRequest("Notebook", null)));

        verify(recursoRepository, never()).save(any(RecursoFisico.class));
    }

    //  deleteById 

    @Test
    void deleteById_DeberiaEliminarRecurso_CuandoNoTieneHistorialNiMantenimientos() {
        RecursoFisico r = crearRecurso(15L);
        when(recursoRepository.findById(15L)).thenReturn(Optional.of(r));

        recursoService.deleteById(15L);

        verify(recursoRepository).delete(r);
    }

    @Test
    void deleteById_DeberiaLanzarReferentialIntegrityException_CuandoTieneHistorial() {
        RecursoFisico r = crearRecurso(15L);
        r.getHistorial().add(mock(HistorialEventoRecursoFisico.class));
        when(recursoRepository.findById(15L)).thenReturn(Optional.of(r));

        assertThrows(ReferentialIntegrityException.class, () -> recursoService.deleteById(15L));

        verify(recursoRepository, never()).delete(any(RecursoFisico.class));
    }

    @Test
    void deleteById_DeberiaLanzarReferentialIntegrityException_CuandoTieneMantenimientos() {
        RecursoFisico r = crearRecurso(15L);
        r.getMantenimientos().add(mock(MantenimientoRecurso.class));
        when(recursoRepository.findById(15L)).thenReturn(Optional.of(r));

        assertThrows(ReferentialIntegrityException.class, () -> recursoService.deleteById(15L));

        verify(recursoRepository, never()).delete(any(RecursoFisico.class));
    }

    @Test
    void deleteById_DeberiaLanzarEntityNotFoundException_CuandoNoExiste() {
        when(recursoRepository.findById(15L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> recursoService.deleteById(15L));

        verify(recursoRepository, never()).delete(any(RecursoFisico.class));
    }

    //  existsByIsbn 

    @Test
    void existsByIsbn_DeberiaRetornarTrue_CuandoHayRecursosConEseIsbn() {
        String isbn = fakeIsbn();
        when(recursoRepository.existsByLibroIsbn(isbn)).thenReturn(true);

        assertTrue(recursoService.existsByIsbn(isbn));
        verify(recursoRepository).existsByLibroIsbn(isbn);
    }
}