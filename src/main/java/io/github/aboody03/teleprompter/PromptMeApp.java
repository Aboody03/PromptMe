package io.github.aboody03.teleprompter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class PromptMeApp extends Application {
    private SpeechRecognizer speechRec;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load speech model
        speechRec = new SpeechRecognizer();

        // Window setup
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setResizable(true);

        // Close & Minimize
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("control-button","close-button");
        closeBtn.setOnAction(e -> primaryStage.close());
        closeBtn.setTooltip(UIUtils.makeTooltip("Close App"));
        UIUtils.addHoverGrow(closeBtn);

        Button minBtn = new Button("–");
        minBtn.getStyleClass().addAll("control-button","minimize-button");
        minBtn.setOnAction(e -> primaryStage.setIconified(true));
        minBtn.setTooltip(UIUtils.makeTooltip("Minimize App"));
        UIUtils.addHoverGrow(minBtn);

        // Session timer
        Label timerLabel = new Label("0:00");
        timerLabel.getStyleClass().add("timer-label");
        Polygon playIcon = new Polygon(0.0,0.0, 0.0,16.0, 12.0,8.0);
        playIcon.getStyleClass().add("play-icon");
        Rectangle stopIcon = new Rectangle(12,12);
        stopIcon.getStyleClass().add("stop-icon");
        ToggleButton timerToggle = new ToggleButton();
        timerToggle.setGraphic(playIcon);
        timerToggle.getStyleClass().add("control-button");
        timerToggle.setTooltip(UIUtils.makeTooltip("Start / Stop session timer"));
        timerToggle.setCursor(Cursor.HAND);
        UIUtils.addHoverGrow(timerToggle);

        IntegerProperty elapsedSeconds = new SimpleIntegerProperty(0);
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1),
            e -> elapsedSeconds.set(elapsedSeconds.get()+1)));
        timer.setCycleCount(Timeline.INDEFINITE);
        timerLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int tot = elapsedSeconds.get();
            return String.format("%01d:%02d", tot/60, tot%60);
        }, elapsedSeconds));
        timerToggle.selectedProperty().addListener((obs,was,is) -> {
            if (is) { timerToggle.setGraphic(stopIcon); timer.play(); }
            else  { timerToggle.setGraphic(playIcon); timer.stop(); }
        });

        // Load default script
        String script;
        try (InputStream in = getClass().getResourceAsStream("/script.txt")) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Word nodes & TextFlow
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

        // Scroll pane + slider
        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");

        Slider scrollSlider = new Slider(0,1,0);
        scrollSlider.setOrientation(Orientation.VERTICAL);
        scrollSlider.getStyleClass().add("scroll-slider");
        scrollSlider.setPrefHeight(250);
        scrollSlider.setTooltip(UIUtils.makeTooltip("Scroll Script"));
        scrollSlider.valueProperty().addListener((obs,ov,nv) ->
            scroll.setVvalue(1.0-nv.doubleValue()));
        scroll.vvalueProperty().addListener((obs,ov,nv) ->
            scrollSlider.setValue(1.0-nv.doubleValue()));

        // Highlighting helper
        final int[] currentIndex = {0};
        Consumer<Integer> highlight = idx -> {
            wordNodes.forEach(n -> n.getStyleClass().setAll("teleprompter-word"));
            if (idx < wordNodes.size()) {
                Text curr = wordNodes.get(idx);
                curr.getStyleClass().add("teleprompter-highlight");
                if (idx>0) wordNodes.get(idx-1).getStyleClass().add("teleprompter-near");
                if (idx+1<wordNodes.size()) wordNodes.get(idx+1).getStyleClass().add("teleprompter-near");
                Bounds nb = curr.getBoundsInParent(), cb = flow.getBoundsInLocal(), vp = scroll.getViewportBounds();
                double y = nb.getMinY();
                double v = (y + nb.getHeight()/2 - vp.getHeight()/2)/(cb.getHeight()-vp.getHeight());
                scroll.setVvalue(Math.max(0, Math.min(1,v)));
            }
        };
        highlight.accept(0);
        for (int i=0; i<wordNodes.size(); i++) {
            final int idx = i;
            wordNodes.get(i).setCursor(Cursor.HAND);
            wordNodes.get(i).setOnMouseClicked(e -> {
                currentIndex[0]=idx; highlight.accept(idx);
            });
        }

        // File loading
        DoubleProperty fontSizeProp = new SimpleDoubleProperty(18);
        Consumer<File> loadFile = file -> {
            String newScript = "";
            String name = file.getName().toLowerCase();
            try {
                if (name.endsWith(".txt")) {
                    newScript = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                } else if (name.endsWith(".docx")) {
                    try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(Files.newInputStream(file.toPath()));
                         var ext = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
                        newScript = ext.getText();
                    }
                } else if (name.endsWith(".doc")) {
                    try (var fis = Files.newInputStream(file.toPath());
                         var doc = new org.apache.poi.hwpf.HWPFDocument(fis);
                         var ext = new org.apache.poi.hwpf.extractor.WordExtractor(doc)) {
                        newScript = ext.getText();
                    }
                } else if (name.endsWith(".pdf")) {
                    try (var pd = org.apache.pdfbox.pdmodel.PDDocument.load(file)) {
                        newScript = new org.apache.pdfbox.text.PDFTextStripper().getText(pd);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                return;
            }
            wordNodes.clear();
            flow.getChildren().clear();
            for (String w : newScript.split("[—\\-\\s]+")) {
                Text t = new Text(w + " ");
                t.getStyleClass().add("teleprompter-word");
                t.styleProperty().bind(
                Bindings.concat("-fx-font-size: ", fontSizeProp.asString("%.0f"), "px;")
                );
                final int idx = wordNodes.size();
                t.setCursor(Cursor.HAND);
                t.setOnMouseClicked(e -> {
                    currentIndex[0]=idx; highlight.accept(idx);
                });
                wordNodes.add(t);
            }
            flow.getChildren().setAll(wordNodes);
            currentIndex[0]=0; highlight.accept(0);
        };

        // Mic toggle
        ToggleButton micBtn = new ToggleButton("🎤");
        micBtn.setTooltip(UIUtils.makeTooltip("Start/stop speech recognition"));
        micBtn.getStyleClass().add("control-button");
        micBtn.setCursor(Cursor.HAND);
        UIUtils.addHoverGrow(micBtn);
        micBtn.selectedProperty().addListener((obs,ow,nw) -> {
            if (nw) {
                micBtn.setText("🛑");
                speechRec.start(spoken -> {
                    String heard = spoken.replaceAll("\\W+$","").toLowerCase();
                    for (int off=1; off<=3; off++) {
                        int idx = currentIndex[0]+off;
                        if (idx>=wordNodes.size()) break;
                        String expect = wordNodes.get(idx).getText().trim()
                                         .replaceAll("[\\W—]+$","").toLowerCase();
                        if (HomophoneUtils.wordsMatch(heard,expect)) {
                            final int newIdx = idx;
                            Platform.runLater(() -> {
                                currentIndex[0]=newIdx; highlight.accept(newIdx);
                            });
                            break;
                        }
                    }
                });
            } else {
                micBtn.setText("🎤");
                speechRec.stop();
            }
        });

        // Font size slider
        Slider sizeSlider = new Slider(12,48,fontSizeProp.get());
        sizeSlider.setTooltip(UIUtils.makeTooltip("Adjust text size"));
        sizeSlider.getStyleClass().add("size-slider");
        sizeSlider.setShowTickLabels(true);
        sizeSlider.setShowTickMarks(true);
        sizeSlider.setMajorTickUnit(12);
        sizeSlider.valueProperty().bindBidirectional(fontSizeProp);

        Label sizeLabel = new Label("Text Size:");
        HBox sizeBox = new HBox(4, sizeLabel, sizeSlider);
        sizeBox.getStyleClass().add("size-control");

        wordNodes.forEach(t ->
            t.styleProperty().bind(
            Bindings.concat("-fx-font-size: ", fontSizeProp.asString("%.0f"), "px;")
            )
        );

        // Upload button
        Button uploadBtn = new Button("📂");
        uploadBtn.getStyleClass().add("control-button");
        uploadBtn.setCursor(Cursor.HAND);
        uploadBtn.setTooltip(UIUtils.makeTooltip("Upload a script"));
        UIUtils.addHoverGrow(uploadBtn);
        uploadBtn.setOnAction(evt -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open Teleprompter Script");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text, Word & PDF","*.txt","*.doc","*.docx","*.pdf")
            );
            File file = chooser.showOpenDialog(primaryStage);
            if (file != null) loadFile.accept(file);
        });

        // Theme toggle
        ToggleButton themeToggle = new ToggleButton("🌞");
        themeToggle.setTooltip(UIUtils.makeTooltip("Toggle Light / Dark theme"));
        themeToggle.setCursor(Cursor.HAND);
        UIUtils.addHoverGrow(themeToggle);

        // Layout
        BorderPane root = new BorderPane(scroll);
        themeToggle.setOnAction(e -> {
            if (themeToggle.getText().equals("🌞")) {
                themeToggle.setText("🌙");
                root.getStyleClass().remove("dark-theme");
                root.getStyleClass().add("light-theme");
            } else {
                themeToggle.setText("🌞");
                root.getStyleClass().remove("light-theme");
                root.getStyleClass().add("dark-theme");
            }
        });

        HBox windowControls = new HBox(4, minBtn, closeBtn);
        windowControls.setAlignment(Pos.TOP_RIGHT);
        windowControls.setPadding(new Insets(8));

        HBox leftBox   = new HBox(8, uploadBtn, themeToggle);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        HBox centerBox = new HBox(8, micBtn);
        centerBox.setAlignment(Pos.CENTER); centerBox.setPadding(new Insets(0,0,0,127));
        HBox rightBox  = new HBox(sizeBox);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        BorderPane controls = new BorderPane();
        controls.setLeft(leftBox);
        controls.setCenter(centerBox);
        controls.setRight(rightBox);
        controls.getStyleClass().add("control-bar");
        controls.setPadding(new Insets(8));

        HBox timerBox = new HBox(6, timerToggle, timerLabel);
        timerBox.setAlignment(Pos.CENTER_LEFT);
        timerBox.setPadding(new Insets(8));

        BorderPane topBar = new BorderPane();
        topBar.setLeft(timerBox);
        topBar.setRight(windowControls);
        topBar.getStyleClass().add("control-bar");

        root.setTop(topBar);
        root.setBottom(controls);
        root.setCenter(scroll);
        root.setRight(scrollSlider);
        root.setStyle("-fx-background-color: transparent;");
        root.getStyleClass().add("dark-theme");

        // Scene + drag‑drop + resizing
        Scene scene = new Scene(root,800,300);
        scene.setFill(null);
        scene.getStylesheets().add(getClass().getResource("/teleprompt.css").toExternalForm());

        scene.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        scene.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                loadFile.accept(db.getFiles().get(0));
                success = true;
            }
            e.setDropCompleted(success);
            e.consume();
        });

        scene.setOnKeyPressed(evt -> {
            if (evt.getCode() == KeyCode.SPACE) {
                int next = currentIndex[0]+1;
                if (next < wordNodes.size()) {
                    currentIndex[0]=next;
                    highlight.accept(next);
                }
            }
        });

        ResizeHandler.install(scene, primaryStage);

        primaryStage.setScene(scene);
        primaryStage.setTitle("PromptMe");
        primaryStage.show();
    }
}
