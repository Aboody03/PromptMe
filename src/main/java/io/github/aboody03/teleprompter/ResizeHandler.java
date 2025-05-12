package io.github.aboody03.teleprompter;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class ResizeHandler {
    private static final double MARGIN = 8;
    private static final double MIN_W = 200;
    private static final double MIN_H = 100;

    public static void install(Scene scene, Stage stage) {
        DragData drag = new DragData();

        // change cursor on edges/corners
        scene.setOnMouseMoved(e -> {
            double x = e.getSceneX(), y = e.getSceneY();
            double w = scene.getWidth(), h = scene.getHeight();

            if (x < MARGIN && y < MARGIN)           scene.setCursor(Cursor.NW_RESIZE);
            else if (x < MARGIN && y > h - MARGIN)  scene.setCursor(Cursor.SW_RESIZE);
            else if (x > w - MARGIN && y < MARGIN)  scene.setCursor(Cursor.NE_RESIZE);
            else if (x > w - MARGIN && y > h - MARGIN) scene.setCursor(Cursor.SE_RESIZE);
            else if (x < MARGIN)                    scene.setCursor(Cursor.W_RESIZE);
            else if (x > w - MARGIN)                scene.setCursor(Cursor.E_RESIZE);
            else if (y < MARGIN)                    scene.setCursor(Cursor.N_RESIZE);
            else if (y > h - MARGIN)                scene.setCursor(Cursor.S_RESIZE);
            else                                    scene.setCursor(Cursor.DEFAULT);
        });

        // record start of drag only if not over a control
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            boolean overControl = UIUtils.isOverControl(e.getPickResult().getIntersectedNode());
            drag.dragEnabled = !overControl;
            if (drag.dragEnabled) {
                drag.mouseX = e.getScreenX();
                drag.mouseY = e.getScreenY();
                drag.stageX = stage.getX();
                drag.stageY = stage.getY();
                drag.stageW = stage.getWidth();
                drag.stageH = stage.getHeight();
            }
        });

        // only move/resize if dragEnabled
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!drag.dragEnabled) return;

            Cursor c = scene.getCursor();
            double dx = e.getScreenX() - drag.mouseX;
            double dy = e.getScreenY() - drag.mouseY;

            // 1) move
            if (c == Cursor.DEFAULT) {
                stage.setX(drag.stageX + dx);
                stage.setY(drag.stageY + dy);

            // 2) horizontal resize
            } else {
                if (c == Cursor.E_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.NE_RESIZE) {
                    double nw = drag.stageW + dx;
                    if (nw >= MIN_W) stage.setWidth(nw);
                }
                if (c == Cursor.W_RESIZE || c == Cursor.SW_RESIZE || c == Cursor.NW_RESIZE) {
                    double nw = drag.stageW - dx, nx = drag.stageX + dx;
                    if (nw >= MIN_W) {
                        stage.setX(nx);
                        stage.setWidth(nw);
                    }
                }
                // 3) vertical resize
                if (c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE) {
                    double nh = drag.stageH + dy;
                    if (nh >= MIN_H) stage.setHeight(nh);
                }
                if (c == Cursor.N_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.NW_RESIZE) {
                    double nh = drag.stageH - dy, ny = drag.stageY + dy;
                    if (nh >= MIN_H) {
                        stage.setY(ny);
                        stage.setHeight(nh);
                    }
                }
            }
        });

        // reset cursor after drag
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e ->
            scene.setCursor(Cursor.DEFAULT)
        );
    }
}
