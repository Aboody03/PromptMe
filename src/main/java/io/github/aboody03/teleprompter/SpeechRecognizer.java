package io.github.aboody03.teleprompter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import javax.sound.sampled.*;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

public class SpeechRecognizer {
    private Model model;
    private Recognizer recognizer;
    private volatile boolean recognizing = false;
    private TargetDataLine micLine;

    public SpeechRecognizer() throws IOException {
        Path modelRoot = Path.of("model","vosk-model-small-en-us-0.15");
        if (!Files.isDirectory(modelRoot)) {
            throw new IOException("Vosk model folder not found: "+modelRoot.toAbsolutePath());
        }
        model = new Model(modelRoot.toString());
        recognizer = new Recognizer(model,16000.0f);
    }

    public void start(Consumer<String> onText) {
        recognizing = true;
        Thread t = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(16000,16,1,true,false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                micLine = (TargetDataLine) AudioSystem.getLine(info);
                micLine.open(fmt); micLine.start();

                byte[] buf = new byte[1024];
                while (recognizing) {
                    int len = micLine.read(buf,0,buf.length);
                    if (len<0) break;
                    boolean isFinal = recognizer.acceptWaveForm(buf,len);
                    JSONObject o = new JSONObject(isFinal ? recognizer.getResult() : recognizer.getPartialResult());
                    String text = o.optString(isFinal ? "text" : "partial","");
                    if (!text.isBlank()) {
                        for (String w: text.split("\\s+")) onText.accept(w);
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

    public void stop() {
        recognizing = false;
        if (micLine != null) {
            micLine.stop();
            micLine.close();
        }
    }
}
