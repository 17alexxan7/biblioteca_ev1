package cl.triskeledu.catalogo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requiere MySQL, Eureka y JWT_SECRET; la integracion se verifica con el stack de Docker Compose")
@SpringBootTest
class BibliotecaCatalogoApplicationTests {

	@Test
	void contextLoads() {
	}

}
