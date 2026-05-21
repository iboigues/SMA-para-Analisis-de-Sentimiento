package sma_agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import com.google.gson.Gson;
import jade.lang.acl.MessageTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class SentimentAgent extends Agent {
    /******* Variables globales ********/
    private static final String NEW_COMMENT_CONVERSATION_ID = "new-comment";    //mensajes que vienen del Acquisition Agent
    private static final String RESULT_CONVERSATION_ID = "sentiment-result";    //mensajes que se envian al Visualization Agent
    private static final String ERROR_CONVERSATION_ID = "sentiment-error";
    private boolean apiLevantadaPorAgente = false;
    private boolean apiUp = true;


    private final String sentimentApiUrl = "http://localhost:8000/classifier/classify";
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

    private final Gson gson = new Gson();

    /******* clases necesarias para las peticiones http ********/

    class ClassifierInput {
        String text;

        ClassifierInput(String text) {
            this.text = text;
        }
    }

    class ClassifierOutput {
        String tipo;
        double score;
        String errorMessage;
    }

    class SentimentResult {
        String postId;
        String commentId;
        String text;
        String sentiment;
        double score;
        String timestamp;

        SentimentResult(String postId, String commentId, String text, String sentiment, double score) {
            this.postId = postId;
            this.commentId = commentId;
            this.text = text;
            this.sentiment = sentiment;
            this.score = score;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }
    private String servicio = "sentiment process"; //servicio que proporciona el agente

    /*********************************** DF register ***********************************/

    private void registerAgent(String servicio){
        //descripcion del agente y su nombre (descriptor de servicios)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());
        //el servicio que proporciona y su tipo para localizarlos por el
        ServiceDescription sd = new ServiceDescription();
        sd.setName(servicio);
        sd.setType(servicio);
        //añadimos al descriptor de servicios
        dfd.addServices(sd);
        //realizamos el registro del descriptor de servicios en el DF
        try{
            DFService.register(this,dfd);
            System.out.println("El Agente :" + getLocalName()+ " fue registrado en el DF");

        }
        catch(FIPAException ex)
        {
            System.err.println("El Agente :" + getLocalName()+ " no ha podido registrar el servicio " + ex.getMessage());
            doDelete();
        }
    }

    /*********************************** levantamiento de visualization agent si no hay ***********************************/

    private void levantarVisualizationAgent() {
        try {
            ContainerController container = getContainerController();

            AgentController sentiment = container.createNewAgent(
                "visualization-agent",
                "sma_agents.VisualizationAgent",
                new Object[]{}
            );

            sentiment.start();

            System.out.println("[" + getLocalName() + "] VisualizationAgent levantado dinámicamente");

        } catch (StaleProxyException e) {
            System.err.println("[" + getLocalName() + "] No se pudo levantar VisualizationAgent: " + e.getMessage());
        }
    }
    /*********************************** Aux ***********************************/

    /*metodo para clasificar los comentarios*/

    private ClassifierOutput classifySentiment(String text) {
        try {
            return llamarApiSentiment(text);

        } catch (Exception e) {
            apiUp=false;
            System.out.println("[" + getLocalName() + "] API no disponible. Intentando levantarla con Docker Compose...");
            System.out.println("[" + getLocalName() + "] Error original: " + e.getMessage());

            levantarSentimentApi();

            // Esperamos a que el contenedor arranque del todo
            doWait(10000);

            try {
                System.out.println("[" + getLocalName() + "] Reintentando llamada a la API...");
                return llamarApiSentiment(text);

            } catch (Exception retryException) {
                String error = "Error llamando a sentiment API tras intentar levantarla: "
                    + retryException.getMessage();

                System.out.println("[" + getLocalName() + "] " + error);

                ClassifierOutput errorOutput = new ClassifierOutput();
                errorOutput.tipo = "ERROR";
                errorOutput.score = 0.0;
                errorOutput.errorMessage = error;

                return errorOutput;
            }
        }
    }
    /** método para buscar agentes de visualización **/
    private AID buscaServicioVisualization() {
        DFAgentDescription template = new DFAgentDescription();
        String servicioVisualization = "visualization-agent";
//Creamos un descriptor de servicios
        ServiceDescription sd= new ServiceDescription();
        sd.setType(servicioVisualization); // mismo type con el que se registra los agentes d evisualizacion
        template.addServices(sd);
        try{
//Consultamos al DF los servicios y los devuelve en el dfd
            DFAgentDescription[] result = DFService.search(this,template);
            if (result.length == 0) {
                System.out.println("[" + getLocalName() + "] No se encontró ningún agente con servicio de visualización");
                levantarVisualizationAgent();
                doWait(3000);
                return buscaServicioVisualization();
            }

            AID sentimentAID = result[0].getName();
            return sentimentAID;

        } catch (FIPAException ex) {
            System.err.println("[" + getLocalName() + "] Error buscando agente de sentimiento en el DF: " + ex.getMessage());
            return null;
        }
    }
    /** método para enviar los comentarios y sentimientos a visualización **/

    private void sendResultToVisualizationAgent(String postId, String commentId,
                                                String text, String sentiment, double score){
        SentimentResult result = new SentimentResult(postId, commentId, text, sentiment, score);

        AID visualizeragent = buscaServicioVisualization();

        ACLMessage message = new ACLMessage(ACLMessage.INFORM);
        message.addReceiver(visualizeragent);
        //marcamos el tipo de mensaje como "sentiment-result"
        message.setConversationId(RESULT_CONVERSATION_ID);
        message.setContent(gson.toJson(result));

        send(message);

        System.out.println("[" + getLocalName() + "] Resultado enviado a " + visualizeragent.getName());
    }


    /** método para procesar los comentarios **/
    private void processCommentMessage(ACLMessage message) {
        String content = message.getContent();
        System.out.println("[" + getLocalName() + "] Comentario recibido: " + content);

        String[] parts = content.split(";", 3);

        if (parts.length < 3) {
            String error = "Mensaje mal formado: " + content;

            System.err.println("[" + getLocalName() + "] " + error);

            String postId = parts.length > 0 ? parts[0].trim() : "UNKNOWN_POST";
            String commentId = parts.length > 1 ? parts[1].trim() : "UNKNOWN_COMMENT";

            sendErrorToAcquisitionAgent(message, postId, commentId, error);
            return;
        }

        String postId = parts[0].trim();
        String commentId = parts[1].trim();
        String text = parts[2].trim();

        ClassifierOutput output = classifySentiment(text);

        if (output == null) {
            sendErrorToAcquisitionAgent(
                message,
                postId,
                commentId,
                "La API devolvió una respuesta nula"
            );
            return;
        }

        if (output.tipo == null || output.tipo.startsWith("ERROR")) {
            String error = output.errorMessage != null
                ? output.errorMessage
                : "Error desconocido clasificando el comentario";

            sendErrorToAcquisitionAgent(message, postId, commentId, error);
            return;
        }

        System.out.println("[" + getLocalName() + "] Resultado:");
        System.out.println("    Publicacion: " + postId);
        System.out.println("    Comentario: " + commentId);
        System.out.println("    Sentimiento: " + output.tipo);
        System.out.println("    Score: " + output.score);

        sendResultToVisualizationAgent(postId, commentId, text, output.tipo, output.score);
    }

    /** método para enviar errores al AcquisitionAgent **/
    private void sendErrorToAcquisitionAgent(ACLMessage originalMessage,
                                             String postId,
                                             String commentId,
                                             String error) {

        ACLMessage reply = originalMessage.createReply();

        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId(ERROR_CONVERSATION_ID);
        reply.setContent("ERROR;" + postId + ";" + commentId + ";" + error);

        send(reply);

        System.out.println("[" + getLocalName() + "] Error enviado al AcquisitionAgent");
        System.out.println("    Publicacion: " + postId);
        System.out.println("    Comentario: " + commentId);
        System.out.println("    Error: " + error);
        System.out.println("\n");
    }

    /** método para levantar la API **/

    private void levantarSentimentApi() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker",
                "compose",
                "up",
                "-d",
                "sentiment-api"
            );
            // Ruta raíz del proyecto, donde está el docker-compose.yml
            pb.directory(java.nio.file.Path.of("").toAbsolutePath().toFile());

            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[" + getLocalName() + "] API de sentimiento levantada con Docker Compose");
                apiLevantadaPorAgente = true;
                apiUp = true;
            } else {
                System.err.println("[" + getLocalName() + "] Error levantando API de sentimiento. Exit code: " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("[" + getLocalName() + "] No se pudo levantar la API de sentimiento: " + e.getMessage());
        }
    }


    private ClassifierOutput llamarApiSentiment(String text) throws Exception {
        ClassifierInput input = new ClassifierInput(text);
        String requestBody = gson.toJson(input);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(sentimentApiUrl))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();



        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String error = "Error API sentiment: " + response.statusCode() + " - " + response.body();

            ClassifierOutput errorOutput = new ClassifierOutput();
            errorOutput.tipo = "ERROR_API";
            errorOutput.score = 0.0;
            errorOutput.errorMessage = error;

            return errorOutput;
        }

        return gson.fromJson(response.body(), ClassifierOutput.class);
    }

    @Override
    protected void setup() {

        System.out.println("[" + getLocalName() + "] SentimentAgent iniciado");

        registerAgent(servicio);

        MessageTemplate newCommentTemplate = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
            MessageTemplate.MatchConversationId(NEW_COMMENT_CONVERSATION_ID)
        );

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage message = receive(newCommentTemplate);

                if (message != null && message.getPerformative()==ACLMessage.REQUEST) {
                    processCommentMessage(message);
                } else {
                    block();
                }
            }
        });
    }

    //apaga la api al terminar de cualquier manera
    @Override
    protected void takeDown(){

        if (apiUp) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "docker",
                    "compose",
                    "stop",
                    "sentiment-api"
                );

                pb.directory(java.nio.file.Path.of("").toAbsolutePath().toFile());
                pb.inheritIO();

                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    System.out.println("[" + getLocalName() + "] API de sentimiento apagada correctamente");
                } else {
                    System.err.println("[" + getLocalName() + "] Error apagando API. Exit code: " + exitCode);
                }

            } catch (Exception e) {
                System.err.println("[" + getLocalName() + "] No se pudo apagar la API de sentimiento: " + e.getMessage());
            }
        } else {
            System.out.println("[" + getLocalName() + "] No se apaga la API porque no fue levantada");
        }
    }
}