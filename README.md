# DDD / Hexagonal Project Generator

Demo web para generar proyectos Spring Boot con arquitectura DDD/hexagonal. La app expone una API REST, una UI React compilada como recurso static de Spring Boot y un Dockerfile para desplegar todo como un unico contenedor.

El generador reutiliza la logica local existente de `HexScaffoldService` y los valores de `LocalGeneratorPreset`, pero las peticiones web generan en un directorio temporal, devuelven un ZIP y limpian los archivos al terminar.

## Requisitos

- Java 21
- Maven Wrapper incluido
- Node 20+ para desarrollo frontend local
- Docker para construir la imagen final

## Backend local

```bash
./mvnw spring-boot:run
```

En Windows PowerShell tambien puedes usar:

```powershell
.\mvnw.cmd spring-boot:run
```

La generacion automatica al arrancar queda desactivada por defecto. Para usar el preset local como modo dev explicito:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--generate-local=true
```

## Frontend local

```bash
cd frontend
npm install
npm run dev
```

Vite proxya `/api` hacia `http://localhost:8080`, asi que arranca primero el backend en el puerto 8080.

## Docker

```bash
docker build -t ddd-generator .
docker run -p 8080:8080 ddd-generator
```

Despues abre `http://localhost:8080`.

## Endpoints

- `GET /api/health`: devuelve `{ "status": "ok" }`.
- `GET /api/defaults`: devuelve el preset actual en formato de request web.
- `POST /api/preview`: devuelve una vista simple de carpetas y entidades.
- `POST /api/generate`: devuelve `application/zip` con `Content-Disposition: attachment`.

## Ejemplo de request

```json
{
  "project": "DemoHexProject",
  "groupId": "com.ignacio.demo",
  "artifactId": "",
  "persistence": "h2",
  "examples": [
    {
      "name": "User",
      "structure": "name:String,email:String,age:Integer,birthDate:LocalDate"
    },
    {
      "name": "Order",
      "structure": "code:String,total:BigDecimal,paid:Boolean,createdOn:LocalDate"
    }
  ]
}
```

## Tests

```bash
./mvnw test
```

## Despliegue

En Render, Railway o Fly.io usa el Dockerfile de la raiz. La aplicacion escucha el puerto configurado por `PORT` y cae a `8080` si la variable no existe:

```properties
server.port=${PORT:8080}
```

Configuracion tipica:

- Build command: `docker build -t ddd-generator .`
- Start command: incluido en la imagen con `java -jar /app/app.jar`
- Port: variable `PORT` gestionada por la plataforma o `8080`
