package com.abdulrahmanmahmoud.teleprompter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import javafx.scene.Cursor;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {
    private Model model;
    private Recognizer recognizer;
    private volatile boolean recognizing = false;
    private TargetDataLine micLine;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // ─── window + script load ────────────────────────────────────────────────
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);

        String script;
        try (InputStream in = getClass().getResourceAsStream("/script.txt")) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // ─── build TextFlow ───────────────────────────────────────────────────────
        List<Text> wordNodes = new ArrayList<>();
        for (String w : script.split("\\s+")) {
            Text t = new Text(w + " ");
            t.getStyleClass().add("teleprompter-word");
            wordNodes.add(t);
        }

        TextFlow flow = new TextFlow();
        flow.getChildren().addAll(wordNodes);
        flow.setTextAlignment(TextAlignment.CENTER);
        flow.setLineSpacing(5);
        flow.setPrefWidth(780);

        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");

        StackPane root = new StackPane(scroll);
        root.setStyle("-fx-background-color: transparent; -fx-padding: 20;");

        Scene scene = new Scene(root, 800, 300);
        scene.setFill(null);
        scene.getStylesheets().add(getClass()
            .getResource("/teleprompt.css").toExternalForm());

        // ─── highlighting helper ───────────────────────────────────────────────────
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
        // — enable click‑to‑jump on each word —
        for (int i = 0; i < wordNodes.size(); i++) {
            final int idx = i;
            Text t = wordNodes.get(i);
        
            // show a hand cursor on hover
            t.setCursor(Cursor.HAND);
        
            // when clicked, reset currentIndex and repaint
            t.setOnMouseClicked(evt -> {
            currentIndex[0] = idx;
            highlight.accept(idx);
            });
        }
        highlight.accept(0);

        // ─── manual advance ────────────────────────────────────────────────────────
        scene.setOnKeyPressed(evt -> {
            if (evt.getCode() == KeyCode.SPACE) {
                int next = currentIndex[0] + 1;
                if (next < wordNodes.size()) {
                    currentIndex[0] = next;
                    highlight.accept(next);
                }
            }
        });

        // ─── drag to move ─────────────────────────────────────────────────────────
        final double[] drag = new double[2];
        root.setOnMousePressed(e -> {
            drag[0] = e.getSceneX();
            drag[1] = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            primaryStage.setX(e.getScreenX() - drag[0]);
            primaryStage.setY(e.getScreenY() - drag[1]);
        });

        // ─── unpack & init Vosk ───────────────────────────────────────────────────
        Path tmp = Files.createTempDirectory("vosk-model");
        URI uri = getClass().getResource("/model/vosk-model-small-en-us-0.15").toURI();
        Path rootModel = Path.of(uri);
        Files.walk(rootModel).forEach(src -> {
            try {
                Path rel = rootModel.relativize(src);
                Path dst = tmp.resolve(rel.toString());
                if (Files.isDirectory(src)) Files.createDirectories(dst);
                else Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        model = new Model(tmp.toString());
        recognizer = new Recognizer(model, 16000.0f);

        // ─── microphone toggle ────────────────────────────────────────────────────
        ToggleButton micBtn = new ToggleButton("🎤 Off");
        micBtn.setOnAction(e -> {
            if (micBtn.isSelected()) {
                micBtn.setText("🎤 On");
                startSpeechRecognition(spokenWord -> {
                    // strip trailing punctuation + lowercase
                    String heard = spokenWord.replaceAll("\\W+$", "").toLowerCase();

                    // only ever match the *next* word
                    int idx = currentIndex[0] + 1;
                    if (idx < wordNodes.size()) {
                        String expect = wordNodes.get(idx)
                            .getText()
                            .trim()
                            .replaceAll("\\W+$", "")
                            .toLowerCase();

                        System.out.printf("heard  -> %s%nexpect -> %s%n", heard, expect);

                        if (heard.equals(expect)) {
                            Platform.runLater(() -> {
                                currentIndex[0] = idx;
                                highlight.accept(idx);
                            });
                        }
                    }
                });
            } else {
                micBtn.setText("🎤 Off");
                stopSpeechRecognition();
            }
        });
        root.getChildren().add(micBtn);
        StackPane.setAlignment(micBtn, Pos.TOP_RIGHT);

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

                byte[] buf = new byte[1024];    // 1 KB buffer → ~32 ms of audio
                while (recognizing) {
                    int len = micLine.read(buf, 0, buf.length);
                    if (len < 0) break;

                    // get either a final or a partial result
                    boolean isFinal = recognizer.acceptWaveForm(buf, len);
                    String json = isFinal
                        ? recognizer.getResult()
                        : recognizer.getPartialResult();

                    // logging for inspection
                    System.out.println("VOSK JSON: " + json);

                    JSONObject o = new JSONObject(json);
                    String text = "";
                    if (isFinal && !o.isNull("text")) {
                        text = o.getString("text");
                    } else if (!isFinal && !o.isNull("partial")) {
                        text = o.getString("partial");
                    }

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

    public static void main(String[] args) {
        launch(args);
    }
}
