package sma_agents;

import com.google.api.services.youtube.model.CommentThread;
import jade.core.Agent;
import jade.core.AID;
import jade.core.Service;
import jade.core.behaviours.*;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import youtube.YoutubeCommentsAPI;
import youtube.YoutubeResponse;

import jade.core.Agent;
import jade.domain.FIPAException;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import java.io.IOException;
import java.nio.charset.StandardCharsets; //
import java.nio.file.*;  //para paths y metodos relacionados con leer archivos
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;   //listas de java
import java.util.Set;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;


public class AcquisitionAgent extends Agent{
    /*********************************** variables globales ***********************************/

    private Path commentsFile; //archivo csv
    private final Set<String> processedComments = new HashSet<>(); //set de comentarios procesados
    private final String servicio = "acquire comments"; //servicio que proporciona el agente
    /*********************************** DF register y busqueda servicios ***********************************/

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




    private AID buscaServicioSentiment() {
        DFAgentDescription template = new DFAgentDescription();
        String servicioSentiment = "sentiment process";
//Creamos un descriptor de servicios
        ServiceDescription sd= new ServiceDescription();
        sd.setType(servicioSentiment); // mismo type con el que se registra SentimentAgent
        template.addServices(sd);
        try{
//Consultamos al DF los servicios y los devuelve en el dfd
            DFAgentDescription[] result = DFService.search(this,template);
            if (result.length == 0) {
                System.out.println("[" + getLocalName() + "] No se encontró ningún agente con servicio sentiment process. levantando uno...");
                levantarSentimentAgent();
                doWait(3000);
                return buscaServicioSentiment();
            }

            AID sentimentAID = result[0].getName();
            System.out.println("[" + getLocalName() + "] Usando agente de sentimiento: " + sentimentAID.getLocalName());
            return sentimentAID;

        } catch (FIPAException ex) {
            System.err.println("[" + getLocalName() + "] Error buscando agente de sentimiento en el DF: " + ex.getMessage());
            return null;
        }
    }

    /*********************************** levantamiento de sentiment agent si no hay ***********************************/

    private void levantarSentimentAgent() {
        try {
            ContainerController container = getContainerController();

            AgentController sentiment = container.createNewAgent(
                    "sentiment",
                    "sma_agents.SentimentAgent",
                    new Object[]{}
            );

            sentiment.start();

            System.out.println("[" + getLocalName() + "] SentimentAgent levantado dinámicamente");

        } catch (StaleProxyException e) {
            System.err.println("[" + getLocalName() + "] No se pudo levantar SentimentAgent: " + e.getMessage());
        }
    }




    /*********************************** Aux ***********************************/

    /** método para realizar envio a sentiment **/
    private void sendCommentToSentimentAgent(String postId, String commentId, String text){
        AID sentimentAID = this.buscaServicioSentiment();
        if (sentimentAID == null) {
            System.out.println("[" + getLocalName() + "] No se puede enviar el comentario porque no hay SentimentAgent disponible");
            return;
        }

        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

        message.addReceiver(sentimentAID); //
        message.setConversationId("new-comment"); //el tipo de mensaje que envía para que pueda filtrar
        message.setContent(postId + ";" + commentId + ";" + text); //contenido del mensaje

        send(message);
        System.out.println("[" + getLocalName() + "] Comentario enviado a " + sentimentAID.getName());
        System.out.println("\n");


    }


    /** método para checkear por nuevos comentarios **/
    private void checkNewComments(){
        try {
            // guardamos todas las líneas del csv en una lista
            List<String> lines = Files.readAllLines(commentsFile, StandardCharsets.UTF_8);

            // y por cada una la normalizamos en minusculas y sin espacios, si está vacia o es la primera continuamos
            for(String line : lines){
                line = line.trim();
                if (line.isEmpty() || line.startsWith("postId;")) {
                    continue;
                }

                // separamos cada parte del csv para tener el texto por un lado y generar un id unico
                String[] parts = line.split(";", 2);
                String postId = parts[0].trim();
                String maxComments = parts[1].trim();

                YoutubeResponse response = YoutubeCommentsAPI.getComments(postId, Integer.parseInt(maxComments));

                for (CommentThread comment : response.comments()) {
                    String commenter = comment.getSnippet().getTopLevelComment().getSnippet().getAuthorDisplayName();
                    String commentText = comment.getSnippet().getTopLevelComment().getSnippet().getTextDisplay();

                    String uniqueID = response.title() + "_" + commenter + "_" + commentText;

                    if(processedComments.contains(uniqueID))
                        continue;

                    processedComments.add(uniqueID);
                    sendCommentToSentimentAgent(response.title(),commenter,commentText);
                }
            }

        } catch (IOException e) {
            System.out.println("[" + getLocalName() + "] Error leyendo fichero: " + e.getMessage());
        }
    }

    /** método para manejar errores recibidos desde sentimentAgent **/
    private void handleSentimentError(ACLMessage message) {
        String content = message.getContent();

        if (content == null || content.isBlank()) {
            System.out.println("[" + getLocalName() + "] Error recibido sin contenido");
            return;
        }

        /*
         * Formato esperado:
         * ERROR;postId;commentId;motivo
         */
        String[] parts = content.split(";", 4);

        if (parts.length < 4) {
            System.out.println("[" + getLocalName() + "] Mensaje de error con formato inválido:");
            System.out.println("    " + content);
            return;
        }

        String type = parts[0].trim();
        String postId = parts[1].trim();
        String commentId = parts[2].trim();
        String reason = parts[3].trim();

        if (!type.equalsIgnoreCase("ERROR")) {
            System.out.println("[" + getLocalName() + "] INFORM recibido, pero no es un error:");
            System.out.println("    " + content);
            return;
        }

        String uniqueCommentId = postId + "_" + commentId;

        processedComments.remove(uniqueCommentId);

        System.out.println("[" + getLocalName() + "] Error recibido desde " + message.getSender().getLocalName());
        System.out.println("    Publicacion: " + postId);
        System.out.println("    Comentario: " + commentId);
        System.out.println("    Motivo: " + reason);
        System.out.println("    Comentario eliminado de procesados. Se reintentará en el próximo ciclo.");
        System.out.println("\n");
    }

    /*********************************** Behaviours ***********************************/


    OneShotBehaviour checkBehaviour = new OneShotBehaviour(this) {
        @Override
        public void action() {
            checkNewComments();
        }
    };
    CyclicBehaviour errorBehaviour = new CyclicBehaviour(this) {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("sentiment-error")
            );

            ACLMessage message = receive(template);

            if (message != null) {
                handleSentimentError(message);
            } else {
                block();
            }
        }
    };

    /*********************************** setup ***********************************/


    protected void setup() {
        Object[] args = getArguments();
        String filePath = (String)args[0];
        commentsFile = Paths.get(filePath);

        System.out.println("[" + getLocalName() + "] Agente de adquisicion iniciado");

        registerAgent(servicio);

        System.out.println("[" + getLocalName() + "] Vigilando fichero: " + commentsFile.toAbsolutePath());
        System.out.println("\n");

        addBehaviour(checkBehaviour);
        addBehaviour(errorBehaviour);

    }
}