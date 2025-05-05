package com.abdulrahmanmahmoud.teleprompter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.FileChooser;

public class App extends Application {
    private static final Map<String, Set<String>> HOMOPHONES = loadHomophones();
    private static Map<String, Set<String>> loadHomophones() {
        Map<String, Set<String>> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                App.class.getResourceAsStream("/homophones.txt"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] group = line.split(",");
                for (String w : group) {
                    String key = w.trim().toLowerCase();
                    map.computeIfAbsent(key, k -> new HashSet<>());
                    for (String sib : group) {
                        sib = sib.trim().toLowerCase();
                        if (!sib.equals(key)) {
                            map.get(key).add(sib);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load homophones.txt");
            e.printStackTrace();
        }
        return map;
    }

    private Model model;
    private Recognizer recognizer;
    private volatile boolean recognizing = false;
    private TargetDataLine micLine;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // ─── 1) Window setup ───────────────────────────────────────────────────────
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);

        // ─── 2) Load default script.txt ────────────────────────────────────────────
        String script;
        try (InputStream in = getClass().getResourceAsStream("/script.txt")) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // ─── 3) Create Text nodes & TextFlow ───────────────────────────────────────
        List<Text> wordNodes = new ArrayList<>();
        for (String w : script.split("[—\\-\\s]+")) {
            Text t = new Text(w + " ");
            t.getStyleClass().add("teleprompter-word");
            wordNodes.add(t);
        }
        TextFlow flow = new TextFlow(wordNodes.toArray(new Text[0]));
        flow.setTextAlignment(TextAlignment.CENTER);
        flow.setLineSpacing(5);
        flow.setPrefWidth(780);

        // ─── 4) Scrolling text panel ───────────────────────────────────────────────
        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");

        // ─── 5) Highlighting helper ────────────────────────────────────────────────
        final int[] currentIndex = { 0 };
        Consumer<Integer> highlight = idx -> {
            wordNodes.forEach(n -> n.getStyleClass().setAll("teleprompter-word"));
            if (idx < wordNodes.size()) {
                Text t = wordNodes.get(idx);
                t.getStyleClass().add("teleprompter-highlight");
                Bounds nb = t.getBoundsInParent();
                Bounds cb = flow.getBoundsInLocal();
                Bounds vp = scroll.getViewportBounds();
                double y = nb.getMinY();
                double v = (y + nb.getHeight()/2 - vp.getHeight()/2)
                           / (cb.getHeight() - vp.getHeight());
                scroll.setVvalue(Math.max(0, Math.min(1, v)));
            }
        };
        highlight.accept(0);

        // click‑to‑jump on any word
        for (int i = 0; i < wordNodes.size(); i++) {
            final int idx = i;
            Text t = wordNodes.get(i);
            t.setCursor(Cursor.HAND);
            t.setOnMouseClicked(e -> {
                currentIndex[0] = idx;
                highlight.accept(idx);
            });
        }

        // ─── 6) Mic toggle button ──────────────────────────────────────────────────
        ToggleButton micBtn = new ToggleButton("🎤 Off");
        micBtn.setOnAction(e -> {
            if (micBtn.isSelected()) {
                micBtn.setText("🎤 On");
                startSpeechRecognition(spoken -> {
                    String heard = spoken.replaceAll("\\W+$", "").toLowerCase();
        
                    // look ahead up to 3 words
                    for (int offset = 1; offset <= 3; offset++) {
                        int idx = currentIndex[0] + offset;
                        if (idx >= wordNodes.size()) break;
        
                        String expect = wordNodes.get(idx)
                            .getText().trim()
                            .replaceAll("[\\W—]+$","")
                            .toLowerCase();
        
                        System.out.printf("heard  -> %s%nexpect -> %s (offset %d)%n", heard, expect, offset);
        
                        if (wordsMatch(heard, expect)) {
                            int newIndex = idx;
                            Platform.runLater(() -> {
                                currentIndex[0] = newIndex;
                                highlight.accept(newIndex);
                            });
                            break;  // stop looking further once matched
                        }
                    }
                });
            } else {
                micBtn.setText("🎤 Off");
                stopSpeechRecognition();
            }
        });

        // ─── 7) Upload Script button ───────────────────────────────────────────────
        Button uploadBtn = new Button("Upload Script");
        uploadBtn.setOnAction(evt -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open Teleprompter Script");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            File file = chooser.showOpenDialog(primaryStage);
            if (file != null) {
                try {
                    String newScript = Files.readString(
                        file.toPath(), StandardCharsets.UTF_8
                    );
                    wordNodes.clear();
                    flow.getChildren().clear();
                    String[] parts = newScript.split("[—\\-\\s]+");
                    for (int i = 0; i < parts.length; i++) {
                        Text t = new Text(parts[i] + " ");
                        t.getStyleClass().add("teleprompter-word");
                        final int idx2 = i;
                        t.setCursor(Cursor.HAND);
                        t.setOnMouseClicked(e2 -> {
                            currentIndex[0] = idx2;
                            highlight.accept(idx2);
                        });
                        wordNodes.add(t);
                    }
                    flow.getChildren().addAll(wordNodes);
                    currentIndex[0] = 0;
                    highlight.accept(0);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // ─── 8) Bottom control bar ────────────────────────────────────────────────
        HBox controls = new HBox(10, uploadBtn, micBtn);
        controls.getStyleClass().add("control-bar");
        controls.setPadding(new Insets(5));
        controls.setAlignment(Pos.CENTER_RIGHT);

        // ─── 9) Root layout ───────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.setBottom(controls);
        root.setStyle("-fx-background-color: transparent;");

        // ─── 10) Scene + CSS ──────────────────────────────────────────────────────
        Scene scene = new Scene(root, 800, 300);
        scene.setFill(null);
        scene.getStylesheets().add(getClass()
            .getResource("/teleprompt.css").toExternalForm());

        // manual advance on SPACE
        scene.setOnKeyPressed(evt -> {
            if (evt.getCode() == KeyCode.SPACE) {
                int next = currentIndex[0] + 1;
                if (next < wordNodes.size()) {
                    currentIndex[0] = next;
                    highlight.accept(next);
                }
            }
        });

        // drag window by dragging anywhere
        final double[] drag = new double[2];
        root.setOnMousePressed(e -> {
            drag[0] = e.getSceneX();
            drag[1] = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            primaryStage.setX(e.getScreenX() - drag[0]);
            primaryStage.setY(e.getScreenY() - drag[1]);
        });

        // ─── 11) Load Vosk model once ─────────────────────────────────────────────
        Path tmp = Files.createTempDirectory("vosk-model");
        URI uri = getClass()
            .getResource("/model/vosk-model-small-en-us-0.15")
            .toURI();
        Path modelRoot = Path.of(uri);
        Files.walk(modelRoot).forEach(src -> {
            try {
                Path rel = modelRoot.relativize(src);
                Path dst = tmp.resolve(rel.toString());
                if (Files.isDirectory(src)) Files.createDirectories(dst);
                else Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        model = new Model(tmp.toString());
        recognizer = new Recognizer(model, 16000.0f);

        // ─── 12) Show the stage ───────────────────────────────────────────────────
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startSpeechRecognition(Consumer<String> onText) {
        recognizing = true;
        Thread t = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(16000, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                micLine = (TargetDataLine) AudioSystem.getLine(info);
                micLine.open(fmt);
                micLine.start();

                byte[] buf = new byte[1024];
                while (recognizing) {
                    int len = micLine.read(buf, 0, buf.length);
                    if (len < 0) break;
                    boolean isFinal = recognizer.acceptWaveForm(buf, len);
                    String json = isFinal
                        ? recognizer.getResult()
                        : recognizer.getPartialResult();
                    JSONObject o = new JSONObject(json);
                    String text = isFinal
                        ? o.optString("text", "")
                        : o.optString("partial", "");
                    if (!text.isBlank()) {
                        for (String w : text.split("\\s+")) {
                            onText.accept(w);
                        }
                    }
                }
                micLine.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "Vosk-Thread");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private void stopSpeechRecognition() {
        recognizing = false;
        if (micLine != null) {
            micLine.stop();
            micLine.close();
        }
    }

    private static boolean wordsMatch(String heard, String expect) {
        if (heard.equals(expect)) return true;
        Set<String> alts = HOMOPHONES.get(heard);
        if (alts != null && alts.contains(expect)) return true;
        return levenshtein(heard, expect) <= 1;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m+1], curr = new int[m+1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i-1)==b.charAt(j-1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j]+1, curr[j-1]+1), prev[j-1]+cost);
            }
            System.arraycopy(curr, 0, prev, 0, m+1);
        }
        return prev[m];
    }

    public static void main(String[] args) {
        launch(args);
    }
}
