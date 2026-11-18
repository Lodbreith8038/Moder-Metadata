package org.proyecto.ui;

import org.proyecto.analisis.Metadato;
import org.proyecto.analisis.RegistroAnalizadores;
import org.proyecto.analisis.ResultadoAnalisis;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ventana principal: recibe una imagen o un lote (archivos o carpetas, con
 * botón o arrastrando), analiza cada uno en segundo plano y muestra:
 *   izquierda -> la lista del lote con una etiqueta "IA" / "Sin evidencia"
 *   derecha   -> el veredicto con sus pistas y la tabla de metadatos (estilo ExifTool)
 */
public class VentanaLote extends JFrame {

    // Colores (los mismos tonos de la maqueta)
    static final Color FONDO = new Color(245, 247, 250);
    static final Color IA_FONDO = new Color(250, 238, 218), IA_TEXTO = new Color(133, 79, 11);
    static final Color NEUTRO_FONDO = new Color(236, 236, 232), NEUTRO_TEXTO = new Color(95, 94, 90);
    static final Color ERROR_FONDO = new Color(252, 235, 235), ERROR_TEXTO = new Color(163, 45, 45);

    private static final String TARJETA_VACIA = "vacia", TARJETA_LOTE = "lote";

    private final RegistroAnalizadores registro = new RegistroAnalizadores();
    private final Set<String> yaAgregados = new HashSet<>();

    private final DefaultListModel<ResultadoAnalisis> modeloLista = new DefaultListModel<>();
    private final JList<ResultadoAnalisis> lista = new JList<>(modeloLista);

    private final DefaultTableModel modeloTabla = new DefaultTableModel(new Object[]{"Grupo", "Etiqueta", "Valor"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;   // aquí solo se mira; editar será en el panel de edición
        }
    };
    private final JTable tabla = new JTable(modeloTabla);
    private final TableRowSorter<DefaultTableModel> ordenador = new TableRowSorter<>(modeloTabla);
    private final JTextField buscador = new JTextField(18);

    private final JLabel veredicto = new JLabel();
    private final JLabel estado = new JLabel("Listo");
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel centro = new JPanel(tarjetas);

    private int analizando = 0;   // cuántos lotes siguen en proceso

    public VentanaLote() {
        setTitle("Moder-Metadata");
        setSize(1100, 680);
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(FONDO);
        setContentPane(raiz);

        raiz.add(crearBarraSuperior(), BorderLayout.NORTH);
        centro.add(crearZonaVacia(), TARJETA_VACIA);
        centro.add(crearVistaLote(), TARJETA_LOTE);
        raiz.add(centro, BorderLayout.CENTER);

        estado.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        raiz.add(estado, BorderLayout.SOUTH);

        // Se puede soltar archivos en cualquier parte de la ventana
        TransferHandler recibir = new RecibirArchivos();
        for (JComponent c : new JComponent[]{raiz, lista, tabla, centro}) c.setTransferHandler(recibir);
    }

    // ------------------------------------------------------------------ construcción de la ventana

