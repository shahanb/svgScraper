package svgscrape.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SVGScraper extends JFrame {
    private JTextField htmlFileField;
    private JTextField outputDirField;
    private JTextArea outputArea;
    private JButton startButton;

    public SVGScraper() {
        setTitle("SVG Scraper");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(3, 1));

        JPanel htmlFilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        htmlFileField = new JTextField(20);
        JButton htmlBrowseButton = new JButton("Browse");
        htmlBrowseButton.addActionListener(e -> browseFile(htmlFileField, JFileChooser.FILES_ONLY));
        htmlFilePanel.add(new JLabel("HTML File:"));
        htmlFilePanel.add(htmlFileField);
        htmlFilePanel.add(htmlBrowseButton);

        JPanel outputDirPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        outputDirField = new JTextField(20);
        JButton outputBrowseButton = new JButton("Browse");
        outputBrowseButton.addActionListener(e -> browseFile(outputDirField, JFileChooser.DIRECTORIES_ONLY));
        outputDirPanel.add(new JLabel("Output Directory (optional):"));
        outputDirPanel.add(outputDirField);
        outputDirPanel.add(outputBrowseButton);

        startButton = new JButton("Start");
        startButton.addActionListener(e -> startScraping());

        inputPanel.add(htmlFilePanel);
        inputPanel.add(outputDirPanel);
        inputPanel.add(startButton);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        setupDragAndDrop(htmlFileField, true);
        setupDragAndDrop(outputDirField, false);
    }

    private void setupDragAndDrop(JTextField field, boolean isFile) {
        new DropTarget(field, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) event.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    if (!droppedFiles.isEmpty()) {
                        File droppedFile = droppedFiles.get(0);
                        if (isFile && droppedFile.isFile()) {
                            field.setText(droppedFile.getAbsolutePath());
                        } else if (!isFile && droppedFile.isDirectory()) {
                            field.setText(droppedFile.getAbsolutePath());
                        } else {
                            JOptionPane.showMessageDialog(SVGScraper.this,
                                    "Please drop a " + (isFile ? "file" : "folder") + " here.");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void browseFile(JTextField field, int mode) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(mode);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void startScraping() {
        String htmlFilePath = htmlFileField.getText();
        String outputDir = outputDirField.getText();

        if (htmlFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an HTML file.");
            return;
        }

        File htmlFile = new File(htmlFilePath);
        if (outputDir.isEmpty()) {
            // Create default 'images' folder in the same directory as the HTML file
            outputDir = new File(htmlFile.getParent(), "images").getAbsolutePath();
            outputDirField.setText(outputDir);
        }

        // Make outputDir effectively final by creating a new final variable
        final String finalOutputDir = outputDir;

        startButton.setEnabled(false);
        outputArea.setText("");

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                processSVGFile(htmlFile, finalOutputDir);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    outputArea.append(message + "\n");
                }
            }

            @Override
            protected void done() {
                startButton.setEnabled(true);
            }
        }.execute();
    }

    private void processSVGFile(File htmlFile, String outputDir) {
        try {
            new File(outputDir).mkdirs();

            String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()));
            Document doc = Jsoup.parse(htmlContent);
            Elements imgTags = doc.select("img[src]");

            int svgCounter = 0;
            for (Element imgTag : imgTags) {
                String src = imgTag.attr("src");
                if (src.startsWith("data:image/svg+xml;base64,")) {
                    svgCounter++;
                    String base64Data = src.split(",")[1];
                    byte[] svgBytes = Base64.getDecoder().decode(base64Data);
                    String svgData = new String(svgBytes);

                    String svgFilePath = outputDir + File.separator + "svg_image_" + svgCounter + ".svg";
                    try (FileWriter writer = new FileWriter(svgFilePath)) {
                        writer.write(svgData);
                    }

                    SwingUtilities.invokeLater(() -> outputArea.append("Saved: " + svgFilePath + "\n"));
                }
            }

            SwingUtilities.invokeLater(() -> outputArea.append("All SVG images have been downloaded.\n"));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> outputArea.append("Error: " + e.getMessage() + "\n"));
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("SVGScraper started. Arguments: " + Arrays.toString(args));
            if (args.length > 0) {
                String filePath = args[0];
                System.out.println("Processing file: " + filePath);
                File htmlFile = new File(filePath);
                if (!htmlFile.exists() || !htmlFile.isFile()) {
                    System.err.println("Invalid file: " + filePath);
                    return;
                }
                String outputDir = new File(htmlFile.getParent(), "images").getAbsolutePath();

                SVGScraper scraper = new SVGScraper();
                scraper.processSVGFile(htmlFile, outputDir);

                System.out.println("Processing complete. Output directory: " + outputDir);

                // Show a dialog to indicate completion
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "SVG extraction complete.\nOutput directory: " + outputDir);
                    System.exit(0);
                });
            } else {
                System.out.println("No arguments provided. Launching GUI.");
                SwingUtilities.invokeLater(() -> {
                    new SVGScraper().setVisible(true);
                });
            }
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}