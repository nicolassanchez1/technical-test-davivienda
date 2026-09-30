# Uso de Inteligencia Artificial

> Documento obligatorio segun el apartado 6.2 del enunciado. Se anade una entrada por cada
> fase del desarrollo.

## Herramientas Utilizadas

| Herramienta             | Uso                                                                                                                    |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Claude Code (Anthropic) | Asistente principal en terminal: andamiaje del monorepo, configuracion de tooling, redaccion de tests y documentacion. |

## Casos de Uso de IA

### Fase 0 — Bootstrap del workspace

- Andamiaje del monorepo: workspace de pnpm, TypeScript en modo estricto, ESLint plano,
  Prettier, EditorConfig.
- Hooks de git con husky: `commit-msg` (commitlint + verificacion de autoria) y
  `pre-commit` (lint-staged).
- Esqueleto de Spring Boot con el endpoint de salud y sus tests.
- Configuracion de Spotless con palantir-java-format.
- Pipeline del contrato: `OpenApiContractIT` exporta el documento OpenAPI y
  `openapi-typescript` genera los tipos de `packages/shared`.
- Esqueleto de Vite + React con su test.
- `docker-compose.yml` con PostgreSQL y RabbitMQ, ambos con healthcheck.
- Workflow de CI con los trabajos `quality`, `commits`, `unit`, `contract` y `build`.

### Fase 1 — Cimientos del backend

- `@ConfigurationProperties` validados que detienen el arranque ante un valor incorrecto.
- Origen de datos PostgreSQL con `JdbcClient` y migraciones Flyway.
- Esquema de `documents` y `document_chunks`, con la configuracion de texto `es_unaccent`.
- Filtro de request id con MDC y logging estructurado ECS.
- Manejador global de errores que devuelve `ProblemDetail` (RFC 9457).
- Salud de la aplicacion con base de datos y RabbitMQ.
- Reglas ArchUnit que sostienen las fronteras hexagonales.
- Dockerfile multietapa, JaCoCo y el trabajo de integracion en CI.

Verificaciones empiricas de esta fase, de nuevo antes de construir encima:

- **Testcontainers 2.0.5** (la version que gestiona Spring Boot 4) renombro sus modulos:
  `postgresql` paso a `testcontainers-postgresql` y `junit-jupiter` a
  `testcontainers-junit-jupiter`; ademas hay que importar su BOM y la clase
  `PostgreSQLContainer` dejo de ser generica. Con esos ajustes, `@ServiceConnection`
  funciona sobre Boot 4.
- **Flyway** no arranca solo con `flyway-core` en Boot 4: la autoconfiguracion vive en
  `spring-boot-starter-flyway`. Se detecto porque un test de integracion comprobaba que la
  tabla de historial existiera, no por inspeccion.
- Las reglas de ArchUnit se probaron introduciendo a proposito una clase que las violaba,
  para confirmar que fallan cuando deben en lugar de pasar en vacio.

El stack completo se levanto con `docker compose` y se comprobo de extremo a extremo la
salud con base de datos y RabbitMQ, el eco de `X-Request-Id`, el reemplazo de un valor de
cabecera inseguro y la respuesta `application/problem+json` en una ruta inexistente.

#### Cambio de stack del backend

El `CLAUDE.md` inicial fijaba NestJS como backend. **Cambie esa decision a Java 21 con
Spring Boot**; el `CLAUDE.md` se reescribio para reflejarlo y el backend se migro.

En el momento del cambio no habia codigo de NestJS en ningun commit: el andamiaje existia
solo en el directorio de trabajo, sin subir. Por eso la migracion no necesito reescribir
historia publicada: se descarto ese trabajo sin publicar y la rama se reconstruyo sobre
`main`.

Durante la fase se verificaron de forma empirica dos supuestos del stack, en lugar de
darlos por buenos:

- **NestJS 12 es ESM puro**, lo que obligaba a replantear el runner de tests del backend.
  Fue uno de los motivos para reevaluar el stack.
- **springdoc-openapi 2.8.6 esta construido contra Spring Boot 3**, pero se comprobo que
  sirve un documento OpenAPI 3.1 valido sobre **Spring Boot 4.1.1**. Esa comprobacion es lo
  que permitio quedarse en la ultima version estable de Spring Boot en vez de bajar a una
  linea 3.5 ya fuera de soporte. El trabajo `contract` de CI es lo que mantiene honesta esa
  garantia.