    private JComponent crearBarraSuperior() {
        JButton abrirArchivos = new JButton("Abrir archivos");
        JButton abrirCarpeta = new JButton("Abrir carpeta");
        JButton limpiar = new JButton("Limpiar lista");

        abrirArchivos.addActionListener(e -> elegir(JFileChooser.FILES_ONLY));
        abrirCarpeta.addActionListener(e -> elegir(JFileChooser.DIRECTORIES_ONLY));
        limpiar.addActionListener(e -> limpiar());

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        barra.setBackground(Color.WHITE);
        barra.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 225, 220)));
        barra.add(abrirArchivos);
        barra.add(abrirCarpeta);
        barra.add(limpiar);
        barra.add(Box.createHorizontalStrut(16));
        barra.add(new JLabel("Buscar etiqueta:"));
        barra.add(buscador);

        buscador.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        return barra;
    }

    /** Lo que se ve antes de cargar nada: la zona de arrastre del diseño original. */
    private JComponent crearZonaVacia() {
        JPanel zona = new JPanel(new BorderLayout());
        zona.setBackground(Color.WHITE);
        zona.setBorder(BorderFactory.createDashedBorder(new Color(200, 70, 60), 2f, 8f, 6f, true));

        JLabel mensaje = new JLabel("<html><center>Arrastra aquí tus imágenes o carpetas<br>"
                + "<font size='-1' color='#777777'>PNG y JPG por ahora · se analiza todo el lote</font></center></html>",
                SwingConstants.CENTER);
        mensaje.setFont(mensaje.getFont().deriveFont(Font.BOLD, 20f));
        zona.add(mensaje, BorderLayout.CENTER);

        JButton boton = new JButton("Seleccionar archivos");
        boton.addActionListener(e -> elegir(JFileChooser.FILES_ONLY));
        JPanel pie = new JPanel();
        pie.setOpaque(false);
        pie.setBorder(BorderFactory.createEmptyBorder(0, 10, 30, 10));
        pie.add(boton);
        zona.add(pie, BorderLayout.SOUTH);

        JPanel margen = new JPanel(new BorderLayout());
        margen.setBackground(FONDO);
        margen.setBorder(BorderFactory.createEmptyBorder(40, 70, 40, 70));
        margen.add(zona);
        margen.setTransferHandler(new RecibirArchivos());
        zona.setTransferHandler(new RecibirArchivos());
        return margen;
    }

    private JComponent crearVistaLote() {
        // Izquierda: lista del lote
        lista.setCellRenderer(new FilaLote());
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) mostrar(lista.getSelectedValue());
        });
        JScrollPane izquierda = new JScrollPane(lista);
        izquierda.setMinimumSize(new Dimension(220, 100));

        // Derecha: veredicto arriba, tabla abajo
        veredicto.setOpaque(true);
        veredicto.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        tabla.setRowSorter(ordenador);
        tabla.setRowHeight(24);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        tabla.getColumnModel().getColumn(0).setMinWidth(90);
        tabla.getColumnModel().getColumn(0).setMaxWidth(140);
        tabla.getColumnModel().getColumn(1).setMinWidth(170);
        tabla.getColumnModel().getColumn(1).setMaxWidth(320);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(600);
        tabla.setToolTipText("Doble clic en una fila para ver el valor completo");
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) verValorCompleto();
            }
        });

        JPanel derecha = new JPanel(new BorderLayout());
        derecha.add(veredicto, BorderLayout.NORTH);
        derecha.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, derecha);
        division.setDividerLocation(280);
        division.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        division.setBackground(FONDO);
        return division;
    }

    // ------------------------------------------------------------------ cargar y analizar

    private void elegir(int modo) {
        JFileChooser selector = new JFileChooser();
        selector.setFileSelectionMode(modo);
        selector.setMultiSelectionEnabled(true);
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            agregar(List.of(selector.getSelectedFiles()));
        }
    }

    /** Recibe archivos y/o carpetas y los analiza sin congelar la ventana. */
    void agregar(List<File> entradas) {
        List<File> archivos = new ArrayList<>();
        for (File f : expandir(entradas)) {
            if (yaAgregados.add(f.getAbsolutePath())) archivos.add(f);
        }
        if (archivos.isEmpty()) {
            estado.setText("No hay archivos nuevos para analizar");
            return;
        }
        tarjetas.show(centro, TARJETA_LOTE);
        analizando++;

        // SwingWorker: analiza en otro hilo y va pasando los resultados a la ventana
        new SwingWorker<Void, ResultadoAnalisis>() {
            private int hechos = 0;

            @Override
            protected Void doInBackground() {
                for (File f : archivos) publish(registro.analizar(f));
                return null;
            }

            @Override
            protected void process(List<ResultadoAnalisis> nuevos) {
                for (ResultadoAnalisis r : nuevos) modeloLista.addElement(r);
                hechos += nuevos.size();
                if (lista.getSelectedIndex() < 0) lista.setSelectedIndex(0);
                estado.setText("Analizando " + hechos + " de " + archivos.size() + "…");
            }

            @Override
            protected void done() {
                analizando--;
                if (analizando == 0) mostrarResumen();
            }
        }.execute();
    }

    /** Las carpetas se recorren por dentro (con subcarpetas) y se quedan solo los archivos. */
    private static List<File> expandir(List<File> entradas) {
        List<File> archivos = new ArrayList<>();
        for (File f : entradas) {
            if (f.isFile()) {
                archivos.add(f);
            } else if (f.isDirectory()) {
                try (Stream<Path> rutas = Files.walk(f.toPath())) {
                    rutas.filter(Files::isRegularFile).sorted().forEach(p -> archivos.add(p.toFile()));
                } catch (IOException | UncheckedIOException e) {
                    // carpeta sin permisos: seguimos con lo demás
                }
            }
        }
        return archivos;
    }

    private void limpiar() {
        modeloLista.clear();
        yaAgregados.clear();
        modeloTabla.setRowCount(0);
        tarjetas.show(centro, TARJETA_VACIA);
        estado.setText("Listo");
    }

    private void mostrarResumen() {
        int ia = 0, errores = 0;
        for (int i = 0; i < modeloLista.size(); i++) {
            ResultadoAnalisis r = modeloLista.get(i);
            if (r.tieneError()) errores++;
            else if (r.veredicto().hayEvidencia()) ia++;
        }
        estado.setText(modeloLista.size() + " archivos · " + ia + " con evidencia de IA · "
                + errores + " no se pudieron leer");
    }

    // ------------------------------------------------------------------ mostrar un resultado

    private void mostrar(ResultadoAnalisis r) {
        modeloTabla.setRowCount(0);
        if (r == null) return;

        for (Metadato m : r.metadatos()) {
            modeloTabla.addRow(new Object[]{m.grupo(), m.clave(), resumir(m.valor())});
        }

        if (r.tieneError()) {
            pintarVeredicto(ERROR_FONDO, ERROR_TEXTO, "No se pudo analizar: " + r.error(), List.of());
        } else if (r.veredicto().hayEvidencia()) {
            pintarVeredicto(IA_FONDO, IA_TEXTO, "Hay evidencia de que se hizo con IA", r.veredicto().pistas());
        } else {
            pintarVeredicto(NEUTRO_FONDO, NEUTRO_TEXTO, "Sin evidencia de IA en los metadatos",
                    List.of("Esto no prueba que sea real: los metadatos se pueden borrar."));
        }
    }

    private void pintarVeredicto(Color fondo, Color texto, String titulo, List<String> pistas) {
        StringBuilder html = new StringBuilder("<html><b>").append(escapar(titulo)).append("</b>");
        for (String p : pistas) html.append("<br>• ").append(escapar(p));
        veredicto.setText(html.append("</html>").toString());
        veredicto.setBackground(fondo);
        veredicto.setForeground(texto);
    }

    /** En la tabla mostramos una línea; el valor completo se ve con doble clic. */
    private static String resumir(String valor) {
        if (valor == null) return "";
        String una = valor.replace('\n', ' ').replace('\r', ' ');
        return una.length() > 300 ? una.substring(0, 300) + " …" : una;
    }

    private void verValorCompleto() {
        ResultadoAnalisis r = lista.getSelectedValue();
        int fila = tabla.convertRowIndexToModel(tabla.getSelectedRow());
        if (r == null || fila >= r.metadatos().size()) return;
        Metadato m = r.metadatos().get(fila);

        JTextArea area = new JTextArea(m.valor(), 18, 60);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                m.grupo() + " / " + m.clave(), JOptionPane.PLAIN_MESSAGE);
    }

    private void filtrar() {
        String texto = buscador.getText().trim();
        ordenador.setRowFilter(texto.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(texto), 0, 1, 2));
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ piezas pequeñas

    /** Cómo se dibuja cada archivo en la lista: nombre + etiqueta de color. */
    private static class FilaLote extends JPanel implements ListCellRenderer<ResultadoAnalisis> {
        private final JLabel nombre = new JLabel();
        private final JLabel etiqueta = new JLabel();
        private final Border borde = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 235, 230)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8));

        FilaLote() {
            super(new BorderLayout(8, 0));
            etiqueta.setOpaque(true);
            etiqueta.setFont(etiqueta.getFont().deriveFont(11f));
            etiqueta.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            add(nombre, BorderLayout.CENTER);
            add(etiqueta, BorderLayout.EAST);
            setBorder(borde);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ResultadoAnalisis> l, ResultadoAnalisis r,
                                                      int indice, boolean seleccionado, boolean foco) {
            nombre.setText(r.archivo().getName());
            nombre.setToolTipText(r.archivo().getAbsolutePath());
            if (r.tieneError()) {
                colorear(etiqueta, r.formato().equals("Desconocido") ? "No soportado" : "Error", ERROR_FONDO, ERROR_TEXTO);
            } else if (r.veredicto().hayEvidencia()) {
                colorear(etiqueta, "IA", IA_FONDO, IA_TEXTO);
            } else {
                colorear(etiqueta, "Sin evidencia", NEUTRO_FONDO, NEUTRO_TEXTO);
            }
            setBackground(seleccionado ? l.getSelectionBackground() : Color.WHITE);
            nombre.setForeground(seleccionado ? l.getSelectionForeground() : Color.DARK_GRAY);
            return this;
        }

        private static void colorear(JLabel l, String texto, Color fondo, Color letra) {
            l.setText(texto);
            l.setBackground(fondo);
            l.setForeground(letra);
        }
    }

    /** Acepta archivos y carpetas arrastrados desde el explorador. */
    private class RecibirArchivos extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport soporte) {
            return soporte.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport soporte) {
            if (!canImport(soporte)) return false;
            try {
                agregar((List<File>) soporte.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                return true;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(VentanaLote.this, "No se pudieron recibir los archivos: " + ex.getMessage());
                return false;
            }
        }
    }
}
