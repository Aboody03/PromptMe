package io.github.aboody03.teleprompter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import javafx.util.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.formula.functions.Delta;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.Priority;
import javafx.scene.Node;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


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

    private boolean isOverControl(Node node) {
        while (node != null) {
            if (node instanceof Button ||
                node instanceof ToggleButton ||
                node instanceof Slider) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private boolean dragEnabled = false;

    private Mixer.Info findMixer(String keyword) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().toLowerCase().contains(keyword.toLowerCase())) {
                return info;
            }
        }
        return null;
    }

    private Model model;
    private Recognizer recognizer;
    private volatile boolean recognizing = false;
    private TargetDataLine micLine;
    private Tooltip makeTooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(100));
        tip.setHideDelay(Duration.millis(100));
        tip.setShowDuration(Duration.INDEFINITE);
        return tip;
    }

    private void addHoverGrow(Node node) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(150), node);
        grow.setToX(1.1);
        grow.setToY(1.1);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(150), node);
        shrink.setToX(1.0);
        shrink.setToY(1.0);

        node.setOnMouseEntered(e -> grow.playFromStart());
        node.setOnMouseExited (e -> shrink.playFromStart());
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        // ─── 0) Window setup ───────────────────────────────────────────────────────
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setResizable(true);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("control-button","close-button");
        closeBtn.setOnAction(e -> primaryStage.close());
        closeBtn.setTooltip(makeTooltip("Close App"));
        addHoverGrow(closeBtn);

        Button minBtn = new Button("–");
        minBtn  .getStyleClass().addAll("control-button","minimize-button");
        minBtn.setOnAction(e -> primaryStage.setIconified(true));
        minBtn.setTooltip(makeTooltip("Minimize App"));
        addHoverGrow(minBtn);

        // ─── 1) Session timer ────────────────────────────────────────────────────────
        Label timerLabel = new Label("0:00");
        timerLabel.getStyleClass().add("timer-label");

        Polygon playIcon = new Polygon(
            0.0, 0.0,
            0.0, 16.0,
        12.0, 8.0
        );
        playIcon.getStyleClass().add("play-icon");

        Rectangle stopIcon = new Rectangle(12, 12);
        stopIcon.getStyleClass().add("stop-icon");

        ToggleButton timerToggle = new ToggleButton();
        timerToggle.setGraphic(playIcon);
        timerToggle.getStyleClass().add("control-button");
        timerToggle.setTooltip(makeTooltip("Start / Stop session timer"));
        timerToggle.setCursor(Cursor.HAND);
        addHoverGrow(timerToggle);

        IntegerProperty elapsedSeconds = new SimpleIntegerProperty(0);
        Timeline timer = new Timeline(
        new KeyFrame(Duration.seconds(1), e -> elapsedSeconds.set(elapsedSeconds.get() + 1))
        );
        timer.setCycleCount(Timeline.INDEFINITE);

        timerLabel.textProperty().bind(Bindings.createStringBinding(() -> {
        int tot = elapsedSeconds.get();
        return String.format("%01d:%02d", tot/60, tot%60);
        }, elapsedSeconds));

        timerToggle.selectedProperty().addListener((obs, was, isNow) -> {
        if (isNow) {
            timerToggle.setGraphic(stopIcon);
            timer.play();
        } else {
            timerToggle.setGraphic(playIcon);
            timer.stop();
        }
        });

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

        Slider scrollSlider = new Slider(0, 1, 0);
        scrollSlider.setOrientation(Orientation.VERTICAL);
        scrollSlider.getStyleClass().add("scroll-slider");
        scrollSlider.setPrefHeight(250);
        scrollSlider.setTooltip(makeTooltip("Scroll Script"));
        scrollSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            scroll.setVvalue(1.0 - newVal.doubleValue());
        });
        
        scroll.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            scrollSlider.setValue(1.0 - newVal.doubleValue());
        });
        
        scrollSlider.setValue(1.0 - scroll.getVvalue());
        scrollSlider.getStyleClass().add("scroll-slider");

        // ─── 5) Highlighting helper ────────────────────────────────────────────────
        final int[] currentIndex = { 0 };
        Consumer<Integer> highlight = idx -> {
            // 1) reset every word to the base style
            wordNodes.forEach(n -> n.getStyleClass().setAll("teleprompter-word"));
        
            if (idx < wordNodes.size()) {
                // 2) current word
                Text current = wordNodes.get(idx);
                current.getStyleClass().add("teleprompter-highlight");
        
                // 3) “near” words: i–1 and i+1
                if (idx > 0) {
                    Text prev = wordNodes.get(idx - 1);
                    prev.getStyleClass().add("teleprompter-near");
                }
                if (idx + 1 < wordNodes.size()) {
                    Text next = wordNodes.get(idx + 1);
                    next.getStyleClass().add("teleprompter-near");
                }
        
                // 4) scroll‑into‑view exactly as you had it
                Bounds nb = current.getBoundsInParent();
                Bounds cb = flow.getBoundsInLocal();
                Bounds vp = scroll.getViewportBounds();
                double y = nb.getMinY();
                double v = (y + nb.getHeight() / 2 - vp.getHeight() / 2)
                           / (cb.getHeight() - vp.getHeight());
                scroll.setVvalue(Math.max(0, Math.min(1, v)));
            }
        };
        highlight.accept(0);

        // allow click‑to‑jump on any word
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
        ToggleButton micBtn = new ToggleButton("🎤");
        micBtn.getStyleClass().add("control-button");
        micBtn.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        micBtn.setPickOnBounds(false);
        micBtn.setTooltip(makeTooltip("Toggle microphone"));
        micBtn.setCursor(Cursor.HAND);
        micBtn.setSelected(false);
        addHoverGrow(micBtn);

        // whenever the toggle flips, start/stop recog and swap emoji
        micBtn.selectedProperty().addListener((obs, wasSel, isSel) -> {
            if (isSel) {
                // switched ON
                micBtn.setText("🛑");
                startSpeechRecognition(spoken -> {
                    String heard = spoken.replaceAll("\\W+$", "").toLowerCase();
                    for (int offset = 1; offset <= 3; offset++) {
                        int idx = currentIndex[0] + offset;
                        if (idx >= wordNodes.size()) break;
                        String expect = wordNodes.get(idx)
                            .getText().trim()
                            .replaceAll("[\\W—]+$","")
                            .toLowerCase();
                        System.out.printf("heard -> %s | expect -> %s (off %d)%n",
                                        heard, expect, offset);
                        if (wordsMatch(heard, expect)) {
                            final int newIdx = idx;
                            Platform.runLater(() -> {
                                currentIndex[0] = newIdx;
                                highlight.accept(newIdx);
                            });
                            break;
                        }
                    }
                });
            } else {
                // switched OFF
                micBtn.setText("🎤");
                stopSpeechRecognition();
            }
        });

        // ─── 7) text-size slider & property ─────────────────────────────────────────────
        DoubleProperty fontSizeProp = new SimpleDoubleProperty(18);

        Slider sizeSlider = new Slider(12, 48, fontSizeProp.get());
        sizeSlider.getStyleClass().add("size-slider");
        sizeSlider.setTooltip(makeTooltip("Text Size"));
        sizeSlider.setPrefWidth(150);
        sizeSlider.setShowTickLabels(true);
        sizeSlider.setShowTickMarks(true);
        sizeSlider.setMajorTickUnit(12);
        sizeSlider.valueProperty().bindBidirectional(fontSizeProp);

        Label sizeLabel = new Label("Text Size:");

        fontSizeProp.addListener((obs, oldVal, newVal) -> {
            String style = "-fx-font-size: " + newVal.intValue() + "px;";
            wordNodes.forEach(t -> t.setStyle(style));
        });

        String initStyle = "-fx-font-size: " + fontSizeProp.get() + "px;";
        wordNodes.forEach(t -> t.setStyle(initStyle));

        HBox sizeBox = new HBox(4, sizeLabel, sizeSlider);
        sizeBox.getStyleClass().add("size-control");

        // ─── 8) Upload Script button ───────────────────────────────────────────────
        Button uploadBtn = new Button("📂");
        uploadBtn.getStyleClass().add("control-button");
        uploadBtn.setTooltip(makeTooltip("Upload a script"));
        uploadBtn.setCursor(Cursor.HAND);
        addHoverGrow(uploadBtn);
        uploadBtn.setOnAction(evt -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open Teleprompter Script");
            chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "Text, Word & PDF",
                "*.txt", "*.doc", "*.docx", "*.pdf"
            )
            );
            File file = chooser.showOpenDialog(primaryStage);
            if (file == null) return;

            String newScript = "";
            String name = file.getName().toLowerCase();
            try {
            if (name.endsWith(".txt")) {
                newScript = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } else if (name.endsWith(".docx")) {
                try (var doc = new XWPFDocument(Files.newInputStream(file.toPath()));
                    var ext = new XWPFWordExtractor(doc)) {
                newScript = ext.getText();
                }
            } else if (name.endsWith(".doc")) {
                try (var fis = Files.newInputStream(file.toPath());
                    var doc = new HWPFDocument(fis);
                    var ext = new WordExtractor(doc)) {
                newScript = ext.getText();
                }
            } else if (name.endsWith(".pdf")) {
                try (var pd = PDDocument.load(file)) {
                var stripper = new PDFTextStripper();
                newScript = stripper.getText(pd);
                }
            }
            } catch (Exception ex) {
            ex.printStackTrace();
            return;
            }

            wordNodes.clear();
            flow.getChildren().clear();

            fontSizeProp.set(sizeSlider.getValue());

            for (String w : newScript.split("[—\\-\\s]+")) {
                Text t = new Text(w + " ");
                t.getStyleClass().add("teleprompter-word");
                t.setCursor(Cursor.HAND);
                final int idx2 = wordNodes.size();
                t.setOnMouseClicked(e2 -> {
                    currentIndex[0] = idx2;
                    highlight.accept(idx2);
                });
                wordNodes.add(t);
            }
            flow.getChildren().setAll(wordNodes);

            currentIndex[0] = 0;
            highlight.accept(0);
        });

        // ─── 9) Theme toggle (one button) ─────────────────────────────────────────────
        BorderPane root = new BorderPane(scroll);
        ToggleButton themeToggle = new ToggleButton("🌞");
        themeToggle.setTooltip(makeTooltip("Toggle Light / Dark theme"));
        themeToggle.setCursor(Cursor.HAND);
        addHoverGrow(themeToggle);
        themeToggle.setOnAction(e -> {
            if (themeToggle.getText().equals("🌞")) {
                themeToggle.setText("🌙");
                root.getStyleClass().remove("dark-theme");
                if (!root.getStyleClass().contains("light-theme"))
                    root.getStyleClass().add("light-theme");
            } else {
                themeToggle.setText("🌞");
                root.getStyleClass().remove("light-theme");
                if (!root.getStyleClass().contains("dark-theme"))
                    root.getStyleClass().add("dark-theme");
            }
        });

        // ─── 10) Top & Bottom control bars ─────────────────────────────────────────────────────
        HBox windowControls = new HBox(4, minBtn, closeBtn);
        windowControls.setAlignment(Pos.TOP_RIGHT);
        windowControls.setPadding(new Insets(8));

        HBox leftBox = new HBox(8, uploadBtn, themeToggle);
        leftBox.setAlignment(Pos.CENTER_LEFT);

        HBox centerBox = new HBox(8, micBtn);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(0, 0, 0, 127));

        HBox rightBox = new HBox(sizeBox);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        BorderPane controls = new BorderPane();
        controls.setLeft(leftBox);
        controls.setCenter(centerBox);
        controls.setRight(rightBox);
        controls.getStyleClass().add("control-bar");
        controls.setPadding(new Insets(8));

        BorderPane.setAlignment(scrollSlider, Pos.CENTER);

        HBox timerBox = new HBox(6, timerToggle, timerLabel);
        timerBox.setAlignment(Pos.CENTER_LEFT);
        timerBox.setPadding(new Insets(8));

        BorderPane topBar = new BorderPane();
        topBar.setLeft(timerBox);
        topBar.setRight(windowControls);
        topBar.getStyleClass().add("control-bar");

        // ─── 11) Root layout ────────────────────────────────────────────────────────────
        root.setTop(topBar);
        root.setBottom(controls);
        root.setCenter(scroll);
        root.setRight(scrollSlider);
        root.getStyleClass().add("dark-theme");
        root.setStyle("-fx-background-color: transparent;");


        // ─── 12) Scene + CSS ──────────────────────────────────────────────────────
        Scene scene = new Scene(root, 800, 300);
        scene.setFill(null);
        scene.getStylesheets().add(getClass().getResource("/teleprompt.css").toExternalForm());

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

        // ─── 13) Load Vosk model once ─────────────────────────────────────────────
        Path tmp = Files.createTempDirectory("vosk-model");
        URI uri = getClass().getResource("/model/vosk-model-small-en-us-0.15").toURI();
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

        // ─── 14) Resize the window ──────────────────────────────────────────────────────
        final double RESIZE_MARGIN = 8;
        final double MIN_WIDTH    = 200;
        final double MIN_HEIGHT   = 100;
        DragData drag = new DragData();
        
        // use filters instead of handlers:
        scene.setOnMouseMoved(e -> {
            double x = e.getSceneX();
            double y = e.getSceneY();
            double w = scene.getWidth();
            double h = scene.getHeight();
        
            // corners first:
            if (x < RESIZE_MARGIN && y < RESIZE_MARGIN) {
                scene.setCursor(Cursor.NW_RESIZE);
            } else if (x < RESIZE_MARGIN && y > h - RESIZE_MARGIN) {
                scene.setCursor(Cursor.SW_RESIZE);
            } else if (x > w - RESIZE_MARGIN && y < RESIZE_MARGIN) {
                scene.setCursor(Cursor.NE_RESIZE);
            } else if (x > w - RESIZE_MARGIN && y > h - RESIZE_MARGIN) {
                scene.setCursor(Cursor.SE_RESIZE);
        
            // then pure edges:
            } else if (x < RESIZE_MARGIN) {
                scene.setCursor(Cursor.W_RESIZE);
            } else if (x > w - RESIZE_MARGIN) {
                scene.setCursor(Cursor.E_RESIZE);
            } else if (y < RESIZE_MARGIN) {
                scene.setCursor(Cursor.N_RESIZE);
            } else if (y > h - RESIZE_MARGIN) {
                scene.setCursor(Cursor.S_RESIZE);
        
            // otherwise back to normal:
            } else {
                scene.setCursor(Cursor.DEFAULT);
            }
        });
        
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node hit = e.getPickResult().getIntersectedNode();
            dragEnabled = !isOverControl(hit);
            if (!dragEnabled) return;
        
            drag.mouseX = e.getScreenX();
            drag.mouseY = e.getScreenY();
            drag.stageX = primaryStage.getX();
            drag.stageY = primaryStage.getY();
            drag.stageW = primaryStage.getWidth();
            drag.stageH = primaryStage.getHeight();
        });
        
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            dragEnabled = false;
            scene.setCursor(Cursor.DEFAULT);
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragEnabled) return;
        
            double dx = e.getScreenX() - drag.mouseX;
            double dy = e.getScreenY() - drag.mouseY;
            Cursor cur = scene.getCursor();
        
            // 1) move
            if (cur == Cursor.DEFAULT) {
                primaryStage.setX(drag.stageX + dx);
                primaryStage.setY(drag.stageY + dy);
                return;
            }
            // 2) horizontal resize
            if (cur == Cursor.E_RESIZE || cur == Cursor.SE_RESIZE || cur == Cursor.NE_RESIZE) {
                double nw = drag.stageW + dx;
                if (nw >= MIN_WIDTH) primaryStage.setWidth(nw);
            }
            if (cur == Cursor.W_RESIZE || cur == Cursor.SW_RESIZE || cur == Cursor.NW_RESIZE) {
                double nw = drag.stageW - dx;
                double nx = drag.stageX + dx;
                if (nw >= MIN_WIDTH) {
                    primaryStage.setX(nx);
                    primaryStage.setWidth(nw);
                }
            }
            // 3) vertical resize
            if (cur == Cursor.S_RESIZE || cur == Cursor.SE_RESIZE || cur == Cursor.SW_RESIZE) {
                double nh = drag.stageH + dy;
                if (nh >= MIN_HEIGHT) primaryStage.setHeight(nh);
            }
            if (cur == Cursor.N_RESIZE || cur == Cursor.NE_RESIZE || cur == Cursor.NW_RESIZE) {
                double nh = drag.stageH - dy;
                double ny = drag.stageY + dy;
                if (nh >= MIN_HEIGHT) {
                    primaryStage.setY(ny);
                    primaryStage.setHeight(nh);
                }
            }
        });

        // ─── 15) Show ──────────────────────────────────────────────────────────────
        primaryStage.setScene(scene);
        primaryStage.setTitle("PromptMe");
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
    private static class DragData {
        double mouseX, mouseY;
        double stageX, stageY;
        double stageW, stageH;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