- **PostgreSQL 17 con `unaccent` y la configuracion `es_unaccent`**: se valido contra la
  imagen real que "especificacion tecnica" (sin tildes) encuentra "Especificación técnica".

### Herramientas propias del repositorio

Para que el trabajo con IA fuera repetible y no dependiera de lo que un asistente recuerde
en cada sesion, las reglas del proyecto se empaquetaron como agentes y skills versionados
en `.claude/`:

| Tipo   | Nombre              | Para que sirve                                                                                          |
| ------ | ------------------- | ------------------------------------------------------------------------------------------------------- |
| Agente | `backend-engineer`  | Funcionalidad de Spring Boot: casos de uso, puertos, adaptadores JDBC, controladores y sus tests.       |
| Agente | `search-engineer`   | Camino de busqueda en PostgreSQL: `tsvector`, GIN, ranking, resaltado, `statement_timeout` y `EXPLAIN`. |
| Agente | `frontend-engineer` | SPA de React: paginas, hooks, cliente de API, conexion SSE unica y visor.                               |
| Agente | `spec-auditor`      | Solo lectura: audita una rama contra el enunciado y reporta huecos.                                     |
| Skill  | `verify`            | Ejecuta en local todas las puertas que ejecuta CI y devuelve un unico veredicto.                        |
| Skill  | `phase`             | Lleva una fase del roadmap de principio a fin con el flujo de git y PR.                                 |
| Skill  | `contract-sync`     | Regenera el contrato TypeScript desde el OpenAPI y reporta desviaciones.                                |

Cada agente lleva escritas las restricciones que no se pueden negociar (sin `LIKE`, sin
JPA, sin Lombok, sin inyeccion por campo, sin polling, sin `dangerouslySetInnerHTML`, sin
texto en espanol dentro de los componentes), de modo que la restriccion viaja con la tarea
en lugar de repetirse en cada prompt.

Los agentes implementan y reportan; la sesion principal revisa, prepara y commitea. Ningun
agente hace commits.

### Fase 2 — Carga de documentos (HU-01)

Primera fase delegada de verdad en los agentes del repositorio, no solo en la sesion
principal:

- El agente `backend-engineer` construyo el nucleo no HTTP: modelo de dominio, puertos,
  repositorio JDBC, almacenamiento en disco y validacion de la carga, con sus tests.
- Un segundo encargo al mismo agente, para la capa HTTP, **se corto por limite de sesion**
  cuando ya habia escrito la capa de aplicacion y la de infraestructura. La sesion
  principal termino la capa web y todos los tests. Queda registrado porque describe lo que
  realmente paso, no lo que estaba planeado.

Decisiones del agente que se revisaron y se aceptaron, con su motivo:

- `DuplicateDocumentException` **no** extiende `ApplicationException`. No puede: esa clase
  vive en `shared/web` y carga un `HttpStatus`, y la regla de ArchUnit prohibe que el
  dominio dependa de Spring. Quedo como excepcion de dominio y se mapea en el unico
  `@RestControllerAdvice`.
- `findAll` y `countAll` usan dos sentencias en lugar de un predicado anulable. Un
  `WHERE (CAST(:status AS text) IS NULL OR status = :status)` habria descartado el indice
  de `status`, justo en un proyecto cuya premisa es que no hay escaneos sin indice.
- La validacion de texto comprueba que no haya bytes de control, no que decodifique como
  UTF-8 estricto: el proyecto acepta TXT en Latin-1, donde `é` es `0xE9` y un decode
  estricto rechazaria un archivo valido.

Tres defectos que aparecieron al ejecutar, no al leer:

- **Spring Boot 4 usa Jackson 3** (`tools.jackson.databind`). El bean de Jackson 2 ya no
  existe y el contexto no arrancaba. Las anotaciones siguen en
  `com.fasterxml.jackson.annotation`, asi que `@JsonValue` sigue siendo valido.
- **`?status=PROCESANDO` no enlazaba**: Spring convierte enums por el nombre de la
  constante, que es ingles, y los parametros de consulta no pasan por Jackson. Con un
  `Converter` propio el valor correcto funcionaba, pero al rechazar uno invalido Spring caia
  al conversor por nombre y aceptaba `PROCESSING`, filtrando los nombres internos a la API.
  Se resuelve explicitamente en el controlador.
