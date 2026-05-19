# Sistema Multiagente para Análisis de Sentimiento

Este proyecto implementa un sistema multiagente desarrollado con **JADE en Java** y una API externa en **Python/FastAPI** para realizar análisis de sentimiento sobre comentarios de texto.

El sistema está compuesto por agentes que se comunican mediante mensajes ACL y delegan la clasificación de sentimiento a un modelo de lenguaje ejecutado desde una API Python basada en Hugging Face / `pysentimiento`.

---

## Objetivo

El objetivo principal del proyecto es desarrollar un sistema multiagente capaz de:

- Recibir o generar comentarios de texto.
- Enviar dichos comentarios a un agente especializado en análisis de sentimiento.
- Clasificar cada comentario como positivo, negativo o neutral.
- Enviar los resultados a un agente de visualización.
- Separar la lógica multiagente de la lógica de inteligencia artificial mediante una API independiente.

La arquitectura busca demostrar el uso de agentes autónomos, comunicación mediante mensajes ACL y delegación de tareas inteligentes a servicios externos.

---

## Arquitectura general

El flujo principal del sistema es el siguiente:

```text
AcquisitionAgent
        |
        | ACLMessage - new-comment
        v
SentimentAgent
        |
        | HTTP POST
        v
FastAPI Sentiment API (robertuito)
        |
        | JSON { tipo, score }
        v
SentimentAgent
        |
        | ACLMessage - sentiment-result
        v
VisualizationAgent
```
---
## Componentes principales
-   AcquisitionAgent: agente encargado de obtener o generar comentarios.
-   SentimentAgent: agente encargado de recibir comentarios y solicitar su clasificación a la API Python.
-   Sentiment API: servicio FastAPI que utiliza el modelo 'robertuito' (https://github.com/pysentimiento/pysentimiento) para clasificar sentimiento de texto en español.
-   VisualizationAgent: agente encargado de mostrar o procesar los resultados obtenidos.

---

## Requisitos

- Java JDK 17 o superior
- IntelliJ IDEA 
- Librería java Gson (incluida en `/lib`,debe estar añadida al classpath del módulo Java en IntelliJ)
- Librería java JADE (incluida en `/lib`,debe estar añadida al classpath del módulo Java en IntelliJ)
- Docker version 29.0.1
- Docker Compose version v2.40.3-desktop.1

## Instalación
1. Clonar / descargar el proyecto
2. Añadir las librerías encontradas en `/lib` al proyecto (File > Project Structure > Modules > Dependencies > + > JARs or Directories)
3. Construir imagen docker `sentiment-api` desde el directorio `/sentiment_api`
    Mandato: `docker build -t sentiment-api .`

## Ejecución
1. Levantar la API con el docker compose (y esperar a que termine de levantarse)
    Mandato: `docker compose up -d`
    Puede comprobarse que esté en funcionamiento desde `http://localhost:8000/`
2. Ejecutar los agentes JADE *posiblemente toque añadir aquí la configuracion de como hacerlo*


## Declaración de uso de IA
Se usó IA generativa para buscar y entender y usar librerías java como:

- Gson (usada para convertir objetos Java a formato JSON antes de enviarlos a la API Python, y para convertir la respuesta JSON de la API en objetos Java manejables dentro del agente.)
    
- java.net (usada para construir y enviar peticiones HTTP desde el agente `SentimentAgent` hacia la API de FastAPI, incluyendo la configuración de la URL, el método `POST`, las cabeceras HTTP y el cuerpo de la petición.)