package io.github.aboody03.teleprompter;

import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public class UIUtils {
    public static Tooltip makeTooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(100));
        tip.setHideDelay(Duration.millis(100));
        tip.setShowDuration(Duration.INDEFINITE);
        return tip;
    }

    public static void addHoverGrow(Node node) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(150), node);
        grow.setToX(1.1);
        grow.setToY(1.1);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(150), node);
        shrink.setToX(1.0);
        shrink.setToY(1.0);
        node.setOnMouseEntered(e -> grow.playFromStart());
        node.setOnMouseExited(e -> shrink.playFromStart());
    }

    public static boolean isOverControl(Node node) {
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
}