- **Un parametro de consulta invalido devolvia 500** en lugar de 400.

### Fase 3 — Procesamiento e indexación

Delegada en dos encargos al agente `backend-engineer`: primero extractores y chunkers
(logica pura, muy testeable), despues el pipeline del worker. El primer encargo llego a
chocar con el limite de sesion y se recupero solo.

Decisiones del agente que se revisaron:

- **Aceptada, y la verificacion humana se equivoco primero.** El agente cambio el orden de
  deteccion de codificacion: en vez de "detectar con juniversalchardet y decodificar", intenta
  primero un decode UTF-8 estricto y solo cae al detector si falla. Justifico el cambio
  diciendo que el detector lee prosa UTF-8 en espanol como una codificacion china. Lo puse a
  prueba contra la libreria con una frase corta, el detector acerto UTF-8, y di la afirmacion
  por falsa: reescribi el comentario del codigo culpando a las muestras cortas. Estaba mal.
  Al repetir la prueba contra el fixture real del repositorio, de 370 bytes, el detector
  devuelve `GB18030` y convierte `Especificación` en `Especificaci贸n`; con 24 bytes devuelve
  UTF-8. Mi muestra era justo el caso que el detector acierta. El agente tenia razon, el
  comentario quedo con el motivo reproducible, y `CLAUDE.md` se alineo con la implementacion.
  Queda anotado porque describe lo que hace util revisar: la revision encontro el error, y
  despues se encontro el suyo propio.
- **Aceptada.** Una sola sentencia con `unnest` en lugar de un lote JDBC, porque
  `JdbcClient` no expone API de lote en Spring Framework 7 y el proyecto no admite bajar de
  `JdbcClient`. Sigue siendo un solo viaje y un solo plan, y sigue siendo `EXPLAIN`-able.
- **Aceptada.** Un archivo ilegible se trata como fallo transitorio y no como veredicto sobre
  el contenido: el volumen compartido puede ir retrasado, y marcar `ERROR` ahi seria mentir.

Lo que garantiza el pipeline, y como se prueba:

- La indexacion ocurre en **una sola transaccion**, y el `UPDATE` lleva
  `AND status = 'PROCESANDO'`. Si otro consumidor ya termino el documento, la actualizacion
  afecta cero filas, la transaccion se revierte y no se anuncia nada.
- El evento de estado se publica **solo despues del commit**, igual que el job de carga.
- Un fallo determinista (PDF sin capa de texto, archivo corrupto, contenido vacio) marca
  `ERROR` con su codigo y va a la dead-letter queue sin reintentos. Un fallo transitorio se
  reintenta tres veces con backoff exponencial antes de rendirse.

Verificacion end to end fuera de los tests: con `docker compose up` se cargo un documento en
espanol, paso de `PROCESANDO` a `INDEXADO` en dos segundos, quedo partido en dos fragmentos
por sus encabezados, y contra la base real la consulta `especificacion tecnica` **sin
tildes** encontro el contenido escrito **con** tildes.

### Fase 4 — Busqueda (HU-02)

Delegada en el agente `search-engineer`, cuyo ambito es justamente la consulta, los pesos, el
resaltado y la latencia. El brief le fijo la forma de la consulta en lugar de dejarsela
inventar, porque es el nucleo del ejercicio y tiene que poder defenderse tal como esta escrita.

Decisiones del agente que se revisaron:

- **Aceptada.** Anadio `document_id` como criterio de desempate en `ORDER BY rank DESC`. Los
  empates de ranking son comunes y sin desempate la paginacion no es estable: un mismo
  documento podia aparecer en dos paginas o en ninguna.
- **Aceptada.** El resultado es una proyeccion y no el documento completo: un acierto siempre
  esta `INDEXADO`, asi que sus campos de error sobran, y `storage_key` y `sha256` no tienen por
  que salir del servidor.
- **Aceptada.** Limite de longitud de la consulta. Una cadena sin cota es un parseo sin cota, y
  eso es un agujero de latencia.
- **Aceptada, y corrige un problema que venia de antes.** `DocumentsSchemaIT` contaba filas
  sobre toda la tabla, de modo que solo pasaba si ninguna otra prueba habia insertado nada;
  estaba verde por el orden de ejecucion. El agente lo acoto al checksum que inserta.
