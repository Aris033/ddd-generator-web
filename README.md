# ⚙️ DDD / Hexagonal Project Generator

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![React](https://img.shields.io/badge/React-TypeScript-blue)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED)
![Architecture](https://img.shields.io/badge/Architecture-DDD%20%2F%20Hexagonal-purple)

Demo web para generar proyectos **Spring Boot** con arquitectura **DDD / Hexagonal**, interfaz en **React** y despliegue mediante **Docker** en un único contenedor.

🚀 **Demo online:**  
https://ddd-generator-web.onrender.com/

> ⚠️ La demo está desplegada en un servicio gratuito de Render.  
> Puede que la primera carga tarde un poco si la aplicación está inactiva. Entra en la URL, espera unos segundos/minutos y el servicio debería arrancar automáticamente.

---

## ✨ Qué hace este proyecto

Este generador crea una estructura base de proyecto **Spring Boot** siguiendo una arquitectura limpia basada en capas:

- **Domain**
- **Application**
- **Infrastructure**
- **Controllers**
- **Persistence**
- **DTOs / Mappers**
- **Repositories**
- **Use cases**

Desde la interfaz web puedes definir:

- Nombre del proyecto
- `groupId`
- `artifactId`
- Tipo de persistencia
- Entidades a generar
- Campos de cada entidad

Por defecto, el proyecto generado incluye configuración con **H2**, por lo que puedes probar rápidamente la aplicación generada sin tener que configurar una base de datos externa.

---

## 🧱 Stack técnico

Este proyecto está construido con:

- **Java 21**
- **Spring Boot**
- **React**
- **TypeScript**
- **Vite**
- **Docker**
- **Maven**

La aplicación final se despliega como un único servicio:

```text
React Frontend
      ↓
Spring Boot Backend
      ↓
Docker Container
```

---

## 🖥️ Cómo funciona

La app tiene una UI sencilla donde defines las entidades que quieres generar.

Ejemplo:

```text
Entity: User
Fields: name:String,email:String,age:Integer,birthDate:LocalDate
```

Y genera un proyecto Spring Boot con estructura DDD / Hexagonal listo para descargar como `.zip`.

---

## 📦 Características principales

- Generación de proyectos Spring Boot con estructura DDD / Hexagonal.
- Descarga del proyecto generado en formato `.zip`.
- Interfaz web en React.
- API REST con Spring Boot.
- Preview básico de la estructura generada.
- Configuración H2 por defecto.
- Generación temporal segura en backend.
- Limpieza automática de archivos temporales.
- Dockerfile multi-stage para frontend + backend.
- Preparado para despliegue en Render, Railway, Fly.io u otras plataformas con Docker.

---

## 📁 Estructura del repositorio

```text
.
├── src/
│   └── main/
│       ├── java/
│       └── resources/
├── frontend/
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
├── Dockerfile
├── .dockerignore
├── pom.xml
├── mvnw
└── README.md
```

---

## 🚀 Ejecutar backend en local

```bash
./mvnw spring-boot:run
```

En Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

La generación automática al arrancar queda desactivada por defecto.

Para usar el preset local como modo de desarrollo explícito:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--generate-local=true
```

---

## ⚛️ Ejecutar frontend en local

```bash
cd frontend
npm install
npm run dev
```

Vite proxya las peticiones `/api` hacia:

```text
http://localhost:8080
```

Por tanto, arranca primero el backend en el puerto `8080`.

---

## 🐳 Docker

Construir la imagen:

```bash
docker build -t ddd-generator .
```

Ejecutar el contenedor:

```bash
docker run -p 8080:8080 ddd-generator
```

Después abre:

```text
http://localhost:8080
```

El Dockerfile compila el frontend, lo copia dentro de los recursos estáticos de Spring Boot y genera un único `.jar` ejecutable.

---

## 🌐 Endpoints disponibles

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/health` | Comprueba que la API está viva |
| `GET` | `/api/defaults` | Devuelve un ejemplo inicial de generación |
| `POST` | `/api/preview` | Devuelve una vista previa de la estructura |
| `POST` | `/api/generate` | Genera y descarga el proyecto en `.zip` |

---

## 🧪 Ejemplo de request

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

---

## ✅ Tests

Ejecutar tests:

```bash
./mvnw test
```

En Windows PowerShell:

```powershell
.\mvnw.cmd test
```

---

## ☁️ Despliegue

La aplicación está preparada para desplegarse como un único contenedor Docker.

En plataformas como **Render**, **Railway** o **Fly.io**, usa el `Dockerfile` de la raíz del proyecto.

La aplicación escucha el puerto configurado por la variable `PORT` y cae a `8080` si la variable no existe:

```properties
server.port=${PORT:8080}
```

Configuración típica:

```text
Runtime: Docker
Dockerfile path: ./Dockerfile
Port: PORT gestionado por la plataforma o 8080
Start command: incluido en la imagen Docker
```

---

## 🧠 Decisiones técnicas

- El frontend React se compila y se sirve desde Spring Boot como contenido estático.
- El backend expone una API REST para generar proyectos.
- Las peticiones web generan los proyectos en directorios temporales.
- El resultado se devuelve como `.zip`.
- No se guardan proyectos generados de forma permanente en el servidor.
- H2 se usa como persistencia por defecto en los proyectos generados.
- Todo se empaqueta en un único contenedor Docker para simplificar el despliegue.

---

## 📌 Estado del proyecto

Proyecto creado como demo técnica y portfolio para mostrar:

- Spring Boot
- Arquitectura DDD / Hexagonal
- Generación de código
- React + TypeScript
- Docker
- Despliegue cloud

---

## 👤 Autor

Proyecto desarrollado por **Ignacio Aristi**.
