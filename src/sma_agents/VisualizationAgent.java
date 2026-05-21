package sma_agents;


import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import com.google.gson.Gson;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agente de visualizacion del SMA.
 */
public class VisualizationAgent extends Agent {
    /********************* Variables globales **********************/
    public static final String SERVICE_TYPE = "visualization-agent";
    public static final String SERVICE_NAME = "visualization-agent";
    public static final String CONVERSATION_ID = "sentiment-result";

    private final Gson gson = new Gson();

    private JFrame frame;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JLabel totalLabel;
    private JLabel posLabel;
    private JLabel negLabel;
    private JLabel neuLabel;
    private JLabel errorLabel;

    private int total = 0;
    private int pos = 0;
    private int neg = 0;
    private int neu = 0;
    private int error = 0;

    /********************* clases necesarias para los mensajes **********************/
    public static class SentimentResult {
        String postId;
        String commentId;
        String text;
        String sentiment;
        double score;
        String timestamp;
    }

/********************* Aux **********************/

    /**metodo para crear la interfaz grafica**/
    private void createUi(){

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                frame = new JFrame("SMA - Analisis de sentimiento");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setLayout(new BorderLayout());

                // panel de contadores
                JPanel countersPanel = new JPanel(new GridLayout(1, 5));

                totalLabel = new JLabel("Total: 0");
                posLabel = new JLabel("POS: 0");
                negLabel = new JLabel("NEG: 0");
                neuLabel = new JLabel("NEU: 0");
                errorLabel = new JLabel("ERROR: 0");

                countersPanel.add(totalLabel);
                countersPanel.add(posLabel);
                countersPanel.add(negLabel);
                countersPanel.add(neuLabel);
                countersPanel.add(errorLabel);

                // tabla principal
                tableModel = new DefaultTableModel(
                        new Object[]{"Hora", "Post", "Comentario", "Sentimiento", "Score", "Texto"},
                        0
                ) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };

                JTable table = new JTable(tableModel);

                table.setAutoCreateRowSorter(true);

                table.getColumnModel().getColumn(0).setPreferredWidth(110);
                table.getColumnModel().getColumn(1).setPreferredWidth(70);
                table.getColumnModel().getColumn(2).setPreferredWidth(90);
                table.getColumnModel().getColumn(3).setPreferredWidth(90);
                table.getColumnModel().getColumn(4).setPreferredWidth(70);
                table.getColumnModel().getColumn(5).setPreferredWidth(500);

                // area de logs
                logArea = new JTextArea(6, 80);
                logArea.setEditable(false);

                frame.add(countersPanel, BorderLayout.NORTH);
                frame.add(new JScrollPane(table), BorderLayout.CENTER);
                frame.add(new JScrollPane(logArea), BorderLayout.SOUTH);

                frame.setSize(1000, 600);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    /**metodo para registrar el servicio en el DF**/
    private void registerVisualizationService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_TYPE);
        sd.setName(SERVICE_NAME);
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("[" + getLocalName() + "] Servicio registrado en DF: " + SERVICE_TYPE);
        } catch (FIPAException e) {
            System.err.println("[" + getLocalName() + "] No se pudo registrar el servicio en DF: " + e.getMessage());
        }
    }

    /**metodo para actualizar los contadores**/

    private void updateCounters(String sentiment){
        total++;

        if ("POS".equalsIgnoreCase(sentiment)) {
            pos++;

        } else if ("NEG".equalsIgnoreCase(sentiment)) {
            neg++;

        } else if ("NEU".equalsIgnoreCase(sentiment)) {
            neu++;

        } else {
            error++;
        }
    }

    /**metodo para refrescar las etiquetas de contadores**/
    private void refreshCounters() {
        if (totalLabel != null)
            totalLabel.setText("Total: " + total);
        if (posLabel != null)
            posLabel.setText("POS: " + pos);
        if (negLabel != null)
            negLabel.setText("NEG: " + neg);
        if (neuLabel != null)
            neuLabel.setText("NEU: " + neu);
        if (errorLabel != null)
            errorLabel.setText("ERROR: " + error);
    }

    /**metodo para actualizar la interfaz**/
    private void updateUi(final SentimentResult result) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (tableModel != null) {
                    tableModel.addRow(new Object[]{
                            result.timestamp,
                            result.postId,
                            result.commentId,
                            result.sentiment,
                            String.format("%.4f", result.score),
                            result.text
                    });
                }

                refreshCounters();

                if (logArea != null) {
                    logArea.append("[" + result.timestamp + "] "
                            + result.postId + "/" + result.commentId
                            + " -> " + result.sentiment
                            + " (score=" + String.format("%.4f", result.score) + ")\n");
                }
            }
        });
    }

    /**metodo para registrar mensajes mal formados**/

    private void registerMalformedMessage(final String content){
        error++;
        total++;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                refreshCounters();

                if (logArea != null) {
                    logArea.append("[ERROR] Mensaje mal formado: " + content + "\n");
                }
            }
        });
    }

    /**metodo para procesar los mensajes**/
    private void processVisualizationMessage(ACLMessage message) {
        try {
            SentimentResult result = gson.fromJson(message.getContent(), SentimentResult.class);

            if (result == null || result.postId == null || result.commentId == null || result.sentiment == null) {
                registerMalformedMessage(message.getContent());
                return;
            }

            if (result.timestamp == null || result.timestamp.trim().isEmpty()) {
                result.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            }

            updateCounters(result.sentiment);
            updateUi(result);

            System.out.println("[" + getLocalName() + "] Resultado visualizado: "
                    + result.postId + "/" + result.commentId + " -> " + result.sentiment);

        } catch (Exception e) {
            registerMalformedMessage(message.getContent());
            System.err.println("[" + getLocalName() + "] Error procesando mensaje de visualizacion: " + e.getMessage());
        }
    }




    @Override
    protected void setup() {

        System.out.println("[" + getLocalName() + "] VisualizationAgent iniciado");

        // crear interfaz
        createUi();

        // registrar servicio
        registerVisualizationService();

        // plantilla para filtrar mensajes
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId(CONVERSATION_ID)
        );

        // comportamiento ciclico
        addBehaviour(new CyclicBehaviour(this) {

            @Override
            public void action() {

                ACLMessage message = receive(template);

                if (message != null) {

                    processVisualizationMessage(message);

                } else {
                    block();
                }
            }
        });
    }



    @Override
    protected void takeDown() {

        try {
            DFService.deregister(this);

        } catch (FIPAException ignored) {
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                if (frame != null) {
                    frame.dispose();
                }
            }
        });

        System.out.println("[" + getLocalName() + "] VisualizationAgent finalizado");
    }
}