- **Aceptada, y es la mas interesante.** Su primera version del test de plan exigia un
  `Bitmap Index Scan` incluso con los tres filtros aplicados. PostgreSQL invierte ese plan
  legitimamente: con un unico documento candidato entra por el indice de categoria y aplica el
  `@@` como filtro de join. El test ahora exige lo que de verdad importa, que no haya `Seq Scan`
  y que se entre por un indice, y mantiene la exigencia del indice GIN para la consulta sin
  filtros.

Verificacion propia, porque es la afirmacion que sostiene todo el ejercicio: se sembraron
5.000 documentos y 20.000 fragmentos y se corrio `EXPLAIN (ANALYZE, BUFFERS)` sobre la consulta
real. Usa `Bitmap Index Scan on document_chunks_search_vector_idx`, entra a `documents` por su
clave primaria y **no aparece ningun `Seq Scan`**. El peor caso, un termino presente en los
20.000 fragmentos, sigue usando el indice y se resuelve en **192 ms**, muy por debajo del techo
de un segundo. El `LIMIT` ocurre antes de los joins externos, de modo que `ts_headline` solo
procesa las filas devueltas.

Sobre el HTTP se comprobo que `especificacion tecnica` sin tildes encuentra `Especificación
técnica` con los terminos marcados entre `⟦` y `⟧`, que la frase entre comillas y la exclusion
con `-termino` funcionan, que los filtros por metadatos acotan, y que un documento en
`PROCESANDO` no aparece hasta que el worker lo indexa.

### Fase 5 — Notificaciones en tiempo real (HU-04)

Delegada en el agente `backend-engineer`. El fan-out de RabbitMQ y la publicación tras commit
ya existían desde la fase 3, así que el encargo fue solo el último tramo: de la cola de cada
instancia al navegador.

