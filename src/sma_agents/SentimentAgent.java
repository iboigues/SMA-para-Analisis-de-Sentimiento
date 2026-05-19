package sma_agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

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

    private String visualizationAgentName = "visualizer";
    private final String sentimentApiUrl = "http://localhost:8000/classifier/classify";
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

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

    /******* Aux ********/

    /*metodo para clasificar los comentarios*/

    private ClassifierOutput classifySentiment(String text){
        try {
            // creacion del json classifierinput
            ClassifierInput input = new ClassifierInput(text);
            String requestBody = gson.toJson(input);

            // creacion de la peticion
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sentimentApiUrl))   //declaración del a uri a la que lanzar la request
                    .timeout(Duration.ofSeconds(20))    //timeout de respuesta
                    .header("Content-Type", "application/json")//tipo de body, obligatorio
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody)) //convertir el string request en un body http
                    .build();   //construirla

            //envio de peticion request, el body de entrada es un string
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[" + getLocalName() + "] Error API sentiment: "
                        + response.statusCode() + " - " + response.body());

                ClassifierOutput errorOutput = new ClassifierOutput();
                errorOutput.tipo = "ERROR_API";
                errorOutput.score = 0.0;
                return errorOutput;
            }

            //convertir
            ClassifierOutput output = gson.fromJson(response.body(), ClassifierOutput.class);
            System.out.println("[" + getLocalName() + "] sentiment: "+output.tipo+"\tscore: "+output.score);

            return output;

        } catch (Exception e) {
            System.out.println("[" + getLocalName() + "] Error llamando a sentiment API: " + e.getMessage());

            ClassifierOutput errorOutput = new ClassifierOutput();
            errorOutput.tipo = "ERROR";
            errorOutput.score = 0.0;

            return errorOutput;
        }
    }
    /*metodo para enviar los comentarios y sentimientos a visualización*/

    private void sendResultToVisualizationAgent(String postId, String commentId,
                                                String text, String sentiment, double score){
        SentimentResult result = new SentimentResult(postId, commentId, text, sentiment, score);

        ACLMessage message = new ACLMessage(ACLMessage.INFORM);
        message.addReceiver(new AID(visualizationAgentName, AID.ISLOCALNAME));
        //marcamos el tipo de mensaje como "sentiment-result"
        message.setConversationId(RESULT_CONVERSATION_ID);
        message.setContent(gson.toJson(result));

        send(message);

        System.out.println("[" + getLocalName() + "] Resultado enviado a " + visualizationAgentName);
    }


    /*metodo para procesar los comentarios*/

    private void processCommentMessage(ACLMessage message){
        // separamos el contenido del mensaje como el csv
        String content = message.getContent();
        System.out.println("[" + getLocalName() + "] Comentario recibido: " + content);

        String[] parts = content.split(";", 3);
        if (parts.length < 3) {
            System.err.println("[" + getLocalName() + "] Mensaje mal formado: " + content);
            return;
        }
        String postId = parts[0].trim();
        String commentId = parts[1].trim();
        String text = parts[2].trim();

        // enviamos el texto a clasificar
        ClassifierOutput output = classifySentiment(text);

        System.out.println("[" + getLocalName() + "] Resultado:");
        System.out.println("    Publicacion: " + postId);
        System.out.println("    Comentario: " + commentId);
        System.out.println("    Sentimiento: " + output.tipo);
        System.out.println("    Score: " + output.score);

        //lo enviamos al visualizador
        sendResultToVisualizationAgent(postId, commentId, text, output.tipo, output.score);
    }





    @Override
    protected void setup() {
        Object[] args = getArguments();

        if (args != null && args.length > 0 && args[0] != null) {
            visualizationAgentName = args[0].toString();
        }

        System.out.println("[" + getLocalName() + "] SentimentAgent iniciado");
        System.out.println("[" + getLocalName() + "] Enviará resultados a: " + visualizationAgentName);

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
}