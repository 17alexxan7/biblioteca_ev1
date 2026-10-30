package cl.triskeledu.usuarios.service;

import cl.triskeledu.common.exception.DuplicateResourceException;
import cl.triskeledu.common.exception.EntityNotFoundException;
import cl.triskeledu.usuarios.dto.UsuarioRequest;
import cl.triskeledu.usuarios.dto.UsuarioResponse;
import cl.triskeledu.usuarios.mapper.UsuarioMapper;
import cl.triskeledu.usuarios.model.Usuario;
import cl.triskeledu.usuarios.repository.UsuarioRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para UsuarioService.
 * Se aíslan repositorio, mapper y PasswordEncoder con Mockito (sin BD ni contexto Spring).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioMapper usuarioMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private final Faker faker = new Faker();

    @BeforeEach
    void setUp() {
        // Simula Usuario -> UsuarioResponse (sin contraseña).
        lenient().when(usuarioMapper.toResponse(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            UsuarioResponse r = new UsuarioResponse();
            r.setId(u.getId());
            r.setNombre(u.getNombre());
            r.setApellido(u.getApellido());
            r.setEmail(u.getEmail());
            r.setRol(u.getRol());
            r.setActivo(u.getActivo());
            return r;
        });

        // Simula la conversión de listas.
        lenient().when(usuarioMapper.toResponseList(anyList())).thenAnswer(inv -> {
            List<Usuario> usuarios = inv.getArgument(0);
            return usuarios.stream().map(usuarioMapper::toResponse).toList();
        });

        // updateEntity es void: se usa doAnswer para copiar los datos del request.
        lenient().doAnswer(inv -> {
            UsuarioRequest req = inv.getArgument(0);
            Usuario u = inv.getArgument(1);
            u.setNombre(req.getNombre());
            u.setApellido(req.getApellido());
            u.setEmail(req.getEmail());
            u.setRol(req.getRol());
            return null;
        }).when(usuarioMapper).updateEntity(any(UsuarioRequest.class), any(Usuario.class));

        // Simula el hash: devuelve un valor distinto al original.
        lenient().when(passwordEncoder.encode(anyString()))
                .thenAnswer(inv -> "HASH_" + inv.getArgument(0));
    }

    private Usuario crearUsuarioSimulado(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre(faker.name().firstName());
        u.setApellido(faker.name().lastName());
        u.setEmail(faker.internet().emailAddress());
        u.setPassword("HASH_" + faker.internet().password(8, 20));
        u.setRol("Cliente");
        u.setActivo(true);
        return u;
    }

    private UsuarioRequest crearRequestSimulado() {
        UsuarioRequest r = new UsuarioRequest();
        r.setNombre(faker.name().firstName());
        r.setApellido(faker.name().lastName());
        r.setEmail(faker.internet().emailAddress());
        r.setPassword(faker.internet().password(8, 20));
        r.setRol("Cliente");
        return r;
    }

    // ─── findAll / findById / findByEmail ────────────────────────────────

    @Test
    void findAll_DeberiaRetornarListaDeUsuarios_CuandoExistenRegistros() {
        Usuario u1 = crearUsuarioSimulado(1L);
        Usuario u2 = crearUsuarioSimulado(2L);
        when(usuarioRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UsuarioResponse> resultado = usuarioService.findAll();

        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        assertEquals(u1.getEmail(), resultado.get(0).getEmail());
        verify(usuarioRepository).findAll();
    }

    @Test
    void findById_DeberiaRetornarUsuario_CuandoIdExiste() {
        Usuario u = crearUsuarioSimulado(10L);
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        UsuarioResponse resultado = usuarioService.findById(10L);

        assertNotNull(resultado);
        assertEquals(u.getId(), resultado.getId());
        assertEquals(u.getEmail(), resultado.getEmail());
        verify(usuarioRepository).findById(10L);
    }

    @Test
    void findById_DeberiaLanzarEntityNotFoundException_CuandoIdNoExiste() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.findById(999L));
        verify(usuarioRepository).findById(999L);
    }

    @Test
    void findByEmail_DeberiaRetornarUsuario_CuandoEmailExiste() {
        Usuario u = crearUsuarioSimulado(3L);
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));

        UsuarioResponse resultado = usuarioService.findByEmail(u.getEmail());

        assertEquals(u.getEmail(), resultado.getEmail());
        verify(usuarioRepository).findByEmail(u.getEmail());
    }

    @Test
    void findByEmail_DeberiaLanzarEntityNotFoundException_CuandoEmailNoExiste() {
        String email = faker.internet().emailAddress();
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.findByEmail(email));
    }

    // ─── create ──────────────────────────────────────────────────────────

    @Test
    void create_DeberiaGuardarUsuarioActivoConPasswordHasheada_CuandoEmailEsUnico() {
        UsuarioRequest request = crearRequestSimulado();
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(100L);
            return u;
        });

        UsuarioResponse resultado = usuarioService.create(request);

        assertNotNull(resultado);
        assertEquals(100L, resultado.getId());
        assertEquals(request.getEmail(), resultado.getEmail());
        assertTrue(resultado.getActivo(), "El usuario nuevo debe quedar activo");

        // La contraseña guardada debe ser el hash, nunca el texto plano.
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNotEquals(request.getPassword(), captor.getValue().getPassword());
        assertEquals("HASH_" + request.getPassword(), captor.getValue().getPassword());
        verify(passwordEncoder).encode(request.getPassword());
    }

    @Test
    void create_DeberiaLanzarDuplicateResourceException_CuandoEmailYaExiste() {
        UsuarioRequest request = crearRequestSimulado();
        Usuario existente = crearUsuarioSimulado(2L);
        existente.setEmail(request.getEmail());
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(existente));

        assertThrows(DuplicateResourceException.class, () -> usuarioService.create(request));

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ─── update ──────────────────────────────────────────────────────────

    @Test
    void update_NoDeberiaValidarUnicidad_CuandoElEmailNoCambia() {
        Usuario existente = crearUsuarioSimulado(5L);
        UsuarioRequest request = crearRequestSimulado();
        // Mismo email pero con distinto case: la comparación es equalsIgnoreCase.
        request.setEmail(existente.getEmail().toUpperCase());
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse resultado = usuarioService.update(5L, request);

        assertNotNull(resultado);
        assertEquals(request.getNombre(), resultado.getNombre());
        verify(usuarioRepository, never()).findByEmail(anyString());
        verify(usuarioRepository).save(any(Usuario.class));
        verify(passwordEncoder).encode(request.getPassword());
    }

    @Test
    void update_DeberiaActualizar_CuandoElEmailCambiaYEsUnico() {
        Usuario existente = crearUsuarioSimulado(5L);
        UsuarioRequest request = crearRequestSimulado();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse resultado = usuarioService.update(5L, request);

        assertEquals(request.getEmail(), resultado.getEmail());
        verify(usuarioRepository).findByEmail(request.getEmail());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void update_DeberiaLanzarDuplicateResourceException_CuandoElNuevoEmailPerteneceAOtro() {
        Usuario existente = crearUsuarioSimulado(5L);
        UsuarioRequest request = crearRequestSimulado();
        Usuario otro = crearUsuarioSimulado(8L);
        otro.setEmail(request.getEmail());
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(otro));

        assertThrows(DuplicateResourceException.class, () -> usuarioService.update(5L, request));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void update_DeberiaLanzarEntityNotFoundException_CuandoIdNoExiste() {
        when(usuarioRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> usuarioService.update(404L, crearRequestSimulado()));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // ─── deleteById ──────────────────────────────────────────────────────

    @Test
    void deleteById_DeberiaEliminarUsuario_CuandoExiste() {
        Usuario u = crearUsuarioSimulado(15L);
        when(usuarioRepository.findById(15L)).thenReturn(Optional.of(u));

        usuarioService.deleteById(15L);

        verify(usuarioRepository).delete(u);
    }

    @Test
    void deleteById_DeberiaLanzarEntityNotFoundException_CuandoNoExiste() {
        when(usuarioRepository.findById(15L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.deleteById(15L));

        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    // ─── activar / desactivar ────────────────────────────────────────────

    @Test
    void activar_DeberiaMarcarUsuarioComoActivo() {
        Usuario u = crearUsuarioSimulado(20L);
        u.setActivo(false);
        when(usuarioRepository.findById(20L)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse resultado = usuarioService.activar(20L);

        assertTrue(resultado.getActivo());
        verify(usuarioRepository).save(u);
    }

    @Test
    void desactivar_DeberiaMarcarUsuarioComoInactivo() {
        Usuario u = crearUsuarioSimulado(21L);
        u.setActivo(true);
        when(usuarioRepository.findById(21L)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse resultado = usuarioService.desactivar(21L);

        assertFalse(resultado.getActivo());
        verify(usuarioRepository).save(u);
    }

    @Test
    void activar_DeberiaLanzarEntityNotFoundException_CuandoIdNoExiste() {
        when(usuarioRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> usuarioService.activar(77L));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
}