Durante esta fase se planteó **cambiar el tiempo real a polling**. Se verificó contra el
enunciado antes de decidir: HU-04 lo prohíbe por su nombre ("la actualización del estado de
carga del documento no debe realizarse mediante _polling_ tradicional"), sus criterios de
aceptación admiten solo WebSocket, SSE o suscripciones GraphQL, y el checklist del apartado 7
lo marca como ítem evaluable. Se mantuvo SSE.

Decisiones del agente que se revisaron:

- **Aceptada.** El controlador vive en `events/web/` y no en `events/`, porque una regla de
  ArchUnit exige que todo `@RestController` resida en un paquete `web`. Relajar la regla no era
  opción.
- **Aceptada.** El endpoint lleva `@Profile("!worker")`: el contexto del worker sigue creando
  los beans `@RestController` aunque no levante servidor, así que sin el perfil el worker no
  arrancaba.
- **Aceptada.** El identificador del evento se deriva del propio cambio en lugar de ser un
  contador, de modo que el mismo cambio llega con el mismo `id` a todas las conexiones.
- **Aceptada, con una variable de entorno de más.** `APP_SSE_TIMEOUT_MS` no estaba en la lista
  de `CLAUDE.md`; la alternativa era dejar el tiempo de expiración incrustado en el código. Se
  valida al arranque que supere al latido, porque un stream que cierra antes de su primer latido
  dejaría a todos los clientes reconectando en bucle. `CLAUDE.md` se actualizó.
- **Aceptada.** Una escritura fallida cierra el emisor en lugar de fallarlo: fallarlo devolvía
  el error al servlet, que intentaba responder a un socket muerto con un documento de problema y
  registraba una desconexión normal como fallo.

La auditoría posterior encontró un hueco que ni el agente ni la verificación manual habían
visto: si la publicación del anuncio fallaba en la ventana posterior al commit, el trabajo
volvía a la cola, encontraba el documento ya terminal y salía en silencio. El documento quedaba
indexado y ningún cliente se enteraba nunca. Se corrigió haciendo que ese camino **repita el
anuncio** en lugar de salir callado, con sus pruebas unitarias y de integración. También se
corrigieron dos comentarios que describían mal el mecanismo: uno decía que la cola la nombraba
el broker cuando la nombra el cliente, que es justamente lo que permite sobrevivir a un
reinicio del broker.

Verificación propia sobre el stack completo, con un cliente escuchando `/api/events` mientras se
cargaba un documento: llegó `INDEXADO` con su `id` y el valor en español del contrato, sin que el
cliente preguntara nada. Con un PDF sin capa de texto llegó `ERROR` con `PDF_NO_TEXT_LAYER`, y
los latidos aparecieron en la conexión ociosa. Las cabeceras incluyen `X-Accel-Buffering: no`,
que es lo que impide que un proxy retenga el stream.

### Fase 6 — Interfaz de carga y estado en vivo

Delegada en el agente `frontend-engineer`. Las versiones de las dependencias se fijaron antes de
delegar, todas con mas de ocho dias publicadas, porque la politica de pnpm rechaza lo recien
publicado y eso ya habia costado una vuelta en la fase 0.

Lo mas util que aporto el agente no fue codigo sino un diagnostico: para leer los errores por
archivo de una carga rechazada tuvo que **escribir un tipo a mano**, porque springdoc no describe
los miembros de un `ProblemDetail` y el contrato generado declaraba los cuerpos 413 y 422 con el
esquema de la respuesta exitosa. El contrato mentia sobre los errores justo en la parte que el
enunciado evalua. Se verifico, se corrigio en el backend declarando el esquema del problema y sus
miembros, y el frontend paso a tomar ese tipo del contrato como todos los demas.

Otras decisiones del agente que se revisaron:

- **Aceptada.** La reconciliacion al reconectar es una sola lectura de la primera pagina en lugar
  de una consulta por documento: no existe un endpoint por lote, y `?status=PROCESANDO` solo dice
  quien sigue procesando, no en que termino el resto.
- **Aceptada.** La parte `metadata` viaja como `Blob` con su charset declarado. Un campo de texto
  plano en `FormData` no lleva `Content-Type`, y Spring lo lee con una codificacion adivinada, lo
  que rompe los titulos con tildes.
- **Aceptada.** El `detail` que devuelve la API no se muestra nunca: el backend responde en ingles
  por contrato, asi que la interfaz traduce por codigo de estado. Hay una prueba que falla si el
  texto en ingles llega a la pantalla.
- **Aceptada.** `retry: false` en consultas y mutaciones. Un bucle de reintentos no puede hacer de
  suscripcion.

Tambien corrigio una imprecision mia: `CLAUDE.md` decia que un documento cuyo texto ya contiene
los delimitadores "vuelve con ellos duplicados", como si el backend los escapara. No los escapa;
la duplicacion es consecuencia de que `ts_headline` envuelve un texto que ya venia envuelto. La
frase se reescribio para decir por que ocurre.

Verificacion propia sobre el stack completo detras de nginx: la SPA se sirve, una ruta profunda
cae en el `index.html`, la API responde por el proxy, y con un cliente escuchando `/api/events`
**a traves de nginx** llego el evento `INDEXADO` del documento recien subido, seguido de los
latidos. Sin `proxy_buffering off` eso no ocurre y no hay ningun error que lo delate.

## Prompts Clave

| Objetivo            | Prompt (resumido)                                                                                                                                       | Refinamiento aplicado                                                                                                   |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Definir el proyecto | Documento inicial con requisitos duros, stack aprobado, flujo de git y roadmap por fases, usado como memoria persistente del repositorio (`CLAUDE.md`). | Se convirtio en la fuente de verdad del proyecto; cada decision que cambia se actualiza en el mismo PR.                 |
| Leer el enunciado   | "El PDF del enunciado es solo imagen: leelo visualmente y consultalo ante cualquier duda; si contradice al `CLAUDE.md`, gana el PDF."                   | Evita que el asistente invente requisitos: el enunciado queda por encima de la memoria del proyecto.                    |
| Cambio de stack     | "El backend debe ser Java 21 + Spring Boot, no NestJS. Reescribe `CLAUDE.md` y elimina toda referencia obsoleta."                                       | Se pidio ademas hacer `grep` de los terminos obsoletos sobre `CLAUDE.md`, README, docs y CI para no dejar restos.       |
| Autoria             | "Nunca anadas `Co-Authored-By`, firmas de herramienta ni enlaces de sesion a commits, PR, codigo o documentacion."                                      | Se reforzo con un hook `commit-msg` y un trabajo de CI que rechazan esos patrones, en vez de confiar en la instruccion. |
| Latencia            | "El enunciado dice 400 ms - 1000 ms: interpretalo como techo duro, nunca anadas retardo artificial."                                                    | La interpretacion quedo documentada en `architecture.md` para poder defenderla en la sustentacion.                      |

## Validación Humana

**TODO**
