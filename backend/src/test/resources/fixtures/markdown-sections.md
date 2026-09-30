Resumen del documento con una **palabra destacada** antes de cualquier título.

# Guía de despliegue

La guía cubre el despliegue con `docker compose` y la [documentación oficial](https://example.test/despliegue/produccion) amplía cada paso.

## Requisitos

- PostgreSQL 17 con la extensión *unaccent*
- RabbitMQ 4 con la cola `documents.process`

### Variables de entorno

Cada variable se valida al arrancar:

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
SELECT set_config('statement_timeout', '900', true);
```

#### Valores por defecto

El puerto de la API es 8081 y el de la web 8080.

## Verificación

<div class="nota">Este HTML no se indexa.</div>

![Diagrama de la arquitectura](https://example.test/arquitectura.png)

---

El estado pasa de PROCESANDO a INDEXADO cuando termina la indexación.
