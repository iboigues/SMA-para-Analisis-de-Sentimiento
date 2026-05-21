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
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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

    // Paneles y pestañas principales
    private JTabbedPane mainTabbedPane;
    private JTabbedPane detailsTabbedPane; // Contenedor de pestañas secundarias por Post

    private DefaultTableModel postSummaryTableModel;
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

    // Mapa para gestionar la lógica de negocio y las tablas de cada Post
    private final Map<String, PostContainer> postMap = new HashMap<>();

    /********************* Estructuras auxiliares **********************/
    public static class SentimentResult {
        String postId;
        String commentId;
        String text;
        String sentiment;
        double score;
        String timestamp;
    }

    // Estructura para almacenar los contadores y el modelo de tabla de CADA post
    private static class PostContainer {
        String postId;
        int totalComments = 0;
        int posCount = 0;
        int negCount = 0;
        int neuCount = 0;
        int rowIndex = -1; // Fila en la tabla de resumen general

        DefaultTableModel detailTableModel; // Tabla exclusiva de este Post

        PostContainer(String postId) {
            this.postId = postId;
        }
    }

    /********************* Interfaz Gráfica **********************/

    private void createUi(){
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame = new JFrame("SMA - Analisis de Sentimiento por Post");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setLayout(new BorderLayout());

                // 1. Contadores Globales
                JPanel countersPanel = new JPanel(new GridLayout(1, 5));
                totalLabel = new JLabel("Total Global: 0");
                posLabel = new JLabel("POS: 0");
                negLabel = new JLabel("NEG: 0");
                neuLabel = new JLabel("NEU: 0");
                errorLabel = new JLabel("ERROR: 0");

                countersPanel.add(totalLabel);
                countersPanel.add(posLabel);
                countersPanel.add(negLabel);
                countersPanel.add(neuLabel);
                countersPanel.add(errorLabel);
                frame.add(countersPanel, BorderLayout.NORTH);

                // 2. Contenedor Principal de Pestañas
                mainTabbedPane = new JTabbedPane();

                // --- PESTAÑA 1: RESUMEN GENERAL ---
                postSummaryTableModel = new DefaultTableModel(
                    new Object[]{"ID del Post", "Total Comentarios", "Positivos (POS)", "Negativos (NEG)", "Neutros (NEU)"},
                    0
                ) {
                    @Override
                    public boolean isCellEditable(int row, int column) { return false; }
                };
                JTable postSummaryTable = new JTable(postSummaryTableModel);
                postSummaryTable.setAutoCreateRowSorter(true);
                JPanel summaryPanel = new JPanel(new BorderLayout());
                summaryPanel.add(new JScrollPane(postSummaryTable), BorderLayout.CENTER);

                // --- PESTAÑA 2: DETALLES FILTRADOS (Contenedor Dinámico) ---
                detailsTabbedPane = new JTabbedPane(JTabbedPane.LEFT); // Pestañas laterales para mejor orden

                mainTabbedPane.addTab("Resumen de Posts", summaryPanel);
                mainTabbedPane.addTab("Detalles por Post", detailsTabbedPane);
                frame.add(mainTabbedPane, BorderLayout.CENTER);

                // 3. Logs
                logArea = new JTextArea(5, 80);
                logArea.setEditable(false);
                frame.add(new JScrollPane(logArea), BorderLayout.SOUTH);

                frame.setSize(1200, 700);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }

    private void registerVisualizationService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_TYPE);
        sd.setName(SERVICE_NAME);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println("Error registrando en DF: " + e.getMessage());
        }
    }

    private void updateCounters(String postId, String sentiment){
        total++;
        if ("POS".equalsIgnoreCase(sentiment)) pos++;
        else if ("NEG".equalsIgnoreCase(sentiment)) neg++;
        else if ("NEU".equalsIgnoreCase(sentiment)) neu++;
        else error++;

        if (postId != null && !postId.trim().isEmpty()) {
            postMap.putIfAbsent(postId, new PostContainer(postId));
            PostContainer container = postMap.get(postId);

            container.totalComments++;
            if ("POS".equalsIgnoreCase(sentiment)) container.posCount++;
            else if ("NEG".equalsIgnoreCase(sentiment)) container.negCount++;
            else if ("NEU".equalsIgnoreCase(sentiment)) container.neuCount++;
        }
    }

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

    private void updateUi(final SentimentResult result) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                PostContainer container = postMap.get(result.postId);
                if (container == null) return;

                // 1. Gestionar o Crear la pestaña de detalle específica de este Post
                if (container.detailTableModel == null) {
                    // Crear el modelo de tabla exclusivo para el post
                    container.detailTableModel = new DefaultTableModel(
                        new Object[]{"Hora", "Comentario ID", "Sentimiento", "Score", "Texto"},
                        0
                    ) {
                        @Override
                        public boolean isCellEditable(int row, int column) { return false; }
                    };

                    JTable postDetailTable = new JTable(container.detailTableModel);
                    postDetailTable.setAutoCreateRowSorter(true);

                    // Ajustar anchos
                    postDetailTable.getColumnModel().getColumn(0).setPreferredWidth(90);
                    postDetailTable.getColumnModel().getColumn(1).setPreferredWidth(100);
                    postDetailTable.getColumnModel().getColumn(2).setPreferredWidth(90);
                    postDetailTable.getColumnModel().getColumn(3).setPreferredWidth(70);
                    postDetailTable.getColumnModel().getColumn(4).setPreferredWidth(500);

                    // Añadir la tabla al contenedor de pestañas secundario
                    JPanel panelPost = new JPanel(new BorderLayout());
                    panelPost.add(new JScrollPane(postDetailTable), BorderLayout.CENTER);

                    // Limitar el largo del título de la pestaña si el ID es muy largo
                    String tabTitle = result.postId.length() > 20 ? result.postId.substring(0, 17) + "..." : result.postId;
                    detailsTabbedPane.addTab(tabTitle, panelPost);
                }

                // 2. Insertar el comentario en la tabla de su respectivo Post
                container.detailTableModel.addRow(new Object[]{
                    result.timestamp,
                    result.commentId,
                    result.sentiment,
                    String.format("%.4f", result.score),
                    result.text
                });

                // 3. Actualizar la fila en la tabla de Resumen General
                if (postSummaryTableModel != null) {
                    Object[] rowSummary = new Object[]{
                        container.postId,
                        container.totalComments,
                        container.posCount,
                        container.negCount,
                        container.neuCount
                    };

                    if (container.rowIndex == -1) {
                        container.rowIndex = postSummaryTableModel.getRowCount();
                        postSummaryTableModel.addRow(rowSummary);
                    } else {
                        postSummaryTableModel.setValueAt(container.totalComments, container.rowIndex, 1);
                        postSummaryTableModel.setValueAt(container.posCount, container.rowIndex, 2);
                        postSummaryTableModel.setValueAt(container.negCount, container.rowIndex, 3);
                        postSummaryTableModel.setValueAt(container.neuCount, container.rowIndex, 4);
                    }
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

    private void registerMalformedMessage(final String content){
        error++; total++;
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

            updateCounters(result.postId, result.sentiment);
            updateUi(result);
        } catch (Exception e) {
            registerMalformedMessage(message.getContent());
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
