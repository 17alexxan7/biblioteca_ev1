package cl.triskeledu.usuarios.service;

import cl.triskeledu.common.security.JwtProperties;
import cl.triskeledu.common.security.JwtTokenProvider;
import cl.triskeledu.usuarios.dto.LoginRequest;
import cl.triskeledu.usuarios.dto.LoginResponse;
import cl.triskeledu.usuarios.dto.RegisterRequest;
import cl.triskeledu.usuarios.dto.UsuarioResponse;
import cl.triskeledu.usuarios.mapper.UsuarioMapper;
import cl.triskeledu.usuarios.model.CredencialUsuario;
import cl.triskeledu.usuarios.model.Usuario;
import cl.triskeledu.usuarios.repository.UsuarioRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para AuthService.
 * Se aíslan repositorio, mapper, PasswordEncoder y JwtTokenProvider con Mockito
 * (sin BD, sin contexto Spring y sin necesidad de JWT_SECRET).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioMapper usuarioMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    // Objeto real: solo guarda el tiempo de expiración (por defecto 3600000 ms).
    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

    @InjectMocks
    private AuthService authService;

    private final Faker faker = new Faker();

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Usuario crearUsuario(boolean activo) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNombre(faker.name().firstName());
        u.setApellido(faker.name().lastName());
        u.setEmail(faker.internet().emailAddress());
        u.setPassword("HASH_" + faker.internet().password(8, 20));
        u.setRol("Cliente");
        u.setActivo(activo);
        return u;
    }

    private CredencialUsuario crearCredencial(Usuario u, boolean bloqueado, int intentos) {
        CredencialUsuario c = CredencialUsuario.builder()
                .usuario(u)
                .bloqueado(bloqueado)
                .intentosFallidos(intentos)
                .build();
        u.setCredencial(c);
        return c;
    }

    private LoginRequest crearLoginRequest(String email) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(faker.internet().password(8, 20));
        return r;
    }

    private RegisterRequest crearRegisterRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setNombre(faker.name().firstName());
        r.setApellido(faker.name().lastName());
        r.setEmail(faker.internet().emailAddress());
        r.setPassword(faker.internet().password(8, 20));
        return r;
    }

    // ─── login ───────────────────────────────────────────────────────────

    @Test
    void login_DeberiaRetornarTokenYResetearIntentos_CuandoCredencialesSonValidas() {
        Usuario u = crearUsuario(true);
        CredencialUsuario cred = crearCredencial(u, false, 3);
        LoginRequest request = crearLoginRequest(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(request.getPassword(), u.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generarToken(eq(u.getEmail()), eq("Cliente"), anyString()))
                .thenReturn("token-de-prueba");

        LoginResponse resultado = authService.login(request);

        assertEquals("token-de-prueba", resultado.getToken());
        assertEquals(u.getEmail(), resultado.getEmail());
        assertEquals("Cliente", resultado.getRol());
        assertEquals(u.getNombre() + " " + u.getApellido(), resultado.getNombre());
        assertEquals(3600000L, resultado.getExpiresIn());
        // Acceso exitoso: intentos reseteados y último acceso registrado.
        assertEquals(0, cred.getIntentosFallidos());
        assertNotNull(cred.getUltimoAcceso());
        verify(usuarioRepository).save(u);
    }

    @Test
    void login_DeberiaLanzarExcepcion_CuandoEmailNoExiste() {
        LoginRequest request = crearLoginRequest(faker.internet().emailAddress());
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));

        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(jwtTokenProvider, never()).generarToken(anyString(), anyString(), anyString());
    }

    @Test
    void login_DeberiaLanzarExcepcion_CuandoCuentaEstaInactiva() {
        Usuario u = crearUsuario(false);
        LoginRequest request = crearLoginRequest(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));

        assertTrue(ex.getMessage().contains("desactivada"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generarToken(anyString(), anyString(), anyString());
    }

    @Test
    void login_DeberiaLanzarExcepcion_CuandoCuentaEstaBloqueada() {
        Usuario u = crearUsuario(true);
        crearCredencial(u, true, 5);
        LoginRequest request = crearLoginRequest(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));

        assertTrue(ex.getMessage().contains("bloqueada"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generarToken(anyString(), anyString(), anyString());
    }

    @Test
    void login_DeberiaIncrementarIntentos_CuandoPasswordEsIncorrecta() {
        Usuario u = crearUsuario(true);
        CredencialUsuario cred = crearCredencial(u, false, 1);
        LoginRequest request = crearLoginRequest(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(request.getPassword(), u.getPassword())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));

        assertEquals("Credenciales inválidas", ex.getMessage());
        assertEquals(2, cred.getIntentosFallidos());
        assertFalse(cred.getBloqueado());
        verify(usuarioRepository).save(u);
        verify(jwtTokenProvider, never()).generarToken(anyString(), anyString(), anyString());
    }

    @Test
    void login_DeberiaBloquearCuenta_CuandoSeAlcanzaElMaximoDeIntentos() {
        Usuario u = crearUsuario(true);
        // Con 4 intentos previos, el siguiente fallo es el quinto.
        CredencialUsuario cred = crearCredencial(u, false, 4);
        LoginRequest request = crearLoginRequest(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(request.getPassword(), u.getPassword())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(request));

        assertEquals(5, cred.getIntentosFallidos());
        assertTrue(cred.getBloqueado(), "La cuenta debe bloquearse al 5.º intento fallido");
        verify(usuarioRepository).save(u);
    }

    @Test
    void login_DeberiaCrearCredencialConUnIntento_CuandoElUsuarioNoTeniaCredencial() {
        Usuario u = crearUsuario(true);
        LoginRequest request = crearLoginRequest(u.getEmail());
        when(usuarioRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(request.getPassword(), u.getPassword())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(request));

        assertNotNull(u.getCredencial());
        assertEquals(1, u.getCredencial().getIntentosFallidos());
        assertFalse(u.getCredencial().getBloqueado());
    }

    // ─── register ────────────────────────────────────────────────────────

    @Test
    void register_DeberiaCrearUsuarioClienteActivoConPasswordHasheada_CuandoEmailEsUnico() {
        RegisterRequest request = crearRegisterRequest();
        when(usuarioRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("HASH_" + request.getPassword());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        UsuarioResponse respuesta = new UsuarioResponse();
        respuesta.setEmail(request.getEmail());
        respuesta.setRol("Cliente");
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(respuesta);

        UsuarioResponse resultado = authService.register(request);

        assertEquals(request.getEmail(), resultado.getEmail());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals("Cliente", guardado.getRol(), "El registro público siempre asigna rol Cliente");
        assertTrue(guardado.getActivo());
        assertNotEquals(request.getPassword(), guardado.getPassword());
        assertEquals("HASH_" + request.getPassword(), guardado.getPassword());
        // Se crean credenciales por defecto.
        assertNotNull(guardado.getCredencial());
        assertFalse(guardado.getCredencial().getBloqueado());
        assertEquals(0, guardado.getCredencial().getIntentosFallidos());
    }

    @Test
    void register_DeberiaLanzarExcepcion_CuandoEmailYaExiste() {
        RegisterRequest request = crearRegisterRequest();
        when(usuarioRepository.existsByEmail(request.getEmail())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));

        assertTrue(ex.getMessage().contains(request.getEmail()));
        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}
