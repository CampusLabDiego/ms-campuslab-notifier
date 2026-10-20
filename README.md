# Notificaciones y auditoría CampusLab

Consume tareas y eventos de RabbitMQ para enviar correos,
generar documentos PDF y persistir el historial de auditoría.

## Tecnologías

- Java 21 y Spring Boot.
- Spring AMQP.
- Spring Mail.
- Apache PDFBox.
- Spring Data JPA y MySQL.
- Spring Security OAuth2 Resource Server.
- Maven y Docker.

## Consumidores

| Cola | Responsabilidad |
|---|---|
| q.cmd.email | Envío de correos |
| q.cmd.prep | Generación de tickets de preparación |
| q.cmd.voucher | Generación de comprobantes PDF |
| q.evt.audit | Persistencia de eventos de auditoría |

Cada cola tiene su DLQ.

## Confirmación y errores

Los consumidores utilizan ACK manual después del procesamiento.

Los errores recuperables se reintentan hasta tres intentos por
entrega. Si persisten, el mensaje se rechaza sin requeue y va a la DLQ.

Los errores permanentes se rechazan directamente.
Los listeners de DLQ registran los metadatos del mensaje.

Las tareas y los eventos se identifican para reconocer duplicados.

## Correos

La configuración SMTP se recibe mediante variables de entorno:

- MAIL_HOST.
- MAIL_PORT.
- MAIL_USERNAME.
- MAIL_PASSWORD.
- MAIL_FROM.
- MAIL_SMTP_AUTH.
- MAIL_STARTTLS.
- MAIL_STARTTLS_REQUIRED.

MAIL_TEST_RECIPIENT permite redirigir los correos durante pruebas.
Si está vacío, se usa el destinatario del evento.

La aceptación por el servidor SMTP no garantiza la entrega final
al buzón del destinatario. Puede existir un rebote posterior.

Si el proceso falla después de la aceptación SMTP y antes de
confirmar la persistencia, existe posibilidad de envío duplicado.

## Documentos

Se generan tickets de preparación y comprobantes PDF.

Directorio configurado:

```text
/app/storage
```

El volumen notifier_documents conserva los archivos.

Las fechas mostradas en los documentos utilizan America/Santiago.

## API de notificaciones

```text
GET /api/notifications
GET /api/notifications/{taskId}
GET /api/notifications/{taskId}/document
```

- Admin y Operador consultan las tareas autorizadas de gestión.
- Cliente consulta únicamente sus tareas y documentos.
- La descarga valida la propiedad y disponibilidad del archivo.

## API de auditoría

```text
GET /api/audit
GET /api/audit?bookingId=12
```

Disponible para Admin y Auditor, en modo lectura.

Devuelve hasta 500 eventos recientes y permite filtrar por reserva.

El historial diferencia:

- Actor que realizó la solicitud.
- Estado anterior, registrado y solicitado.
- Confirmación o rechazo automático.
- Identificador de operación relacionado.
- Momento del evento y momento de recepción.

La auditoría usa RabbitMQ y MySQL.
No reconstruye operaciones anteriores a su activación.

## Configuración adicional

- DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD.
- RABBITMQ_ADDRESSES, RABBITMQ_USER, RABBITMQ_PASSWORD, RABBITMQ_VHOST.
- DOCUMENTS_PATH.

Base de datos: campuslab_notifier.
Puerto: 8083.

## Compilación

```powershell
.\mvnw.cmd clean package -DskipTests
```

Este comando compila y empaqueta; omite la ejecución de pruebas.

El despliegue está definido en infra-campuslab.