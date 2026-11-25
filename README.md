# Notificaciones y auditoría CampusLab

Procesa tareas de notificación y eventos de auditoría. Envía correos, genera documentos PDF y almacena el historial de eventos.

## Tecnologías

- Java 21.
- Spring Boot.
- Spring Web MVC.
- Spring Security OAuth2 Resource Server.
- Spring Data JPA y MySQL.
- Spring AMQP y RabbitMQ.
- Spring Kafka.
- Spring Mail.
- Apache PDFBox.
- Maven y Docker.

## Funcionalidades

- Enviar correos asociados a las reservas.
- Generar tickets de preparación.
- Generar comprobantes en PDF.
- Consultar tareas y documentos según el rol del usuario.
- Consumir eventos de auditoría desde RabbitMQ y Kafka.
- Persistir el historial de auditoría en MySQL.

## Consumidores RabbitMQ

| Cola | Responsabilidad |
|---|---|
| `q.cmd.email` | Envío de correos |
| `q.cmd.prep` | Generación de tickets de preparación |
| `q.cmd.voucher` | Generación de comprobantes PDF |
| `q.evt.audit` | Persistencia de eventos de auditoría |

Las colas funcionales cuentan con colas de errores asociadas.

Los consumidores utilizan ACK manual. Los errores recuperables se reintentan de forma acotada; si el procesamiento sigue fallando, el mensaje se rechaza y se deriva a su DLQ.

## Consumo de eventos Kafka

El servicio consume los tópicos:

- `bookings.events`
- `audit.timeline`

El grupo de consumidores es:

```text
campuslab-notifier
```

Después de procesar el evento y persistirlo correctamente, el consumidor confirma manualmente su recepción.

Si falla el procesamiento, intenta publicar el mensaje original en el tópico de errores correspondiente:

- `bookings.events.DLT`
- `audit.timeline.DLT`

El evento original se confirma solo después de que Kafka acepta su publicación en la DLT. Si el envío a la DLT falla, el consumidor puede volver a recibir el evento.

La conexión se configura mediante:

```text
KAFKA_BOOTSTRAP_SERVERS
```

El tópico `demo.carga` se utiliza para pruebas de carga aisladas. No forma parte de los tópicos consumidos por este servicio.

## Correos

La configuración SMTP se recibe por variables de entorno:

```text
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_SMTP_AUTH
MAIL_STARTTLS
MAIL_STARTTLS_REQUIRED
MAIL_TEST_RECIPIENT
```

`MAIL_TEST_RECIPIENT` permite redirigir los mensajes durante pruebas. Si no tiene valor, se utiliza el destinatario indicado por el evento.

La aceptación de un mensaje por el servidor SMTP no garantiza su entrega final al buzón. Si el proceso falla después de que el servidor acepta el correo y antes de confirmar el procesamiento, existe la posibilidad de un envío duplicado.

## Documentos PDF

El servicio genera tickets de preparación y comprobantes PDF.

La ruta de almacenamiento se configura mediante:

```text
DOCUMENTS_PATH
```

En Docker Compose se utiliza un volumen persistente para conservar los documentos. Las fechas de los documentos se muestran usando la zona horaria `America/Santiago`.

## API de notificaciones

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/notifications` | Consultar notificaciones autorizadas |
| GET | `/api/notifications/{taskId}` | Consultar una tarea |
| GET | `/api/notifications/{taskId}/document` | Descargar el documento asociado |

Cliente consulta únicamente sus tareas y documentos. Admin y Operador acceden a las tareas autorizadas según sus funciones.

## API de auditoría

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/audit` | Consultar eventos recientes |
| GET | `/api/audit?bookingId=12` | Filtrar eventos por reserva |

La consulta está disponible para los roles Admin y Auditor. Devuelve hasta 500 eventos recientes.

El historial diferencia el estado anterior, el estado solicitado y el estado registrado. También conserva el actor, el identificador de operación y los momentos de ocurrencia y recepción.

La auditoría registra eventos desde su activación; no reconstruye operaciones históricas anteriores.

## Configuración

Variables principales:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
RABBITMQ_ADDRESSES
RABBITMQ_USER
RABBITMQ_PASSWORD
RABBITMQ_VHOST
KAFKA_BOOTSTRAP_SERVERS
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_TEST_RECIPIENT
DOCUMENTS_PATH
```

Base de datos: `campuslab_notifier`  
Puerto interno: `8083`

Las credenciales y direcciones del entorno se configuran fuera del repositorio. No se deben subir secretos al control de versiones.

## Compilación

En PowerShell, desde la carpeta del repositorio:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Este comando compila y empaqueta el servicio, pero omite la ejecución de pruebas.

## Despliegue

El servicio se construye y ejecuta mediante Docker Compose en el repositorio `infra-campuslab`.
