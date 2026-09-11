# Biblioteca-Springboot — Sistema de Microservicios

Sistema de biblioteca basado en microservicios con Spring Boot, desarrollado como parte de la evaluación de Arquitectura de Software (EV1 DevOps). Este documento describe la estrategia de control de versiones y flujo de trabajo colaborativo utilizada sobre el microservicio `ms-catalogo`.

## Estructura del proyecto

Monorepo Maven con los siguientes módulos:

- `api-gateway` — punto de entrada único al sistema
- `eureka` — servidor de descubrimiento de servicios
- `common` — clases y utilidades compartidas entre microservicios
- `ms-catalogo` — gestión de libros y categorías (**módulo foco de esta evaluación**)
- `ms-recursos` — gestión de recursos bibliográficos
- `ms-usuarios` — gestión de usuarios

`ms-catalogo`, `ms-usuarios` y `ms-recursos` dependen de `common`, por lo que el monorepo se mantiene completo para no romper el build, aunque el trabajo de ramas/commits de esta evaluación se concentró en `ms-catalogo`.

## Estrategia de branching: GitFlow

Se optó por **GitFlow** por sobre trunk-based development por las siguientes razones:

1. **Separación clara entre código estable y en desarrollo.** `main` representa siempre una versión desplegable/estable del sistema, mientras que `develop` concentra la integración de nuevas funcionalidades antes de pasar a producción. Esto reduce el riesgo de introducir cambios no probados directamente en la rama principal.
2. **Trazabilidad por tipo de cambio.** Al tener ramas `feature/` y `hotfix/` explícitas, el historial de Git deja claro qué commits corresponden a una nueva funcionalidad y cuáles a una corrección urgente, algo que trunk-based (con commits directos y flags de features) tiende a diluir.
3. **Adecuado para el contexto de la evaluación.** El encargo pide simular un flujo colaborativo con revisión de código vía Pull Request antes de integrar cambios. GitFlow encaja naturalmente con ese requisito porque cada rama de trabajo nace y muere a través de un PR, mientras que trunk-based generalmente asume equipos con CI/CD muy maduro e integración casi continua a `main`, lo cual no es el escenario de este proyecto (equipo pequeño, sin pipeline de despliegue automático).
4. **Manejo explícito de hotfixes.** GitFlow define un camino directo para corregir errores en producción (`hotfix/` nace desde `main` y se reintegra tanto a `main` como a `develop`), evitando que una corrección urgente quede atrapada detrás de features en desarrollo que aún no están listas para salir.

>  *Nota personal Considero que es una buena forma de mantener el orden y algo que igual te protegue de no cometer errores muy graves , para temas laborales o para proyectos compartidos . considero que el uso de GitFlow es Fundamental...*


    *Alexander Mercado*

### Estructura de ramas

```
main                        ← versión estable/desplegable
  └── develop                ← integración de funcionalidades en desarrollo
        ├── feature/<nombre> ← nuevas funcionalidades, nace y muere en develop
        └── (hotfix/<nombre> se reintegra aquí también, tras pasar por main)
main
  └── hotfix/<nombre>        ← corrección urgente, nace desde main
```

### Convención de nombres de ramas

| Tipo | Formato | Ejemplo |
|---|---|---|
| Funcionalidad | `feature/<nombre-descriptivo>` | `feature/busqueda-por-nombre` |
| Corrección urgente | `hotfix/<nombre-descriptivo>` | `hotfix/validar-busqueda-titulo-vacio` |

Los nombres van en minúsculas, con palabras separadas por guion medio, describiendo brevemente el propósito del cambio (no el ticket ni la fecha).

### Convención de mensajes de commit

Se usa un formato inspirado en [Conventional Commits](https://www.conventionalcommits.org/):

| Prefijo | Uso |
|---|---|
| `feat:` | nueva funcionalidad |
| `fix:` | corrección de errores |
| `chore:` | tareas de mantenimiento (configuración, estructura, dependencias) |

Ejemplos usados en este repositorio:
- `feat: agregar endpoint de busqueda de libros por titulo`
- `feat: agregar endpoint de listado de libros por categoria`
- `fix: validar ISBN vacio en existsByIsbn`
- `chore: estructura inicial del proyecto Biblioteca-Springboot`

### Estrategia de merge y revisión de código

1. Toda funcionalidad o corrección se desarrolla en su propia rama (`feature/` o `hotfix/`), nunca directamente sobre `main` o `develop`.
2. Al finalizar el desarrollo se hace `push` de la rama y se abre un **Pull Request**:
   - `feature/*` → PR hacia `develop`
   - `hotfix/*` → PR hacia `main`
3. Cada PR pasa por una **revisión de código** (review/approve) antes de mergear, documentando en la descripción del PR qué cambia y por qué.
4. Tras aprobar, se realiza el merge y se elimina la rama de trabajo para mantener el repositorio limpio.
5. Todo `hotfix` aplicado a `main` se sincroniza de vuelta hacia `develop` mediante un merge, para que la corrección no se pierda en el próximo ciclo de desarrollo.

### Flujo aplicado en este repositorio

| Rama | Origen | Destino (PR) | Commit principal |
|---|---|---|---|
| `feature/busqueda-por-nombre` | `develop` | `develop` (PR #1) | `824b241` |
| `feature/libros-por-categoria` | `develop` | `develop` (PR #2) | `9079bf6` |
| `hotfix/validar-busqueda-titulo-vacio` | `main` | `main` (PR #3) | `26560f7` |

Tras el merge del hotfix a `main`, se sincronizó `develop` mediante `git merge main` para incorporar la corrección al flujo de desarrollo en curso.


## Declaración de uso de IA

Este trabajo utilizó asistencia de IA (Claude, Anthropic) como apoyo para: estructurar el flujo de ramas GitFlow, redactar la documentación técnica de este README, y resolver dudas puntuales de comandos Git durante la implementación. Las decisiones de diseño, la reflexión individual y la justificación técnica final fueron revisadas y adaptadas por el autor.


## Nota sobre CI

El workflow de GitHub Actions ejecuta únicamente la compilación del proyecto (`mvn compile`). Los tests de integración no se incluyen en el pipeline porque requieren una instancia de MySQL configurada, que no está disponible en el runner de GitHub Actions. Para ejecutar los tests, se debe contar con MySQL local configurado según las credenciales de `application.properties`.

